package com.outloop.prototype.sanitizer

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.Confidence
import com.outloop.classifier.ObservationOutcome
import com.outloop.classifier.ReasonCode
import com.outloop.classifier.ScreenClassifier
import com.outloop.classifier.StructuralSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

/**
 * Synthetic structural unit tests for [AccessibilityNodeSanitizer] and end-to-end classifier validation.
 *
 * NOTE: These fixtures are synthetic models using mocked [AccessibilityNodeInfo] instances
 * designed to simulate realistic nested Android layout structures, edge cases, and traversal constraints.
 * They verify sanitizer behavior and classifier integration against synthetic trees, but do not
 * replace live observation on real Android devices running Instagram.
 */
class AccessibilityNodeSanitizerTest {

    private val classifier = ScreenClassifier()
    private val displayHeight = 2400
    private val displayWidth = 1080

    // Helper to construct a synthetic mock AccessibilityNodeInfo
    private fun mockNode(
        packageName: String = "com.instagram.android",
        className: String = "android.widget.FrameLayout",
        left: Int = 0,
        top: Int = 0,
        right: Int = 1080,
        bottom: Int = 2400,
        isScrollable: Boolean = false,
        isEditable: Boolean = false,
        isSelected: Boolean = false,
        isFocused: Boolean = false,
        isVisibleToUser: Boolean = true,
        viewId: String? = null,
        children: List<AccessibilityNodeInfo> = emptyList(),
        collectionInfo: AccessibilityNodeInfo.CollectionInfo? = null
    ): AccessibilityNodeInfo {
        val node = mock(AccessibilityNodeInfo::class.java)
        whenever(node.packageName).thenReturn(packageName)
        whenever(node.className).thenReturn(className)
        doAnswer { invocation ->
            val rect = invocation.getArgument<Rect>(0)
            rect.left = left
            rect.top = top
            rect.right = right
            rect.bottom = bottom
            null
        }.`when`(node).getBoundsInScreen(any())

        whenever(node.isScrollable).thenReturn(isScrollable)
        whenever(node.isEditable).thenReturn(isEditable)
        whenever(node.isSelected).thenReturn(isSelected)
        whenever(node.isFocused).thenReturn(isFocused)
        whenever(node.isVisibleToUser).thenReturn(isVisibleToUser)
        whenever(node.viewIdResourceName).thenReturn(viewId)
        whenever(node.collectionInfo).thenReturn(collectionInfo)
        whenever(node.childCount).thenReturn(children.size)
        whenever(node.getChild(anyInt())).thenAnswer { invocation ->
            val index = invocation.getArgument<Int>(0)
            children.getOrNull(index)
        }
        return node
    }

    // 1. Synthetic test: Null root node returns empty snapshot and ROOT_UNAVAILABLE outcome
    @Test
    fun testSyntheticNullRootNodeReturnsEmptySnapshot() {
        val snapshot = AccessibilityNodeSanitizer.sanitize(null, displayHeight, displayWidth)

        assertNull(snapshot.packageName)
        assertFalse(snapshot.isForeground)
        assertFalse(snapshot.hasRootNode)
        assertEquals(0, snapshot.totalNodeCount)
        assertEquals(0, snapshot.maxDepth)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.MISSING_ROOT, result.reasonCode)
        assertEquals(ObservationOutcome.ROOT_UNAVAILABLE, result.diagnosticSummary?.observationOutcome)
    }

    // 2. Synthetic test: Horizontal activity pager containing Home and DM content
    // Verifies that a horizontal ViewPager does NOT trigger VERTICAL_PAGER
    @Test
    fun testSyntheticHorizontalActivityPagerDoesNotProduceVerticalPager() {
        val feedContent = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 200, right = 1080, bottom = 2100
        )
        val dmContent = mockNode(
            className = "android.widget.FrameLayout",
            left = 1080, top = 0, right = 2160, bottom = 2400
        )
        val horizontalViewPager = mockNode(
            className = "androidx.viewpager.widget.ViewPager",
            viewId = "com.instagram.android:id/main_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(feedContent, dmContent)
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(horizontalViewPager)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasHorizontalPager)
        assertFalse(snapshot.hasVerticalPager)
        assertFalse(snapshot.hasUnknownOrientationPager)
    }

    // 3. Synthetic test: Genuine vertical media pager
    // Verifies that a clips viewer pager triggers VERTICAL_PAGER
    @Test
    fun testSyntheticGenuineVerticalMediaPagerProducesVerticalPager() {
        val videoSurface = mockNode(
            className = "android.view.SurfaceView",
            viewId = "com.instagram.android:id/clips_video_container",
            left = 0, top = 0, right = 1080, bottom = 2400
        )
        val verticalPager = mockNode(
            className = "androidx.viewpager2.widget.ViewPager2",
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(videoSurface)
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(verticalPager)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasVerticalPager)
        assertFalse(snapshot.hasHorizontalPager)
        assertTrue(snapshot.hasMediaPlaybackContainer)
    }

    // 4. Synthetic test: Bottom navigation nested beneath wrappers
    // Verifies detection of bottom navigation and selected tab index even when nested inside containers
    @Test
    fun testSyntheticBottomNavigationNestedBeneathWrappers() {
        val tab0 = mockNode(className = "android.widget.FrameLayout", isSelected = false)
        val tab1 = mockNode(className = "android.widget.FrameLayout", isSelected = false)
        val tab2 = mockNode(className = "android.widget.FrameLayout", isSelected = false)
        val tab3 = mockNode(className = "android.widget.FrameLayout", isSelected = true) // Reels tab active
        val tab4 = mockNode(className = "android.widget.FrameLayout", isSelected = false)

        val bottomNavBar = mockNode(
            className = "android.widget.LinearLayout",
            left = 0, top = 2150, right = 1080, bottom = 2350, // bottom of display (top: 89.5%)
            children = listOf(tab0, tab1, tab2, tab3, tab4)
        )
        val lithoWrapper = mockNode(
            className = "com.facebook.litho.ComponentHost",
            left = 0, top = 2150, right = 1080, bottom = 2350,
            children = listOf(bottomNavBar)
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(lithoWrapper)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasBottomNavigationBar)
        assertEquals(5, snapshot.bottomNavChildCount)
        assertEquals(3, snapshot.selectedBottomTabOrdinal)
    }

    // 5. Synthetic test: Wide feed subtree that would otherwise exhaust traversal budget
    // Verifies that bounded structural traversal prevents feed card explosion from starving sibling bottom nav
    @Test
    fun testSyntheticWideFeedSubtreeDoesNotStarveBottomNavigation() {
        // Build 15 feed items, each with 10 child views (150+ sub-views)
        val feedItems = (0 until 15).map { itemIndex ->
            val subViews = (0 until 10).map { _ ->
                mockNode(className = "android.widget.TextView", left = 20, top = 20, right = 100, bottom = 50)
            }
            mockNode(
                className = "android.widget.FrameLayout",
                left = 0, top = itemIndex * 400, right = 1080, bottom = (itemIndex + 1) * 400,
                children = subViews
            )
        }
        val feedRecyclerView = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 150, right = 1080, bottom = 2150,
            children = feedItems
        )

        // Sibling bottom navigation bar
        val tabs = (0 until 5).map { idx ->
            mockNode(className = "android.widget.FrameLayout", isSelected = idx == 0)
        }
        val bottomNav = mockNode(
            className = "android.widget.LinearLayout",
            left = 0, top = 2180, right = 1080, bottom = 2380,
            children = tabs
        )

        val mainContainer = mockNode(
            className = "android.widget.FrameLayout",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(feedRecyclerView, bottomNav)
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(mainContainer)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        // Bottom nav MUST be observed despite large feed subtree
        assertTrue(snapshot.hasBottomNavigationBar)
        assertEquals(5, snapshot.bottomNavChildCount)
        assertEquals(0, snapshot.selectedBottomTabOrdinal)
        assertTrue(snapshot.totalNodeCount < 250)
        assertFalse(snapshot.reachedNodeLimit)
    }

    // 6. Synthetic test: DM inbox with Notes tray and conversation list
    // Verifies that the Notes tray at the top is not confused with Home Stories, and DM_INBOX is classified
    @Test
    fun testSyntheticDmInboxWithNotesTrayClassifiesAsDmInbox() {
        val topAppBar = mockNode(
            className = "android.widget.Toolbar",
            left = 0, top = 50, right = 1080, bottom = 180
        )
        val notesTray = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 190, right = 1080, bottom = 380 // top 15% avatar notes row
        )
        val messageList = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 400, right = 1080, bottom = 2400 // central messages thread list
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(topAppBar, notesTray, messageList)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasTopAppBar)
        assertTrue(snapshot.hasMessageList)
        assertFalse(snapshot.hasBottomNavigationBar)
        assertFalse(snapshot.hasChatInputBar)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.DM_INBOX, result.candidateState)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
    }

    // 7. Synthetic test: DM conversation with chat composer
    // Verifies that conversation list + active composer classifies as DM_CONVERSATION
    @Test
    fun testSyntheticDmConversationWithComposerClassifiesAsDmConversation() {
        val topAppBar = mockNode(
            className = "android.widget.Toolbar",
            left = 0, top = 50, right = 1080, bottom = 180
        )
        val messageThread = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 190, right = 1080, bottom = 2200
        )
        val chatComposer = mockNode(
            className = "android.widget.EditText",
            isEditable = true,
            left = 0, top = 2220, right = 1080, bottom = 2380
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(topAppBar, messageThread, chatComposer)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasChatInputBar)
        assertTrue(snapshot.hasMessageList)
        assertFalse(snapshot.hasBottomNavigationBar)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.DM_CONVERSATION, result.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
    }

    // 8. Synthetic test: Reel opened from DM with a reply composer
    // Verifies that full-screen video with an inline reply composer classifies as FULL_SCREEN_VIDEO, NOT REELS_TAB or DM_CONVERSATION
    @Test
    fun testSyntheticReelOpenedFromDmWithReplyComposerClassifiesAsFullScreenVideo() {
        val videoContainer = mockNode(
            className = "android.view.SurfaceView",
            viewId = "com.instagram.android:id/clips_video_container",
            left = 0, top = 0, right = 1080, bottom = 2400
        )
        val verticalClipsPager = mockNode(
            className = "androidx.viewpager2.widget.ViewPager2",
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(videoContainer)
        )
        // Inline reply composer bar at the bottom of the Reel
        val inlineReplyComposer = mockNode(
            className = "android.widget.EditText",
            isEditable = true,
            left = 0, top = 2250, right = 1080, bottom = 2380
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(verticalClipsPager, inlineReplyComposer)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasMediaPlaybackContainer)
        assertTrue(snapshot.hasVerticalPager)
        assertTrue(snapshot.hasChatInputBar)
        assertFalse(snapshot.hasBottomNavigationBar)
        assertFalse(snapshot.hasMessageList)

        val result = classifier.classify(snapshot)

        // Must classify as FULL_SCREEN_VIDEO with LOW confidence, NEVER REELS_TAB or DM_CONVERSATION
        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
        assertFalse(result.candidateState == CandidateScreenState.REELS_TAB)
        assertFalse(result.candidateState == CandidateScreenState.DM_CONVERSATION)
    }

    // 9. Synthetic test: Truncated traversal returns TRUNCATED_TRAVERSAL and PARTIAL_OR_TRUNCATED outcome
    @Test
    fun testSyntheticTruncatedTraversalReturnsTruncatedOutcome() {
        // Create snapshot with reachedNodeLimit = true
        val truncatedSnapshot = com.outloop.classifier.StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 250,
            reachedNodeLimit = true,
            hasVerticalPager = true, // Media pager present
            hasBottomNavigationBar = false // Bottom nav not observed because of truncation
        )

        val result = classifier.classify(truncatedSnapshot)

        // Must return UNKNOWN with TRUNCATED_TRAVERSAL, NEVER FULL_SCREEN_VIDEO
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.TRUNCATED_TRAVERSAL, result.reasonCode)
        assertEquals(ObservationOutcome.PARTIAL_OR_TRUNCATED, result.diagnosticSummary?.observationOutcome)
        assertFalse(result.candidateState == CandidateScreenState.FULL_SCREEN_VIDEO)
    }

    // 10. Synthetic test: Bottom action row without tab selection is NOT bottom navigation
    // (Simulates the 6 emoji buttons in an open DM conversation)
    @Test
    fun testSyntheticActionRowAtBottomWithoutTabSelectionIsNotBottomNav() {
        // 6 action items at bottom of screen, none selected, no nav ID
        val emojiButtons = (0 until 6).map {
            mockNode(className = "android.widget.TextView", isSelected = false, left = it * 180, top = 2200, right = (it + 1) * 180, bottom = 2380)
        }
        val emojiRow = mockNode(
            className = "android.widget.LinearLayout",
            left = 0, top = 2200, right = 1080, bottom = 2380,
            children = emojiButtons
        )
        val chatComposer = mockNode(
            className = "android.widget.EditText",
            isEditable = true,
            left = 0, top = 2050, right = 1080, bottom = 2200
        )
        val messageList = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 180, right = 1080, bottom = 2050
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(messageList, chatComposer, emojiRow)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        // Must NOT recognize emoji row as bottom nav
        assertFalse(snapshot.hasBottomNavigationBar)
        assertNull(snapshot.bottomNavChildCount)
        assertTrue(snapshot.hasChatInputBar)
        assertTrue(snapshot.hasMessageList)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.DM_CONVERSATION, result.candidateState)
        assertFalse(result.reasonCode == ReasonCode.CONFLICTING_SIGNALS)
    }

    // 11. Synthetic test: Leaf view at depth 14 does NOT set reachedDepthLimit
    @Test
    fun testSyntheticLeafAtDepth14DoesNotSetReachedDepthLimit() {
        // Build a single linear branch to depth 14 with a leaf at depth 14
        var currentNode = mockNode(className = "android.widget.TextView", children = emptyList()) // depth 14 leaf (0 children)
        for (d in 13 downTo 0) {
            currentNode = mockNode(className = "android.widget.FrameLayout", children = listOf(currentNode))
        }

        val snapshot = AccessibilityNodeSanitizer.sanitize(currentNode, displayHeight, displayWidth)

        assertEquals(15, snapshot.totalNodeCount) // depth 0 to 14 = 15 nodes
        assertEquals(14, snapshot.maxDepth)
        assertFalse(snapshot.reachedDepthLimit) // Leaf with 0 children: no children were omitted!
    }

    // 12. Synthetic test: Profile with Story Highlights and video thumbnail classifies as PROFILE
    @Test
    fun testSyntheticProfileWithHighlightsAndReelsGridClassifiesAsProfile() {
        val profileHeader = mockNode(
            className = "android.widget.LinearLayout",
            viewId = "com.instagram.android:id/profile_header",
            left = 0, top = 150, right = 1080, bottom = 800,
            children = listOf(
                mockNode(className = "android.widget.ImageView"),
                mockNode(className = "android.widget.TextView"),
                mockNode(className = "android.widget.Button")
            )
        )
        // Story highlights at upper part of profile
        val highlightsTray = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 820, right = 1080, bottom = 1050
        )
        // Profile grid with video thumbnail
        val videoThumbnail = mockNode(
            className = "android.view.TextureView",
            left = 0, top = 1100, right = 360, bottom = 1460
        )
        val gridList = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 1080, right = 1080, bottom = 2200,
            children = listOf(videoThumbnail)
        )
        // Bottom nav with Profile tab (index 4) selected
        val tabs = (0 until 5).map { idx ->
            mockNode(className = "android.widget.FrameLayout", isSelected = idx == 4)
        }
        val bottomNav = mockNode(
            className = "android.widget.LinearLayout",
            left = 0, top = 2200, right = 1080, bottom = 2400,
            children = tabs
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(profileHeader, highlightsTray, gridList, bottomNav)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasBottomNavigationBar)
        assertEquals(4, snapshot.selectedBottomTabOrdinal)
        assertFalse(snapshot.hasVerticalPager) // Thumbnail does not trigger vertical pager
        assertFalse(snapshot.hasMessageList) // Bottom nav prevents feed from becoming message list

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // 13. Synthetic test: Regression coverage for Stories
    @Test
    fun testSyntheticStoriesRegressionCoverage() {
        val progressBar = mockNode(
            className = "android.widget.LinearLayout",
            left = 0, top = 20, right = 1080, bottom = 30,
            children = listOf(mockNode(), mockNode(), mockNode())
        )
        val videoSurface = mockNode(
            className = "android.view.SurfaceView",
            left = 0, top = 0, right = 1080, bottom = 2400
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(progressBar, videoSurface)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasStoryProgressIndicators)
        assertTrue(snapshot.hasMediaPlaybackContainer)
        assertFalse(snapshot.hasBottomNavigationBar)
        assertFalse(snapshot.hasChatInputBar)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.STORIES, result.candidateState)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // 14. Synthetic test: Home feed with bottom nav and story tray classifies as HOME_FEED
    @Test
    fun testSyntheticHomeFeedWithStoryTrayClassifiesAsHomeFeed() {
        val storyTray = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 150, right = 1080, bottom = 350
        )
        val feed = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 360, right = 1080, bottom = 2200
        )
        val tabs = (0 until 5).map { idx ->
            mockNode(className = "android.widget.FrameLayout", isSelected = idx == 0) // Tab 0 = Home
        }
        val bottomNav = mockNode(
            className = "android.widget.LinearLayout",
            left = 0, top = 2200, right = 1080, bottom = 2400,
            children = tabs
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(storyTray, feed, bottomNav)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasBottomNavigationBar)
        assertEquals(0, snapshot.selectedBottomTabOrdinal)
        assertTrue(snapshot.hasHorizontalStoryList)
        assertTrue(snapshot.hasScrollableFeed)
        assertFalse(snapshot.hasMessageList) // Bottom nav prevents feed from matching message list
        assertFalse(snapshot.hasVerticalPager)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // 15. Synthetic test: Dedicated Reels tab with bottom nav tab 3 selected
    @Test
    fun testSyntheticDedicatedReelsTabClassifiesAsReelsTab() {
        val videoContainer = mockNode(
            className = "android.view.SurfaceView",
            viewId = "com.instagram.android:id/clips_video_container",
            left = 0, top = 0, right = 1080, bottom = 2200
        )
        val verticalPager = mockNode(
            className = "androidx.viewpager2.widget.ViewPager2",
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2200,
            children = listOf(videoContainer)
        )
        val tabs = (0 until 5).map { idx ->
            mockNode(className = "android.widget.FrameLayout", isSelected = idx == 3) // Tab 3 = Reels
        }
        val bottomNav = mockNode(
            className = "android.widget.LinearLayout",
            left = 0, top = 2200, right = 1080, bottom = 2400,
            children = tabs
        )
        val rootNode = mockNode(
            className = "com.android.internal.policy.DecorView",
            children = listOf(verticalPager, bottomNav)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasBottomNavigationBar)
        assertEquals(3, snapshot.selectedBottomTabOrdinal)
        assertTrue(snapshot.hasVerticalPager)
        assertFalse(snapshot.hasMessageList)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // 16. Synthetic test: Structural tree dump captures node records with accurate hierarchy and zero text
    @Test
    fun testSyntheticStructuralTreeDumpCapturesRecordsWithZeroPrivateData() {
        val childButton = mockNode(
            className = "android.widget.Button",
            viewId = "com.instagram.android:id/action_button",
            left = 50, top = 50, right = 200, bottom = 150
        )
        val parentLayout = mockNode(
            className = "android.widget.LinearLayout",
            viewId = "com.instagram.android:id/main_container",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(childButton)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(
            rootNode = parentLayout,
            displayHeightPixels = displayHeight,
            displayWidthPixels = displayWidth,
            captureTreeDump = true
        )

        assertEquals(2, snapshot.structuralTreeDump.size)

        val rootRecord = snapshot.structuralTreeDump[0]
        assertEquals(0, rootRecord.nodeIndex)
        assertNull(rootRecord.parentIndex)
        assertEquals(0, rootRecord.depth)
        assertEquals("android.widget.LinearLayout", rootRecord.className)
        assertEquals("com.instagram.android:id/main_container", rootRecord.resourceId)
        assertEquals(1, rootRecord.childCount)
        assertEquals("[0,0][1080,2400]", rootRecord.bounds)
        assertEquals("VISITED", rootRecord.traversalStatus)

        val childRecord = snapshot.structuralTreeDump[1]
        assertEquals(1, childRecord.nodeIndex)
        assertEquals(0, childRecord.parentIndex)
        assertEquals(1, childRecord.depth)
        assertEquals("android.widget.Button", childRecord.className)
        assertEquals("com.instagram.android:id/action_button", childRecord.resourceId)
        assertEquals(0, childRecord.childCount)
        assertEquals("[50,50][200,150]", childRecord.bounds)
        assertEquals("VISITED", childRecord.traversalStatus)
    }

    // 17. Synthetic test: Tree dump disabled by default produces empty list
    @Test
    fun testSyntheticTreeDumpDisabledByDefaultProducesEmptyList() {
        val parentLayout = mockNode(
            className = "android.widget.LinearLayout",
            children = listOf(mockNode(className = "android.view.View"))
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(
            rootNode = parentLayout,
            displayHeightPixels = displayHeight,
            displayWidthPixels = displayWidth,
            captureTreeDump = false
        )

        assertTrue(snapshot.structuralTreeDump.isEmpty())
    }

    // 18. Synthetic test: Real-device Instagram hierarchy with root-level tabs and feed post controls
    @Test
    fun testSyntheticInstagramRealDeviceHierarchyWithRootTabsAndFeedControls() {
        // Feed post controls
        val header = mockNode(className = "android.view.ViewGroup", viewId = "com.instagram.android:id/row_feed_profile_header")
        val likeBtn = mockNode(className = "android.widget.ImageView", viewId = "com.instagram.android:id/row_feed_button_like")
        val commentBtn = mockNode(className = "android.widget.ImageView", viewId = "com.instagram.android:id/row_feed_button_comment")
        val saveBtn = mockNode(className = "android.widget.ImageView", viewId = "com.instagram.android:id/row_feed_button_save")
        val videoContainer = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/video_container")
        val mediaGroup = mockNode(className = "android.view.ViewGroup", viewId = "com.instagram.android:id/media_group")

        val feedCard = mockNode(
            className = "android.view.ViewGroup",
            children = listOf(header, likeBtn, commentBtn, saveBtn, videoContainer, mediaGroup)
        )

        val feedRecycler = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 200, right = 1080, bottom = 2200,
            children = listOf(feedCard)
        )

        // ViewPager with swipeable_tab_view_pager
        val viewPager = mockNode(
            className = "androidx.viewpager.widget.ViewPager",
            viewId = "com.instagram.android:id/swipeable_tab_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(feedRecycler)
        )

        // Direct root children tabs
        val feedTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/feed_tab", isSelected = true)
        val clipsTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/clips_tab", isSelected = false)
        val directTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/direct_tab", isSelected = false)
        val searchTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/search_tab", isSelected = false)
        val profileTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/profile_tab", isSelected = false)

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(viewPager, feedTab, clipsTab, directTab, searchTab, profileTab)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasBottomNavigationBar)
        assertEquals("feed_tab", snapshot.selectedTabId)
        assertEquals(0, snapshot.selectedBottomTabOrdinal)
        assertTrue(snapshot.hasFeedPostControls)
        assertTrue(snapshot.hasHorizontalPager)
        assertFalse(snapshot.hasVerticalPager) // Horizontal ViewPager must not set vertical pager
        assertFalse(snapshot.hasMessageList) // Feed post controls must prevent message list

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
        assertFalse(result.candidateState == CandidateScreenState.DM_INBOX)
        assertFalse(result.candidateState == CandidateScreenState.FULL_SCREEN_VIDEO)
    }

    // =========================================================================
    // Phase 3 — Observed Evidence Regression Test Suite (Scenarios 1 - 13)
    // =========================================================================

    // Scenario 1: Home Feed with selected feed_tab, visible feed content, and off-screen DM/Reels pages
    @Test
    fun testPhase3_Scenario1_HomeFeedWithOffScreenPages() {
        val feedLike = mockNode(className = "android.widget.ImageView", viewId = "com.instagram.android:id/row_feed_button_like", left = 20, top = 1200, right = 80, bottom = 1260)
        val feedMedia = mockNode(className = "android.view.ViewGroup", viewId = "com.instagram.android:id/media_group", left = 0, top = 400, right = 1080, bottom = 1180)
        val feedCard = mockNode(className = "android.view.ViewGroup", left = 0, top = 200, right = 1080, bottom = 1800, children = listOf(feedMedia, feedLike))
        val feedPage = mockNode(
            className = "android.widget.FrameLayout",
            left = 0, top = 0, right = 1080, bottom = 2400,
            isVisibleToUser = true,
            children = listOf(feedCard)
        )

        // Off-screen DM page (to the right: x >= 1080)
        val dmComposer = mockNode(className = "android.widget.EditText", viewId = "com.instagram.android:id/row_thread_composer_edittext", left = 1080, top = 2200, right = 2160, bottom = 2400, isEditable = true, isVisibleToUser = false)
        val dmPage = mockNode(
            className = "android.widget.FrameLayout",
            left = 1080, top = 0, right = 2160, bottom = 2400,
            isVisibleToUser = false,
            children = listOf(dmComposer)
        )

        // Off-screen Reels page (to the left: x <= 0)
        val reelsPager = mockNode(className = "androidx.viewpager.widget.ViewPager", viewId = "com.instagram.android:id/clips_viewer_view_pager", left = -1080, top = 0, right = 0, bottom = 2400, isVisibleToUser = false)
        val reelsPage = mockNode(
            className = "android.widget.FrameLayout",
            left = -1080, top = 0, right = 0, bottom = 2400,
            isVisibleToUser = false,
            children = listOf(reelsPager)
        )

        val mainPager = mockNode(
            className = "androidx.viewpager.widget.ViewPager",
            viewId = "com.instagram.android:id/swipeable_tab_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(reelsPage, feedPage, dmPage)
        )

        // Visible bottom nav tabs
        val feedTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/feed_tab", left = 0, top = 2220, right = 216, bottom = 2400, isSelected = true)
        val clipsTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/clips_tab", left = 648, top = 2220, right = 864, bottom = 2400, isSelected = false)

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(mainPager, feedTab, clipsTab)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasBottomNavigationBar)
        assertEquals("feed_tab", snapshot.selectedTabId)
        assertEquals(0, snapshot.selectedBottomTabOrdinal)
        assertTrue(snapshot.hasFeedPostControls)
        assertFalse(snapshot.hasChatInputBar)
        assertFalse(snapshot.hasClipsViewer)
        assertFalse(snapshot.hasVerticalPager)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.HOME_FEED, result.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
    }

    // Scenario 2: Dedicated Reels with selected clips_tab, visible Clips resource IDs, and horizontal pager class metadata
    @Test
    fun testPhase3_Scenario2_DedicatedReelsWithClipsTabAndCollectionInfo() {
        val videoContainer = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/clips_video_container", left = 0, top = 0, right = 1080, bottom = 2220)
        val ufi = mockNode(className = "android.view.ViewGroup", viewId = "com.instagram.android:id/clips_ufi_component", left = 900, top = 1200, right = 1080, bottom = 1800)

        val colInfo = mock(AccessibilityNodeInfo.CollectionInfo::class.java)
        whenever(colInfo.rowCount).thenReturn(5)
        whenever(colInfo.columnCount).thenReturn(1)

        val clipsPager = mockNode(
            className = "androidx.viewpager.widget.ViewPager",
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2220,
            collectionInfo = colInfo,
            children = listOf(videoContainer, ufi)
        )

        val feedTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/feed_tab", left = 0, top = 2220, right = 216, bottom = 2400, isSelected = false)
        val clipsTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/clips_tab", left = 648, top = 2220, right = 864, bottom = 2400, isSelected = true)

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(clipsPager, feedTab, clipsTab)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth, captureTreeDump = true)

        assertTrue(snapshot.hasBottomNavigationBar)
        assertEquals("clips_tab", snapshot.selectedTabId)
        assertEquals(3, snapshot.selectedBottomTabOrdinal)
        assertTrue(snapshot.hasClipsViewer)
        assertTrue(snapshot.hasClipsComponents)
        assertTrue(snapshot.hasVerticalPager)
        assertFalse(snapshot.hasHorizontalPager)

        val pagerRecord = snapshot.structuralTreeDump.firstOrNull { it.resourceId?.contains("clips_viewer_view_pager") == true }
        assertEquals("VERTICAL", pagerRecord?.pagerOrientation)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // Scenario 3: DM Inbox with selected direct_tab, visible inbox list, and off-screen Feed/Profile/Reels nodes
    @Test
    fun testPhase3_Scenario3_DmInboxWithOffScreenStaleNodes() {
        val title = mockNode(className = "android.widget.TextView", viewId = "com.instagram.android:id/igds_action_bar_title", left = 50, top = 60, right = 300, bottom = 140)
        val threadList = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            viewId = "com.instagram.android:id/inbox_refreshable_thread_list_recyclerview",
            isScrollable = true,
            left = 0, top = 160, right = 1080, bottom = 2400
        )
        val inboxPage = mockNode(
            className = "android.widget.FrameLayout",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(title, threadList)
        )

        val feedLike = mockNode(className = "android.widget.ImageView", viewId = "com.instagram.android:id/row_feed_button_like", left = -1080, top = 1200, right = -1000, bottom = 1260, isVisibleToUser = false)
        val feedPage = mockNode(className = "android.widget.FrameLayout", left = -1080, top = 0, right = 0, bottom = 2400, isVisibleToUser = false, children = listOf(feedLike))

        val directTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/direct_tab", isSelected = true)

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(inboxPage, feedPage, directTab)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasDmInboxList)
        assertTrue(snapshot.hasMessageList)
        assertFalse(snapshot.hasFeedPostControls)
        assertEquals("direct_tab", snapshot.selectedTabId)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.DM_INBOX, result.candidateState)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // Scenario 4: DM Conversation with visible message list and composer, plus off-screen Feed/Reels nodes
    @Test
    fun testPhase3_Scenario4_DmConversationWithOffScreenFeedAndReelsNodes() {
        val messageList = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            viewId = "com.instagram.android:id/message_list",
            isScrollable = true,
            left = 0, top = 160, right = 1080, bottom = 2200
        )
        val composer = mockNode(
            className = "android.widget.EditText",
            viewId = "com.instagram.android:id/row_thread_composer_edittext",
            isEditable = true,
            left = 40, top = 2220, right = 900, bottom = 2360
        )
        val conversationPage = mockNode(
            className = "android.widget.FrameLayout",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(messageList, composer)
        )

        val feedLike = mockNode(className = "android.widget.ImageView", viewId = "com.instagram.android:id/row_feed_button_like", left = -1080, top = 1000, right = -980, bottom = 1080, isVisibleToUser = false)
        val offScreenFeedTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/feed_tab", left = -1080, top = 2200, right = -864, bottom = 2400, isSelected = true, isVisibleToUser = false)
        val offScreenFeedPage = mockNode(className = "android.widget.FrameLayout", left = -1080, top = 0, right = 0, bottom = 2400, isVisibleToUser = false, children = listOf(feedLike, offScreenFeedTab))

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(conversationPage, offScreenFeedPage)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasDmComposer)
        assertTrue(snapshot.hasDmMessageList)
        assertFalse(snapshot.hasFeedPostControls)
        assertFalse(snapshot.hasBottomNavigationBar)
        assertNull(snapshot.selectedTabId)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.DM_CONVERSATION, result.candidateState)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
    }

    // Scenario 5: Reel opened from a DM with visible Clips structures and no main bottom navigation
    @Test
    fun testPhase3_Scenario5_ReelOpenedFromDmWithoutBottomNavClassifiesAsFullScreenVideo() {
        val videoContainer = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/clips_video_container", left = 0, top = 0, right = 1080, bottom = 2400)
        val clipsMedia = mockNode(className = "android.view.ViewGroup", viewId = "com.instagram.android:id/clips_media_component", left = 0, top = 0, right = 1080, bottom = 2400)
        val clipsPager = mockNode(
            className = "androidx.viewpager.widget.ViewPager",
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(videoContainer, clipsMedia)
        )
        val replyBox = mockNode(className = "android.widget.EditText", viewId = "com.instagram.android:id/row_thread_composer_edittext", left = 40, top = 2240, right = 900, bottom = 2360, isEditable = true)

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(clipsPager, replyBox)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasClipsViewer)
        assertTrue(snapshot.hasClipsComponents)
        assertFalse(snapshot.hasBottomNavigationBar)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
    }

    // Scenario 6: Profile with selected profile_tab and visible profile-specific structures
    @Test
    fun testPhase3_Scenario6_ProfileWithSelectedTabAndStats() {
        val stats = mockNode(className = "android.view.ViewGroup", viewId = "com.instagram.android:id/profile_header_post_count_front_familiar", left = 300, top = 200, right = 500, bottom = 320)
        val followers = mockNode(className = "android.view.ViewGroup", viewId = "com.instagram.android:id/profile_header_followers_stacked_familiar", left = 520, top = 200, right = 720, bottom = 320)
        val header = mockNode(className = "android.widget.LinearLayout", left = 0, top = 160, right = 1080, bottom = 600, children = listOf(stats, followers))
        val highlights = mockNode(className = "androidx.recyclerview.widget.RecyclerView", viewId = "com.instagram.android:id/highlights_reel_tray_recycler_view", isScrollable = true, left = 0, top = 620, right = 1080, bottom = 850)
        val profileTabs = mockNode(className = "android.widget.HorizontalScrollView", viewId = "com.instagram.android:id/profile_tab_layout", left = 0, top = 860, right = 1080, bottom = 960)

        val profileTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/profile_tab", left = 864, top = 2220, right = 1080, bottom = 2400, isSelected = true)
        val feedTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/feed_tab", left = 0, top = 2220, right = 216, bottom = 2400, isSelected = false)

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(header, highlights, profileTabs, feedTab, profileTab)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasProfileHeaderStats)
        assertTrue(snapshot.hasProfileTabs)
        assertEquals("profile_tab", snapshot.selectedTabId)
        assertEquals(4, snapshot.selectedBottomTabOrdinal)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // Scenario 7: Stories with visible reel_viewer_root and Story-specific structures while main pager remains in tree
    @Test
    fun testPhase3_Scenario7_StoriesOverlayingMainPager() {
        val feedLike = mockNode(className = "android.widget.ImageView", viewId = "com.instagram.android:id/row_feed_button_like", left = 20, top = 1000, right = 80, bottom = 1060)
        val backgroundPager = mockNode(className = "androidx.viewpager.widget.ViewPager", left = 0, top = 0, right = 1080, bottom = 2400, children = listOf(feedLike))
        val backgroundBottomNav = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/feed_tab", isSelected = true)

        val storyProgress = mockNode(className = "android.view.View", viewId = "com.instagram.android:id/story_progress", left = 0, top = 20, right = 1080, bottom = 30)
        val storyHeader = mockNode(className = "android.view.ViewGroup", viewId = "com.instagram.android:id/reel_viewer_header_container", left = 0, top = 40, right = 1080, bottom = 160)
        val storyRoot = mockNode(
            className = "android.widget.FrameLayout",
            viewId = "com.instagram.android:id/reel_viewer_root",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(storyProgress, storyHeader)
        )

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(backgroundPager, backgroundBottomNav, storyRoot)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertTrue(snapshot.hasStoryViewer)
        assertTrue(snapshot.hasStoryProgressIndicators)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.STORIES, result.candidateState)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // Scenario 8: Off-screen Feed structures must not make an active DM, Profile, or Story viewer classify as Home Feed
    @Test
    fun testPhase3_Scenario8_OffScreenFeedDoesNotOverrideActiveDmOrProfileOrStory() {
        val dmInboxList = mockNode(className = "androidx.recyclerview.widget.RecyclerView", viewId = "com.instagram.android:id/inbox_refreshable_thread_list_recyclerview", left = 0, top = 150, right = 1080, bottom = 2400)
        val activeDm = mockNode(className = "android.widget.FrameLayout", left = 0, top = 0, right = 1080, bottom = 2400, children = listOf(dmInboxList))

        val feedLike = mockNode(className = "android.widget.ImageView", viewId = "com.instagram.android:id/row_feed_button_like", left = -1080, top = 1000, right = -1000, bottom = 1060, isVisibleToUser = false)
        val inactiveFeedTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/feed_tab", left = -1080, top = 2220, right = -864, bottom = 2400, isSelected = true, isVisibleToUser = false)
        val inactiveFeedPage = mockNode(className = "android.widget.FrameLayout", left = -1080, top = 0, right = 0, bottom = 2400, isVisibleToUser = false, children = listOf(feedLike, inactiveFeedTab))

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(activeDm, inactiveFeedPage)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)
        assertFalse(snapshot.hasFeedPostControls)
        assertNull(snapshot.selectedTabId)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.DM_INBOX, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.HOME_FEED)
    }

    // Scenario 9: A fully off-screen Clips viewer must not cause an active Profile or DM Inbox to classify as Reels
    @Test
    fun testPhase3_Scenario9_OffScreenClipsViewerDoesNotMakeProfileClassifyAsReels() {
        val stats = mockNode(className = "android.view.ViewGroup", viewId = "com.instagram.android:id/profile_header_post_count_front_familiar", left = 300, top = 200, right = 500, bottom = 320)
        val profilePage = mockNode(className = "android.widget.FrameLayout", left = 0, top = 0, right = 1080, bottom = 2220, children = listOf(stats))
        val profileTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/profile_tab", left = 864, top = 2220, right = 1080, bottom = 2400, isSelected = true)

        val clipsViewer = mockNode(className = "androidx.viewpager.widget.ViewPager", viewId = "com.instagram.android:id/clips_viewer_view_pager", left = 1080, top = 0, right = 2160, bottom = 2400, isVisibleToUser = false)
        val offScreenPage = mockNode(className = "android.widget.FrameLayout", left = 1080, top = 0, right = 2160, bottom = 2400, isVisibleToUser = false, children = listOf(clipsViewer))

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(profilePage, profileTab, offScreenPage)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)
        assertFalse(snapshot.hasClipsViewer)
        assertFalse(snapshot.hasVerticalPager)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
        assertFalse(result.candidateState == CandidateScreenState.REELS_TAB)
    }

    // Scenario 10: Truncated tree with strong specific evidence can classify; truncated tree with insufficient evidence remains unknown
    @Test
    fun testPhase3_Scenario10_TruncatedTreeWithStrongVsInsufficientEvidence() {
        val storySnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 200,
            maxDepth = 14,
            reachedDepthLimit = true,
            hasStoryViewer = true,
            hasStoryProgressIndicators = true
        )
        val storyResult = classifier.classify(storySnapshot)
        assertEquals(CandidateScreenState.STORIES, storyResult.candidateState)
        assertEquals(ObservationOutcome.CLASSIFIED, storyResult.diagnosticSummary?.observationOutcome)

        val ambiguousSnapshot = StructuralSnapshot(
            packageName = "com.instagram.android",
            isForeground = true,
            hasRootNode = true,
            totalNodeCount = 120,
            maxDepth = 14,
            reachedDepthLimit = true,
            hasBottomNavigationBar = false
        )
        val ambiguousResult = classifier.classify(ambiguousSnapshot)
        assertEquals(CandidateScreenState.UNKNOWN, ambiguousResult.candidateState)
        assertEquals(ReasonCode.TRUNCATED_TRAVERSAL, ambiguousResult.reasonCode)
        assertEquals(ObservationOutcome.PARTIAL_OR_TRUNCATED, ambiguousResult.diagnosticSummary?.observationOutcome)
    }

    // Scenario 11: Missing root remains ROOT_UNAVAILABLE
    @Test
    fun testPhase3_Scenario11_MissingRootRemainsRootUnavailable() {
        val snapshot = AccessibilityNodeSanitizer.sanitize(null, displayHeight, displayWidth)
        assertEquals(0, snapshot.totalNodeCount)
        assertFalse(snapshot.hasRootNode)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.MISSING_ROOT, result.reasonCode)
        assertEquals(ObservationOutcome.ROOT_UNAVAILABLE, result.diagnosticSummary?.observationOutcome)
    }

    // Scenario 12: Invalid/empty bounds and visibility inconsistencies are handled safely
    @Test
    fun testPhase3_Scenario12_InvalidEmptyBoundsAndVisibilityInconsistencies() {
        val emptyNode = mockNode(className = "android.view.View", viewId = "com.instagram.android:id/row_feed_button_like", left = 0, top = 0, right = 0, bottom = 0)
        val invertedNode = mockNode(className = "android.widget.EditText", viewId = "com.instagram.android:id/row_thread_composer_edittext", left = 500, top = 500, right = 400, bottom = 400, isEditable = true)
        val invisibleNode = mockNode(className = "androidx.recyclerview.widget.RecyclerView", viewId = "com.instagram.android:id/inbox_refreshable_thread_list_recyclerview", left = 0, top = 100, right = 1080, bottom = 2000, isVisibleToUser = false)

        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(emptyNode, invertedNode, invisibleNode)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)

        assertFalse(snapshot.hasFeedPostControls)
        assertFalse(snapshot.hasChatInputBar)
        assertFalse(snapshot.hasDmInboxList)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.NO_SUPPORTED_SIGNALS, result.reasonCode)
    }

    // Scenario 13: Selected tab reporting matches the actual selected resource ID and uses a documented 0-based convention
    @Test
    fun testPhase3_Scenario13_SelectedTabReportingMatchesResourceIdAndZeroBasedOrdinal() {
        val tabs = listOf(
            Triple("com.instagram.android:id/feed_tab", "feed_tab", 0),
            Triple("com.instagram.android:id/search_tab", "search_tab", 1),
            Triple("com.instagram.android:id/clips_tab", "clips_tab", 3),
            Triple("com.instagram.android:id/profile_tab", "profile_tab", 4)
        )

        for ((resId, expectedId, expectedOrdinal) in tabs) {
            val tabIcon = mockNode(className = "android.widget.ImageView", isSelected = true)
            val tabContainer = mockNode(
                className = "android.widget.FrameLayout",
                viewId = resId,
                isSelected = false,
                children = listOf(tabIcon)
            )
            val rootNode = mockNode(className = "android.widget.FrameLayout", children = listOf(tabContainer))

            val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)
            assertEquals("Expected selectedTabId for $resId", expectedId, snapshot.selectedTabId)
            assertEquals("Expected selectedBottomTabOrdinal for $resId", expectedOrdinal, snapshot.selectedBottomTabOrdinal)
        }

        val directTab = mockNode(className = "android.widget.FrameLayout", viewId = "com.instagram.android:id/direct_tab", isSelected = true)
        val directRoot = mockNode(className = "android.widget.FrameLayout", children = listOf(directTab))
        val directSnapshot = AccessibilityNodeSanitizer.sanitize(directRoot, displayHeight, displayWidth)
        assertEquals("direct_tab", directSnapshot.selectedTabId)
        assertNull("direct_tab must not claim ordinal 3", directSnapshot.selectedBottomTabOrdinal)
    }

    // --- Reliability Audit Sanitizer Tests ---

    // Audit 1: Null root node produces unavailable snapshot
    @Test
    fun testAudit_NullRootProducesMissingRootSnapshot() {
        val snapshot = AccessibilityNodeSanitizer.sanitize(null, displayHeight, displayWidth)
        assertFalse(snapshot.hasRootNode)
        assertEquals(0, snapshot.totalNodeCount)
        assertFalse(snapshot.isForeground)
        assertEquals("NONE", snapshot.rootAcquisitionMethod)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.MISSING_ROOT, result.reasonCode)
        assertEquals(ObservationOutcome.ROOT_UNAVAILABLE, result.diagnosticSummary?.observationOutcome)
    }

    // Audit 2: Depth limit (depth >= 14) on generic container without decisive signals yields TRUNCATED_TRAVERSAL
    @Test
    fun testAudit_DepthTruncationOnGenericListYieldsTruncatedTraversal() {
        // Build a 15-level deep hierarchy chain of FrameLayouts with a leaf RecyclerView
        var current = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            left = 0, top = 200, right = 1080, bottom = 2200,
            children = listOf(mockNode(className = "android.widget.TextView", left = 20, top = 200, right = 400, bottom = 300))
        )
        for (d in 13 downTo 0) {
            current = mockNode(
                className = "android.widget.FrameLayout",
                left = 0, top = 0, right = 1080, bottom = 2400,
                children = listOf(current)
            )
        }

        val snapshot = AccessibilityNodeSanitizer.sanitize(current, displayHeight, displayWidth)
        assertTrue(snapshot.reachedDepthLimit)
        assertTrue(snapshot.maxDepth >= 14)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.TRUNCATED_TRAVERSAL, result.reasonCode)
        assertEquals(ObservationOutcome.PARTIAL_OR_TRUNCATED, result.diagnosticSummary?.observationOutcome)
    }

    // Audit 3: Stories hierarchy with off-screen Clips viewer classifies as Stories
    @Test
    fun testAudit_StoriesHierarchyWithOffScreenClipsViewerClassifiesStories() {
        val storyRoot = mockNode(
            className = "android.widget.FrameLayout",
            viewId = "com.instagram.android:id/reel_viewer_root",
            left = 0, top = 0, right = 1080, bottom = 2400
        )
        val offScreenClipsViewer = mockNode(
            className = "androidx.viewpager.widget.ViewPager",
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            left = -1080, top = 0, right = 0, bottom = 2400,
            isVisibleToUser = false
        )
        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(offScreenClipsViewer, storyRoot)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)
        assertTrue(snapshot.hasStoryViewer)
        assertFalse(snapshot.hasClipsViewer) // Off-screen must not contribute

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.STORIES, result.candidateState)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }

    // Audit 4: Reel opened from DM without bottom navigation classifies conservatively as Full-Screen Video
    @Test
    fun testAudit_ReelFromDmWithoutBottomNavClassifiesFullScreenVideo() {
        val clipsViewer = mockNode(
            className = "androidx.viewpager.widget.ViewPager",
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2400
        )
        val clipsMedia = mockNode(
            className = "android.view.ViewGroup",
            viewId = "com.instagram.android:id/clips_media_component",
            left = 0, top = 0, right = 1080, bottom = 2400
        )
        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(clipsViewer, clipsMedia)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)
        assertTrue(snapshot.hasClipsViewer)
        assertTrue(snapshot.hasClipsComponents)
        assertFalse(snapshot.hasBottomNavigationBar)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.FULL_SCREEN_VIDEO, result.candidateState)
        assertEquals(ReasonCode.AMBIGUOUS_STRUCTURE, result.reasonCode)
        assertEquals(Confidence.LOW, result.confidence)
    }

    // Audit 5: Missing bottom navigation without other signals remains UNKNOWN
    @Test
    fun testAudit_MissingBottomNavWithoutOtherSignalsRemainsUnknown() {
        val genericContainer = mockNode(
            className = "android.widget.FrameLayout",
            left = 0, top = 0, right = 1080, bottom = 2400
        )
        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(genericContainer)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)
        assertFalse(snapshot.hasBottomNavigationBar)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.UNKNOWN, result.candidateState)
        assertEquals(ReasonCode.NO_SUPPORTED_SIGNALS, result.reasonCode)
    }

    // --- Final Detection Reliability Audit Sanitizer Tests ---

    // Final Audit 1: Deep Stories hierarchy reaching depth 14 still captures Story viewer
    @Test
    fun testFinalAudit_DeepStoriesHierarchy_ReachingDepth14_StillCapturesStoryViewer() {
        // Build depth 6 to 15 inside a StoryViewerFragment
        var deepLeaf = mockNode(className = "android.widget.TextView", left = 20, top = 200, right = 400, bottom = 300)
        for (d in 14 downTo 6) {
            deepLeaf = mockNode(className = "android.view.ViewGroup", left = 0, top = 0, right = 1080, bottom = 2400, children = listOf(deepLeaf))
        }

        val storyViewerRoot = mockNode(
            className = "android.widget.FrameLayout",
            viewId = "com.instagram.android:id/reel_viewer_root",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(deepLeaf)
        )

        // Wrap from depth 4 up to depth 0
        var root = storyViewerRoot
        for (d in 4 downTo 0) {
            root = mockNode(className = "android.widget.FrameLayout", left = 0, top = 0, right = 1080, bottom = 2400, children = listOf(root))
        }

        val snapshot = AccessibilityNodeSanitizer.sanitize(root, displayHeight, displayWidth)
        assertTrue("Story viewer root must be captured", snapshot.hasStoryViewer)
        assertTrue("Depth limit was reached in leaf branches", snapshot.reachedDepthLimit)
        assertTrue("Max depth is at least 14", snapshot.maxDepth >= 14)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.STORIES, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
    }

    // Final Audit 2: Deep Reels hierarchy reaching depth 14 still captures bottom nav and clips viewer
    @Test
    fun testFinalAudit_DeepReelsHierarchy_ReachingDepth14_StillCapturesClipsViewerAndTabs() {
        // Deep video sub-component tree reaching depth 15
        var deepLeaf = mockNode(className = "android.view.View", left = 100, top = 100, right = 200, bottom = 200)
        for (d in 15 downTo 5) {
            deepLeaf = mockNode(className = "android.view.ViewGroup", left = 0, top = 0, right = 1080, bottom = 2400, children = listOf(deepLeaf))
        }

        val clipsViewer = mockNode(
            className = "androidx.viewpager.widget.ViewPager",
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(deepLeaf)
        )

        // Shallow bottom nav at depth 2-4
        val clipsTab = mockNode(
            className = "android.widget.FrameLayout",
            viewId = "com.instagram.android:id/clips_tab",
            isSelected = true,
            left = 648, top = 2220, right = 864, bottom = 2400
        )
        val feedTab = mockNode(
            className = "android.widget.FrameLayout",
            viewId = "com.instagram.android:id/feed_tab",
            isSelected = false,
            left = 0, top = 2220, right = 216, bottom = 2400
        )
        val bottomNav = mockNode(
            className = "android.widget.LinearLayout",
            left = 0, top = 2220, right = 1080, bottom = 2400,
            children = listOf(feedTab, clipsTab)
        )

        val mainContainer = mockNode(
            className = "android.widget.FrameLayout",
            left = 0, top = 0, right = 1080, bottom = 2400,
            children = listOf(clipsViewer, bottomNav)
        )

        var root = mainContainer
        for (d in 2 downTo 0) {
            root = mockNode(className = "android.widget.FrameLayout", left = 0, top = 0, right = 1080, bottom = 2400, children = listOf(root))
        }

        val snapshot = AccessibilityNodeSanitizer.sanitize(root, displayHeight, displayWidth)
        assertTrue(snapshot.hasClipsViewer)
        assertTrue(snapshot.hasBottomNavigationBar)
        assertEquals("clips_tab", snapshot.selectedTabId)
        assertEquals(3, snapshot.selectedBottomTabOrdinal)
        assertTrue("Depth limit was reached in leaf branches", snapshot.reachedDepthLimit)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.REELS_TAB, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.MULTIPLE_SIGNALS_MATCH, result.reasonCode)
        assertEquals(ObservationOutcome.CLASSIFIED, result.diagnosticSummary?.observationOutcome)
    }

    // Final Audit 3: Profile with clips grid does not trigger clips viewer or Reels tab
    @Test
    fun testFinalAudit_ProfileWithClipsGrid_DoesNotTriggerClipsViewerOrReelsTab() {
        val stats = mockNode(
            className = "android.view.ViewGroup",
            viewId = "com.instagram.android:id/profile_header_post_count_front_familiar",
            left = 300, top = 200, right = 500, bottom = 320
        )
        val clipsGrid = mockNode(
            className = "androidx.recyclerview.widget.RecyclerView",
            viewId = "com.instagram.android:id/clips_grid_recyclerview",
            isScrollable = true,
            left = 0, top = 600, right = 1080, bottom = 2200
        )
        val profileTab = mockNode(
            className = "android.widget.FrameLayout",
            viewId = "com.instagram.android:id/profile_tab",
            isSelected = true,
            left = 864, top = 2220, right = 1080, bottom = 2400
        )
        val rootNode = mockNode(
            className = "android.widget.FrameLayout",
            children = listOf(stats, clipsGrid, profileTab)
        )

        val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight, displayWidth)
        assertTrue(snapshot.hasProfileHeaderStats)
        assertTrue(snapshot.hasProfileTabs)
        assertFalse(snapshot.hasClipsViewer)
        assertEquals("profile_tab", snapshot.selectedTabId)

        val result = classifier.classify(snapshot)
        assertEquals(CandidateScreenState.PROFILE, result.candidateState)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(ReasonCode.KNOWN_STRUCTURE_MATCH, result.reasonCode)
    }
}
