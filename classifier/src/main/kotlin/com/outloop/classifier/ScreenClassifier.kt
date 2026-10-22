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
        val summary = createDiagnosticSummary(snapshot)

        // 1. Verify root accessibility node presence and minimal hierarchy size
        if (!snapshot.hasRootNode || snapshot.totalNodeCount == 0) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.MISSING_ROOT,
                timestamp = now,
                evidenceExplanation = "No accessibility root node or empty hierarchy structure available to inspect.",
                isDeviceVerified = false,
                diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.ROOT_UNAVAILABLE)
            )
        }

        // 2. Verify target application package
        if (snapshot.packageName != INSTAGRAM_PACKAGE) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.APP_NOT_FOREGROUND,
                timestamp = now,
                evidenceExplanation = "Active package '${snapshot.packageName ?: "null"}' is not Instagram ($INSTAGRAM_PACKAGE).",
                isDeviceVerified = false,
                diagnosticSummary = summary
            )
        }

        // 3. Verify foreground status
        if (!snapshot.isForeground) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.APP_NOT_FOREGROUND,
                timestamp = now,
                evidenceExplanation = "Instagram is not the active foreground application.",
                isDeviceVerified = false,
                diagnosticSummary = summary
            )
        }


        // 4. Evidence-based priority matching:
        // Priority 1: Stories signature
        // Requirements: Visible Story viewer root/components, or story progress indicators + media container without bottom nav.
        if (matchesStories(snapshot)) {
            val conf = if (snapshot.hasStoryViewer) Confidence.HIGH else Confidence.MEDIUM
            return ClassificationResult(
                candidateState = CandidateScreenState.STORIES,
                confidence = conf,
                reasonCode = ReasonCode.KNOWN_STRUCTURE_MATCH,
                timestamp = now,
                evidenceExplanation = "Story viewer structure detected.",
                isDeviceVerified = false,
                diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.CLASSIFIED)
            )
        }

        // Priority 2: DM Conversation signature
        // Requirements: Active chat composer (EditText or composer bar) + message list container.
        if (matchesDmConversation(snapshot)) {
            val conf = if (snapshot.hasDmComposer && (snapshot.hasDmMessageList || snapshot.hasMessageList)) Confidence.HIGH else Confidence.MEDIUM
            return ClassificationResult(
                candidateState = CandidateScreenState.DM_CONVERSATION,
                confidence = conf,
                reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH,
                timestamp = now,
                evidenceExplanation = "Chat input composer and conversation message list detected.",
                isDeviceVerified = false,
                diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.CLASSIFIED)
            )
        }

        // Priority 3: Dedicated Reels Tab signature
        // Requirements: Bottom navigation bar present with Clips tab selected, clips viewer / components / vertical pager active.
        if (matchesDedicatedReelsTab(snapshot)) {
            val isClipsTab = snapshot.selectedTabId == "clips_tab" || (snapshot.hasCommentsPanel && (snapshot.hasClipsViewer || snapshot.hasClipsComponents))
            val conf = if ((snapshot.hasClipsViewer || snapshot.hasClipsComponents) && isClipsTab) Confidence.HIGH else Confidence.MEDIUM
            return ClassificationResult(
                candidateState = CandidateScreenState.REELS_TAB,
                confidence = conf,
                reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH,
                timestamp = now,
                evidenceExplanation = if (snapshot.hasCommentsPanel) {
                    "Dedicated Reels container active with comments overlay panel."
                } else {
                    "Bottom navigation bar detected with Reels tab and full-screen clips container."
                },
                isDeviceVerified = false,
                diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.CLASSIFIED)
            )
        }

        // Priority 4: DM Inbox signature
        // Requirements: Inbox refreshable thread list or direct tab selected, without chat composer or feed controls.
        if (matchesDmInbox(snapshot)) {
            val conf = if (snapshot.hasDmInboxList) Confidence.HIGH else Confidence.MEDIUM
            return ClassificationResult(
                candidateState = CandidateScreenState.DM_INBOX,
                confidence = conf,
                reasonCode = ReasonCode.KNOWN_STRUCTURE_MATCH,
                timestamp = now,
                evidenceExplanation = "Direct message inbox thread list detected.",
                isDeviceVerified = false,
                diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.CLASSIFIED)
            )
        }

        // Priority 5: User Profile signature
        // Requirements: Profile header structure with profile stats or selected profile tab.
        if (matchesProfile(snapshot)) {
            val conf = if (snapshot.hasProfileHeaderStats && snapshot.selectedTabId == "profile_tab") Confidence.HIGH else Confidence.MEDIUM
            return ClassificationResult(
                candidateState = CandidateScreenState.PROFILE,
                confidence = conf,
                reasonCode = ReasonCode.KNOWN_STRUCTURE_MATCH,
                timestamp = now,
                evidenceExplanation = "User profile header structure and profile tab navigation detected.",
                isDeviceVerified = false,
                diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.CLASSIFIED)
            )
        }

        // Priority 6: Ordinary Home Feed signature
        // Requirements: Bottom navigation bar present with Home tab selected, or horizontal story tray, or feed post controls.
        if (matchesHomeFeed(snapshot)) {
            val conf = if (snapshot.selectedBottomTabOrdinal == HOME_TAB_ORDINAL || snapshot.hasHorizontalStoryList || snapshot.selectedTabId == "feed_tab") {
                Confidence.MEDIUM
            } else {
                Confidence.LOW
            }
            return ClassificationResult(
                candidateState = CandidateScreenState.HOME_FEED,
                confidence = conf,
                reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH,
                timestamp = now,
                evidenceExplanation = "Home feed active with feed controls, story tray, or feed tab selection.",
                isDeviceVerified = false,
                diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.CLASSIFIED)
            )
        }

        // Priority 7: Standalone Full-Screen Video (e.g. Reel opened from DM, Explore video)
        // Requirements: Media container or vertical pager detected without dedicated Reels bottom navigation.
        if (matchesFullScreenVideo(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.FULL_SCREEN_VIDEO,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.AMBIGUOUS_STRUCTURE,
                timestamp = now,
                evidenceExplanation = "Media container or vertical pager detected without dedicated Reels tab bottom navigation indicators.",
                isDeviceVerified = false,
                diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.CLASSIFIED)
            )
        }

        // 5. Check for conflicting or contradictory layout cues that could not be resolved by priority matching
        if (hasConflictingSignals(snapshot)) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.CONFLICTING_SIGNALS,
                timestamp = now,
                evidenceExplanation = "Conflicting structural signals detected simultaneously (e.g. profile header coexisting with vertical pager or bottom nav with chat composer).",
                isDeviceVerified = false,
                diagnosticSummary = summary
            )
        }

        // 6. Evaluate Ambiguous Profile Structure (profile header present, but bottom tab selection is unconfirmed)
        if (snapshot.hasProfileHeader && !snapshot.hasProfileHeaderStats && snapshot.selectedBottomTabOrdinal == null && snapshot.selectedTabId == null) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.INSUFFICIENT_EVIDENCE,
                timestamp = now,
                evidenceExplanation = "Profile-like header observed, but bottom navigation tab selection is unconfirmed.",
                isDeviceVerified = false,
                diagnosticSummary = summary
            )
        }

        // 7. Evaluate Truncated / Partial traversal when evidence is insufficient
        if (snapshot.reachedNodeLimit || snapshot.reachedDepthLimit || snapshot.isPartialTree) {
            return ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.TRUNCATED_TRAVERSAL,
                timestamp = now,
                evidenceExplanation = "Hierarchy traversal truncated before complete layout could be inspected (nodes: ${snapshot.totalNodeCount}, maxDepth: ${snapshot.maxDepth}, reachedNodeLimit: ${snapshot.reachedNodeLimit}, reachedDepthLimit: ${snapshot.reachedDepthLimit}, partialTree: ${snapshot.isPartialTree}).",
                isDeviceVerified = false,
                diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.PARTIAL_OR_TRUNCATED)
            )
        }

        // 8. Unrecognized or insufficient signals fallback
        return ClassificationResult(
            candidateState = CandidateScreenState.UNKNOWN,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.NO_SUPPORTED_SIGNALS,
            timestamp = now,
            evidenceExplanation = "Observed layout contains no recognized structural navigation signatures (total nodes: ${snapshot.totalNodeCount}, max depth: ${snapshot.maxDepth}).",
            isDeviceVerified = false,
            diagnosticSummary = summary.copy(observationOutcome = ObservationOutcome.NO_MATCHING_SIGNALS)
        )
    }

    private fun createDiagnosticSummary(snapshot: StructuralSnapshot): DiagnosticSummary {
        val activeSignals = mutableListOf<String>()
        if (snapshot.hasBottomNavigationBar) activeSignals.add("BOTTOM_NAV")
        if (snapshot.hasVerticalPager) activeSignals.add("VERTICAL_PAGER")
        if (snapshot.hasHorizontalPager) activeSignals.add("HORIZONTAL_PAGER")
        if (snapshot.hasUnknownOrientationPager) activeSignals.add("UNKNOWN_ORIENTATION_PAGER")
        if (snapshot.hasHorizontalStoryList) activeSignals.add("STORY_TRAY")
        if (snapshot.hasScrollableFeed) activeSignals.add("SCROLLABLE_FEED")
        if (snapshot.hasTopAppBar) activeSignals.add("TOP_APP_BAR")
        if (snapshot.hasChatInputBar) activeSignals.add("CHAT_INPUT")
        if (snapshot.hasMessageList) activeSignals.add("MESSAGE_LIST")
        if (snapshot.hasStoryProgressIndicators) activeSignals.add("STORY_PROGRESS")
        if (snapshot.hasMediaPlaybackContainer) activeSignals.add("MEDIA_CONTAINER")
        if (snapshot.hasProfileHeader) activeSignals.add("PROFILE_HEADER")
        if (snapshot.hasFeedPostControls) activeSignals.add("FEED_POST_CONTROLS")
        if (snapshot.hasDmInboxList) activeSignals.add("DM_INBOX_LIST")
        if (snapshot.hasDmComposer) activeSignals.add("DM_COMPOSER")
        if (snapshot.hasDmMessageList) activeSignals.add("DM_MESSAGE_LIST")
        if (snapshot.hasClipsViewer) activeSignals.add("CLIPS_VIEWER")
        if (snapshot.hasClipsComponents) activeSignals.add("CLIPS_COMPONENTS")
        if (snapshot.hasProfileHeaderStats) activeSignals.add("PROFILE_HEADER_STATS")
        if (snapshot.hasProfileTabs) activeSignals.add("PROFILE_TABS")
        if (snapshot.hasStoryViewer) activeSignals.add("STORY_VIEWER")

        val initialOutcome = if (!snapshot.hasRootNode || snapshot.totalNodeCount == 0) {
            ObservationOutcome.ROOT_UNAVAILABLE
        } else if (snapshot.reachedNodeLimit || snapshot.reachedDepthLimit || snapshot.isPartialTree) {
            ObservationOutcome.PARTIAL_OR_TRUNCATED
        } else {
            ObservationOutcome.NO_MATCHING_SIGNALS
        }

        return DiagnosticSummary(
            eventType = snapshot.eventType,
            isInstagramPackage = snapshot.packageName == INSTAGRAM_PACKAGE,
            hasRootNode = snapshot.hasRootNode,
            nodesVisited = snapshot.totalNodeCount,
            maxDepthReached = snapshot.maxDepth,
            reachedNodeLimit = snapshot.reachedNodeLimit,
            reachedDepthLimit = snapshot.reachedDepthLimit,
            hasBounds = snapshot.hasBounds,
            bottomNavObservable = snapshot.hasBottomNavigationBar || snapshot.bottomNavObserved,
            bottomNavChildCount = snapshot.bottomNavChildCount,
            selectedBottomTabOrdinal = snapshot.selectedBottomTabOrdinal,
            selectedTabId = snapshot.selectedTabId,
            hasFeedPostControls = snapshot.hasFeedPostControls,
            observedClassCounts = snapshot.observedClassCounts,
            activeStructuralSignals = activeSignals,
            observationOutcome = initialOutcome,
            rootAcquisitionMethod = snapshot.rootAcquisitionMethod,
            isPartialTree = snapshot.isPartialTree,
            structuralTreeDump = snapshot.structuralTreeDump
        )
    }

    private fun hasConflictingSignals(snapshot: StructuralSnapshot): Boolean {
        // Bottom nav should not coexist with an active DM chat composer (unless in full-screen media with bottom nav or comments panel)
        if (snapshot.hasBottomNavigationBar && snapshot.hasChatInputBar && !snapshot.hasMediaPlaybackContainer && !snapshot.hasDmComposer && !snapshot.hasCommentsPanel) {
            return true
        }
        // Story progress indicators should not coexist with a chat input composer (unless story viewer)
        if (snapshot.hasStoryProgressIndicators && snapshot.hasChatInputBar && !snapshot.hasStoryViewer) {
            return true
        }
        // Profile header should not coexist with story progress indicators
        if (snapshot.hasProfileHeader && snapshot.hasStoryProgressIndicators && !snapshot.hasStoryViewer) {
            return true
        }
        // Profile header should not coexist with a full-screen vertical pager
        if (snapshot.hasProfileHeader && snapshot.hasVerticalPager && snapshot.selectedTabId != "profile_tab") {
            return true
        }
        // Profile header should not coexist with Home tab or Reels tab selection
        if (snapshot.hasProfileHeader && snapshot.selectedBottomTabOrdinal in listOf(HOME_TAB_ORDINAL, REELS_TAB_ORDINAL)) {
            return true
        }
        // Profile header should not coexist with a horizontal stories tray unless confirmed on Profile tab (highlights)
        if (snapshot.hasProfileHeader && snapshot.hasHorizontalStoryList && snapshot.selectedBottomTabOrdinal != PROFILE_TAB_ORDINAL && snapshot.selectedTabId != "profile_tab") {
            return true
        }
        // Feed post controls should not coexist with a DM chat composer
        if (snapshot.hasFeedPostControls && snapshot.hasChatInputBar && !snapshot.hasDmComposer) {
            return true
        }
        // Feed post controls should not coexist with Profile tab selection
        if (snapshot.hasFeedPostControls && snapshot.selectedTabId == "profile_tab") {
            return true
        }
        // Home tab should not coexist with a vertical clips pager
        if (snapshot.selectedTabId == "feed_tab" && (snapshot.hasVerticalPager || snapshot.hasClipsViewer)) {
            return true
        }
        return false
    }

    private fun matchesDedicatedReelsTab(snapshot: StructuralSnapshot): Boolean {
        if (snapshot.hasStoryViewer || snapshot.hasDmComposer || snapshot.hasDmInboxList) return false
        if (snapshot.selectedTabId in listOf("feed_tab", "profile_tab", "search_tab", "direct_tab")) return false
        if (snapshot.selectedBottomTabOrdinal in listOf(HOME_TAB_ORDINAL, PROFILE_TAB_ORDINAL, 1)) return false

        val isClipsTab = snapshot.selectedTabId == "clips_tab" || snapshot.selectedBottomTabOrdinal == REELS_TAB_ORDINAL
        val hasClipsEvidence = snapshot.hasClipsViewer || snapshot.hasClipsComponents || snapshot.hasVerticalPager

        // Dedicated Reels requires bottom navigation + selected clips tab + clips evidence
        if (isClipsTab && hasClipsEvidence) {
            return !snapshot.hasChatInputBar || snapshot.hasClipsViewer || snapshot.hasClipsComponents || snapshot.hasCommentsPanel
        }

        // Active Reel with comments bottom sheet open:
        // Reel remains active underneath, so session is preserved and associated with the Reel
        if (snapshot.hasCommentsPanel && hasClipsEvidence && !snapshot.hasFeedPostControls && !snapshot.hasHorizontalStoryList) {
            return true
        }

        // Fallback: bottom nav + vertical pager + no feed controls / stories / profile / message list
        if (snapshot.hasBottomNavigationBar && snapshot.hasVerticalPager && !snapshot.hasFeedPostControls &&
            !snapshot.hasHorizontalStoryList && !snapshot.hasProfileHeader && !snapshot.hasMessageList) {
            return isClipsTab || snapshot.selectedBottomTabOrdinal == null
        }

        return false
    }

    private fun matchesDmConversation(snapshot: StructuralSnapshot): Boolean {
        // Comments sheet has a comment composer and comment list; must not be classified as DM
        if (snapshot.hasCommentsPanel) return false
        // If specific DM conversation indicators are present:
        if (snapshot.hasDmComposer && (snapshot.hasDmMessageList || snapshot.hasMessageList)) {
            return true
        }
        if (snapshot.hasDmMessageList && snapshot.hasChatInputBar) {
            return true
        }
        // General fallback: chat input + message list, no bottom nav, no feed controls
        if (snapshot.hasChatInputBar && snapshot.hasMessageList && !snapshot.hasBottomNavigationBar && !snapshot.hasFeedPostControls) {
            return true
        }
        return false
    }

    private fun matchesDmInbox(snapshot: StructuralSnapshot): Boolean {
        if (snapshot.hasDmComposer) return false
        if (snapshot.hasStoryViewer) return false
        if (snapshot.selectedTabId in listOf("feed_tab", "clips_tab", "profile_tab")) return false
        if (snapshot.selectedBottomTabOrdinal in listOf(HOME_TAB_ORDINAL, REELS_TAB_ORDINAL, PROFILE_TAB_ORDINAL)) return false

        // Explicit DM Inbox list:
        if (snapshot.hasDmInboxList) {
            return true
        }
        // Direct tab selected without feed post controls or clips viewer:
        if (snapshot.selectedTabId == "direct_tab" && !snapshot.hasFeedPostControls && !snapshot.hasClipsViewer && !snapshot.hasVerticalPager) {
            return true
        }
        // Fallback signature: message list + top bar, without chat composer, bottom nav, media playback, or feed controls
        return snapshot.hasMessageList &&
                !snapshot.hasChatInputBar &&
                !snapshot.hasBottomNavigationBar &&
                snapshot.hasTopAppBar &&
                !snapshot.hasMediaPlaybackContainer &&
                !snapshot.hasFeedPostControls
    }

    private fun matchesStories(snapshot: StructuralSnapshot): Boolean {
        if (snapshot.hasStoryViewer) {
            return true
        }
        return snapshot.hasStoryProgressIndicators &&
                snapshot.hasMediaPlaybackContainer &&
                !snapshot.hasBottomNavigationBar &&
                !snapshot.hasChatInputBar
    }

    private fun matchesProfile(snapshot: StructuralSnapshot): Boolean {
        if (snapshot.hasStoryViewer || snapshot.hasDmComposer || snapshot.hasDmInboxList) return false
        if (snapshot.selectedTabId in listOf("feed_tab", "clips_tab", "direct_tab")) return false
        if (snapshot.selectedBottomTabOrdinal in listOf(HOME_TAB_ORDINAL, REELS_TAB_ORDINAL)) return false

        // Explicit profile evidence:
        val hasProfileSpecificEvidence = snapshot.hasProfileHeaderStats || snapshot.hasProfileTabs
        if (snapshot.selectedTabId == "profile_tab" && (hasProfileSpecificEvidence || snapshot.hasProfileHeader)) {
            return true
        }
        if (snapshot.hasBottomNavigationBar && snapshot.selectedBottomTabOrdinal == PROFILE_TAB_ORDINAL &&
            (hasProfileSpecificEvidence || snapshot.hasProfileHeader)) {
            return true
        }
        if (hasProfileSpecificEvidence && snapshot.hasProfileHeader && !snapshot.hasFeedPostControls && !snapshot.hasVerticalPager) {
            return true
        }

        // Fallback: selected profile_tab
        if (snapshot.selectedTabId == "profile_tab") {
            return true
        }

        return false
    }

    private fun matchesHomeFeed(snapshot: StructuralSnapshot): Boolean {
        if (snapshot.hasStoryViewer || snapshot.hasDmComposer || snapshot.hasDmInboxList) return false
        if (snapshot.hasProfileHeader) return false
        if (snapshot.hasChatInputBar && !snapshot.hasCommentsPanel) return false
        if (snapshot.hasStoryProgressIndicators) return false
        if (snapshot.hasVerticalPager) return false
        if (snapshot.selectedTabId in listOf("clips_tab", "profile_tab", "direct_tab")) return false
        if (snapshot.selectedBottomTabOrdinal != null &&
            snapshot.selectedBottomTabOrdinal in listOf(REELS_TAB_ORDINAL, PROFILE_TAB_ORDINAL)) {
            return false
        }

        // Definitive evidence: selected feed_tab or presence of feed post controls
        if (snapshot.selectedTabId == "feed_tab" || snapshot.hasFeedPostControls) {
            return true
        }

        if (!snapshot.hasBottomNavigationBar) return false

        // Matches if stories tray is present, or Home tab ordinal is active, or scrollable feed container is present
        return snapshot.hasHorizontalStoryList ||
                snapshot.selectedBottomTabOrdinal == HOME_TAB_ORDINAL ||
                snapshot.hasScrollableFeed
    }

    private fun matchesFullScreenVideo(snapshot: StructuralSnapshot): Boolean {
        if (snapshot.hasStoryViewer || snapshot.hasStoryProgressIndicators) return false
        if (snapshot.hasDmInboxList) return false
        if (snapshot.selectedTabId in listOf("feed_tab", "profile_tab", "search_tab")) return false
        if (snapshot.selectedBottomTabOrdinal in listOf(HOME_TAB_ORDINAL, PROFILE_TAB_ORDINAL)) return false

        // Dedicated Reels tab check: if bottom nav has clips tab selected, it belongs to REELS_TAB, not standalone
        if (snapshot.hasBottomNavigationBar && (snapshot.selectedTabId == "clips_tab" || snapshot.selectedBottomTabOrdinal == REELS_TAB_ORDINAL)) {
            return false
        }

        // Standalone video requires absence of bottom navigation bar.
        if (snapshot.hasBottomNavigationBar) return false

        // If message list is present without media, it's a conversation/inbox, not full screen video.
        if (snapshot.hasMessageList && !snapshot.hasMediaPlaybackContainer && !snapshot.hasClipsViewer && !snapshot.hasClipsComponents) return false

        val hasSpecificClipsEvidence = snapshot.hasClipsViewer || snapshot.hasClipsComponents
        // Traversal node limit or partial tree prevents proving absence of bottom navigation for generic video.
        if (!hasSpecificClipsEvidence && (snapshot.reachedNodeLimit || snapshot.isPartialTree)) {
            return false
        }

        // A Reel opened from DM contains Clips components / viewer, and lacks bottom navigation.
        return snapshot.hasClipsViewer || snapshot.hasClipsComponents || snapshot.hasMediaPlaybackContainer || snapshot.hasVerticalPager
    }
}

