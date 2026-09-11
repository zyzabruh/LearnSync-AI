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
        language: String,
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
                    generateSummarySection(config, courseTitle, chunks[0], true, prefs, 0, 1, language)
                }

                onProgress("Génération des flashcards et QCM...")
                val practiceResult = executeWithRetry(maxAttempts = 2) {
                    generatePracticeSection(config, courseTitle, chunks[0], true, prefs, 0, 1, language)
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
                                            totalChunks = numChunks,
                                            language = language
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
                                            totalChunks = numChunks,
                                            language = language
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

    /**
     * Génère du contenu SUPPLÉMENTAIRE (flashcards + QCM) : les questions
     * existantes sont envoyées à l'IA comme liste d'exclusion pour éviter les
     * doublons, et le résultat est filtré une seconde fois côté app. Rien
     * n'est supprimé : c'est au ViewModel d'ajouter le résultat à la base.
     */
    override suspend fun generateAdditionalPractice(
        courseTitle: String,
        courseText: String,
        existingFlashcardQuestions: List<String>,
        existingQuizQuestions: List<String>,
        language: String,
        onProgress: (String) -> Unit
    ): Result<Pair<List<GeneratedFlashcard>, List<GeneratedQuizQuestion>>> = withContext(Dispatchers.IO) {
        try {
            val trimmedText = courseText.trim()
            if (trimmedText.isBlank() || trimmedText.length < 30) {
                return@withContext Result.failure(
                    IllegalArgumentException("Le document contient trop peu de texte pour générer du matériel pédagogique.")
                )
            }

            val config = configProvider?.invoke() ?: AiConfig()
            val chunkSize = if (config.isLocal) 20000 else 35000
            val chunks = splitIntoChunks(trimmedText, chunkSize)
            val exclusionBlock = buildExclusionBlock(existingFlashcardQuestions, existingQuizQuestions)

            onProgress(if (chunks.size == 1) "Génération de nouveau contenu..." else "Génération sur ${chunks.size} sections...")

            val semaphore = Semaphore(2)
            val chunkResults = coroutineScope {
                chunks.map { chunk ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            executeWithRetry(maxAttempts = 2) {
                                generateAdditionalPracticeSection(config, courseTitle, chunk, exclusionBlock, language)
                            }
                        }
                    }
                }.awaitAll()
            }

            // Fusion + dédoublonnage interne, puis exclusion des questions déjà en base
            val existingKeys = (existingFlashcardQuestions + existingQuizQuestions)
                .map { it.trim().lowercase() }
                .toSet()

            val allCards = chunkResults.flatMap { it.first }
            val allQuiz = chunkResults.flatMap { it.second }

            val newCards = deduplicateFlashcards(allCards)
                .filter { it.question.trim().lowercase() !in existingKeys }
            val newQuiz = deduplicateAndValidateQuiz(allQuiz)
                .filter { it.question.trim().lowercase() !in existingKeys }

            Result.success(Pair(newCards, newQuiz))
        } catch (t: Throwable) {
            Result.failure(mapUserFacingException(t))
        }
    }

    override suspend fun generateFlashcardsFromExcerpt(
        excerpt: String,
        language: String
    ): Result<List<GeneratedFlashcard>> = withContext(Dispatchers.IO) {
        try {
            val text = excerpt.trim()
            if (text.length < 10) {
                return@withContext Result.failure(
                    IllegalArgumentException("Sélectionnez un passage plus long pour générer des cartes.")
                )
            }
            val config = configProvider?.invoke() ?: AiConfig()
            val prompt = """
                Tu es un ingénieur pédagogique. À partir du court extrait ci-dessous, génère 1 à 3 flashcards (mélange question/réponse et cloze avec {{doubles accolades}} si pertinent).
                ${languageInstruction(language)}

                Format JSON STRICT (sans texte introductif ni markdown) :
                {
                  "flashcards": [
                    {
                      "question": "Question précise, OU phrase à trou avec {{passage à mémoriser}}",
                      "answer": "Réponse concise",
                      "explanation": "Brève explication (1 phrase max)",
                      "source": "Court extrait source (1 phrase)"
                    }
                  ]
                }

                EXTRAIT :
                ${text.take(2000)}
            """.trimIndent()
            val rawText = executeWithRetry(maxAttempts = 2) {
                chatCompletion(config, prompt, temperature = 0.3)
            }
            val cards = parsePracticeSection(rawText).first
                .map { if (it.source.isBlank()) it.copy(source = text.take(200)) else it }
            Result.success(cards)
        } catch (t: Throwable) {
            Result.failure(mapUserFacingException(t))
        }
    }

    override suspend fun tutorAsk(
        courseTitle: String,
        courseContext: String,
        history: List<Pair<String, String>>,
        question: String,
        language: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Contexte centré sur les passages pertinents pour la question
            // (sinon le tuteur ne voyait que le début du document).
            val context = selectRelevantContext(courseContext, question, maxChars = 20000)
            if (context.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Aucun contenu de cours disponible pour le tuteur.")
                )
            }
            val historyBlock = history.takeLast(6).joinToString("\n") { (role, text) ->
                if (role == "user") "Élève : ${text.take(1000)}" else "Tuteur : ${text.take(1500)}"
            }
            val prompt = """
                Tu es un tuteur pédagogique bienveillant et exigeant pour le cours "$courseTitle".
                ${languageInstruction(language)}
                Réponds UNIQUEMENT à partir du contenu du cours ci-dessous. Si la question sort du cours, dis-le et propose ce que le cours permet d'étudier. Réponse concise et structurée.

                CONTENU DU COURS :
                $context

                ${if (historyBlock.isNotBlank()) "ÉCHANGES PRÉCÉDENTS :\n$historyBlock\n" else ""}
                QUESTION DE L'ÉLÈVE : ${question.trim().take(1000)}
            """.trimIndent()
            android.util.Log.d("AiRepo", "tutorAsk: courseTitle=$courseTitle contextLen=${context.length} history=${history.size} question=${question.take(80)} language=$language")
            val config = configProvider?.invoke() ?: AiConfig()
            android.util.Log.d("AiRepo", "tutorAsk config: baseUrl=${config.baseUrl} apiKey=${if (config.apiKey.isNotBlank()) "***" else "EMPTY"} model=${config.modelName} isLocal=${config.isLocal}")
            val answer = executeWithRetry(maxAttempts = 2) {
                chatCompletion(config, prompt, temperature = 0.3, useJsonFormat = false)
            }
            Result.success(answer.trim())
        } catch (t: Throwable) {
            Result.failure(mapUserFacingException(t))
        }
    }

    override suspend fun explainCard(
        question: String,
        answer: String,
        sourceExcerpt: String,
        language: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val config = configProvider?.invoke() ?: AiConfig()
            val prompt = """
                Tu es un tuteur pédagogique. Explique brièvement (3 phrases maximum) pourquoi la réponse ci-dessous répond à la question. Pas d'introduction, pas de conclusion.
                ${languageInstruction(language)}

                QUESTION : ${question.trim().take(500)}

                RÉPONSE ATTENDUE : ${answer.trim().take(800)}
                ${if (sourceExcerpt.isNotBlank()) "CONTEXTE DU COURS : ${sourceExcerpt.trim().take(1000)}" else ""}
            """.trimIndent()
            val explanation = executeWithRetry(maxAttempts = 2) {
                chatCompletion(config, prompt, temperature = 0.3, useJsonFormat = false)
            }
            Result.success(explanation.trim())
        } catch (t: Throwable) {
            Result.failure(mapUserFacingException(t))
        }
    }

    /**
     * Contexte pertinent pour le tuteur : découpe le cours en passages, score
     * chacun par recouvrement de mots-clés avec la question et conserve les
     * meilleurs jusqu'au budget (ordre d'origine préservé). Repli : début du document.
     */
    internal fun selectRelevantContext(courseContext: String, question: String, maxChars: Int = 20000): String {
        val text = courseContext.trim()
        if (text.length <= maxChars) return text
        val keywords = extractKeywords(question)
        if (keywords.isEmpty()) return text.take(maxChars)
        val scored = splitIntoPassages(text, targetSize = 2000).mapIndexedNotNull { index, passage ->
            val hits = keywords.count { it in passage.lowercase() }
            if (hits > 0) Triple(index, hits, passage) else null
        }.sortedWith(compareByDescending<Triple<Int, Int, String>> { it.second }.thenBy { it.first })
        if (scored.isEmpty()) return text.take(maxChars)
        val picked = mutableListOf<Triple<Int, Int, String>>()
        var used = 0
        for (entry in scored) {
            if (used >= maxChars) break
            picked.add(entry)
            used += entry.third.length
        }
        return picked.sortedBy { it.first }.joinToString("\n\n") { it.third }.take(maxChars)
    }

    private fun splitIntoPassages(text: String, targetSize: Int): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val passages = mutableListOf<String>()
        val current = StringBuilder()
        for (p in paragraphs) {
            if (current.length + p.length + 2 > targetSize && current.isNotEmpty()) {
                passages.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(p)
        }
        if (current.isNotEmpty()) passages.add(current.toString())
        return passages.ifEmpty { listOf(text) }
    }

    private fun extractKeywords(question: String): Set<String> {
        return question.lowercase()
            .split(Regex("[^a-zàâäéèêëîïôöùûüçœæ0-9]+"))
            .filter { it.length >= 4 && it !in TUTOR_STOPWORDS }
            .toSet()
    }

    private companion object {
        private val TUTOR_STOPWORDS = setOf(
            "avec", "avoir", "cette", "comme", "comment", "dans", "donne", "dont",
            "elle", "entre", "explique", "expliquer", "faire", "leurs", "mais",
            "nous", "pour", "pourquoi", "quand", "quelle", "quelles", "quels",
            "sont", "tout", "toute", "toutes", "vous", "quoi", "that", "this",
            "with", "what", "when", "which", "votre", "notre", "définition"
        )
    }

    /** Consigne de langue de sortie pour les prompts : "auto" suit la langue du document. */
    private fun languageInstruction(language: String): String = when (language) {
        "auto" -> "CONSIGNE DE LANGUE : détecte automatiquement la langue du document fourni et rédige TOUT le contenu (questions, réponses, options, explications, résumé) dans cette langue."
        else -> "CONSIGNE DE LANGUE : rédige TOUT le contenu (questions, réponses, options, explications, résumé) en ${languageDisplayName(language)}, quelle que soit la langue du document."
    }

    private fun languageDisplayName(code: String) = when (code) {
        "fr" -> "français"
        "en" -> "anglais"
        "es" -> "espagnol"
        "de" -> "allemand"
        "it" -> "italien"
        "pt" -> "portugais"
        "nl" -> "néerlandais"
        "ar" -> "arabe"
        else -> code
    }

    /** Bloc compact des questions existantes, injecté dans le prompt d'exclusion. */
    private fun buildExclusionBlock(
        existingFlashcardQuestions: List<String>,
        existingQuizQuestions: List<String>
    ): String {
        if (existingFlashcardQuestions.isEmpty() && existingQuizQuestions.isEmpty()) {
            return "Aucune question existante."
        }
        return buildString {
            existingFlashcardQuestions.take(80).forEach { q ->
                append("- (flashcard) ").append(q.trim().take(140)).append("\n")
            }
            existingQuizQuestions.take(80).forEach { q ->
                append("- (QCM) ").append(q.trim().take(140)).append("\n")
            }
        }
    }

    private suspend fun generateAdditionalPracticeSection(
        config: AiConfig,
        courseTitle: String,
        courseText: String,
        exclusionBlock: String,
        language: String
    ): Pair<List<GeneratedFlashcard>, List<GeneratedQuizQuestion>> {
        val langRule = languageInstruction(language)
        val prompt = """
            Tu es un ingénieur pédagogique et un professeur universitaire.
            Analyse le texte de cours ci-dessous intitulé "$courseTitle" et génère des NOUVELLES flashcards et QCM.
            $langRule

            Les questions suivantes EXISTENT DÉJÀ dans la base de l'utilisateur.
            NE LES RÉPÈTE PAS et ne génère aucune variante quasi identique (même sens, formulation différente) :
            $exclusionBlock

            Règles strictes :
            1. Base-toi uniquement sur le cours fourni.
            2. Génère autant de flashcards et de QCM que nécessaire pour couvrir les concepts, définitions, formules ou faits clés du texte qui ne sont PAS déjà couverts par la liste d'exclusion ci-dessus, sans limite supérieure.
            3. Les QCM doivent avoir exactement 4 options distinctes et une bonne réponse identique à l'une des 4 options.
            4. Réponds UNIQUEMENT en JSON valide.

            Format JSON STRICT (sans texte introductif ni markdown) :
            {
              "flashcards": [ { "question": "...", "answer": "...", "explanation": "..." } ],
              "quizQuestions": [ { "question": "...", "options": ["Option 1", "Option 2", "Option 3", "Option 4"], "correctAnswer": "Option 1", "explanation": "..." } ]
            }

            TEXTE DU COURS :
            $courseText
        """.trimIndent()

        val rawText = chatCompletion(config, prompt, temperature = 0.5)
        return parsePracticeSection(rawText)
    }

    private suspend fun generateSummarySection(
        config: AiConfig,
        courseTitle: String,
        courseText: String,
        isFullDoc: Boolean,
        prefs: UserPreferences,
        chunkIndex: Int,
        totalChunks: Int,
        language: String
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

        val langRule = languageInstruction(language)
        val prompt = """
            Tu es un ingénieur pédagogique et un professeur universitaire.
            Analyse le texte de cours ci-dessous intitulé "$courseTitle" et génère la section synthétique.
            $langRule

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
        totalChunks: Int,
        language: String
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
            Analyse le texte de cours ci-dessous intitulé "$courseTitle" et génère les exercices (flashcards et QCM).
            ${languageInstruction(language)}

            Format JSON STRICT (sans texte introductif ni markdown) :
            {
              "flashcards": [
                {
                  "question": "Question atomique et précise, OU phrase à trou cloze avec les passages à mémoriser entre {{doubles accolades}}",
                  "answer": "Réponse concise et exacte (maximum 2 phrases)",
                  "explanation": "Brève explication (maximum 1 phrase)",
                  "source": "Court extrait du texte source (1 phrase) d'où vient cette carte"
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
            3. Pour les faits, dates, définitions et formules : génère aussi des cartes cloze en écrivant la phrase complète dans "question" avec le passage à mémoriser entre {{doubles accolades}} (ex. "La {{mitochondrie}} produit l'ATP"), "answer" pouvant alors être vide ou rappeler la phrase.
            4. Varie les types de questions : définitions, "pourquoi / comment" (explication), comparaisons ("différence entre X et Y"), application concrète (cas, exemple, exercice).
            5. $quizRule
            6. Réponds UNIQUEMENT en JSON valide.

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
    private suspend fun chatCompletion(config: AiConfig, prompt: String, temperature: Double, useJsonFormat: Boolean = true): String {
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
                val source = obj.optString("source", "")
                    .ifBlank { obj.optString("contexte", "") }
                    .ifBlank { obj.optString("context", "") }
                    .ifBlank { obj.optString("extrait", "") }
                    .trim()

                if (question.isNotBlank() && answer.isNotBlank()) {
                    flashcards.add(GeneratedFlashcard(question, answer, explanation, source))
                } else if (question.isNotBlank() && question.contains("{{") && question.contains("}}")) {
                    // Carte cloze : la phrase à trous suffit, la réponse est déduite.
                    val fullAnswer = answer.ifBlank { question.replace("{{", "").replace("}}", "") }
                    flashcards.add(GeneratedFlashcard(question, fullAnswer, explanation, source))
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
