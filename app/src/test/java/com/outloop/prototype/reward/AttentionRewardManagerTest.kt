package com.outloop.prototype.reward

import com.outloop.prototype.history.DailyAttentionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic JVM unit tests for [AttentionRewardManager] and [AttentionReward].
 */
class AttentionRewardManagerTest {

    private lateinit var storage: InMemoryRewardStorage
    private lateinit var rewardManager: AttentionRewardManager

    @Before
    fun setUp() {
        storage = InMemoryRewardStorage()
        rewardManager = AttentionRewardManager(storage)
    }

    @Test
    fun testInitialRewardsAreLocked() {
        val rewards = rewardManager.rewards.value
        assertEquals(4, rewards.size)
        assertTrue(rewards.none { it.isUnlocked })
    }

    @Test
    fun testLimitKeeperUnlockedWhenAllowanceExhaustedAndDismissedCompliantly() {
        val record = DailyAttentionRecord(
            date = "2026-10-21",
            accumulatedReelsMs = 15 * 60 * 1000L,
            normalLimitMinutes = 15L,
            effectiveLimitMinutes = 15L,
            isAllowanceEnabled = true,
            allowanceExhausted = true,
            restrictionDisplayedCount = 1,
            restrictionDismissedCount = 1,
            hasSufficientTrackingCoverage = true
        )

        rewardManager.evaluateRewards(listOf(record))

        val limitKeeper = rewardManager.rewards.value.first { it.id == AttentionReward.ID_LIMIT_KEEPER }
        assertTrue("Limit Keeper should be unlocked", limitKeeper.isUnlocked)
        assertEquals("2026-10-21", limitKeeper.unlockedAtDate)
    }

    @Test
    fun testLimitKeeperNotUnlockedIfCoverageWasInsufficient() {
        val record = DailyAttentionRecord(
            date = "2026-10-21",
            accumulatedReelsMs = 15 * 60 * 1000L,
            normalLimitMinutes = 15L,
            effectiveLimitMinutes = 15L,
            isAllowanceEnabled = true,
            allowanceExhausted = true,
            restrictionDismissedCount = 1,
            hasSufficientTrackingCoverage = false // Service disabled or crashed
        )

        rewardManager.evaluateRewards(listOf(record))

        val limitKeeper = rewardManager.rewards.value.first { it.id == AttentionReward.ID_LIMIT_KEEPER }
        assertFalse("Cannot award Limit Keeper if tracking coverage was insufficient", limitKeeper.isUnlocked)
    }

    @Test
    fun testLessScrollMoreLifeRequiresSufficientHistory() {
        // Only 1 day of history
        val rec1 = DailyAttentionRecord(
            date = "2026-10-21",
            accumulatedReelsMs = 10 * 60 * 1000L,
            hasSufficientTrackingCoverage = true
        )
        rewardManager.evaluateRewards(listOf(rec1))

        val lessScroll1 = rewardManager.rewards.value.first { it.id == AttentionReward.ID_LESS_SCROLL }
        assertFalse("Needs at least 3 verified days", lessScroll1.isUnlocked)
        assertFalse("Progress should indicate unavailable with < 3 days", lessScroll1.isAvailable)

        // 3 days of history with reduction
        val rec2 = DailyAttentionRecord(
            date = "2026-10-22",
            accumulatedReelsMs = 40 * 60 * 1000L,
            hasSufficientTrackingCoverage = true
        )
        val rec3 = DailyAttentionRecord(
            date = "2026-10-23",
            accumulatedReelsMs = 30 * 60 * 1000L,
            hasSufficientTrackingCoverage = true
        )
        // Day 4 has 15m, which is less than average of days 2 & 3 (35m)
        val rec4 = DailyAttentionRecord(
            date = "2026-10-24",
            accumulatedReelsMs = 15 * 60 * 1000L,
            hasSufficientTrackingCoverage = true
        )

        rewardManager.evaluateRewards(listOf(rec1, rec2, rec3, rec4))

        val lessScroll2 = rewardManager.rewards.value.first { it.id == AttentionReward.ID_LESS_SCROLL }
        assertTrue("Less Scroll should unlock when usage reduced relative to average", lessScroll2.isUnlocked)
        assertEquals("2026-10-24", lessScroll2.unlockedAtDate)
    }

    @Test
    fun testConsistencyChampionUnlockedWithThreeCompliantDays() {
        val days = listOf(
            DailyAttentionRecord("2026-10-20", accumulatedReelsMs = 10 * 60 * 1000L, effectiveLimitMinutes = 15L, isAllowanceEnabled = true),
            DailyAttentionRecord("2026-10-21", accumulatedReelsMs = 25 * 60 * 1000L, normalLimitMinutes = 15L, extraAllowanceMinutes = 15L, effectiveLimitMinutes = 30L, isAllowanceEnabled = true), // approved extra time
            DailyAttentionRecord("2026-10-22", accumulatedReelsMs = 45 * 60 * 1000L, effectiveLimitMinutes = 15L, isAllowanceEnabled = true, isRestrictionRelaxed = true) // relaxed
        )

        rewardManager.evaluateRewards(days)

        val consistency = rewardManager.rewards.value.first { it.id == AttentionReward.ID_CONSISTENCY }
        assertTrue("Consistency Champion earned across 3 compliant days (including exceptions)", consistency.isUnlocked)
        assertEquals(3, consistency.progressCurrent)
    }

    @Test
    fun testComebackWinUnlockedAfterReboundingFromOverBudgetDay() {
        val days = listOf(
            DailyAttentionRecord("2026-10-21", accumulatedReelsMs = 40 * 60 * 1000L, effectiveLimitMinutes = 15L, isAllowanceEnabled = true, isRestrictionRelaxed = false), // difficult day
            DailyAttentionRecord("2026-10-22", accumulatedReelsMs = 12 * 60 * 1000L, effectiveLimitMinutes = 15L, isAllowanceEnabled = true) // compliant return
        )

        rewardManager.evaluateRewards(days)

        val comeback = rewardManager.rewards.value.first { it.id == AttentionReward.ID_COMEBACK_WIN }
        assertTrue("Comeback Win unlocked after bouncing back from difficult day", comeback.isUnlocked)
        assertEquals("2026-10-22", comeback.unlockedAtDate)
    }

    @Test
    fun testDuplicatePreventionAndIdempotency() {
        val record = DailyAttentionRecord(
            date = "2026-10-21",
            accumulatedReelsMs = 15 * 60 * 1000L,
            normalLimitMinutes = 15L,
            effectiveLimitMinutes = 15L,
            isAllowanceEnabled = true,
            allowanceExhausted = true,
            restrictionDismissedCount = 1,
            hasSufficientTrackingCoverage = true
        )

        rewardManager.evaluateRewards(listOf(record))
        val firstUnlockDate = storage.loadUnlockedDate(AttentionReward.ID_LIMIT_KEEPER)
        assertEquals("2026-10-21", firstUnlockDate)

        // Evaluate again on subsequent day
        val nextDayRecord = DailyAttentionRecord(
            date = "2026-10-22",
            accumulatedReelsMs = 15 * 60 * 1000L,
            normalLimitMinutes = 15L,
            effectiveLimitMinutes = 15L,
            isAllowanceEnabled = true,
            allowanceExhausted = true,
            restrictionDismissedCount = 1,
            hasSufficientTrackingCoverage = true
        )

        rewardManager.evaluateRewards(listOf(record, nextDayRecord))
        assertEquals("Original unlock date must be preserved without duplicate awards", firstUnlockDate, storage.loadUnlockedDate(AttentionReward.ID_LIMIT_KEEPER))
    }
}
