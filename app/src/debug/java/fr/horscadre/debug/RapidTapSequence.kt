package fr.horscadre.debug

/** Opens the developer tools after seven consecutive taps, no more than 600 ms apart. */
internal class RapidTapSequence(private val maxGapMs: Long = 600L, private val requiredTaps: Int = 7) {
    private var count = 0
    private var lastTapAt = Long.MIN_VALUE

    fun registerTap(atMillis: Long): Boolean {
        count = if (lastTapAt != Long.MIN_VALUE && atMillis - lastTapAt in 0..maxGapMs) count + 1 else 1
        lastTapAt = atMillis
        if (count < requiredTaps) return false
        count = 0
        lastTapAt = Long.MIN_VALUE
        return true
    }
}
