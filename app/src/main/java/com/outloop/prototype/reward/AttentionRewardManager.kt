package com.outloop.prototype.reward

import android.content.Context
import com.outloop.prototype.history.DailyAttentionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Storage interface for persisting unlocked rewards.
 */
interface RewardStorage {
    fun loadUnlockedDate(rewardId: String): String?
    fun saveUnlockedDate(rewardId: String, date: String)
    fun clear()
}

/**
 * SharedPreferences storage for rewards.
 */
class SharedPrefsRewardStorage(context: Context) : RewardStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "outloop_reward_preferences"
        private const val KEY_PREFIX = "reward_unlocked_"
    }

    override fun loadUnlockedDate(rewardId: String): String? = prefs.getString("$KEY_PREFIX$rewardId", null)
    override fun saveUnlockedDate(rewardId: String, date: String) {
        prefs.edit().putString("$KEY_PREFIX$rewardId", date).apply()
    }
    override fun clear() {
        prefs.edit().clear().apply()
    }
}

/**
 * In-memory storage for unit tests.
 */
class InMemoryRewardStorage : RewardStorage {
    private val map = mutableMapOf<String, String>()
    override fun loadUnlockedDate(rewardId: String): String? = map[rewardId]
    override fun saveUnlockedDate(rewardId: String, date: String) {
        map[rewardId] = date
    }
    override fun clear() {
        map.clear()
    }
}

/**
 * Evaluates and manages local attention milestone rewards.
 */
class AttentionRewardManager(
    private val storage: RewardStorage = InMemoryRewardStorage()
) {
    companion object {
        @Volatile
        private var instance: AttentionRewardManager? = null

        fun getInstance(context: Context): AttentionRewardManager {
            return instance ?: synchronized(this) {
                instance ?: AttentionRewardManager(
                    storage = SharedPrefsRewardStorage(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val lock = Any()
    private val _rewards = MutableStateFlow<List<AttentionReward>>(emptyList())
    val rewards: StateFlow<List<AttentionReward>> = _rewards.asStateFlow()

    init {
        // Initialize default reward list
        evaluateRewards(emptyList())
    }

    /**
     * Evaluates reward qualifications against verified daily attention history records.
     * Guaranteed idempotent: once a reward is unlocked, its unlock date is preserved.
     */
    fun evaluateRewards(records: List<DailyAttentionRecord>) {
        synchronized(lock) {
            // Filter to verified days with sufficient tracking coverage
            val verifiedRecords = records.filter { it.hasSufficientTrackingCoverage }.sortedBy { it.date }

            // 1. Limit Keeper: Left Reels after allowance reached
            val limitKeeperDate = storage.loadUnlockedDate(AttentionReward.ID_LIMIT_KEEPER)
            val isLimitKeeperEarned = limitKeeperDate != null || verifiedRecords.any { rec ->
                rec.isAllowanceEnabled &&
                rec.allowanceExhausted &&
                rec.restrictionDismissedCount > 0 &&
                rec.wasAllowanceRespected
            }
            val limitKeeperUnlocked = limitKeeperDate ?: if (isLimitKeeperEarned) {
                val qualifyingDate = verifiedRecords.lastOrNull { rec ->
                    rec.isAllowanceEnabled && rec.allowanceExhausted && rec.restrictionDismissedCount > 0 && rec.wasAllowanceRespected
                }?.date ?: "today"
                storage.saveUnlockedDate(AttentionReward.ID_LIMIT_KEEPER, qualifyingDate)
                qualifyingDate
            } else null

            // 2. Less Scroll, More Life: Reduced usage relative to previous 2+ days average
            val lessScrollDate = storage.loadUnlockedDate(AttentionReward.ID_LESS_SCROLL)
            val hasEnoughHistoryForComparison = verifiedRecords.size >= 3
            val isLessScrollEarned = lessScrollDate != null
            var lessScrollUnlocked = lessScrollDate

            if (!isLessScrollEarned && hasEnoughHistoryForComparison) {
                // Check latest day against previous 2 days average
                val latest = verifiedRecords.last()
                val previousTwo = verifiedRecords.dropLast(1).takeLast(2)
                val avgPreviousMs = previousTwo.map { it.accumulatedReelsMs }.average()
                if (latest.accumulatedReelsMs < avgPreviousMs && avgPreviousMs > 60_000L) {
                    lessScrollUnlocked = latest.date
                    storage.saveUnlockedDate(AttentionReward.ID_LESS_SCROLL, latest.date)
                }
            }

            // 3. Consistency Champion: Stayed within effective allowance for 3 active days
            val consistencyDate = storage.loadUnlockedDate(AttentionReward.ID_CONSISTENCY)
            var compliantDaysCount = 0
            for (rec in verifiedRecords.takeLast(5)) {
                if (rec.isAllowanceEnabled && rec.wasAllowanceRespected) {
                    compliantDaysCount++
                }
            }
            val isConsistencyEarned = consistencyDate != null || compliantDaysCount >= 3
            val consistencyUnlocked = consistencyDate ?: if (isConsistencyEarned) {
                val qualifyingDate = verifiedRecords.lastOrNull { it.wasAllowanceRespected }?.date ?: "today"
                storage.saveUnlockedDate(AttentionReward.ID_CONSISTENCY, qualifyingDate)
                qualifyingDate
            } else null

            // 4. Comeback Win: Returning to intended routine after a difficult day
            val comebackDate = storage.loadUnlockedDate(AttentionReward.ID_COMEBACK_WIN)
            val isComebackEarned = comebackDate != null
            var comebackUnlocked = comebackDate

            if (!isComebackEarned && verifiedRecords.size >= 2) {
                for (i in 1 until verifiedRecords.size) {
                    val prev = verifiedRecords[i - 1]
                    val curr = verifiedRecords[i]
                    // Over-budget unrelaxed day followed by compliant day
                    if (prev.isAllowanceEnabled && !prev.wasAllowanceRespected &&
                        curr.isAllowanceEnabled && curr.wasAllowanceRespected) {
                        comebackUnlocked = curr.date
                        storage.saveUnlockedDate(AttentionReward.ID_COMEBACK_WIN, curr.date)
                        break
                    }
                }
            }

            _rewards.value = listOf(
                AttentionReward(
                    id = AttentionReward.ID_LIMIT_KEEPER,
                    title = "Limit Keeper",
                    subtitle = "Stepped away at limit",
                    description = "Respected your daily Reels allowance and stepped away when the limit was reached.",
                    criteriaExplanation = "Leave Reels after reaching your configured daily limit.",
                    unlockedAtDate = limitKeeperUnlocked,
                    isUnlocked = limitKeeperUnlocked != null,
                    progressCurrent = if (limitKeeperUnlocked != null) 1 else 0,
                    progressTarget = 1,
                    isAvailable = true
                ),
                AttentionReward(
                    id = AttentionReward.ID_LESS_SCROLL,
                    title = "Less Scroll, More Life",
                    subtitle = "Reduced screen time",
                    description = "Reduced your daily Reels usage compared to your previous days' average.",
                    criteriaExplanation = "Requires at least 2 prior days of history. Use less Reels time than your previous average.",
                    unlockedAtDate = lessScrollUnlocked,
                    isUnlocked = lessScrollUnlocked != null,
                    progressCurrent = if (lessScrollUnlocked != null) 1 else if (hasEnoughHistoryForComparison) 0 else 0,
                    progressTarget = 1,
                    isAvailable = hasEnoughHistoryForComparison || lessScrollUnlocked != null
                ),
                AttentionReward(
                    id = AttentionReward.ID_CONSISTENCY,
                    title = "Consistency Champion",
                    subtitle = "3 days within budget",
                    description = "Kept within your effective Reels allowance across 3 active days.",
                    criteriaExplanation = "Stay within your effective limit (including approved extra time) for 3 days.",
                    unlockedAtDate = consistencyUnlocked,
                    isUnlocked = consistencyUnlocked != null,
                    progressCurrent = minOf(compliantDaysCount, 3),
                    progressTarget = 3,
                    isAvailable = true
                ),
                AttentionReward(
                    id = AttentionReward.ID_COMEBACK_WIN,
                    title = "Comeback Win",
                    subtitle = "Rebounded on track",
                    description = "Returned to your intended routine after a difficult, over-budget day.",
                    criteriaExplanation = "Follow an over-budget day with a day respecting your allowance.",
                    unlockedAtDate = comebackUnlocked,
                    isUnlocked = comebackUnlocked != null,
                    progressCurrent = if (comebackUnlocked != null) 1 else 0,
                    progressTarget = 1,
                    isAvailable = verifiedRecords.size >= 2 || comebackUnlocked != null
                )
            )
        }
    }
}
