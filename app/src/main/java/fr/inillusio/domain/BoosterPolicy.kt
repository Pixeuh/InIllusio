package fr.inillusio.domain

/** Pure limits and availability rules for the optional rewarded booster. */
object BoosterPolicy {
    const val CARDS_PER_REWARD = 5

    fun availableCount(validatedUnarchivedCount: Int, discoveredCount: Int): Int =
        (validatedUnarchivedCount - discoveredCount).coerceAtLeast(0)

    fun canOfferRewardedAd(validatedUnarchivedCount: Int, discoveredCount: Int): Boolean =
        availableCount(validatedUnarchivedCount, discoveredCount) > 0
}
