package com.outloop.classifier

/**
 * Pure Kotlin classifier for inferring Instagram screen states based on sanitized structural layout snapshots.
 *
 * Designed to be completely independent of Android framework classes, enabling deterministic JVM unit testing
 * without an emulator or device.
 */
class ScreenClassifier(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val REELS_TAB_ORDINAL = 3
        const val HOME_TAB_ORDINAL = 0
        const val PROFILE_TAB_ORDINAL = 4
    }

    /**
     * Evaluates a privacy-safe [StructuralSnapshot] and returns a [ClassificationResult].
     *
     * Adheres strictly to the following core engineering principles:
     * 1. Returns UNKNOWN whenever structural evidence is insufficient.
     * 2. A visible video container NEVER by itself implies the dedicated Reels tab.
     * 3. A full-screen video opened from a DM or external link is classified as FULL_SCREEN_VIDEO, not REELS_TAB.
     * 4. Returns UNKNOWN when structural cues are contradictory.
     * 5. Does not claim real-device verification unless explicitly confirmed by real-device observations.
     */
    fun classify(snapshot: StructuralSnapshot): ClassificationResult {
        val now = clock()

        // 1. Verify target application package
        if (snapshot.packageName != INSTAGRAM_PACKAGE) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.APP_NOT_FOREGROUND,
                timestamp = now,
                evidenceExplanation = "Active package '${snapshot.packageName ?: "null"}' is not Instagram ($INSTAGRAM_PACKAGE).",
                isDeviceVerified = false
            )
        }

        // 2. Verify foreground status
        if (!snapshot.isForeground) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.APP_NOT_FOREGROUND,
                timestamp = now,
                evidenceExplanation = "Instagram is not the active foreground application.",
                isDeviceVerified = false
            )
        }

        // 3. Verify root accessibility node presence and minimal hierarchy size
        if (!snapshot.hasRootNode || snapshot.totalNodeCount == 0) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.MISSING_ROOT,
                timestamp = now,
                evidenceExplanation = "No accessibility root node or empty hierarchy structure available to inspect.",
                isDeviceVerified = false
            )
        }

        // 4. Check for conflicting or contradictory layout cues
        if (hasConflictingSignals(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.CONFLICTING_SIGNALS,
                timestamp = now,
                evidenceExplanation = "Conflicting structural signals detected simultaneously (e.g. bottom navigation coexisting with active chat composer).",
                isDeviceVerified = false
            )
        }

        // 5. Evaluate Dedicated Reels Tab signature
        // Requirements: Bottom navigation bar present, Reels tab selected (or ordinal 3), vertical pager present,
        // and absence of chat, story progress, or profile header.
        if (matchesDedicatedReelsTab(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.REELS_TAB,
                confidence = Confidence.MEDIUM,
                reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH,
                timestamp = now,
                evidenceExplanation = "Bottom navigation bar detected with Reels tab selected and full-screen vertical pager active.",
                isDeviceVerified = false
            )
        }

        // 6. Evaluate DM Conversation signature
        // Requirements: Active chat composer (EditText at bottom) + message list container, no bottom tab bar.
        if (matchesDmConversation(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.DM_CONVERSATION,
                confidence = Confidence.MEDIUM,
                reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH,
                timestamp = now,
                evidenceExplanation = "Chat input composer and conversation message list detected without bottom navigation.",
                isDeviceVerified = false
            )
        }

        // 7. Evaluate DM Inbox signature
        // Requirements: Top navigation bar + message list container, no chat composer, no bottom navigation, no video.
        if (matchesDmInbox(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.DM_INBOX,
                confidence = Confidence.MEDIUM,
                reasonCode = ReasonCode.KNOWN_STRUCTURE_MATCH,
                timestamp = now,
                evidenceExplanation = "Direct message thread list and top action bar detected without bottom navigation or chat composer.",
                isDeviceVerified = false
            )
        }

        // 8. Evaluate Stories signature
        // Requirements: Segmented story progress bars at top + media container, no bottom navigation, no chat composer.
        if (matchesStories(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.STORIES,
                confidence = Confidence.MEDIUM,
                reasonCode = ReasonCode.KNOWN_STRUCTURE_MATCH,
                timestamp = now,
                evidenceExplanation = "Story progress indicators and media playback container detected without bottom navigation.",
                isDeviceVerified = false
            )
        }

        // 9. Evaluate User Profile signature
        // Requirements: Profile header structure present, and bottom nav profile tab active or profile layout container.
        if (matchesProfile(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.PROFILE,
                confidence = Confidence.MEDIUM,
                reasonCode = ReasonCode.KNOWN_STRUCTURE_MATCH,
                timestamp = now,
                evidenceExplanation = "User profile header structure and profile tab navigation detected.",
                isDeviceVerified = false
            )
        }

        // 10. Evaluate Ordinary Home Feed signature
        // Requirements: Bottom navigation bar on Home tab, horizontal stories tray near top, and feed scroll container.
        if (matchesHomeFeed(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.HOME_FEED,
                confidence = Confidence.MEDIUM,
                reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH,
                timestamp = now,
                evidenceExplanation = "Bottom navigation home tab active with top horizontal stories tray and scrollable feed.",
                isDeviceVerified = false
            )
        }

        // 11. Evaluate Full-Screen Video (e.g. Reel opened from DM, Explore video, or standalone video)
        // Visible video/pager container, but WITHOUT the dedicated Reels bottom navigation cues.
        if (matchesFullScreenVideo(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.FULL_SCREEN_VIDEO,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.AMBIGUOUS_STRUCTURE,
                timestamp = now,
                evidenceExplanation = "Media container or vertical pager detected without dedicated Reels tab bottom navigation indicators.",
                isDeviceVerified = false
            )
        }

        // 12. Unrecognized or insufficient signals fallback
        return ClassificationResult(
            candidateState = CandidateScreenState.UNKNOWN,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.NO_SUPPORTED_SIGNALS,
            timestamp = now,
            evidenceExplanation = "Observed layout contains no recognized structural navigation signatures (total nodes: ${snapshot.totalNodeCount}, max depth: ${snapshot.maxDepth}).",
            isDeviceVerified = false
        )
    }

    private fun hasConflictingSignals(snapshot: StructuralSnapshot): Boolean {
        // Bottom nav should not coexist with an active DM chat composer
        if (snapshot.hasBottomNavigationBar && snapshot.hasChatInputBar) {
            return true
        }
        // Story progress indicators should not coexist with a chat input composer
        if (snapshot.hasStoryProgressIndicators && snapshot.hasChatInputBar) {
            return true
        }
        // Profile header should not coexist with story progress indicators
        if (snapshot.hasProfileHeader && snapshot.hasStoryProgressIndicators) {
            return true
        }
        return false
    }

    private fun matchesDedicatedReelsTab(snapshot: StructuralSnapshot): Boolean {
        if (!snapshot.hasBottomNavigationBar) return false
        if (!snapshot.hasVerticalPager) return false
        if (snapshot.hasChatInputBar) return false
        if (snapshot.hasStoryProgressIndicators) return false
        if (snapshot.hasProfileHeader) return false

        // Check tab selection if available
        val tabMatches = snapshot.selectedBottomTabOrdinal == null ||
                snapshot.selectedBottomTabOrdinal == REELS_TAB_ORDINAL

        return tabMatches
    }

    private fun matchesDmConversation(snapshot: StructuralSnapshot): Boolean {
        return snapshot.hasChatInputBar &&
                snapshot.hasMessageList &&
                !snapshot.hasBottomNavigationBar
    }

    private fun matchesDmInbox(snapshot: StructuralSnapshot): Boolean {
        return snapshot.hasMessageList &&
                !snapshot.hasChatInputBar &&
                !snapshot.hasBottomNavigationBar &&
                snapshot.hasTopAppBar &&
                !snapshot.hasMediaPlaybackContainer
    }

    private fun matchesStories(snapshot: StructuralSnapshot): Boolean {
        return snapshot.hasStoryProgressIndicators &&
                snapshot.hasMediaPlaybackContainer &&
                !snapshot.hasBottomNavigationBar &&
                !snapshot.hasChatInputBar
    }

    private fun matchesProfile(snapshot: StructuralSnapshot): Boolean {
        if (snapshot.hasChatInputBar || snapshot.hasStoryProgressIndicators) return false
        if (!snapshot.hasProfileHeader) return false

        val tabMatches = snapshot.selectedBottomTabOrdinal == null ||
                snapshot.selectedBottomTabOrdinal == PROFILE_TAB_ORDINAL
        return tabMatches
    }

    private fun matchesHomeFeed(snapshot: StructuralSnapshot): Boolean {
        if (!snapshot.hasBottomNavigationBar) return false
        if (snapshot.hasChatInputBar) return false
        if (snapshot.hasStoryProgressIndicators) return false

        val isHomeTab = snapshot.selectedBottomTabOrdinal == null ||
                snapshot.selectedBottomTabOrdinal == HOME_TAB_ORDINAL

        return isHomeTab && snapshot.hasHorizontalStoryList
    }

    private fun matchesFullScreenVideo(snapshot: StructuralSnapshot): Boolean {
        if (snapshot.hasChatInputBar || snapshot.hasStoryProgressIndicators) return false
        return snapshot.hasMediaPlaybackContainer || snapshot.hasVerticalPager
    }
}
