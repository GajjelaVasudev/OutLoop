package com.outloop.prototype.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic JVM unit tests for [AttentionHistoryRepository] and [DailyAttentionRecord].
 */
class AttentionHistoryRepositoryTest {

    private lateinit var storage: InMemoryAttentionHistoryStorage
    private lateinit var repository: AttentionHistoryRepository

    @Before
    fun setUp() {
        storage = InMemoryAttentionHistoryStorage()
        repository = AttentionHistoryRepository(storage)
    }

    @Test
    fun testRecordDailyUsageAndRetrieval() {
        repository.recordDailyUsage(
            date = "2026-10-21",
            accumulatedMs = 15 * 60 * 1000L,
            normalLimitMinutes = 30L,
            extraAllowanceMinutes = 0L,
            isAllowanceEnabled = true,
            isRestrictionRelaxed = false,
            allowanceExhausted = false
        )

        val record = repository.getRecord("2026-10-21")
        assertEquals("2026-10-21", record.date)
        assertEquals(15 * 60 * 1000L, record.accumulatedReelsMs)
        assertEquals(30L, record.normalLimitMinutes)
        assertEquals(30L, record.effectiveLimitMinutes)
        assertTrue(record.isAllowanceEnabled)
        assertFalse(record.isRestrictionRelaxed)
        assertFalse(record.allowanceExhausted)
        assertTrue("Usage was within limit", record.wasAllowanceRespected)
    }

    @Test
    fun testWasAllowanceRespectedLogic() {
        // Case 1: Within limit
        val compliant = DailyAttentionRecord(
            date = "2026-10-21",
            accumulatedReelsMs = 20 * 60 * 1000L,
            effectiveLimitMinutes = 30L,
            isAllowanceEnabled = true
        )
        assertTrue(compliant.wasAllowanceRespected)

        // Case 2: Over limit without relaxation
        val exceeded = DailyAttentionRecord(
            date = "2026-10-21",
            accumulatedReelsMs = 35 * 60 * 1000L,
            effectiveLimitMinutes = 30L,
            isAllowanceEnabled = true,
            isRestrictionRelaxed = false
        )
        assertFalse(exceeded.wasAllowanceRespected)

        // Case 3: Over normal limit but within extra allowance
        val withExtra = DailyAttentionRecord(
            date = "2026-10-21",
            accumulatedReelsMs = 35 * 60 * 1000L,
            normalLimitMinutes = 30L,
            extraAllowanceMinutes = 15L,
            effectiveLimitMinutes = 45L,
            isAllowanceEnabled = true
        )
        assertTrue("Extra allowance counts as legitimate compliance", withExtra.wasAllowanceRespected)

        // Case 4: Relaxed restriction
        val relaxed = DailyAttentionRecord(
            date = "2026-10-21",
            accumulatedReelsMs = 60 * 60 * 1000L,
            effectiveLimitMinutes = 15L,
            isAllowanceEnabled = true,
            isRestrictionRelaxed = true
        )
        assertTrue("Relaxed restriction counts as legitimate exception", relaxed.wasAllowanceRespected)
    }

    @Test
    fun testRecordDiscreteEvents() {
        val date = "2026-10-22"

        repository.recordRestrictionDisplayed(date)
        repository.recordRestrictionDisplayed(date)
        repository.recordRestrictionDismissed(date)
        repository.recordScrollRescueResetCompleted(date)
        repository.recordClockDiscrepancy(date)

        val record = repository.getRecord(date)
        assertEquals(2, record.restrictionDisplayedCount)
        assertEquals(1, record.restrictionDismissedCount)
        assertEquals(1, record.scrollRescueResetsCompleted)
        assertTrue(record.clockDiscrepancyObserved)
        assertTrue(record.allowanceExhausted)
    }

    @Test
    fun testTrackingCoverageFlag() {
        val date = "2026-10-23"
        repository.recordDailyUsage(
            date = date,
            accumulatedMs = 5000L,
            normalLimitMinutes = 15L,
            extraAllowanceMinutes = 0L,
            isAllowanceEnabled = true,
            isRestrictionRelaxed = false,
            allowanceExhausted = false
        )
        assertTrue(repository.getRecord(date).hasSufficientTrackingCoverage)

        repository.recordTrackingInterruption(date)
        assertFalse(repository.getRecord(date).hasSufficientTrackingCoverage)
    }

    @Test
    fun testHistoryRetentionCapEnforces30Days() {
        for (day in 1..40) {
            val dateStr = String.format("2026-11-%02d", day)
            repository.recordDailyUsage(
                date = dateStr,
                accumulatedMs = 1000L * day,
                normalLimitMinutes = 30L,
                extraAllowanceMinutes = 0L,
                isAllowanceEnabled = true,
                isRestrictionRelaxed = false,
                allowanceExhausted = false
            )
        }

        val all = repository.allRecords.value
        assertEquals("History must be capped at 30 days", 30, all.size)
        // Ensure newest records are retained
        assertEquals("2026-11-40", all.first().date)
    }

    @Test
    fun testPersistenceAcrossRepositoryRecreation() {
        repository.recordDailyUsage(
            date = "2026-10-21",
            accumulatedMs = 600_000L,
            normalLimitMinutes = 15L,
            extraAllowanceMinutes = 10L,
            isAllowanceEnabled = true,
            isRestrictionRelaxed = false,
            allowanceExhausted = false
        )
        repository.recordScrollRescueResetCompleted("2026-10-21")

        // Recreate repo with same storage
        val repo2 = AttentionHistoryRepository(storage)
        val rec = repo2.getRecord("2026-10-21")
        assertEquals(600_000L, rec.accumulatedReelsMs)
        assertEquals(1, rec.scrollRescueResetsCompleted)
        assertEquals(25L, rec.effectiveLimitMinutes)
    }
}
