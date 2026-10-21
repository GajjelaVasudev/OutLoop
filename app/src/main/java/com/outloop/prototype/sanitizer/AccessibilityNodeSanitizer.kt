package com.outloop.prototype.sanitizer

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.outloop.classifier.StructuralSnapshot

/**
 * Sanitizes Android [AccessibilityNodeInfo] trees into privacy-minimized [StructuralSnapshot] instances.
 *
 * CRITICAL PRIVACY ASSURANCES:
 * - NEVER reads or accesses `node.text`.
 * - NEVER reads or accesses `node.contentDescription`.
 * - NEVER extracts private messages, usernames, URLs, or personal data.
 * - Collects ONLY layout geometry, widget class names, and Boolean structural presence flags.
 * - Traverses to a bounded depth and node count to eliminate performance bottlenecks and memory churn.
 * - Does NOT retain references to [AccessibilityNodeInfo] objects.
 */
object AccessibilityNodeSanitizer {

    private const val MAX_DEPTH = 14
    private const val MAX_NODE_COUNT = 250
    private const val TARGET_PACKAGE = "com.instagram.android"

    /**
     * Inspects the accessibility tree starting at [rootNode] and returns a sanitized [StructuralSnapshot].
     *
     * @param rootNode The accessibility root node of the active window, or null if unavailable.
     * @param displayHeightPixels The display height in pixels for relative screen boundary calculations.
     * @param displayWidthPixels The display width in pixels for horizontal distribution calculations.
     * @param eventType Optional incoming accessibility event type name for diagnostic tracking.
     */
    /**
     * Inspects the accessibility tree starting at [rootNode] and returns a sanitized [StructuralSnapshot].
     *
     * @param rootNode The accessibility root node of the active window, or null if unavailable.
     * @param displayHeightPixels The display height in pixels for relative screen boundary calculations.
     * @param displayWidthPixels The display width in pixels for horizontal distribution calculations.
     * @param eventType Optional incoming accessibility event type name for diagnostic tracking.
     * @param isPartialTree True if inspecting an event-source subtree rather than confirmed window root.
     * @param rootAcquisitionMethod Method by which rootNode was retrieved ("ROOT_IN_ACTIVE_WINDOW", "EVENT_SOURCE_SUBTREE", etc.).
     * @param captureTreeDump True to capture node-by-node privacy-safe structural metadata for diagnostic analysis.
     */
    private data class QueueItem(
        val node: AccessibilityNodeInfo,
        val depth: Int,
        val parentIdx: Int?,
        val isAncestorBoundsOnScreen: Boolean
    )

    fun sanitize(
        rootNode: AccessibilityNodeInfo?,
        displayHeightPixels: Int,
        displayWidthPixels: Int = 1080,
        eventType: String? = null,
        isPartialTree: Boolean = false,
        rootAcquisitionMethod: String = if (rootNode != null) "ROOT_IN_ACTIVE_WINDOW" else "NONE",
        captureTreeDump: Boolean = false
    ): StructuralSnapshot {
        if (rootNode == null) {
            return StructuralSnapshot(
                packageName = null,
                isForeground = false,
                hasRootNode = false,
                totalNodeCount = 0,
                maxDepth = 0,
                eventType = eventType,
                isPartialTree = false,
                rootAcquisitionMethod = "NONE",
                structuralTreeDump = emptyList()
            )
        }

        val observedPackage = rootNode.packageName?.toString()
        val isTargetApp = observedPackage == TARGET_PACKAGE

        var totalNodes = 0
        var maxObservedDepth = 0
        var reachedNodeLimit = false
        var reachedDepthLimit = false
        var hasValidBounds = false

        val observedClasses = mutableSetOf<String>()
        val classCounts = mutableMapOf<String, Int>()
        val hints = mutableSetOf<String>()
        val treeDump = if (captureTreeDump) mutableListOf<com.outloop.classifier.StructuralNodeRecord>() else emptyList<com.outloop.classifier.StructuralNodeRecord>()

        var hasBottomNav = false
        var bottomNavChildCount: Int? = null
        var selectedTabOrdinal: Int? = null
        var selectedTabId: String? = null
        var hasFeedPostControls = false
        val observedTabIds = mutableSetOf<String>()
        var hasVerticalPager = false
        var hasHorizontalPager = false
        var hasUnknownOrientationPager = false
        var hasScrollableFeed = false
        var hasHorizontalStoryList = false
        var hasTopAppBar = false
        var hasChatInput = false
        var hasMessageList = false
        var hasStoryProgress = false
        var hasMediaPlayback = false
        var hasProfileHeaderStructure = false

        // Explicit observed Instagram identifiers
        var hasDmInboxList = false
        var hasDmComposer = false
        var hasDmMessageList = false
        var hasClipsViewer = false
        var hasClipsComponents = false
        var hasProfileHeaderStats = false
        var hasProfileTabs = false
        var hasStoryViewer = false

        // Breadth-First / Depth-Bounded traversal
        val nodeBounds = Rect()
        val rootBounds = Rect()
        rootNode.getBoundsInScreen(rootBounds)
        val rootWidth = rootBounds.right - rootBounds.left
        val rootHeight = rootBounds.bottom - rootBounds.top
        val rootIntersects = if (displayWidthPixels > 0 && displayHeightPixels > 0) {
            rootBounds.bottom > 0 && rootBounds.top < displayHeightPixels &&
            rootBounds.right > 0 && rootBounds.left < displayWidthPixels
        } else true
        val rootAncestorOnScreen = (rootWidth > 0 && rootHeight > 0 && rootIntersects) || rootNode.childCount > 0

        val queue = ArrayDeque<QueueItem>()
        queue.add(QueueItem(rootNode, 0, null, rootAncestorOnScreen))

        while (queue.isNotEmpty()) {
            if (totalNodes >= MAX_NODE_COUNT) {
                reachedNodeLimit = true
                // Recycle remaining nodes in the queue to avoid binder leaks
                while (queue.isNotEmpty()) {
                    val (remNode, remDepth, remParent) = queue.removeFirst()
                    if (captureTreeDump) {
                        val remRawClass = remNode.className?.toString() ?: "android.view.View"
                        val remViewId = try { remNode.viewIdResourceName } catch (_: Exception) { null }
                        val remBounds = Rect()
                        remNode.getBoundsInScreen(remBounds)
                        (treeDump as MutableList<com.outloop.classifier.StructuralNodeRecord>).add(
                            com.outloop.classifier.StructuralNodeRecord(
                                nodeIndex = treeDump.size,
                                parentIndex = remParent,
                                depth = remDepth,
                                className = remRawClass,
                                resourceId = remViewId,
                                childCount = remNode.childCount,
                                isClickable = remNode.isClickable,
                                isScrollable = remNode.isScrollable,
                                isSelected = remNode.isSelected,
                                isVisibleToUser = remNode.isVisibleToUser,
                                bounds = "[${remBounds.left},${remBounds.top}][${remBounds.right},${remBounds.bottom}]",
                                traversalStatus = "NODE_LIMIT_SKIPPED"
                            )
                        )
                    }
                    if (remNode !== rootNode) {
                        @Suppress("DEPRECATION")
                        remNode.recycle()
                    }
                }
                break
            }

            val (node, depth, parentIdx, isAncestorBoundsOnScreen) = queue.removeFirst()
            val currentNodeIndex = if (captureTreeDump) (treeDump as MutableList<com.outloop.classifier.StructuralNodeRecord>).size else totalNodes
            totalNodes++
            if (depth > maxObservedDepth) {
                maxObservedDepth = depth
            }

            try {
                // Extract sanitized simple class name (e.g. "ViewPager2", "RecyclerView", "EditText")
                val rawClass = node.className?.toString() ?: ""
                val simpleClass = rawClass.substringAfterLast('.')
                if (simpleClass.isNotEmpty()) {
                    observedClasses.add(simpleClass)
                    classCounts[simpleClass] = (classCounts[simpleClass] ?: 0) + 1
                }

                node.getBoundsInScreen(nodeBounds)
                val boundWidth = nodeBounds.right - nodeBounds.left
                val boundHeight = nodeBounds.bottom - nodeBounds.top
                val isValidBounds = boundWidth > 0 && boundHeight > 0
                if (isValidBounds) {
                    hasValidBounds = true
                }

                val intersectsDisplay = if (displayWidthPixels > 0 && displayHeightPixels > 0) {
                    nodeBounds.bottom > 0 && nodeBounds.top < displayHeightPixels &&
                    nodeBounds.right > 0 && nodeBounds.left < displayWidthPixels
                } else {
                    true
                }

                // Distinguish structural capture vs visible active evidence:
                val isCurrentBoundsOnScreen = isValidBounds && intersectsDisplay
                val isExplicitlyOffScreen = isValidBounds && !intersectsDisplay
                val childAncestorBoundsOnScreen = isAncestorBoundsOnScreen && !isExplicitlyOffScreen
                val isEligibleForActiveEvidence = isAncestorBoundsOnScreen && isCurrentBoundsOnScreen && node.isVisibleToUser

                val childCount = node.childCount
                val viewId = try { node.viewIdResourceName } catch (_: Exception) { null }
                val idEntry = viewId?.substringAfterLast(':')?.substringAfterLast('/')?.lowercase()

                val isClipsViewPager = (viewId != null && (
                    viewId.contains("clips_viewer_view_pager", ignoreCase = true) ||
                    viewId.contains("reels_viewer_view_pager", ignoreCase = true) ||
                    viewId.contains("clips_viewer_pager", ignoreCase = true)
                ))

                // Derive active-screen signals ONLY from eligible on-screen visible nodes
                if (isEligibleForActiveEvidence) {
                    // 1. Direct Instagram bottom navigation tab recognition & explicit resource IDs
                    if (idEntry != null) {
                        when {
                            // Bottom navigation tabs
                            idEntry in listOf("feed_tab", "tab_feed") -> {
                                observedTabIds.add("feed_tab")
                                hasBottomNav = true
                                hints.add("TAB_FEED_OBSERVED")
                                if (isTabSelected(node)) {
                                    selectedTabId = "feed_tab"
                                    selectedTabOrdinal = 0
                                    hints.add("TAB_FEED_SELECTED")
                                }
                            }
                            idEntry in listOf("search_tab", "explore_tab", "tab_search") -> {
                                observedTabIds.add("search_tab")
                                hasBottomNav = true
                                hints.add("TAB_SEARCH_OBSERVED")
                                if (isTabSelected(node)) {
                                    selectedTabId = "search_tab"
                                    selectedTabOrdinal = 1
                                    hints.add("TAB_SEARCH_SELECTED")
                                }
                            }
                            idEntry in listOf("clips_tab", "reels_tab", "tab_clips") -> {
                                observedTabIds.add("clips_tab")
                                hasBottomNav = true
                                hints.add("TAB_CLIPS_OBSERVED")
                                if (isTabSelected(node)) {
                                    selectedTabId = "clips_tab"
                                    selectedTabOrdinal = 3
                                    hints.add("TAB_CLIPS_SELECTED")
                                }
                            }
                            idEntry in listOf("direct_tab", "tab_direct") -> {
                                observedTabIds.add("direct_tab")
                                hints.add("TAB_DIRECT_OBSERVED")
                                if (isTabSelected(node)) {
                                    selectedTabId = "direct_tab"
                                    hints.add("TAB_DIRECT_SELECTED")
                                }
                            }
                            idEntry in listOf("profile_tab", "tab_profile") -> {
                                observedTabIds.add("profile_tab")
                                hasBottomNav = true
                                hints.add("TAB_PROFILE_OBSERVED")
                                if (isTabSelected(node)) {
                                    selectedTabId = "profile_tab"
                                    selectedTabOrdinal = 4
                                    hints.add("TAB_PROFILE_SELECTED")
                                }
                            }

                            // DM Inbox
                            idEntry == "inbox_refreshable_thread_list_recyclerview" || idEntry.contains("inbox_refreshable_thread_list") -> {
                                hasDmInboxList = true
                                hasMessageList = true
                                hints.add("DM_INBOX_LIST")
                            }
                            idEntry == "igds_action_bar_title" || idEntry.contains("action_bar_title") -> {
                                hasTopAppBar = true
                                hints.add("TOP_ACTION_BAR_TITLE")
                            }

                            // DM Conversation
                            idEntry == "message_list" || idEntry.contains("message_list") -> {
                                hasDmMessageList = true
                                hasMessageList = true
                                hints.add("DM_MESSAGE_LIST")
                            }
                            idEntry == "message_composer_bar" || idEntry.contains("message_composer_bar") ||
                            idEntry == "row_thread_composer_edittext" || idEntry.contains("row_thread_composer_edittext") -> {
                                hasDmComposer = true
                                hasChatInput = true
                                hints.add("DM_COMPOSER")
                            }
                            idEntry == "thread_fragment_container" || idEntry.contains("thread_fragment_container") -> {
                                hasDmMessageList = true
                                hints.add("DM_THREAD_CONTAINER")
                            }

                            // Reels
                            idEntry == "clips_viewer_view_pager" || idEntry.contains("clips_viewer_view_pager") -> {
                                hasClipsViewer = true
                                hasVerticalPager = true
                                hints.add("CLIPS_VIEWER_VIEW_PAGER")
                            }
                            idEntry in listOf(
                                "clips_media_component",
                                "clips_single_media_component",
                                "clips_video_container",
                                "clips_media_info_component",
                                "clips_ufi_component",
                                "clips_caption_component",
                                "clips_author_info_component"
                            ) || (idEntry.startsWith("clips_") && !idEntry.contains("tab") && !idEntry.contains("grid")) -> {
                                hasClipsComponents = true
                                if (idEntry.contains("video") || idEntry.contains("media")) {
                                    hasMediaPlayback = true
                                }
                                hints.add("CLIPS_COMPONENT_${idEntry.uppercase()}")
                            }

                            // Profile
                            idEntry in listOf(
                                "profile_header_post_count_front_familiar",
                                "profile_header_followers_stacked_familiar",
                                "profile_header_following_stacked_familiar"
                            ) || idEntry.contains("post_count") || idEntry.contains("followers_stacked") || idEntry.contains("following_stacked") -> {
                                hasProfileHeaderStats = true
                                hasProfileHeaderStructure = true
                                hints.add("PROFILE_HEADER_STATS")
                            }
                            idEntry == "highlights_reel_tray_recycler_view" || idEntry.contains("highlights_reel_tray") -> {
                                hasHorizontalStoryList = true
                                hints.add("PROFILE_HIGHLIGHTS_TRAY")
                            }
                            idEntry in listOf("profile_tab_layout", "profile_viewpager", "clips_grid_recyclerview") ||
                            idEntry.contains("profile_tab_layout") || idEntry.contains("profile_viewpager") || idEntry.contains("clips_grid_recyclerview") -> {
                                hasProfileTabs = true
                                hints.add("PROFILE_TABS")
                            }
                            idEntry == "coordinator_root_layout" -> {
                                hints.add("COORDINATOR_ROOT_LAYOUT")
                            }

                            // Stories
                            idEntry in listOf(
                                "reel_viewer_root",
                                "reel_viewer_header_container",
                                "reel_viewer_header",
                                "reel_viewer_text_container",
                                "story_progress"
                            ) || idEntry.contains("reel_viewer") || idEntry.contains("story_progress") -> {
                                hasStoryViewer = true
                                hasStoryProgress = true
                                hasMediaPlayback = true
                                hints.add("STORY_VIEWER_COMPONENT")
                            }

                            // Feed Post Controls
                            idEntry.startsWith("row_feed_") ||
                            idEntry.contains("feed_button_like") ||
                            idEntry.contains("feed_button_comment") ||
                            idEntry.contains("feed_button_save") ||
                            idEntry == "media_group" -> {
                                hasFeedPostControls = true
                                hints.add("FEED_POST_CONTROLS")
                            }
                        }
                    }
                    if (observedTabIds.isNotEmpty()) {
                        bottomNavChildCount = maxOf(bottomNavChildCount ?: 0, observedTabIds.size)
                    }

                    // 2. Fallback: Detect bottom-anchored navigation bar container
                    val isSpanningWidth = displayWidthPixels <= 0 || boundWidth >= (displayWidthPixels * 0.70).toInt()
                    val isNearBottom = displayHeightPixels > 0 && nodeBounds.top >= (displayHeightPixels * 0.65).toInt()
                    val isCompactBottomBarHeight = displayHeightPixels > 0 && boundHeight <= (displayHeightPixels * 0.20).toInt()
                    val isAtBottom = isSpanningWidth && isNearBottom && isCompactBottomBarHeight

                    val isNavNamed = viewId != null && (
                        viewId.contains("tab_bar", ignoreCase = true) ||
                        viewId.contains("bottom_nav", ignoreCase = true) ||
                        viewId.contains("main_tab", ignoreCase = true) ||
                        viewId.contains("tabs", ignoreCase = true) ||
                        viewId.contains("navigation_bar", ignoreCase = true)
                    )

                    if (isAtBottom && !hasBottomNav) {
                        if (childCount in 4..6) {
                            var tabSelected: Int? = null
                            for (i in 0 until childCount) {
                                val child = node.getChild(i) ?: continue
                                try {
                                    if (isTabSelected(child)) {
                                        tabSelected = i
                                    }
                                } finally {
                                    @Suppress("DEPRECATION")
                                    child.recycle()
                                }
                            }

                            if (tabSelected != null || isNavNamed) {
                                hasBottomNav = true
                                bottomNavChildCount = childCount
                                selectedTabOrdinal = tabSelected
                                hints.add("BOTTOM_NAV_${childCount}_ITEMS")
                            }
                        } else if (childCount in 1..3) {
                            for (c in 0 until childCount) {
                                val child = node.getChild(c) ?: continue
                                try {
                                    val subCount = child.childCount
                                    val childViewId = try { child.viewIdResourceName } catch (_: Exception) { null }
                                    val isChildNavNamed = childViewId != null && (
                                        childViewId.contains("tab_bar", ignoreCase = true) ||
                                        childViewId.contains("bottom_nav", ignoreCase = true) ||
                                        childViewId.contains("main_tab", ignoreCase = true) ||
                                        childViewId.contains("tabs", ignoreCase = true)
                                    )

                                    if (subCount in 4..6) {
                                        var tabSelected: Int? = null
                                        for (i in 0 until subCount) {
                                            val tab = child.getChild(i) ?: continue
                                            try {
                                                if (isTabSelected(tab)) {
                                                    tabSelected = i
                                                }
                                            } finally {
                                                @Suppress("DEPRECATION")
                                                tab.recycle()
                                            }
                                        }

                                        if (tabSelected != null || isChildNavNamed || isNavNamed) {
                                            hasBottomNav = true
                                            bottomNavChildCount = subCount
                                            selectedTabOrdinal = tabSelected
                                            hints.add("BOTTOM_NAV_NESTED_${subCount}_ITEMS")
                                            break
                                        }
                                    }
                                } finally {
                                    @Suppress("DEPRECATION")
                                    child.recycle()
                                }
                            }
                        }
                    }

                    // 3. Detect chat input composer
                    if (node.isEditable || simpleClass == "EditText") {
                        hasChatInput = true
                        hints.add("CHAT_INPUT_COMPOSER")
                    }

                    // 4. Detect pagination container with reliable orientation evidence
                    val isPagerClass = simpleClass in listOf("ViewPager", "ViewPager2", "VerticalViewPager") ||
                        rawClass.contains("Pager", ignoreCase = true) ||
                        rawClass.contains("ClipsView", ignoreCase = true)

                    val isExplicitHorizontalPager = !isClipsViewPager && (
                        (viewId != null && (
                            viewId.contains("swipeable_tab_view_pager", ignoreCase = true) ||
                            viewId.contains("main_view_pager", ignoreCase = true) ||
                            viewId.contains("tab_pager", ignoreCase = true)
                        )) || rawClass.contains("HorizontalViewPager", ignoreCase = true) ||
                        (simpleClass == "ViewPager" && viewId?.contains("clips_viewer", ignoreCase = true) != true)
                    )

                    val isExplicitVerticalClipsPager = isClipsViewPager || (isPagerClass && !isExplicitHorizontalPager && (
                        rawClass.contains("VerticalViewPager", ignoreCase = true)
                    ))

                    if (isClipsViewPager || isExplicitVerticalClipsPager) {
                        hasVerticalPager = true
                        hints.add("VERTICAL_PAGER_CONTAINER")
                    } else if (isExplicitHorizontalPager) {
                        hasHorizontalPager = true
                        hints.add("HORIZONTAL_PAGER_CONTAINER")
                    } else if (simpleClass == "ViewPager2" || rawClass.contains("ViewPager2", ignoreCase = true)) {
                        val collectionInfo = try { node.collectionInfo } catch (_: Exception) { null }
                        if (collectionInfo != null) {
                            if (collectionInfo.rowCount > 1 && collectionInfo.columnCount <= 1) {
                                hasVerticalPager = true
                                hints.add("VERTICAL_PAGER_COLLECTION")
                            } else if (collectionInfo.columnCount > 1 && collectionInfo.rowCount <= 1) {
                                hasHorizontalPager = true
                                hints.add("HORIZONTAL_PAGER_COLLECTION")
                            } else {
                                hasUnknownOrientationPager = true
                                hints.add("PAGER_ORIENTATION_UNKNOWN")
                            }
                        } else {
                            hasUnknownOrientationPager = true
                            hints.add("PAGER_ORIENTATION_UNKNOWN")
                        }
                    }

                    // 5. Detect horizontal stories tray
                    val isStoryTrayBounds = displayHeightPixels > 0 &&
                        nodeBounds.top <= (displayHeightPixels * 0.25).toInt() &&
                        boundHeight <= (displayHeightPixels * 0.25).toInt() &&
                        nodeBounds.bottom <= (displayHeightPixels * 0.35).toInt()

                    if (isStoryTrayBounds && node.isScrollable && (
                        simpleClass in listOf("RecyclerView", "ComponentHost", "LithoView") ||
                        rawClass.contains("Story", ignoreCase = true) ||
                        rawClass.contains("Tray", ignoreCase = true)
                    )) {
                        hasHorizontalStoryList = true
                        hints.add("HORIZONTAL_STORY_TRAY")
                    }

                    // 6. Detect top action / app bar
                    val isAtExtremeTop = displayHeightPixels > 0 && nodeBounds.top <= (displayHeightPixels * 0.08).toInt()
                    val isCompactBarHeight = displayHeightPixels > 0 && boundHeight <= (displayHeightPixels * 0.12).toInt()
                    if (isAtExtremeTop && isCompactBarHeight && simpleClass in listOf("Toolbar", "ActionBar", "ViewGroup", "LinearLayout")) {
                        hasTopAppBar = true
                    }

                    // 7. Detect scrollable feed container
                    val isSubstantialListHeight = displayHeightPixels <= 0 || boundHeight >= (displayHeightPixels * 0.30).toInt()
                    val isScrollableList = node.isScrollable && !isAtBottom && !isStoryTrayBounds && isSubstantialListHeight &&
                        !isExplicitVerticalClipsPager && !isExplicitHorizontalPager
                    if (isScrollableList) {
                        if (simpleClass in listOf("RecyclerView", "ListView") || rawClass.contains("LithoView", ignoreCase = true)) {
                            hasScrollableFeed = true
                            hints.add("SCROLLABLE_FEED_CONTAINER")
                        }
                    }

                    // 8. Detect video/media playback container
                    if (simpleClass in listOf("SurfaceView", "TextureView", "VideoView") ||
                        rawClass.contains("Video", ignoreCase = true) ||
                        rawClass.contains("Player", ignoreCase = true) ||
                        (viewId != null && (viewId.contains("clips_video", ignoreCase = true) || viewId.contains("reels_video", ignoreCase = true)))
                    ) {
                        hasMediaPlayback = true
                        hints.add("MEDIA_PLAYBACK_SURFACE")
                    }

                    // 9. Detect story progress indicators
                    if ((nodeBounds.top in 0..100 && boundHeight in 2..20 && childCount > 1) ||
                        (viewId != null && viewId.contains("reel_viewer_root", ignoreCase = true))
                    ) {
                        hasStoryProgress = true
                        hints.add("STORY_PROGRESS_BARS")
                    }

                    // 10. Detect profile header layout
                    val isBelowTopBar = displayHeightPixels > 0 && nodeBounds.top >= (displayHeightPixels * 0.04).toInt()
                    val isSubstantialHeaderHeight = displayHeightPixels > 0 && boundHeight >= (displayHeightPixels * 0.12).toInt()
                    val isInUpperHalf = displayHeightPixels > 0 && nodeBounds.bottom <= (displayHeightPixels * 0.55).toInt()
                    val isProfileNamed = (viewId != null && (viewId.contains("profile", ignoreCase = true) || viewId.contains("bio", ignoreCase = true))) ||
                        rawClass.contains("Profile", ignoreCase = true)

                    if (isBelowTopBar && isSubstantialHeaderHeight && isInUpperHalf && childCount >= 3 &&
                        simpleClass in listOf("ViewGroup", "LinearLayout", "ConstraintLayout") && isProfileNamed) {
                        hasProfileHeaderStructure = true
                        hints.add("PROFILE_HEADER_STRUCTURE")
                    }
                }

                // Bounded structural traversal:
                // Cap queued children of scrollable feed container to 3 to prevent card explosion
                val isStoryTrayBounds = displayHeightPixels > 0 &&
                    nodeBounds.top <= (displayHeightPixels * 0.25).toInt() &&
                    boundHeight <= (displayHeightPixels * 0.25).toInt() &&
                    nodeBounds.bottom <= (displayHeightPixels * 0.35).toInt()
                val isSubstantialListHeight = displayHeightPixels <= 0 || boundHeight >= (displayHeightPixels * 0.30).toInt()
                val isScrollableFeedList = node.isScrollable && (simpleClass in listOf("RecyclerView", "ListView")) && !isStoryTrayBounds && isSubstantialListHeight
                val childrenToQueue = if (isScrollableFeedList) minOf(childCount, 3) else childCount

                if (captureTreeDump) {
                    val boundsStr = "[${nodeBounds.left},${nodeBounds.top}][${nodeBounds.right},${nodeBounds.bottom}]"
                    val cInfo = try {
                        node.collectionInfo?.let { "rows=${it.rowCount},cols=${it.columnCount}" }
                    } catch (_: Exception) { null }

                    val explicitOrientation = run {
                        val c = try { node.collectionInfo } catch (_: Exception) { null }
                        if (c != null) {
                            if (c.rowCount > 1 && c.columnCount <= 1) "VERTICAL"
                            else if (c.columnCount > 1 && c.rowCount <= 1) "HORIZONTAL"
                            else if (isClipsViewPager) "VERTICAL"
                            else "UNKNOWN"
                        } else if (isClipsViewPager || rawClass.contains("VerticalViewPager", ignoreCase = true)) {
                            "VERTICAL"
                        } else if (viewId != null && (viewId.contains("swipeable_tab_view_pager", ignoreCase = true) || viewId.contains("main_view_pager", ignoreCase = true) || viewId.contains("tab_pager", ignoreCase = true))) {
                            "HORIZONTAL"
                        } else if ((rawClass == "androidx.viewpager.widget.ViewPager" || simpleClass == "ViewPager") && !isClipsViewPager) {
                            "HORIZONTAL"
                        } else {
                            null
                        }
                    }

                    val status = if (isScrollableFeedList && childCount > 3) {
                        "CHILDREN_CAPPED (3 of $childCount queued)"
                    } else if (depth >= MAX_DEPTH && childCount > 0) {
                        "DEPTH_LIMIT ($childCount unvisited)"
                    } else {
                        "VISITED"
                    }

                    (treeDump as MutableList<com.outloop.classifier.StructuralNodeRecord>).add(
                        com.outloop.classifier.StructuralNodeRecord(
                            nodeIndex = currentNodeIndex,
                            parentIndex = parentIdx,
                            depth = depth,
                            className = rawClass,
                            resourceId = viewId,
                            childCount = childCount,
                            isClickable = node.isClickable,
                            isScrollable = node.isScrollable,
                            isSelected = node.isSelected,
                            isVisibleToUser = node.isVisibleToUser,
                            bounds = boundsStr,
                            collectionInfo = cInfo,
                            pagerOrientation = explicitOrientation,
                            traversalStatus = status
                        )
                    )
                }

                if (depth < MAX_DEPTH) {
                    for (i in 0 until childrenToQueue) {
                        val child = node.getChild(i)
                        if (child != null) {
                            queue.add(QueueItem(child, depth + 1, currentNodeIndex, childAncestorBoundsOnScreen))
                        }
                    }
                } else {
                    if (childrenToQueue > 0) {
                        reachedDepthLimit = true
                    }
                }

            } finally {
                // Safely recycle dequeued node (only if not the rootNode owned by the service)
                if (node !== rootNode) {
                    @Suppress("DEPRECATION")
                    node.recycle()
                }
            }
        }

        val resolvedHasMessageList = hasMessageList || hasDmMessageList || hasDmInboxList || (hasScrollableFeed && !hasBottomNav && !hasMediaPlayback && !hasFeedPostControls && (hasTopAppBar || hasChatInput))

        return StructuralSnapshot(
            packageName = observedPackage,
            isForeground = isTargetApp,
            hasRootNode = true,
            totalNodeCount = totalNodes,
            maxDepth = maxObservedDepth,
            observedWidgetClasses = observedClasses,
            hasBottomNavigationBar = hasBottomNav,
            selectedBottomTabOrdinal = selectedTabOrdinal,
            selectedTabId = selectedTabId,
            hasFeedPostControls = hasFeedPostControls,
            hasVerticalPager = hasVerticalPager,
            hasHorizontalPager = hasHorizontalPager,
            hasUnknownOrientationPager = hasUnknownOrientationPager,
            isPartialTree = isPartialTree,
            rootAcquisitionMethod = rootAcquisitionMethod,
            hasScrollableFeed = hasScrollableFeed,
            hasHorizontalStoryList = hasHorizontalStoryList,
            hasTopAppBar = hasTopAppBar,
            hasChatInputBar = hasChatInput,
            hasMessageList = resolvedHasMessageList,
            hasStoryProgressIndicators = hasStoryProgress,
            hasMediaPlaybackContainer = hasMediaPlayback,
            hasProfileHeader = hasProfileHeaderStructure,
            bottomNavObserved = hasBottomNav,
            bottomNavChildCount = bottomNavChildCount,
            reachedNodeLimit = reachedNodeLimit,
            reachedDepthLimit = reachedDepthLimit,
            hasBounds = hasValidBounds,
            observedClassCounts = classCounts,
            eventType = eventType,
            detectedStructuralHints = hints,
            hasDmInboxList = hasDmInboxList,
            hasDmComposer = hasDmComposer,
            hasDmMessageList = hasDmMessageList,
            hasClipsViewer = hasClipsViewer,
            hasClipsComponents = hasClipsComponents,
            hasProfileHeaderStats = hasProfileHeaderStats,
            hasProfileTabs = hasProfileTabs,
            hasStoryViewer = hasStoryViewer,
            structuralTreeDump = treeDump
        )
    }

    private fun isTabSelected(tabNode: AccessibilityNodeInfo): Boolean {
        if (tabNode.isSelected || tabNode.isFocused) return true
        for (j in 0 until tabNode.childCount) {
            val subChild = tabNode.getChild(j) ?: continue
            try {
                if (subChild.isSelected || subChild.isFocused) return true
                for (k in 0 until subChild.childCount) {
                    val grandChild = subChild.getChild(k) ?: continue
                    try {
                        if (grandChild.isSelected || grandChild.isFocused) return true
                    } finally {
                        @Suppress("DEPRECATION")
                        grandChild.recycle()
                    }
                }
            } finally {
                @Suppress("DEPRECATION")
                subChild.recycle()
            }
        }
        return false
    }
}
