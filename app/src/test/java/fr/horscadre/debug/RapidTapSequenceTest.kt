package fr.horscadre.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidTapSequenceTest {
    @Test fun opensAfterSevenRapidTaps() {
        val sequence = RapidTapSequence()
        repeat(6) { index -> assertFalse(sequence.registerTap(index * 400L)) }
        assertTrue(sequence.registerTap(2400L))
    }

    @Test fun slowTapStartsANewSequence() {
        val sequence = RapidTapSequence()
        repeat(6) { index -> assertFalse(sequence.registerTap(index * 400L)) }
        assertFalse(sequence.registerTap(3000L))
        repeat(5) { index -> assertFalse(sequence.registerTap(3400L + index * 400L)) }
        assertTrue(sequence.registerTap(5400L))
    }
}
