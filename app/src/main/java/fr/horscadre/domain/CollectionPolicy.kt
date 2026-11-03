package fr.horscadre.domain

object DailyRotation {
    fun choose(todayEpochDay: Long, eligibleIds: List<String>, previousId: String?): String? {
        if (eligibleIds.isEmpty()) return null
        val candidates = eligibleIds.filterNot { it == previousId }.ifEmpty { eligibleIds }
        return candidates[todayEpochDay.mod(candidates.size.toLong()).toInt()]
    }
}

/** Pure daily selection rule, shared by normal app loads and background renewal. */
object DailySelectionPolicy {
    fun select(
        today: String,
        previousDate: String?,
        previousId: String?,
        catalogIds: List<String>,
        candidateIds: List<String>
    ): String? {
        if (catalogIds.isEmpty() || candidateIds.isEmpty()) return null
        if (previousDate == today && previousId in catalogIds) return previousId
        return DailyRotation.choose(java.time.LocalDate.parse(today).toEpochDay(), candidateIds, previousId)
    }
}

object DailyRenewalPolicy {
    fun shouldApply(consented: Boolean, alreadyAppliedToday: Boolean): Boolean = consented && !alreadyAppliedToday
}
