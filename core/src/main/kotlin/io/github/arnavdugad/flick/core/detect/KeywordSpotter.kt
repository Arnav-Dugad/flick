package io.github.arnavdugad.flick.core.detect

import io.github.arnavdugad.flick.core.model.TriggerType

/**
 * Finds command words in streaming speech-recognition text. Recognisers send growing partial
 * transcripts ("next", "next next", ...); the spotter fires only for occurrences it hasn't
 * reported yet in the current utterance.
 */
class KeywordSpotter(private val keywords: Map<String, TriggerType> = DEFAULT_KEYWORDS) {

    private val seen = HashMap<TriggerType, Int>()

    /** Feed the latest transcript of the current utterance; returns newly spoken commands. */
    fun onTranscript(text: String): List<TriggerType> {
        val counts = HashMap<TriggerType, Int>()
        for (word in tokenize(text)) {
            val t = keywords[word] ?: continue
            counts[t] = (counts[t] ?: 0) + 1
        }
        val fired = ArrayList<TriggerType>()
        for ((t, n) in counts) {
            val already = seen[t] ?: 0
            repeat((n - already).coerceAtLeast(0)) { fired += t }
            if (n > already) seen[t] = n
        }
        return fired
    }

    /** Start of a new utterance. */
    fun reset() = seen.clear()

    companion object {
        val DEFAULT_KEYWORDS: Map<String, TriggerType> = mapOf(
            "next" to TriggerType.VOICE_NEXT,
            "back" to TriggerType.VOICE_BACK,
            "like" to TriggerType.VOICE_LIKE,
            "pause" to TriggerType.VOICE_PAUSE,
            "stop" to TriggerType.VOICE_STOP,
        )

        fun tokenize(text: String): List<String> =
            text.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotEmpty() }
    }
}
