package com.outloop.prototype.reward

/**
 * Meaningful on-device achievement recognizing healthy attention patterns.
 */
data class AttentionReward(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val criteriaExplanation: String,
    val unlockedAtDate: String? = null,
    val isUnlocked: Boolean = unlockedAtDate != null,
    val progressCurrent: Int = 0,
    val progressTarget: Int = 1,
    val isAvailable: Boolean = true
) {
    companion object {
        const val ID_LIMIT_KEEPER = "limit_keeper"
        const val ID_LESS_SCROLL = "less_scroll_more_life"
        const val ID_CONSISTENCY = "consistency_champion"
        const val ID_COMEBACK_WIN = "comeback_win"
    }
}
