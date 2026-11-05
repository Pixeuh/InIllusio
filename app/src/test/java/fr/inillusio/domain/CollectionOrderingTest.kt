package fr.inillusio.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionOrderingTest {
    private data class Card(val id: String, val categoryId: String, val categoryName: String, val savedAt: Long)

    private val cards = listOf(
        Card("d-soc-old", "sociologie", "Sociologie", 100L),
        Card("b-soc-new", "sociologie", "Sociologie", 200L),
        Card("c-anthro-new", "anthropologie", "Anthropologie", 200L),
        Card("a-anthro-new", "anthropologie", "Anthropologie", 200L)
    )

    private fun ordered(
        order: CollectionSortOrder,
        selected: Set<String>? = null
    ) = filterAndSortCollection(cards, selected, order) {
        CollectionOrderKey(it.id, it.categoryId, it.categoryName, it.savedAt)
    }.map { it.id }

    @Test
    fun receivedDateOrdersAreStableWhenAcquisitionTimesMatch() {
        assertEquals(
            listOf("a-anthro-new", "b-soc-new", "c-anthro-new", "d-soc-old"),
            ordered(CollectionSortOrder.RECEIVED_NEWEST)
        )
        assertEquals(
            listOf("d-soc-old", "a-anthro-new", "b-soc-new", "c-anthro-new"),
            ordered(CollectionSortOrder.RECEIVED_OLDEST)
        )
    }

    @Test
    fun categoryOrdersKeepNewestFirstWithinEachDiscipline() {
        assertEquals(
            listOf("a-anthro-new", "c-anthro-new", "b-soc-new", "d-soc-old"),
            ordered(CollectionSortOrder.CATEGORY_A_TO_Z)
        )
        assertEquals(
            listOf("b-soc-new", "d-soc-old", "a-anthro-new", "c-anthro-new"),
            ordered(CollectionSortOrder.CATEGORY_Z_TO_A)
        )
    }

    @Test
    fun categoryFiltersCanSelectOneOrSeveralDisciplinesAndReset() {
        assertEquals(listOf("d-soc-old", "b-soc-new"), ordered(CollectionSortOrder.RECEIVED_OLDEST, setOf("sociologie")))
        assertEquals(
            listOf("a-anthro-new", "b-soc-new", "c-anthro-new", "d-soc-old"),
            ordered(CollectionSortOrder.RECEIVED_NEWEST, setOf("anthropologie", "sociologie"))
        )
        assertEquals(emptyList<String>(), ordered(CollectionSortOrder.RECEIVED_NEWEST, emptySet()))
        assertEquals(emptyList<String>(), ordered(CollectionSortOrder.RECEIVED_NEWEST, setOf("inconnue")))
        assertEquals(4, ordered(CollectionSortOrder.RECEIVED_NEWEST).size)
    }
}
