package fr.inillusio.domain

import fr.inillusio.data.local.dao.SourceDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BibliographicCitationTest {
    @Test fun shortCitationUsesSurnameMainTitleAndYear() {
        val citation = SourceDetails(
            type = "livre",
            title = "Le Suicide. Étude de sociologie (texte original)",
            year = 1897,
            author = "Émile Durkheim"
        ).shortCitation()

        assertEquals("Durkheim · Le Suicide · 1897", citation)
    }

    @Test fun compactCitationBoundsLongAuthorAndTitle() {
        val citation = SourceDetails(
            type = "lien",
            title = "Temporal dynamics of woolly mammoth genome erosion prior to extinction — Cell",
            year = 2024,
            author = "Marianne Dehasque et al."
        ).shortCitation()

        assertEquals("Dehasque et al. · Temporal dynamics of woolly… · 2024", citation)
        assertTrue(citation.length < 55)
    }
}
