package fr.horscadre.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplanationExcerptTest {
    @Test fun shortExplanationIsReturnedUnchanged() {
        assertEquals("Une explication courte.", explanationExcerpt("Une explication courte."))
    }

    @Test fun longExplanationIsTruncatedAtAWordBoundary() {
        val result = explanationExcerpt("Les relations sociales structurent une partie de nos expériences.", maxCharacters = 28)
        assertEquals("Les relations sociales…", result)
        assertEquals(23, result.length)
    }

    @Test fun nonPositiveLimitProducesNoExcerpt() {
        assertEquals("", explanationExcerpt("Texte", maxCharacters = 0))
    }
}
