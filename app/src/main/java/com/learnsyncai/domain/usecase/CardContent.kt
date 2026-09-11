package com.learnsyncai.domain.usecase

import com.learnsyncai.domain.model.CardDirection
import com.learnsyncai.domain.model.CardType
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.ReviewItem

/**
 * Contenu des cartes avancées (style RemNote) : texte à trous (cloze),
 * sens inversé, et expansion d'une liste de cartes en file de révision.
 */
object CardContent {

    private val CLOZE_PATTERN = Regex("\\{\\{(.+?)\\}\\}")
    const val CLOZE_GAP = "[…]"

    /** Vrai si le texte contient au moins une occultation {{…}}. */
    fun hasClozes(text: String): Boolean = CLOZE_PATTERN.containsMatchIn(text)

    /** Liste des segments occultés, dans l'ordre. */
    fun occlusions(text: String): List<String> =
        CLOZE_PATTERN.findAll(text).map { it.groupValues[1] }.toList()

    /** Type auto-détecté depuis la question ({{…}} → cloze). */
    fun detectType(question: String): String =
        if (hasClozes(question)) CardType.CLOZE else CardType.BASIC

    /** Texte avec les {{…}} retirés (réponse d'une carte cloze). */
    fun stripMarkers(text: String): String =
        text.replace(CLOZE_PATTERN, "$1")

    /**
     * Question affichée : l'occultation active est masquée ([…]), les autres
     * restent visibles. Hors cloze : question (ou réponse si inversé).
     */
    fun resolvePrompt(item: ReviewItem): String {
        val card = item.card
        if (card.cardType == CardType.CLOZE) {
            val index = item.clozeIndex
            if (index == null) return stripMarkers(card.question)
            var seen = -1
            return CLOZE_PATTERN.replace(card.question) { match ->
                seen += 1
                if (seen == index) CLOZE_GAP else match.groupValues[1]
            }
        }
        return if (item.reversed) card.answer else card.question
    }

    /** Réponse affichée (texte complet, occultations révélées). */
    fun resolveAnswer(item: ReviewItem): String {
        val card = item.card
        if (card.cardType == CardType.CLOZE) return stripMarkers(card.question)
        return if (item.reversed) card.question else card.answer
    }

    /** Réponse attendue pour le mode « taper la réponse ». */
    fun expectedTypedAnswer(item: ReviewItem): String {
        val card = item.card
        if (card.cardType == CardType.CLOZE) {
            val occl = occlusions(card.question)
            return item.clozeIndex?.let { occl.getOrNull(it) } ?: occl.joinToString(" / ")
        }
        return if (item.reversed) card.question else card.answer
    }

    fun normalizeAnswer(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    fun checkTypedAnswer(item: ReviewItem, typed: String): Boolean =
        normalizeAnswer(typed).isNotEmpty() &&
            normalizeAnswer(typed) == normalizeAnswer(expectedTypedAnswer(item))
}

/**
 * Expansion d'une liste de cartes en file d'items de révision :
 * cloze multiple → un item par occultation, "both" → aller + retour.
 */
object ReviewQueue {
    fun expand(cards: List<Flashcard>, shuffle: Boolean = true): List<ReviewItem> {
        val items = mutableListOf<ReviewItem>()
        for (card in cards.distinctBy { it.id }) {
            val occlusions = if (card.cardType == CardType.CLOZE) {
                CardContent.occlusions(card.question)
            } else emptyList()
            if (occlusions.isNotEmpty()) {
                occlusions.indices.forEach { items += ReviewItem(card, clozeIndex = it) }
            } else when (card.direction) {
                CardDirection.REVERSE -> items += ReviewItem(card, reversed = true)
                CardDirection.BOTH -> {
                    items += ReviewItem(card)
                    items += ReviewItem(card, reversed = true)
                }
                else -> items += ReviewItem(card)
            }
        }
        return if (shuffle) items.shuffled() else items
    }
}

/** Carte brute extraite d'une ligne de notes (avant attribution FSRS). */
data class ParsedNoteCard(
    val question: String,
    val answer: String,
    val direction: String = CardDirection.FORWARD,
    val typeAnswer: Boolean = false
)

/**
 * Parseur de notes style RemNote (une carte par ligne, marqueurs espacés) :
 * - `Question >> Réponse` : carte simple (sens aller)
 * - `Réponse << Question` : sens retour
 * - `Concept <> Définition` : les deux sens
 * - `attribut ;; description` : descripteur (aller)
 * - `Concept :: définition` : concept (les deux sens)
 * - ligne avec {{trou}} : carte cloze (marqueurs prioritaires)
 * - autre ligne : simple note, ignorée.
 */
object NoteCards {
    private data class Marker(val token: String, val direction: String)

    private val MARKERS = listOf(
        Marker(" >> ", CardDirection.FORWARD),
        Marker(" << ", CardDirection.REVERSE),
        Marker(" <> ", CardDirection.BOTH),
        Marker(" ;; ", CardDirection.FORWARD),
        Marker(" :: ", CardDirection.BOTH)
    )

    fun parse(text: String): List<ParsedNoteCard> =
        text.lines().mapNotNull { parseLine(it) }

    fun parseLine(rawLine: String): ParsedNoteCard? {
        val line = rawLine.trim()
        if (line.length < 4) return null
        if (CardContent.hasClozes(line)) {
            val answer = CardContent.stripMarkers(line)
            if (answer.isBlank()) return null
            return ParsedNoteCard(question = line, answer = answer)
        }
        for (marker in MARKERS) {
            val idx = line.indexOf(marker.token)
            if (idx >= 0) {
                val left = line.substring(0, idx).trim()
                val right = line.substring(idx + marker.token.length).trim()
                if (left.length < 2 || right.length < 2) return null
                return ParsedNoteCard(question = left, answer = right, direction = marker.direction)
            }
        }
        return null
    }
}
