package com.outloop.classifier

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Synthetic unit tests for [ScreenClassifier].
 *
 * NOTE: All fixtures in this test suite are purely synthetic layout models used to verify
 * classifier inference logic, confidence policies, and error handling.
 * Passing these unit tests verifies the algorithm's decision-making on synthetic inputs,
 * but DOES NOT validate actual detection against Instagram's live production interface.
 * Real-device observation is required to establish production accuracy.
 */
class ScreenClassifierTest {

    private lateinit var classifier: ScreenClassifier
    private val fixedTimestamp = 1700000000000L

    @BeforeEach
    fun setUp() {
        classifier = ScreenClassifier(clock = { fixedTimestamp })
    }

    // 1. Empty structural metadata
    @Test
    fun testEmptyStructuralMetadata() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 0,
            maxDepth = 0
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.MISSING_ROOT, result.reasonCode)
        assertFalse(result.isDeviceVerified)
    }

    // 2. Missing accessibility root
    @Test
    fun testMissingAccessibilityRoot() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = false,
            totalNodeCount = 0
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.MISSING_ROOT, result.reasonCode)
    }

    // 3. Ambiguous video-like screen (video container without dedicated Reels navigation)
    @Test
    fun testAmbiguousVideoLikeScreen() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 15,
            maxDepth = 5,
            hasMediaPlaybackContainer = true,
            hasBottomNavigationBar = false, // Crucial: No bottom navigation
            hasChatInputBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
        assertFalse(result.isDeviceVerified)
    }

    // 4. Synthetic dedicated-Reels pattern
    @Test
    fun testSyntheticDedicatedReelsPattern() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 42,
            maxDepth = 8,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 3, // 4th tab (Reels)
            hasVerticalPager = true,
            hasChatInputBar = false,
            hasStoryProgressIndicators = false,
            hasProfileHeader = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
        assertFalse(result.isDeviceVerified)
    }

    // 5. Synthetic DM inbox pattern
    @Test
    fun testSyntheticDmInboxPattern() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 35,
            maxDepth = 6,
            hasTopAppBar = true,
            hasMessageList = true,
            hasBottomNavigationBar = false,
            hasChatInputBar = false,
            hasMediaPlaybackContainer = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.DM_INBOX, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // 6. Synthetic DM conversation pattern
    @Test
    fun testSyntheticDmConversationPattern() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 50,
            maxDepth = 7,
            hasTopAppBar = true,
            hasMessageList = true,
            hasChatInputBar = true, // Chat composer present
            hasBottomNavigationBar = false // Bottom tabs hidden during conversation
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.DM_CONVERSATION, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // 7. Synthetic Stories pattern
    @Test
    fun testSyntheticStoriesPattern() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 20,
            maxDepth = 5,
            hasStoryProgressIndicators = true,
            hasMediaPlaybackContainer = true,
            hasBottomNavigationBar = false,
            hasChatInputBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.STORIES, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // 8. Synthetic profile pattern
    @Test
    fun testSyntheticProfilePattern() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 60,
            maxDepth = 9,
            hasProfileHeader = true,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 4, // 5th tab (Profile)
            hasChatInputBar = false,
            hasStoryProgressIndicators = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // 9. Synthetic ordinary-feed pattern
    @Test
    fun testSyntheticOrdinaryFeedPattern() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 55,
            maxDepth = 8,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 0, // Home tab
            hasHorizontalStoryList = true, // Horizontal story tray at top
            observedWidgetClasses = setOf("RecyclerView", "FrameLayout", "ImageView"),
            hasChatInputBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // 10. Conflicting signals (e.g. bottom nav simultaneously visible with active chat composer)
    @Test
    fun testConflictingSignals() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 40,
            hasBottomNavigationBar = true,
            hasChatInputBar = true // Contradictory in standard Instagram UX
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.CONFLICTING_SIGNALS, result.reasonCode)
    }

    // 11. Unsupported structures
    @Test
    fun testUnsupportedStructures() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 8,
            maxDepth = 2,
            observedWidgetClasses = setOf("View", "FrameLayout")
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.NO_SUPPORTED_SIGNALS, result.reasonCode)
    }

    // 12. Non-Instagram package
    @Test
    fun testNonInstagramPackage() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.example.otherapp",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 20
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.APP_NOT_FOREGROUND, result.reasonCode)
    }

    // 13. Reel opened from DM does not classify as dedicated REELS_TAB
    @Test
    fun testVideoOpenedFromDmDoesNotClassifyAsReelsTab() {
        // A video or Reel opened from a DM conversation has media playback,
        // but DOES NOT have the dedicated bottom navigation bar.
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 25,
            maxDepth = 6,
            hasVerticalPager = true,
            hasMediaPlaybackContainer = true,
            hasBottomNavigationBar = false, // NO bottom navigation
            hasChatInputBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        // Must classify as FULL_SCREEN_VIDEO with LOW confidence, NEVER REELS_TAB
        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
        assertFalse(result.candidateState == CandidateScreenState.REELS_TAB)
    }

    // 14. Instagram in background returns APP_NOT_FOREGROUND
    @Test
    fun testInstagramInBackgroundReturnsAppNotForeground() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = false,
            hasRootNode = true,
            totalNodeCount = 30
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.APP_NOT_FOREGROUND, result.reasonCode)
    }

    // 15. Profile-like header coexisting with vertical pager returns CONFLICTING_SIGNALS
    @Test
    fun testProfileHeaderWithVerticalPagerReturnsConflictingSignals() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 45,
            hasBottomNavigationBar = true,
            hasVerticalPager = true, // Reels/video indicator
            hasProfileHeader = true  // Profile indicator
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.CONFLICTING_SIGNALS, result.reasonCode)
        assertFalse(result.candidateState == CandidateScreenState.PROFILE)
    }

    // 16. Profile-like header coexisting with horizontal stories tray returns CONFLICTING_SIGNALS
    @Test
    fun testProfileHeaderWithHorizontalStoryListReturnsConflictingSignals() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 50,
            hasBottomNavigationBar = true,
            hasHorizontalStoryList = true, // Home feed indicator
            hasProfileHeader = true       // Profile indicator
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.CONFLICTING_SIGNALS, result.reasonCode)
        assertFalse(result.candidateState == CandidateScreenState.PROFILE)
    }

    // 17. Profile header with Home tab ordinal (0) returns CONFLICTING_SIGNALS
    @Test
    fun testProfileHeaderWithHomeTabOrdinalReturnsConflictingSignals() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 45,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 0, // Home tab selected
            hasProfileHeader = true       // Contradicts home tab
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.CONFLICTING_SIGNALS, result.reasonCode)
    }

    // 18. Profile header with unconfirmed tab ordinal returns INSUFFICIENT_EVIDENCE
    @Test
    fun testProfileHeaderWithUndeterminedTabOrdinalReturnsInsufficientEvidence() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 45,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = null, // Undetermined tab ordinal
            hasProfileHeader = true,
            hasVerticalPager = false,
            hasHorizontalStoryList = false
        )

        val result = classifier.classify(syntheticSnapshot)

        // Must NOT return PROFILE with MEDIUM confidence when tab confirmation is missing
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.INSUFFICIENT_EVIDENCE, result.reasonCode)
    }

    // 19. Dedicated Reels tab with null tab ordinal still classifies as REELS_TAB via vertical pager + bottom nav
    @Test
    fun testDedicatedReelsTabWithNullTabOrdinalClassifiesAsReelsTab() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 40,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = null, // Tab ordinal not resolved by accessibility
            hasVerticalPager = true,
            hasHorizontalStoryList = false,
            hasProfileHeader = false,
            hasChatInputBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // 20. Home feed with null tab ordinal still classifies as HOME_FEED via horizontal stories tray + bottom nav
    @Test
    fun testHomeFeedWithNullTabOrdinalClassifiesAsHomeFeed() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 40,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = null, // Tab ordinal not resolved
            hasHorizontalStoryList = true,
            hasVerticalPager = false,
            hasProfileHeader = false,
            hasChatInputBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // 21. Scrolled Home feed without horizontal stories tray classifies as HOME_FEED via bottom nav Home tab
    @Test
    fun testScrolledHomeFeedWithoutStoryTrayClassifiesAsHomeFeed() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 70,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 0, // Home tab explicitly selected
            hasHorizontalStoryList = false, // Stories tray scrolled off-screen
            hasScrollableFeed = true,       // Scrollable feed container present
            hasVerticalPager = false,
            hasProfileHeader = false,
            hasChatInputBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // 22. Scrolled Home feed without selected ordinal classifies as HOME_FEED via scrollable feed + bottom nav
    @Test
    fun testScrolledHomeFeedViaFeedContainerWithoutSelectedOrdinalClassifiesAsHomeFeed() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 65,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = null,
            hasHorizontalStoryList = false,
            hasScrollableFeed = true,
            hasVerticalPager = false,
            hasProfileHeader = false,
            hasChatInputBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // 23. Standalone video/reel opened from DM (without bottom nav) classifies as FULL_SCREEN_VIDEO
    @Test
    fun testReelOpenedFromDmWithoutBottomNavClassifiesAsFullScreenVideo() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 30,
            hasBottomNavigationBar = false, // No bottom navigation when opened from DM
            hasVerticalPager = true,
            hasMediaPlaybackContainer = true,
            hasChatInputBar = false,
            hasStoryProgressIndicators = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
    }

    // 24. Video with bottom navigation does not classify as FULL_SCREEN_VIDEO
    @Test
    fun testVideoWithBottomNavDoesNotClassifyAsFullScreenVideo() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 45,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 3,
            hasVerticalPager = true,
            hasMediaPlaybackContainer = true
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.FULL_SCREEN_VIDEO)
    }

    // 25. Diagnostic summary correctly populates metadata and structural signals
    @Test
    fun testDiagnosticSummaryPopulation() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 82,
            maxDepth = 9,
            hasBottomNavigationBar = true,
            bottomNavObserved = true,
            bottomNavChildCount = 5,
            selectedBottomTabOrdinal = 3,
            hasVerticalPager = true,
            hasBounds = true,
            observedClassCounts = mapOf("ViewPager2" to 1, "FrameLayout" to 12),
            eventType = "TYPE_WINDOW_STATE_CHANGED"
        )

        val result = classifier.classify(syntheticSnapshot)
        val diag = result.diagnosticSummary

        org.junit.jupiter.api.Assertions.assertNotNull(diag)
        assertEquals("TYPE_WINDOW_STATE_CHANGED", diag?.eventType)
        assertEquals(true, diag?.isInstagramPackage)
        assertEquals(82, diag?.nodesVisited)
        assertEquals(9, diag?.maxDepthReached)
        assertEquals(true, diag?.bottomNavObservable)
        assertEquals(5, diag?.bottomNavChildCount)
        assertEquals(3, diag?.selectedBottomTabOrdinal)
        assertTrue(diag?.activeStructuralSignals?.contains("BOTTOM_NAV") == true)
        assertTrue(diag?.activeStructuralSignals?.contains("VERTICAL_PAGER") == true)
    }

    // 26. Truncated traversal does not falsely claim absence of bottom nav
    @Test
    fun testTruncatedTraversalDoesNotClassifyAsFullScreenVideo() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 250,
            reachedNodeLimit = true,
            hasVerticalPager = true,
            hasBottomNavigationBar = false // Unobserved because traversal stopped early
        )

        val result = classifier.classify(syntheticSnapshot)

        // Must NOT match FULL_SCREEN_VIDEO
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.TRUNCATED_TRAVERSAL, result.reasonCode)
        assertEquals(ObservationOutcome.PARTIAL_OR_TRUNCATED, result.diagnosticSummary?.observationOutcome)
    }

    // 27. Partial tree traversal flag preserves insufficient-evidence outcome
    @Test
    fun testPartialTreePreservesPartialOutcome() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 45,
            isPartialTree = true,
            hasMediaPlaybackContainer = true,
            hasBottomNavigationBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.TRUNCATED_TRAVERSAL, result.reasonCode)
        assertEquals(ObservationOutcome.PARTIAL_OR_TRUNCATED, result.diagnosticSummary?.observationOutcome)
    }

    // 28. Horizontal pager does not trigger VERTICAL_PAGER or dedicated Reels
    @Test
    fun testHorizontalPagerDoesNotTriggerDedicatedReels() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 60,
            hasHorizontalPager = true,
            hasVerticalPager = false,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 0 // Home tab
        )

        val result = classifier.classify(syntheticSnapshot)

        assertFalse(result.candidateState == CandidateScreenState.REELS_TAB)
        assertFalse(result.candidateState == CandidateScreenState.FULL_SCREEN_VIDEO)
    }

    // 29. Reel opened from DM with reply composer classifies as FULL_SCREEN_VIDEO, NOT DM_CONVERSATION
    @Test
    fun testReelOpenedFromDmWithReplyComposerClassifiesAsFullScreenVideo() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 50,
            hasVerticalPager = true,
            hasMediaPlaybackContainer = true,
            hasChatInputBar = true, // Inline reply composer
            hasMessageList = false, // Standalone reel, not message thread
            hasBottomNavigationBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
        assertFalse(result.candidateState == CandidateScreenState.DM_CONVERSATION)
        assertFalse(result.candidateState == CandidateScreenState.REELS_TAB)
    }

    // 30. Definitive bottom nav preserves classification even if leaf descendants reached depth limit
    @Test
    fun testDefinitiveBottomNavPreservesClassificationDespiteDepthLimit() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 194,
            maxDepth = 14,
            reachedDepthLimit = true, // Deep feed card descendants hit depth 14
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 0, // Tab 0 = Home definitive
            hasHorizontalStoryList = true,
            hasScrollableFeed = true
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
        assertFalse(result.reasonCode == ReasonCode.TRUNCATED_TRAVERSAL)
    }

    // 31. Profile with story highlights does not trigger CONFLICTING_SIGNALS when profile tab is active
    @Test
    fun testProfileWithHighlightsDoesNotConflict() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 120,
            hasProfileHeader = true,
            hasHorizontalStoryList = true, // Highlights on profile
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 4 // Profile tab active
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
        assertFalse(result.reasonCode == ReasonCode.CONFLICTING_SIGNALS)
    }

    // 32. Home feed does not classify as Profile even if profile elements are present
    @Test
    fun testHomeFeedDoesNotClassifyAsProfile() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 180,
            hasBottomNavigationBar = true,
            selectedBottomTabOrdinal = 0, // Home tab active
            hasHorizontalStoryList = true,
            hasScrollableFeed = true
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.PROFILE)
    }

    // 33. Reel opened from DM without bottom nav does not classify as dedicated Reels tab
    @Test
    fun testReelOpenedFromDmDoesNotClassifyAsDedicatedReelsTab() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 60,
            maxDepth = 14,
            reachedDepthLimit = true,
            hasVerticalPager = true,
            hasMediaPlaybackContainer = true,
            hasBottomNavigationBar = false // No bottom nav in shared reel viewer
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.REELS_TAB)
    }

    // 34. Synthetic test: Home feed with selected feed_tab and feed post controls classifies as HOME_FEED
    @Test
    fun testSyntheticHomeFeedWithSelectedFeedTabAndFeedPostControls() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 95,
            hasBottomNavigationBar = true,
            selectedTabId = "feed_tab",
            selectedBottomTabOrdinal = 0,
            hasFeedPostControls = true,
            hasHorizontalPager = true,
            hasScrollableFeed = true,
            hasMediaPlaybackContainer = true // Post video playing inline
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.DM_INBOX)
        assertFalse(result.candidateState == CandidateScreenState.FULL_SCREEN_VIDEO)
    }

    // 35. Synthetic test: Feed post controls prevent DM_INBOX classification
    @Test
    fun testSyntheticFeedPostControlsPreventDmInbox() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 90,
            hasFeedPostControls = true,
            hasScrollableFeed = true,
            hasTopAppBar = true,
            hasBottomNavigationBar = false // Even if bottom nav was missed
        )

        val result = classifier.classify(syntheticSnapshot)

        assertFalse(result.candidateState == CandidateScreenState.DM_INBOX)
    }

    // 36. Synthetic test: Genuine DM Inbox without feed post controls classifies as DM_INBOX
    @Test
    fun testSyntheticGenuineDmInboxWithoutFeedControls() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 96,
            hasFeedPostControls = false,
            hasMessageList = true,
            hasTopAppBar = true,
            hasBottomNavigationBar = false,
            hasChatInputBar = false,
            hasMediaPlaybackContainer = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.DM_INBOX, result.candidateState)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // 37. Synthetic test: DM conversation with chat input and no feed controls classifies as DM_CONVERSATION
    @Test
    fun testSyntheticDmConversationWithChatInputAndNoFeedControls() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 85,
            hasFeedPostControls = false,
            hasChatInputBar = true,
            hasMessageList = true,
            hasBottomNavigationBar = false
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.DM_CONVERSATION, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.FULL_SCREEN_VIDEO)
    }

    // 38. Synthetic test: Dedicated Reels tab with clips_tab selected classifies as REELS_TAB
    @Test
    fun testSyntheticDedicatedReelsWithClipsTabSelected() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 75,
            hasBottomNavigationBar = true,
            selectedTabId = "clips_tab",
            selectedBottomTabOrdinal = 3,
            hasVerticalPager = true,
            hasMediaPlaybackContainer = true
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.FULL_SCREEN_VIDEO)
    }

    // 39. Synthetic test: Profile with profile_tab selected classifies as PROFILE
    @Test
    fun testSyntheticProfileWithProfileTabSelected() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 110,
            hasBottomNavigationBar = true,
            selectedTabId = "profile_tab",
            selectedBottomTabOrdinal = 4,
            hasProfileHeader = true,
            hasScrollableFeed = true
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
    }

    // 40. Synthetic test: Contradictory feed post controls and chat input returns CONFLICTING_SIGNALS
    @Test
    fun testSyntheticContradictoryFeedControlsAndChatInputReturnsConflictingSignals() {
        val syntheticSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 80,
            hasFeedPostControls = true,
            hasChatInputBar = true // Contradicts feed
        )

        val result = classifier.classify(syntheticSnapshot)

        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.CONFLICTING_SIGNALS, result.reasonCode)
    }

    // =========================================================================
    // Phase 3 — Observed Evidence Regression Test Suite (Scenarios 1 - 13)
    // =========================================================================

    // Scenario 1: Home Feed with selected feed_tab, visible feed content, and off-screen DM/Reels pages
    @Test
    fun testPhase3_Scenario1_HomeFeedClassification() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 120,
            hasBottomNavigationBar = true,
            selectedTabId = "feed_tab",
            selectedBottomTabOrdinal = 0,
            hasFeedPostControls = true,
            hasHorizontalStoryList = true
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // Scenario 2: Dedicated Reels with selected clips_tab, visible Clips resource IDs, and horizontal pager class metadata
    @Test
    fun testPhase3_Scenario2_DedicatedReelsClassification() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 95,
            hasBottomNavigationBar = true,
            selectedTabId = "clips_tab",
            selectedBottomTabOrdinal = 3,
            hasClipsViewer = true,
            hasClipsComponents = true,
            hasVerticalPager = true,
            hasHorizontalPager = false
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // Scenario 3: DM Inbox with selected direct_tab and visible inbox list
    @Test
    fun testPhase3_Scenario3_DmInboxClassification() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 80,
            selectedTabId = "direct_tab",
            hasDmInboxList = true,
            hasMessageList = true,
            hasTopAppBar = true,
            hasBottomNavigationBar = false
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.DM_INBOX, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // Scenario 4: DM Conversation with visible message list and composer
    @Test
    fun testPhase3_Scenario4_DmConversationClassification() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 110,
            hasDmComposer = true,
            hasDmMessageList = true,
            hasChatInputBar = true,
            hasMessageList = true,
            hasBottomNavigationBar = false
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.DM_CONVERSATION, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // Scenario 5: Reel opened from a DM with visible Clips structures and no main bottom navigation
    @Test
    fun testPhase3_Scenario5_ReelOpenedFromDmClassification() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 55,
            hasClipsViewer = true,
            hasClipsComponents = true,
            hasVerticalPager = true,
            hasChatInputBar = true, // Quick reply box in DM Reel
            hasBottomNavigationBar = false
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
    }

    // Scenario 6: Profile with selected profile_tab and visible profile-specific structures
    @Test
    fun testPhase3_Scenario6_ProfileClassification() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 130,
            hasBottomNavigationBar = true,
            selectedTabId = "profile_tab",
            selectedBottomTabOrdinal = 4,
            hasProfileHeader = true,
            hasProfileHeaderStats = true,
            hasProfileTabs = true
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // Scenario 7: Stories with visible reel_viewer_root while underlying main pager remains in tree
    @Test
    fun testPhase3_Scenario7_StoriesClassification() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 60,
            hasStoryViewer = true,
            hasStoryProgressIndicators = true,
            hasMediaPlaybackContainer = true,
            hasBottomNavigationBar = true // Background pager bottom nav remains
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.STORIES, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // Scenario 8: Off-screen Feed structures must not make an active DM Conversation classify as Home Feed
    @Test
    fun testPhase3_Scenario8_DmConversationNotOverriddenByOffScreenFeed() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 110,
            hasDmComposer = true,
            hasDmMessageList = true,
            hasChatInputBar = true,
            hasMessageList = true,
            hasBottomNavigationBar = false
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.DM_CONVERSATION, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.HOME_FEED)
    }

    // Scenario 9: Off-screen Clips viewer must not make Profile classify as Reels
    @Test
    fun testPhase3_Scenario9_ProfileNotOverriddenByOffScreenClips() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 90,
            hasBottomNavigationBar = true,
            selectedTabId = "profile_tab",
            selectedBottomTabOrdinal = 4,
            hasProfileHeader = true,
            hasProfileHeaderStats = true
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.REELS_TAB)
    }

    // Scenario 10: Truncated tree with strong specific evidence can classify; truncated tree with insufficient evidence remains unknown
    @Test
    fun testPhase3_Scenario10_TruncatedClassificationEvidence() {
        // Strong evidence: DM Conversation truncated at depth 14
        val strongSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 180,
            maxDepth = 14,
            reachedDepthLimit = true,
            hasDmComposer = true,
            hasDmMessageList = true
        )
        val strongResult = classifier.classify(strongSnapshot)
        assertEquals(CandidateScreenState.DM_CONVERSATION, strongResult.candidateState)
        assertEquals(ObservationOutcome.CLASSIFIED, strongResult.diagnosticSummary?.observationOutcome)

        // Insufficient evidence truncated at depth 14
        val weakSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 90,
            maxDepth = 14,
            reachedDepthLimit = true
        )
        val weakResult = classifier.classify(weakSnapshot)
        assertEquals(CandidateScreenState.UNKNOWN, weakResult.candidateState)
        assertEquals(ReasonCode.TRUNCATED_TRAVERSAL, weakResult.reasonCode)
        assertEquals(ObservationOutcome.PARTIAL_OR_TRUNCATED, weakResult.diagnosticSummary?.observationOutcome)
    }

    // Scenario 11: Missing root remains ROOT_UNAVAILABLE
    @Test
    fun testPhase3_Scenario11_MissingRootOutcome() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = false,
            totalNodeCount = 0
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.MISSING_ROOT, result.reasonCode)
        assertEquals(ObservationOutcome.ROOT_UNAVAILABLE, result.diagnosticSummary?.observationOutcome)
    }

    // Scenario 12: Invalid or empty snapshot handled safely
    @Test
    fun testPhase3_Scenario12_EmptySnapshotHandledSafely() {
        val snapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 5,
            hasBounds = false
        )
        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.NO_SUPPORTED_SIGNALS, result.reasonCode)
    }

    // Scenario 13: Selected tab reporting matches actual selected resource ID and uses documented 0-based ordinal
    @Test
    fun testPhase3_Scenario13_TabSelectionAndOrdinalMapping() {
        // Feed tab
        val feedSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            selectedTabId = "feed_tab",
            selectedBottomTabOrdinal = 0,
            hasBottomNavigationBar = true,
            hasFeedPostControls = true,
            totalNodeCount = 50
        )
        assertEquals(CandidateScreenState.HOME_FEED, classifier.classify(feedSnapshot).candidateState)

        // Clips tab
        val clipsSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            selectedTabId = "clips_tab",
            selectedBottomTabOrdinal = 3,
            hasBottomNavigationBar = true,
            hasClipsViewer = true,
            totalNodeCount = 50
        )
        assertEquals(CandidateScreenState.REELS_TAB, classifier.classify(clipsSnapshot).candidateState)

        // Profile tab
        val profileSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            selectedTabId = "profile_tab",
            selectedBottomTabOrdinal = 4,
            hasBottomNavigationBar = true,
            hasProfileHeaderStats = true,
            totalNodeCount = 50
        )
        assertEquals(CandidateScreenState.PROFILE, classifier.classify(profileSnapshot).candidateState)
    }

    // --- Reliability Audit Tests ---

    // Audit 1: A missing root must produce an unavailable observation and must not be treated as fresh evidence for a new screen
    @Test
    fun testAudit_MissingRootProducesUnavailableObservationAndNotFreshScreen() {
        val missingRootSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = false,
            totalNodeCount = 0
        )
        val result = classifier.classify(missingRootSnapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.MISSING_ROOT, result.reasonCode)
        assertEquals(ObservationOutcome.ROOT_UNAVAILABLE, result.diagnosticSummary?.observationOutcome)
        assertFalse(result.isDeviceVerified)
        assertEquals(0, result.diagnosticSummary?.nodesVisited)
    }

    // Audit 2: A partial or truncated hierarchy must not produce a high-confidence classification from insufficient signals
    @Test
    fun testAudit_PartialOrTruncatedHierarchyProducesLowConfidenceUnknownWhenInsufficientSignals() {
        val truncatedGenericSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 80,
            maxDepth = 14,
            reachedDepthLimit = true,
            isPartialTree = true,
            hasScrollableFeed = true // generic scrollable feed only, no tab, no feed controls, no composer
        )
        val result = classifier.classify(truncatedGenericSnapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.TRUNCATED_TRAVERSAL, result.reasonCode)
        assertEquals(ObservationOutcome.PARTIAL_OR_TRUNCATED, result.diagnosticSummary?.observationOutcome)
    }

    // Audit 3: Repeated identical observations must not create incorrect screen transitions
    @Test
    fun testAudit_RepeatedIdenticalObservationsProduceConsistentResultsWithoutDrift() {
        val reelsSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasBottomNavigationBar = true,
            selectedTabId = "clips_tab",
            selectedBottomTabOrdinal = 3,
            hasClipsViewer = true,
            hasVerticalPager = true,
            totalNodeCount = 75
        )

        // Classify 5 consecutive times
        val results = (1..5).map { classifier.classify(reelsSnapshot) }
        results.forEach { res ->
            assertEquals(CandidateScreenState.REELS_TAB, res.candidateState)
            assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, res.reasonCode)
            assertEquals(ObservationOutcome.CLASSIFIED, res.diagnosticSummary?.observationOutcome)
        }
    }

    // Audit 4: A sequence of old-screen -> missing-root -> new-screen observations must not let old screen bleed into missing root
    @Test
    fun testAudit_ObservationSequenceTransitionDoesNotBleedOldScreenIntoMissingRoot() {
        val feedSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasBottomNavigationBar = true,
            selectedTabId = "feed_tab",
            selectedBottomTabOrdinal = 0,
            hasFeedPostControls = true,
            totalNodeCount = 90
        )
        val missingRootSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = false,
            totalNodeCount = 0
        )
        val reelsSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasBottomNavigationBar = true,
            selectedTabId = "clips_tab",
            selectedBottomTabOrdinal = 3,
            hasClipsViewer = true,
            totalNodeCount = 70
        )

        val feedResult = classifier.classify(feedSnapshot)
        assertEquals(CandidateScreenState.HOME_FEED, feedResult.candidateState)
        assertEquals(ObservationOutcome.CLASSIFIED, feedResult.diagnosticSummary?.observationOutcome)

        val nullRootResult = classifier.classify(missingRootSnapshot)
        assertEquals(CandidateScreenState.UNKNOWN, nullRootResult.candidateState)
        assertEquals(ReasonCode.MISSING_ROOT, nullRootResult.reasonCode)
        assertEquals(ObservationOutcome.ROOT_UNAVAILABLE, nullRootResult.diagnosticSummary?.observationOutcome)

        val reelsResult = classifier.classify(reelsSnapshot)
        assertEquals(CandidateScreenState.REELS_TAB, reelsResult.candidateState)
        assertEquals(ObservationOutcome.CLASSIFIED, reelsResult.diagnosticSummary?.observationOutcome)
    }

    // Audit 5: A Stories hierarchy containing off-screen Reels components must still classify as Stories
    @Test
    fun testAudit_StoriesHierarchyWithOffScreenReelsComponentsClassifiesAsStories() {
        val storiesSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasStoryViewer = true,
            hasStoryProgressIndicators = true,
            hasMediaPlaybackContainer = true,
            // Off-screen Reels components present in snapshot must NOT override Stories
            hasClipsViewer = false,
            hasClipsComponents = false,
            hasBottomNavigationBar = false,
            totalNodeCount = 110
        )
        val result = classifier.classify(storiesSnapshot)
        assertEquals(CandidateScreenState.STORIES, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
    }

    // Audit 6: A Reel opened from a DM with ambiguous navigation history must remain conservatively classified as Full-Screen Video
    @Test
    fun testAudit_ReelOpenedFromDmRemainsConservativelyFullScreenVideo() {
        val reelFromDmSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasClipsViewer = true,
            hasClipsComponents = true,
            hasVerticalPager = true,
            hasMediaPlaybackContainer = true,
            hasBottomNavigationBar = false, // Crucial: Reel from DM lacks main bottom nav bar
            selectedTabId = null,
            selectedBottomTabOrdinal = null,
            totalNodeCount = 85
        )
        val result = classifier.classify(reelFromDmSnapshot)
        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
    }

    // Audit 7: Missing bottom-navigation evidence must not, by itself, cause a false classification
    @Test
    fun testAudit_MissingBottomNavigationDoesNotCauseFalseClassification() {
        // Without bottom nav, feed cannot be claimed as Home Feed unless explicit feed controls exist
        val genericListWithoutBottomNav = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasBottomNavigationBar = false,
            hasScrollableFeed = true,
            totalNodeCount = 45
        )
        val result = classifier.classify(genericListWithoutBottomNav)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.NO_SUPPORTED_SIGNALS, result.reasonCode)
        assertEquals(ObservationOutcome.NO_MATCHING_SIGNALS, result.diagnosticSummary?.observationOutcome)
    }

    // --- Final Detection Reliability Audit Tests ---

    // Final Audit 1: Stories vs Dedicated Reels disambiguation
    @Test
    fun testFinalAudit_StoriesVsDedicatedReels_Disambiguation() {
        val storiesOverReels = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasStoryViewer = true,
            hasStoryProgressIndicators = true,
            hasMediaPlaybackContainer = true,
            // Sibling/background clips pager present in snapshot must NOT override Stories
            hasClipsViewer = true,
            hasVerticalPager = true,
            hasBottomNavigationBar = false,
            totalNodeCount = 120
        )
        val result = classifier.classify(storiesOverReels)
        assertEquals(CandidateScreenState.STORIES, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // Final Audit 2: Home Feed vs Dedicated Reels Tab disambiguation
    @Test
    fun testFinalAudit_HomeFeedVsDedicatedReelsTab_Disambiguation() {
        val homeFeed = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasBottomNavigationBar = true,
            selectedTabId = "feed_tab",
            selectedBottomTabOrdinal = 0,
            hasFeedPostControls = true,
            hasScrollableFeed = true,
            totalNodeCount = 95
        )
        val feedResult = classifier.classify(homeFeed)
        assertEquals(CandidateScreenState.HOME_FEED, feedResult.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, feedResult.reasonCode)

        val dedicatedReels = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasBottomNavigationBar = true,
            selectedTabId = "clips_tab",
            selectedBottomTabOrdinal = 3,
            hasClipsViewer = true,
            hasVerticalPager = true,
            totalNodeCount = 80
        )
        val reelsResult = classifier.classify(dedicatedReels)
        assertEquals(CandidateScreenState.REELS_TAB, reelsResult.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, reelsResult.reasonCode)
        assertEquals(Confidence.HIGH, reelsResult.confidence)
    }

    // Final Audit 3: Profile Media vs Dedicated Reels Tab (clips_grid_recyclerview in Profile)
    @Test
    fun testFinalAudit_ProfileMediaVsDedicatedReelsTab_ProfileGridDoesNotTriggerReels() {
        val profileWithClipsGrid = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasBottomNavigationBar = true,
            selectedTabId = "profile_tab",
            selectedBottomTabOrdinal = 4,
            hasProfileHeader = true,
            hasProfileHeaderStats = true,
            hasProfileTabs = true, // clips_grid_recyclerview mapped to profile tabs
            hasClipsViewer = false,
            hasClipsComponents = false,
            totalNodeCount = 110
        )
        val result = classifier.classify(profileWithClipsGrid)
        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
    }

    // Final Audit 4: Truncated Reels hierarchy with decisive signals classifies with high confidence
    @Test
    fun testFinalAudit_TruncatedReelsHierarchy_WithDecisiveSignals_ClassifiesHighConfidence() {
        val truncatedReelsWithSignals = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasBottomNavigationBar = true,
            selectedTabId = "clips_tab",
            selectedBottomTabOrdinal = 3,
            hasClipsViewer = true,
            hasClipsComponents = true,
            reachedDepthLimit = true, // Leaves reached depth 14
            maxDepth = 14,
            totalNodeCount = 112
        )
        val result = classifier.classify(truncatedReelsWithSignals)
        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
        assertTrue(result.diagnosticSummary?.reachedDepthLimit == true)
    }

    // Final Audit 5: Truncated Reels hierarchy WITHOUT decisive signals yields TRUNCATED_TRAVERSAL
    @Test
    fun testFinalAudit_TruncatedReelsHierarchy_WithoutDecisiveSignals_YieldsTruncatedTraversal() {
        val truncatedAmbiguous = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasVerticalPager = true,
            hasBottomNavigationBar = false, // Bottom nav was not captured / truncated
            selectedTabId = null,
            selectedBottomTabOrdinal = null,
            reachedNodeLimit = true, // Node limit reached before structure could be verified
            totalNodeCount = 250
        )
        val result = classifier.classify(truncatedAmbiguous)
        // Without bottom nav or clips tab, and node budget exhausted, cannot prove bottom nav absence; must be TRUNCATED_TRAVERSAL
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.TRUNCATED_TRAVERSAL, result.reasonCode)
        assertEquals(ObservationOutcome.PARTIAL_OR_TRUNCATED, result.diagnosticSummary?.observationOutcome)
    }

    // Final Audit 6: Partial tree cannot accidentally create a false positive for Dedicated Reels Tab
    @Test
    fun testFinalAudit_PartialTree_CannotAccidentallyCreateFalsePositiveForReelsTab() {
        val partialTreeSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            isPartialTree = true,
            rootAcquisitionMethod = "EVENT_SOURCE_SUBTREE",
            hasVerticalPager = true,
            hasBottomNavigationBar = false,
            totalNodeCount = 35
        )
        val result = classifier.classify(partialTreeSnapshot)
        assertFalse(result.candidateState == CandidateScreenState.REELS_TAB, "Partial tree without bottom nav must never be REELS_TAB")
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.TRUNCATED_TRAVERSAL, result.reasonCode)
        assertEquals(ObservationOutcome.PARTIAL_OR_TRUNCATED, result.diagnosticSummary?.observationOutcome)
    }

    // Final Audit 7: Reel opened inside a DM conversation remains conservatively classified as Full-Screen Video
    @Test
    fun testFinalAudit_ReelOpenedInsideDm_LacksBottomNav_ClassifiesAsFullScreenVideo() {
        val reelFromDm = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            hasClipsViewer = true,
            hasClipsComponents = true,
            hasVerticalPager = true,
            hasMediaPlaybackContainer = true,
            hasBottomNavigationBar = false, // Reel from DM lacks Instagram bottom nav
            selectedTabId = null,
            selectedBottomTabOrdinal = null,
            totalNodeCount = 92
        )
        val result = classifier.classify(reelFromDm)
        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
    }

    @Test
    fun testReelWithCommentsPanel_ClassifiedAsReelsTabNotDmConversation() {
        val snapshotWithComments = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 75,
            maxDepth = 9,
            hasClipsViewer = true,
            hasClipsComponents = true,
            hasVerticalPager = true,
            hasMediaPlaybackContainer = true,
            hasCommentsPanel = true,
            hasChatInputBar = true,
            hasMessageList = true,
            hasBottomNavigationBar = false,
            selectedTabId = null
        )

        val result = classifier.classify(snapshotWithComments)

        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
        assertFalse(result.candidateState == CandidateScreenState.DM_CONVERSATION)
    }

    @Test
    fun testCommentsPanelOnHomeFeed_DoesNotClassifyAsReelsTab() {
        val feedCommentsSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 80,
            maxDepth = 9,
            hasCommentsPanel = true,
            hasChatInputBar = true,
            hasFeedPostControls = true,
            hasHorizontalStoryList = true,
            hasBottomNavigationBar = true,
            selectedTabId = "feed_tab"
        )

        val result = classifier.classify(feedCommentsSnapshot)

        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.REELS_TAB)
        assertFalse(result.candidateState == CandidateScreenState.DM_CONVERSATION)
    }
}
