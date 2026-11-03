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

    @Test fun cardCitationUsesTheExistingPrimaryAuthorTitleAndYearFormat() {
        val citation = SourceDetails(
            type = "article",
            title = "Anatomie du goût — Actes de la recherche en sciences sociales",
            year = 1976,
            author = "Pierre Bourdieu et Monique de Saint Martin"
        ).shortCitation()

        assertEquals("Bourdieu · Anatomie du goût · 1976", citation)
    }

    @Test fun optionalBibliographicFieldsAreOmitted() {
        val citation = SourceDetails(
            type = "rapport",
            title = "Rapport de recherche",
            year = null,
            author = null
        ).shortCitation()

        assertEquals("Rapport de recherche", citation)
    }
}
