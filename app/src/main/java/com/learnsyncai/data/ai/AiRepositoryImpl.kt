package com.learnsyncai.data.ai

import com.learnsyncai.domain.model.GeneratedFlashcard
import com.learnsyncai.domain.model.GeneratedQuizQuestion
import com.learnsyncai.domain.model.StudyGenerationResult
import com.learnsyncai.domain.model.UserPreferences
import com.learnsyncai.domain.repository.AiRepository
import com.learnsyncai.domain.usecase.QuizValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class AiConfig(
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai",
    val apiKey: String = "",
    val modelName: String = "gemini-2.5-flash",
    // true = modèle local (Gemma via MediaPipe) : baseUrl contient alors le chemin du fichier
    val isLocal: Boolean = false
)

class AiRepositoryImpl(
    private val openAiClient: OpenAiCompatibleClient = OpenAiCompatibleClient(),
    private val localLlmClient: LocalLlmClient? = null,
    private val configProvider: (suspend () -> AiConfig)? = null,
    private val preferencesProvider: (suspend () -> UserPreferences)? = null
) : AiRepository {

    override suspend fun generateStudyMaterial(
        courseTitle: String,
        courseText: String,
        onProgress: (String) -> Unit
    ): Result<StudyGenerationResult> = withContext(Dispatchers.IO) {
        try {
            val trimmedText = courseText.trim()
            if (trimmedText.isBlank() || trimmedText.length < 30) {
                return@withContext Result.failure(
                    IllegalArgumentException("Le document contient trop peu de texte pour générer du matériel pédagogique.")
                )
            }

            val config = configProvider?.invoke() ?: AiConfig()
            val prefs = preferencesProvider?.invoke() ?: UserPreferences.DEFAULT

            // Contexte local généreux (32k tokens) : sections proches du cloud.
            val chunkSize = if (config.isLocal) 20000 else 35000
            val chunks = splitIntoChunks(trimmedText, chunkSize)
            val numChunks = chunks.size

            if (numChunks == 1) {
                onProgress("Génération de la synthèse et des notions clés...")
                val summaryResult = executeWithRetry(maxAttempts = 2) {
                    generateSummarySection(config, courseTitle, chunks[0], true, prefs, 0, 1)
                }

                onProgress("Génération des flashcards et QCM...")
                val practiceResult = executeWithRetry(maxAttempts = 2) {
                    generatePracticeSection(config, courseTitle, chunks[0], true, prefs, 0, 1)
                }

                val validQuiz = QuizValidator.filterValidQuestions(practiceResult.second)
                val finalResult = StudyGenerationResult(
                    summary = summaryResult.first,
                    keyPoints = summaryResult.second,
                    flashcards = practiceResult.first,
                    quizQuestions = validQuiz,
                    mnemonicTips = summaryResult.third
                )
                return@withContext Result.success(finalResult)
            } else {
                onProgress("Analyse accélérée de $numChunks sections du document...")
                // 2 requêtes IA en parallèle max (respecte les rate limits tout en
                // réduisant fortement le temps de génération sur gros documents).
                val semaphore = Semaphore(2)
                val chunkResults = coroutineScope {
                    chunks.mapIndexed { index, chunk ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val summaryJob = async(Dispatchers.IO) {
                                    executeWithRetry(maxAttempts = 2) {
                                        generateSummarySection(
                                            config = config,
                                            courseTitle = "$courseTitle (Partie ${index + 1}/$numChunks)",
                                            courseText = chunk,
                                            isFullDoc = false,
                                            prefs = prefs,
                                            chunkIndex = index,
                                            totalChunks = numChunks
                                        )
                                    }
                                }
                                val practiceJob = async(Dispatchers.IO) {
                                    executeWithRetry(maxAttempts = 2) {
                                        generatePracticeSection(
                                            config = config,
                                            courseTitle = "$courseTitle (Partie ${index + 1}/$numChunks)",
                                            courseText = chunk,
                                            isFullDoc = false,
                                            prefs = prefs,
                                            chunkIndex = index,
                                            totalChunks = numChunks
                                        )
                                    }
                                }
                                val sumRes = summaryJob.await()
                                val pracRes = practiceJob.await()
                                Pair(sumRes, pracRes)
                            }
                        }
                    }.awaitAll()
                }

                val allFlashcards = mutableListOf<GeneratedFlashcard>()
                val allQuizQuestions = mutableListOf<GeneratedQuizQuestion>()
                val allKeyPoints = mutableListOf<String>()
                val allMnemonicTips = mutableListOf<String>()
                val chunkSummaries = mutableListOf<String>()

                chunkResults.forEachIndexed { idx, (sumRes, pracRes) ->
                    val sectionPrefix = "### Section ${idx + 1}\n"
                    chunkSummaries.add(sectionPrefix + sumRes.first)
                    allKeyPoints.addAll(sumRes.second)
                    allMnemonicTips.addAll(sumRes.third)
                    allFlashcards.addAll(pracRes.first)
                    allQuizQuestions.addAll(pracRes.second)
                }

                onProgress("Finalisation du matériel pédagogique...")
                val combinedSummary = chunkSummaries.filter { it.isNotBlank() }.joinToString("\n\n")
                val distinctKeyPoints = allKeyPoints.distinctBy { it.trim().lowercase() }
                val distinctMnemonicTips = allMnemonicTips.distinctBy { it.trim().lowercase() }
                val distinctFlashcards = deduplicateFlashcards(allFlashcards)
                val distinctValidQuizQuestions = deduplicateAndValidateQuiz(allQuizQuestions)

                return@withContext Result.success(
                    StudyGenerationResult(
                        summary = combinedSummary,
                        keyPoints = distinctKeyPoints,
                        flashcards = distinctFlashcards,
                        quizQuestions = distinctValidQuizQuestions,
                        mnemonicTips = distinctMnemonicTips
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(mapUserFacingException(e))
        }
    }

    private suspend fun generateSummarySection(
        config: AiConfig,
        courseTitle: String,
        courseText: String,
        isFullDoc: Boolean,
        prefs: UserPreferences,
        chunkIndex: Int,
        totalChunks: Int
    ): Triple<String, List<String>, List<String>> {
        // Mode "auto" : aucun nombre imposé — l'IA génère autant que nécessaire.
        val mnemonicCount = if (prefs.mnemonicTipsMode == "custom") {
            distributeCount(prefs.mnemonicTipsCustomCount, chunkIndex, totalChunks)
        } else {
            null
        }
        val mnemonicRule = if (mnemonicCount != null) {
            "Génère exactement $mnemonicCount astuces mnémotechniques concrètes."
        } else {
            "Génère autant d'astuces mnémotechniques concrètes que le contenu s'y prête, sans limite supérieure (au moins une)."
        }

        val prompt = """
            Tu es un ingénieur pédagogique et un professeur universitaire.
            Analyse le texte de cours ci-dessous intitulé "$courseTitle" et génère la section synthétique en français.

            Format JSON STRICT (sans texte introductif ni markdown) :
            {
              "summary": "Résumé structuré, détaillé et approfondi du cours (longueur idéale 800 à 1200 mots si le contenu le permet, structuré en paragraphes clairs)",
              "keyPoints": [
                "Point clé essentiel 1",
                "Point clé essentiel 2"
              ],
              "mnemonicTips": [
                "Astuce mnémotechnique concrète"
              ]
            }

            Règles strictes :
            1. Base-toi uniquement sur le cours fourni.
            2. Identifie tous les concepts clés et n'omets aucune information importante (pas de plafond numérique pour les points clés).
            3. Rédige un résumé riche, détaillé et complet.
            4. $mnemonicRule
            5. Réponds UNIQUEMENT en JSON valide.

            TEXTE DU COURS :
            $courseText
        """.trimIndent()

        val rawText = chatCompletion(config, prompt, temperature = 0.2)

        return parseSummarySection(rawText)
    }

    private suspend fun generatePracticeSection(
        config: AiConfig,
        courseTitle: String,
        courseText: String,
        isFullDoc: Boolean,
        prefs: UserPreferences,
        chunkIndex: Int,
        totalChunks: Int
    ): Pair<List<GeneratedFlashcard>, List<GeneratedQuizQuestion>> {
        // Mode "auto" : aucun nombre imposé — l'IA génère autant que nécessaire
        // pour couvrir tout le contenu, sans plafond.
        val flashcardsCount = if (prefs.flashcardsMode == "custom") {
            distributeCount(prefs.flashcardsCustomCount, chunkIndex, totalChunks)
        } else {
            null
        }

        val quizCount = if (prefs.quizMode == "custom") {
            distributeCount(prefs.quizCustomCount, chunkIndex, totalChunks)
        } else {
            null
        }

        val flashcardsRule = if (flashcardsCount != null) {
            "Génère exactement $flashcardsCount flashcards précises (réponse concise en 2 phrases max)."
        } else {
            "Génère autant de flashcards que nécessaire pour couvrir TOUS les concepts importants du texte, sans limite supérieure : chaque concept, définition, formule ou fait clé doit donner lieu à au moins une flashcard (réponse concise en 2 phrases max)."
        }

        val quizRule = if (quizCount != null) {
            "Génère exactement $quizCount QCM comportant exactement 4 options distinctes et une bonne réponse identique à l'une des 4 options (explication en 1 phrase max)."
        } else {
            "Génère autant de QCM que nécessaire pour couvrir tous les points testables du texte, sans limite supérieure (chacun avec exactement 4 options distinctes, une bonne réponse identique à l'une des options, explication en 1 phrase max)."
        }

        val prompt = """
            Tu es un ingénieur pédagogique et un professeur universitaire.
            Analyse le texte de cours ci-dessous intitulé "$courseTitle" et génère les exercices (flashcards et QCM) en français.
            
            Format JSON STRICT (sans texte introductif ni markdown) :
            {
              "flashcards": [
                {
                  "question": "Question atomique et précise",
                  "answer": "Réponse concise et exacte (maximum 2 phrases)",
                  "explanation": "Brève explication (maximum 1 phrase)"
                }
              ],
              "quizQuestions": [
                {
                  "question": "Question de QCM",
                  "options": ["Option 1", "Option 2", "Option 3", "Option 4"],
                  "correctAnswer": "Option 1",
                  "explanation": "Pourquoi cette réponse est correcte (maximum 1 phrase)"
                }
              ]
            }

            Règles strictes :
            1. Base-toi uniquement sur le cours fourni.
            2. $flashcardsRule
            3. $quizRule
            4. Réponds UNIQUEMENT en JSON valide.

            TEXTE DU COURS :
            $courseText
        """.trimIndent()

        val rawText = chatCompletion(config, prompt, temperature = 0.2)

        val parsed = parsePracticeSection(rawText)

        return completeMissingPractice(config, courseText, flashcardsCount, quizCount, parsed)
    }

    /**
     * Les petits modèles ignorent parfois "génère exactement N". Si une cible
     * chiffrée existe (mode custom) et que la réponse est en dessous, on lance
     * UNE passe complémentaire ciblée (seulement sur le manque), puis on
     * fusionne en dédupliquant par question. En mode auto (cible null), il n'y
     * a pas de manque à combler.
     */
    private suspend fun completeMissingPractice(
        config: AiConfig,
        courseText: String,
        flashcardsTarget: Int?,
        quizTarget: Int?,
        parsed: Pair<List<GeneratedFlashcard>, List<GeneratedQuizQuestion>>
    ): Pair<List<GeneratedFlashcard>, List<GeneratedQuizQuestion>> {
        var cards = parsed.first
        var quiz = parsed.second
        if (flashcardsTarget == null && quizTarget == null) return parsed
        val missingCards = flashcardsTarget?.let { it - cards.size } ?: 0
        val missingQuiz = quizTarget?.let { it - quiz.size } ?: 0
        if (missingCards <= 0 && missingQuiz <= 0) return parsed
        if (courseText.length < 200) return parsed

        val sb = StringBuilder()
        if (missingCards > 0) sb.append("- exactement $missingCards flashcards supplémentaires\n")
        if (missingQuiz > 0) sb.append("- exactement $missingQuiz QCM supplémentaires\n")

        val supplementPrompt = """
            Tu es un ingénieur pédagogique.
            Analyse le texte de cours ci-dessous et génère UNIQUEMENT du contenu NOUVEAU, différent de ce qui a déjà été produit :
            $sb
            Format JSON STRICT :
            {
              "flashcards": [ { "question": "...", "answer": "...", "explanation": "..." } ],
              "quizQuestions": [ { "question": "...", "options": ["Option 1", "Option 2", "Option 3", "Option 4"], "correctAnswer": "Option 1", "explanation": "..." } ]
            }
            Règles strictes :
            1. Base-toi uniquement sur le cours fourni.
            2. Les QCM doivent avoir exactement 4 options distinctes.
            3. Réponds UNIQUEMENT en JSON valide.

            TEXTE DU COURS :
            $courseText
        """.trimIndent()

        try {
            val rawSupplement = chatCompletion(config, supplementPrompt, temperature = 0.4)
            val supplement = parsePracticeSection(rawSupplement)

            val existingCardQuestions = cards.map { it.question.trim().lowercase() }.toMutableSet()
            val newCards = supplement.first.filter { existingCardQuestions.add(it.question.trim().lowercase()) }
            if (missingCards > 0) {
                cards = cards + newCards
                flashcardsTarget?.let { target -> cards = cards.take(target) }
            }

            val existingQuizQuestions = quiz.map { it.question.trim().lowercase() }.toMutableSet()
            val newQuiz = supplement.second.filter { existingQuizQuestions.add(it.question.trim().lowercase()) }
            if (missingQuiz > 0) {
                quiz = quiz + newQuiz
                quizTarget?.let { target -> quiz = quiz.take(target) }
            }
        } catch (_: Exception) {
            // La passe complémentaire est un bonus : on garde le résultat initial.
        }

        return Pair(cards, quiz)
    }

    private fun distributeCount(total: Int, index: Int, totalChunks: Int): Int {
        if (totalChunks <= 1) return total
        val base = total / totalChunks
        val rem = total % totalChunks
        return base + if (index < rem) 1 else 0
    }

    internal suspend fun <T> executeWithRetry(
        maxAttempts: Int = 2,
        initialDelayMs: Long = 500L,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null

        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                val isTransient = isTransientError(e)

                if (attempt < maxAttempts && isTransient) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(3000L)
                } else {
                    break
                }
            }
        }

        throw lastException ?: Exception("Erreur inconnue lors de l'appel IA")
    }

    internal fun isTransientError(throwable: Throwable): Boolean {
        val msg = throwable.message?.lowercase() ?: ""
        return throwable is IOException ||
                msg.contains("429") ||
                msg.contains("quota") ||
                msg.contains("resource_exhausted") ||
                msg.contains("unavailable") ||
                msg.contains("timeout") ||
                msg.contains("deadline") ||
                msg.contains("503") ||
                msg.contains("502") ||
                msg.contains("500") ||
                // JSON invalide : un 2e essai (échantillonnage différent) réussit
                // souvent, surtout en local où il ne coûte rien.
                msg.contains("format json invalide")
    }

    internal fun mapUserFacingException(throwable: Throwable): Throwable {
        val msg = throwable.message?.lowercase() ?: ""
        return when {
            msg.contains("429") || msg.contains("quota") || msg.contains("resource_exhausted") ->
                IllegalStateException("Quota d'IA temporairement atteint. Veuillez patienter une minute avant de réessayer.", throwable)
            msg.contains("401") || msg.contains("unauthorized") || msg.contains("invalid api key") || msg.contains("api_key") ->
                IllegalStateException("Clé API invalide ou non configurée. Rendez-vous dans votre Profil pour vérifier votre clé.", throwable)
            throwable is IOException || msg.contains("network") || msg.contains("timeout") || msg.contains("unavailable") || msg.contains("connect") ->
                IllegalStateException("Problème de connexion avec le service IA. Vérifiez votre accès Internet.", throwable)
            msg.contains("json") || msg.contains("parsing") ->
                IllegalStateException(
                    "La réponse de l'IA n'a pas pu être structurée. Détail : ${throwable.message ?: "non disponible"}",
                    throwable
                )
            else -> throwable
        }
    }

    private fun deduplicateFlashcards(cards: List<GeneratedFlashcard>): List<GeneratedFlashcard> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<GeneratedFlashcard>()
        for (card in cards) {
            val key = card.question.trim().lowercase()
            if (key.length > 3 && seen.add(key)) {
                result.add(card)
            }
        }
        return result
    }

    private fun deduplicateAndValidateQuiz(questions: List<GeneratedQuizQuestion>): List<GeneratedQuizQuestion> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<GeneratedQuizQuestion>()
        for (q in questions) {
            val key = q.question.trim().lowercase()
            if (key.length > 3 && seen.add(key)) {
                val validation = QuizValidator.validateQuestion(q)
                if (validation.isValid) {
                    result.add(q)
                }
            }
        }
        return result
    }

    /**
     * Point d'entrée unique des appels IA : route vers l'API cloud
     * OpenAI-compatible ou vers le moteur local Gemma (MediaPipe).
     */
    private suspend fun chatCompletion(config: AiConfig, prompt: String, temperature: Double): String {
        return if (config.isLocal) {
            val client = localLlmClient
                ?: throw IllegalStateException("Moteur local indisponible.")
            client.generate(config.baseUrl, prompt)
        } else {
            openAiClient.generateChatCompletion(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                modelName = config.modelName,
                prompt = prompt,
                temperature = temperature,
                // 256k tokens de sortie max : les serveurs qui plafonnent en
                // dessous (ex. Gemini 2.5 = 65k) ignorent ou serrent la valeur,
                // donc ça reste sans risque pour les modèles plus petits.
                maxTokens = 262144
            )
        }
    }

    private fun parseJsonObject(rawText: String): JSONObject {
        val extracted = extractJson(rawText)
        try {
            return JSONObject(extracted)
        } catch (_: Exception) {
        }
        // Réparation best-effort : les petites sorties tronquées (limite de
        // tokens) deviennent valides en refermant chaîne/objets/tableaux ouverts.
        val repaired = repairJson(extracted)
        if (repaired != null) {
            try {
                return JSONObject(repaired)
            } catch (_: Exception) {
            }
        }
        val preview = rawText.trim().take(200).replace(Regex("\\s+"), " ")
        throw IllegalArgumentException("Format JSON invalide | Réponse reçue: « $preview »")
    }

    /**
     * Referme le JSON tronqué : suit chaînes/échappements, empile les délimiteurs
     * ouverts et ajoute les fermetures manquantes. Renvoie null si rien à réparer.
     */
    private fun repairJson(input: String): String? {
        val text = input.trim()
        if (!text.contains('{') && !text.contains('[')) return null

        val sb = StringBuilder()
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (ch in text) {
            sb.append(ch)
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                !inString && (ch == '{' || ch == '[') -> stack.addLast(if (ch == '{') '}' else ']')
                !inString && (ch == '}' || ch == ']') -> if (stack.isNotEmpty() && stack.last() == ch) stack.removeLast()
            }
        }
        if (stack.isEmpty() && !inString) return null

        var repaired = sb.toString().trimEnd()
        if (inString) repaired += "\""
        if (repaired.endsWith(",")) repaired = repaired.dropLast(1)
        while (stack.isNotEmpty()) repaired += stack.removeLast()
        return repaired
    }

    internal fun parseSummarySection(rawText: String): Triple<String, List<String>, List<String>> {
        val jsonObject = parseJsonObject(rawText)
        val summary = extractSummary(jsonObject)
        if (summary.isBlank()) {
            throw IllegalArgumentException("La réponse de l'IA ne contient pas de résumé valide.")
        }
        val keyPoints = extractKeyPoints(jsonObject)
        val mnemonicTips = extractMnemonicTips(jsonObject)
        return Triple(summary, keyPoints, mnemonicTips)
    }

    internal fun parsePracticeSection(rawText: String): Pair<List<GeneratedFlashcard>, List<GeneratedQuizQuestion>> {
        val jsonObject = parseJsonObject(rawText)
        val flashcards = extractFlashcards(jsonObject)
        val quizQuestions = extractQuizQuestions(jsonObject)
        return Pair(flashcards, quizQuestions)
    }

    internal fun parseJsonResponse(rawText: String): StudyGenerationResult {
        val jsonObject = parseJsonObject(rawText)
        val summary = extractSummary(jsonObject)
        if (summary.isBlank()) {
            throw IllegalArgumentException("La réponse de l'IA ne contient pas de résumé valide.")
        }
        val keyPoints = extractKeyPoints(jsonObject)
        val mnemonicTips = extractMnemonicTips(jsonObject)
        val flashcards = extractFlashcards(jsonObject)
        val quizQuestions = extractQuizQuestions(jsonObject)
        return StudyGenerationResult(summary, keyPoints, flashcards, quizQuestions, mnemonicTips)
    }

    internal fun extractSummary(jsonObject: JSONObject): String {
        return jsonObject.optString("summary", "")
            .ifBlank { jsonObject.optString("resume", "") }
            .ifBlank { jsonObject.optString("résumé", "") }
            .ifBlank { jsonObject.optString("synthese", "") }
            .ifBlank { jsonObject.optString("synthèse", "") }
            .ifBlank { jsonObject.optString("overview", "") }
            .ifBlank { jsonObject.optString("content", "") }
            .trim()
    }

    internal fun extractKeyPoints(jsonObject: JSONObject): List<String> {
        val keyPointsArray = jsonObject.optJSONArray("keyPoints")
            ?: jsonObject.optJSONArray("key_points")
            ?: jsonObject.optJSONArray("pointsCles")
            ?: jsonObject.optJSONArray("points_cles")
            ?: jsonObject.optJSONArray("points_clés")
            ?: jsonObject.optJSONArray("notionsCles")
            ?: jsonObject.optJSONArray("notions_clés")
            ?: jsonObject.optJSONArray("points")
            ?: jsonObject.optJSONArray("key_concepts")
        return keyPointsArray?.toStringList() ?: emptyList()
    }

    internal fun extractMnemonicTips(jsonObject: JSONObject): List<String> {
        val mnemonicArray = jsonObject.optJSONArray("mnemonicTips")
            ?: jsonObject.optJSONArray("mnemonic_tips")
            ?: jsonObject.optJSONArray("astuces")
            ?: jsonObject.optJSONArray("astucesMnemoniques")
            ?: jsonObject.optJSONArray("astuces_mnemoniques")
            ?: jsonObject.optJSONArray("tips")
            ?: jsonObject.optJSONArray("mnemonics")
        return mnemonicArray?.toStringList() ?: emptyList()
    }

    internal fun extractFlashcards(jsonObject: JSONObject): List<GeneratedFlashcard> {
        val flashcardsArray = jsonObject.optJSONArray("flashcards")
            ?: jsonObject.optJSONArray("flash_cards")
            ?: jsonObject.optJSONArray("cards")
            ?: jsonObject.optJSONArray("cartes")
            ?: jsonObject.optJSONArray("flashcard_list")
        val flashcards = mutableListOf<GeneratedFlashcard>()
        if (flashcardsArray != null) {
            for (i in 0 until flashcardsArray.length()) {
                val obj = flashcardsArray.optJSONObject(i) ?: continue
                val question = obj.optString("question", "")
                    .ifBlank { obj.optString("q", "") }
                    .ifBlank { obj.optString("prompt", "") }
                    .ifBlank { obj.optString("front", "") }
                    .trim()
                val answer = obj.optString("answer", "")
                    .ifBlank { obj.optString("a", "") }
                    .ifBlank { obj.optString("reponse", "") }
                    .ifBlank { obj.optString("réponse", "") }
                    .ifBlank { obj.optString("back", "") }
                    .trim()
                val explanation = obj.optString("explanation", "")
                    .ifBlank { obj.optString("explication", "") }
                    .ifBlank { obj.optString("details", "") }
                    .trim()

                if (question.isNotBlank() && answer.isNotBlank()) {
                    flashcards.add(GeneratedFlashcard(question, answer, explanation))
                }
            }
        }
        return flashcards
    }

    internal fun extractQuizQuestions(jsonObject: JSONObject): List<GeneratedQuizQuestion> {
        val quizArray = jsonObject.optJSONArray("quizQuestions")
            ?: jsonObject.optJSONArray("quiz_questions")
            ?: jsonObject.optJSONArray("quiz")
            ?: jsonObject.optJSONArray("qcm")
            ?: jsonObject.optJSONArray("questions")
            ?: jsonObject.optJSONArray("mcq")
        val quizQuestions = mutableListOf<GeneratedQuizQuestion>()
        if (quizArray != null) {
            for (i in 0 until quizArray.length()) {
                val obj = quizArray.optJSONObject(i) ?: continue
                val question = obj.optString("question", "")
                    .ifBlank { obj.optString("q", "") }
                    .ifBlank { obj.optString("prompt", "") }
                    .trim()

                val rawOptions = mutableListOf<String>()
                val optionsJsonArray = obj.optJSONArray("options")
                    ?: obj.optJSONArray("choices")
                    ?: obj.optJSONArray("propositions")
                    ?: obj.optJSONArray("answers")
                    ?: obj.optJSONArray("choix")
                if (optionsJsonArray != null) {
                    rawOptions.addAll(optionsJsonArray.toStringList())
                } else {
                    val optionsObj = obj.optJSONObject("options")
                        ?: obj.optJSONObject("choices")
                        ?: obj.optJSONObject("propositions")
                    if (optionsObj != null) {
                        val keys = listOf("A", "B", "C", "D", "a", "b", "c", "d", "1", "2", "3", "4")
                        for (k in keys) {
                            val v = optionsObj.optString(k, "").trim()
                            if (v.isNotBlank()) rawOptions.add(v)
                        }
                    }
                }

                val rawCorrectAnswer = obj.optString("correctAnswer", "")
                    .ifBlank { obj.optString("correct_answer", "") }
                    .ifBlank { obj.optString("bonneReponse", "") }
                    .ifBlank { obj.optString("bonne_reponse", "") }
                    .ifBlank { obj.optString("reponseCorrecte", "") }
                    .ifBlank { obj.optString("reponse_correcte", "") }
                    .ifBlank { obj.optString("answer", "") }
                    .ifBlank { obj.optString("correct_option", "") }
                    .ifBlank { obj.optString("correct", "") }
                    .trim()

                val explanation = obj.optString("explanation", "")
                    .ifBlank { obj.optString("explication", "") }
                    .ifBlank { obj.optString("rationale", "") }
                    .trim()

                val normalizedQuiz = normalizeQuizQuestion(question, rawOptions, rawCorrectAnswer, explanation)
                if (normalizedQuiz != null) {
                    quizQuestions.add(normalizedQuiz)
                }
            }
        }
        return quizQuestions
    }

    internal fun normalizeQuizQuestion(
        question: String,
        rawOptions: List<String>,
        rawCorrectAnswer: String,
        explanation: String
    ): GeneratedQuizQuestion? {
        if (question.isBlank() || rawOptions.isEmpty() || rawCorrectAnswer.isBlank()) return null

        val prefixRegex = Regex("^(?:[A-Da-d1-4][.)\\-\\s]+|[•\\-*]\\s*)")
        val cleanedOptions = rawOptions.map { it.replace(prefixRegex, "").trim() }.filter { it.isNotBlank() }

        if (cleanedOptions.size < 4) return null

        var normalizedCorrectAnswer = rawCorrectAnswer.replace(prefixRegex, "").trim()

        val letterIndex = when (rawCorrectAnswer.trim().uppercase()) {
            "A", "1" -> 0
            "B", "2" -> 1
            "C", "3" -> 2
            "D", "4" -> 3
            else -> -1
        }
        if (letterIndex in rawOptions.indices) {
            normalizedCorrectAnswer = cleanedOptions.getOrElse(letterIndex) { rawOptions[letterIndex] }
        } else {
            val match = cleanedOptions.find { it.equals(normalizedCorrectAnswer, ignoreCase = true) }
                ?: rawOptions.find { it.equals(rawCorrectAnswer, ignoreCase = true) }?.let { it.replace(prefixRegex, "").trim() }
            if (match != null) {
                normalizedCorrectAnswer = match
            }
        }

        val distinctOptions = cleanedOptions.distinctBy { it.lowercase() }.toMutableList()
        if (!distinctOptions.any { it.equals(normalizedCorrectAnswer, ignoreCase = true) }) {
            distinctOptions.add(0, normalizedCorrectAnswer)
        }

        if (distinctOptions.size < 4) return null

        val finalOptions = if (distinctOptions.size > 4) {
            val correctOpt = distinctOptions.find { it.equals(normalizedCorrectAnswer, ignoreCase = true) } ?: normalizedCorrectAnswer
            val others = distinctOptions.filter { !it.equals(correctOpt, ignoreCase = true) }.take(3)
            listOf(correctOpt) + others
        } else {
            distinctOptions
        }

        val finalCorrectAnswer = finalOptions.find { it.equals(normalizedCorrectAnswer, ignoreCase = true) } ?: normalizedCorrectAnswer

        val candidate = GeneratedQuizQuestion(
            question = question,
            options = finalOptions,
            correctAnswer = finalCorrectAnswer,
            explanation = explanation
        )

        return if (QuizValidator.validateQuestion(candidate).isValid) candidate else null
    }

    internal fun extractJson(rawText: String): String {
        val trimmed = rawText.trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }

        val cleaned = trimmed
            .replace(Regex("^```(?:json|JSON)?\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("```\\s*$", RegexOption.MULTILINE), "")
            .trim()

        val cbFirst = cleaned.indexOf('{')
        val cbLast = cleaned.lastIndexOf('}')
        if (cbFirst != -1 && cbLast != -1 && cbLast > cbFirst) {
            return cleaned.substring(cbFirst, cbLast + 1)
        }

        return cleaned
    }

    private fun splitIntoChunks(text: String, maxChunkSize: Int): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n\n")
        var currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentChunk.length + paragraph.length + 2 > maxChunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            if (paragraph.length > maxChunkSize) {
                val sentences = paragraph.split(". ")
                for (sentence in sentences) {
                    if (currentChunk.length + sentence.length + 2 > maxChunkSize && currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk = StringBuilder()
                    }
                    currentChunk.append(sentence).append(". ")
                }
            } else {
                currentChunk.append(paragraph).append("\n\n")
            }
        }

        if (currentChunk.isNotBlank()) {
            chunks.add(currentChunk.toString().trim())
        }

        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until this.length()) {
            val str = this.optString(i, "").trim()
            if (str.isNotBlank()) {
                list.add(str)
            }
        }
        return list
    }
}
