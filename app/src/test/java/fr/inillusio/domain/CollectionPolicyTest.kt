package fr.inillusio.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CollectionPolicyTest {
    @Test fun rotationIsStableForSameInputsAndAvoidsImmediateRepeat() {
        val ids = listOf("a", "b", "c")
        assertEquals(DailyRotation.choose(12, ids, null), DailyRotation.choose(12, ids, null))
        assertNotEquals("b", DailyRotation.choose(12, ids, "b"))
    }
    @Test fun emptyCatalogHasNoSelection() { assertNull(DailyRotation.choose(1, emptyList(), null)) }

    @Test fun selectedAnecdoteStaysFixedThroughoutLocalDay() {
        val selected = DailySelectionPolicy.select("2026-11-03", null, null, listOf("a", "b", "c"), listOf("a", "b", "c"))
        assertEquals(selected, DailySelectionPolicy.select("2026-11-03", "2026-11-03", selected, listOf("a", "b", "c"), listOf("a", "b", "c")))
    }

    @Test fun aNewLocalDaySelectsAgainWithoutReplayingMissedDates() {
        val today = LocalDate.parse("2026-11-03")
        val ids = listOf("a", "b", "c", "d")
        val selected = DailySelectionPolicy.select(today.toString(), "2026-10-30", "a", ids, ids)
        assertEquals(DailyRotation.choose(today.toEpochDay(), ids, "a"), selected)
    }

    @Test fun disablingRenewalPreventsAnUpdateAndSuccessfulSameDayDeliveryIsNotRepeated() {
        assertFalse(DailyRenewalPolicy.shouldApply(consented = false, alreadyAppliedToday = false))
        assertFalse(DailyRenewalPolicy.shouldApply(consented = true, alreadyAppliedToday = true))
        assertTrue(DailyRenewalPolicy.shouldApply(consented = true, alreadyAppliedToday = false))
    }
}
