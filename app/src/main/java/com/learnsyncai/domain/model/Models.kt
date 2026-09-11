package com.learnsyncai.domain.model

data class Course(
    val id: String,
    val title: String,
    val description: String,
    val sourceFileName: String,
    val sourceFileUri: String,
    val createdAt: Long,
    val updatedAt: Long,
    val progress: Float,
    val color: String,
    val generationStatus: String,
    /** Étiquettes multiples, stockées séparées par des virgules. */
    val tag: String = "",
    /** Dossier de rangement ("" = sans dossier). */
    val folder: String = "",
    /** Date d'examen en ms (0 = aucune) : pilote le compte à rebours. */
    val examDate: Long = 0L,
    // Langue de réponse IA : "auto" = langue du document, sinon code (fr, en...)
    val language: String = "auto"
) {
    /** Étiquettes découpées et nettoyées. */
    fun tags(): List<String> = CourseTags.parse(tag)
}

/** Étiquettes multiples : format CSV simple dans la colonne `tag` historique. */
object CourseTags {
    fun parse(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun join(tags: List<String>): String =
        tags.map { it.trim().replace(",", " ") }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
}

data class StudyMaterial(
    val id: String,
    val courseId: String,
    val summary: String,
    val keyPoints: List<String>,
    val mnemonicTips: List<String>,
    val generatedAt: Long,
    val version: Int
)

data class Flashcard(
    val id: String,
    val courseId: String,
    val question: String,
    val answer: String,
    val explanation: String,
    val difficulty: Float,
    val box: Int,
    val dueDate: Long,
    val interval: Int,
    val easeFactor: Float,
    val repetitions: Int,
    val lapses: Int,
    val lastReviewedAt: Long?,
    val createdAt: Long,
    /** Carte suspendue : exclue des cartes dues jusqu'à réactivation (sangsues, choix manuel). */
    val suspended: Boolean = false,
    /** Type de carte : "basic" ou "cloze" (texte à trous avec {{occultation}}). */
    val cardType: String = CardType.BASIC,
    /** Sens de révision : "forward", "reverse" ou "both". */
    val direction: String = CardDirection.FORWARD,
    /** Si vrai, la révision demande de taper la réponse au clavier. */
    val typeAnswer: Boolean = false,
    /** Extrait du texte source d'où vient la carte (contexte affiché en révision). */
    val sourceExcerpt: String = "",
    /** Chemin local d'image ("" = aucune) : carte image / occlusion. */
    val imagePath: String = "",
    /** Masque d'occultation en fractions 0..1 (x, y, largeur, hauteur). */
    val maskX: Float = 0f,
    val maskY: Float = 0f,
    val maskW: Float = 0f,
    val maskH: Float = 0f
)

/** Types de cartes (pilier RemNote : basic, cloze…). */
object CardType {
    const val BASIC = "basic"
    const val CLOZE = "cloze"
}

/** Sens de révision d'une carte (RemNote : avant, arrière, bidirectionnel). */
object CardDirection {
    const val FORWARD = "forward"
    const val REVERSE = "reverse"
    const val BOTH = "both"
}

/**
 * Unité de révision : une carte vue sous un angle donné (sens inversé,
 * et/ou une occultation cloze précise). Une carte "both" ou cloze multiple
 * donne plusieurs items notés indépendamment sur le même état FSRS.
 */
data class ReviewItem(
    val card: Flashcard,
    val reversed: Boolean = false,
    val clozeIndex: Int? = null
) {
    fun key(): String = "${card.id}|$reversed|${clozeIndex ?: -1}"
}

/** Annotation PDF par page : note libre ou passage à retenir (cartes). */
data class PdfAnnotation(
    val id: String,
    val courseId: String,
    val page: Int,
    val text: String,
    val kind: String = "note",
    val createdAt: Long
) {
    companion object {
        const val KIND_NOTE = "note"
        const val KIND_KEY = "key"
    }
}

/** Média attaché à un cours : enregistrement audio + transcription. */
data class CourseMedia(
    val id: String,
    val courseId: String,
    val kind: String = "audio",
    val path: String = "",
    val transcript: String = "",
    val createdAt: Long
)

/** Notes libres d'un cours (style RemNote) : une carte par ligne via >>, <<, <>, ;;, ::, {{}}. */
data class CourseNote(
    val id: String,
    val courseId: String,
    val content: String,
    val updatedAt: Long
)

data class QuizQuestion(
    val id: String,
    val courseId: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val difficulty: String
)

data class ReviewLog(
    val id: String,
    val flashcardId: String,
    val reviewedAt: Long,
    val rating: Int,
    val previousInterval: Int,
    val newInterval: Int,
    val responseTime: Long,
    // Cours d'origine de la carte notée : l'historique survit à la
    // régénération/suppression des cartes, on le rattache donc au cours.
    // "" = logs anciens (pré-v11) ou distants sans cette information.
    val courseId: String = ""
)

data class ReviewSession(
    val id: String,
    val courseId: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val cardsReviewed: Int
)

data class UserPreferences(
    val notificationsEnabled: Boolean,
    val dailyGoal: Int,
    val reminderTime: String,
    val theme: String,
    val language: String,
    val aiProvider: String = "GEMINI",
    val aiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai",
    val aiApiKey: String = "",
    val aiModelName: String = "gemini-2.5-flash",
    val flashcardsMode: String = "auto",
    val flashcardsCustomCount: Int = 10,
    val quizMode: String = "auto",
    val quizCustomCount: Int = 5,
    val mnemonicTipsMode: String = "auto",
    val mnemonicTipsCustomCount: Int = 3,
    val autoTtsEnabled: Boolean = false,
    val calendarHorizonDays: Int = 14,
    val calendarStartTime: String = "",
    val calendarDurationMinutes: Int = 30,
    val calendarReminderMinutes: Int = 15,
    val periodicSyncEnabled: Boolean = false,
    /** Points d'expérience (gamification) : +2/carte notée, +5/carte créée, +50/examen. */
    val xp: Int = 0
) {
    companion object {
        val DEFAULT = UserPreferences(
            notificationsEnabled = true,
            dailyGoal = 10,
            reminderTime = "08:00",
            theme = "system",
            language = "fr",
            calendarStartTime = "08:00"
        )
    }
}

data class StudyGenerationResult(
    val summary: String,
    val keyPoints: List<String>,
    val flashcards: List<GeneratedFlashcard>,
    val quizQuestions: List<GeneratedQuizQuestion>,
    val mnemonicTips: List<String>
)

data class GeneratedFlashcard(
    val question: String,
    val answer: String,
    val explanation: String,
    /** Court extrait du texte source d'où vient la carte (contexte en révision). */
    val source: String = ""
)

data class GeneratedQuizQuestion(
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String
)

data class CalendarEvent(
    val id: String,
    val courseId: String,
    val title: String,
    val scheduledDate: Long,
    val androidEventId: Long?,
    val updatedAt: Long
)

data class SyncStatus(
    val id: Int = 1,
    val lastSyncAt: Long? = null,
    val pending: Boolean = false,
    val lastError: String? = null
)

data class AiProfile(
    val id: String,
    val name: String,
    val provider: String, // "GEMINI", "OPENROUTER", "NVIDIA_NIM", "OPENCODE_ZEN", "CUSTOM"
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val isActive: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Mémorialise une suppression locale pour la propager à Firestore : sans lui,
 * une entité supprimée localement est réinsérée à la sync suivante (doublon).
 * Les tombstones sont conservés indéfiniment : ils servent aussi de filtre
 * anti-résurrection à chaque sync descendante.
 */
data class Tombstone(
    val entityType: String,
    val entityId: String,
    val deletedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_COURSE = "COURSE"
        const val TYPE_STUDY_MATERIAL = "STUDY_MATERIAL"
        const val TYPE_FLASHCARD = "FLASHCARD"
        const val TYPE_QUIZ_QUESTION = "QUIZ_QUESTION"
    }
}


