package fr.inillusio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoosterPackGestureTest {
    @Test fun incompleteDragDoesNotOpenThePack() {
        assertFalse(boosterPackSwipeOpens(BOOSTER_PACK_OPEN_THRESHOLD - 0.01f))
        assertFalse(boosterPackSwipeOpens(0f))
    }

    @Test fun dragAtOrPastThresholdOpensThePack() {
        assertTrue(boosterPackSwipeOpens(BOOSTER_PACK_OPEN_THRESHOLD))
        assertTrue(boosterPackSwipeOpens(1f))
    }
}
