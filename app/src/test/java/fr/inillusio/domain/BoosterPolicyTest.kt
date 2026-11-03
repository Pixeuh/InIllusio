package fr.inillusio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoosterPolicyTest {
    @Test fun boosterLimitIsFive() {
        assertEquals(5, BoosterPolicy.CARDS_PER_REWARD)
        assertEquals(5, BoosterPolicy.availableCount(12, 7).coerceAtMost(BoosterPolicy.CARDS_PER_REWARD))
    }

    @Test fun almostCompleteCatalogOffersOnlyTheRemainingCards() {
        assertEquals(2, BoosterPolicy.availableCount(24, 22))
        assertTrue(BoosterPolicy.canOfferRewardedAd(24, 22))
    }

    @Test fun completedCatalogDoesNotOfferAnAd() {
        assertEquals(0, BoosterPolicy.availableCount(24, 24))
        assertFalse(BoosterPolicy.canOfferRewardedAd(24, 24))
        assertFalse(BoosterPolicy.canOfferRewardedAd(0, 0))
    }

    @Test fun invalidCountsCannotProduceNegativeAvailability() {
        assertEquals(0, BoosterPolicy.availableCount(2, 5))
    }
}
