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
}
