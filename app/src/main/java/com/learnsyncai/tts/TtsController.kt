package com.learnsyncai.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Contrôleur de synthèse vocale, hors de la couche UI : le moteur
 * TextToSpeech est initialisé paresseusement à la première demande de
 * lecture, les demandes faites avant que le moteur soit prêt sont mises en
 * attente et rejouées une fois l'initialisation terminée. La lecture des
 * textes passe par les modes de file d'attente Android (QUEUE_FLUSH pour
 * remplacer la lecture en cours, QUEUE_ADD pour enchaîner). [release] doit
 * être appelé quand le propriétaire (ReviewViewModel) est détruit.
 */
class TtsController(context: Context) {
    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    private var ready = false

    /** Demande faite avant la fin de l'initialisation du moteur. */
    private var pendingSpeak: (() -> Unit)? = null

    /** Init paresseux : le moteur n'est créé qu'à la première utilisation. */
    private fun ensureEngine(onReady: () -> Unit) {
        if (ready) {
            onReady()
            return
        }
        pendingSpeak = onReady
        if (engine == null) {
            engine = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    engine?.language = Locale.FRANCE
                    ready = true
                }
                val pending = pendingSpeak
                pendingSpeak = null
                if (ready) pending?.invoke()
            }
        }
    }

    /**
     * Lit un texte à voix haute. [queueMode] = QUEUE_FLUSH (défaut) remplace
     * la lecture en cours, QUEUE_ADD ajoute à la file d'attente.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, utteranceId: String? = null) {
        if (text.isBlank()) return
        ensureEngine { engine?.speak(text, queueMode, null, utteranceId) }
    }

    /** Interrompt la lecture en cours (la file d'attente est vidée). */
    fun stop() {
        engine?.stop()
    }

    /** Libère le moteur : à appeler quand le propriétaire est détruit. */
    fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        pendingSpeak = null
    }
}
