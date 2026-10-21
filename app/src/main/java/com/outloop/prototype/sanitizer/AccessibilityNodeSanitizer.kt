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

    private const val MAX_DEPTH = 10
    private const val MAX_NODE_COUNT = 100
    private const val TARGET_PACKAGE = "com.instagram.android"

    /**
     * Inspects the accessibility tree starting at [rootNode] and returns a sanitized [StructuralSnapshot].
     *
     * @param rootNode The accessibility root node of the active window, or null if unavailable.
     * @param displayHeightPixels The display height in pixels for relative screen boundary calculations.
     */
    fun sanitize(rootNode: AccessibilityNodeInfo?, displayHeightPixels: Int): StructuralSnapshot {
        if (rootNode == null) {
            return StructuralSnapshot(
                packageName = null,
                isForeground = false,
                hasRootNode = false,
                totalNodeCount = 0,
                maxDepth = 0
            )
        }

        val observedPackage = rootNode.packageName?.toString()
        val isTargetApp = observedPackage == TARGET_PACKAGE

        var totalNodes = 0
        var maxObservedDepth = 0
        val observedClasses = mutableSetOf<String>()
        val hints = mutableSetOf<String>()

        var hasBottomNav = false
        var selectedTabOrdinal: Int? = null
        var hasVerticalPager = false
        var hasHorizontalStoryList = false
        var hasTopAppBar = false
        var hasChatInput = false
        var hasMessageList = false
        var hasStoryProgress = false
        var hasMediaPlayback = false
        var hasProfileHeaderStructure = false

        // Breadth-First / Depth-Bounded traversal
        val nodeBounds = Rect()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(Pair(rootNode, 0))

        while (queue.isNotEmpty() && totalNodes < MAX_NODE_COUNT) {
            val (node, depth) = queue.removeFirst()
            totalNodes++
            if (depth > maxObservedDepth) {
                maxObservedDepth = depth
            }

            // Extract sanitized simple class name (e.g. "ViewPager2", "RecyclerView", "EditText")
            val rawClass = node.className?.toString() ?: ""
            val simpleClass = rawClass.substringAfterLast('.')
            if (simpleClass.isNotEmpty()) {
                observedClasses.add(simpleClass)
            }

            node.getBoundsInScreen(nodeBounds)

            // Detect bottom-anchored navigation bar
            // Bottom 20% of the display with 4-5 child items
            val isAtBottom = displayHeightPixels > 0 && nodeBounds.top >= (displayHeightPixels * 0.80).toInt()
            val childCount = node.childCount

            if (isAtBottom && childCount in 4..6) {
                hasBottomNav = true
                hints.add("BOTTOM_NAV_${childCount}_ITEMS")

                // Check which tab child is selected/focused
                for (i in 0 until childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        if (child.isSelected || child.isFocused) {
                            selectedTabOrdinal = i
                        }
                    }
                }
            }

            // Detect chat input composer (e.g. EditText or editable widget)
            if (node.isEditable || simpleClass == "EditText") {
                hasChatInput = true
                hints.add("CHAT_INPUT_COMPOSER")
            }

            // Detect vertical pager (e.g. ViewPager2 or full-screen vertical scrollable container)
            if (simpleClass == "ViewPager2" || (node.isScrollable && nodeBounds.height() >= (displayHeightPixels * 0.70).toInt())) {
                hasVerticalPager = true
                hints.add("VERTICAL_PAGER_CONTAINER")
            }

            // Detect horizontal stories tray (horizontal scroll collection in upper 25% of screen)
            val isNearTop = displayHeightPixels > 0 && nodeBounds.top <= (displayHeightPixels * 0.25).toInt()
            if (isNearTop && node.isScrollable && simpleClass == "RecyclerView") {
                hasHorizontalStoryList = true
                hints.add("HORIZONTAL_STORY_TRAY")
            }

            // Detect top action / app bar
            if (isNearTop && simpleClass in listOf("Toolbar", "ActionBar", "ViewGroup") && nodeBounds.height() in 40..250) {
                hasTopAppBar = true
            }

            // Detect message list (scrollable list taking central screen height)
            if (node.isScrollable && simpleClass == "RecyclerView" && !isAtBottom && !isNearTop) {
                hasMessageList = true
                hints.add("MESSAGE_LIST_CONTAINER")
            }

            // Detect video/media playback container
            if (simpleClass in listOf("SurfaceView", "TextureView", "VideoView") ||
                rawClass.contains("Video", ignoreCase = true) ||
                rawClass.contains("Player", ignoreCase = true)
            ) {
                hasMediaPlayback = true
                hints.add("MEDIA_PLAYBACK_SURFACE")
            }

            // Detect story progress indicators (narrow horizontal elements at extreme top)
            if (nodeBounds.top in 0..100 && nodeBounds.height() in 2..20 && childCount > 1) {
                hasStoryProgress = true
                hints.add("STORY_PROGRESS_BARS")
            }

            // Detect profile header layout
            if (isNearTop && (childCount >= 3) && simpleClass in listOf("ViewGroup", "LinearLayout", "ConstraintLayout")) {
                // If there's an avatar and multiple stat counters in upper third
                if (nodeBounds.bottom <= (displayHeightPixels * 0.45).toInt()) {
                    hasProfileHeaderStructure = true
                    hints.add("PROFILE_HEADER_STRUCTURE")
                }
            }

            // Traverse children up to MAX_DEPTH and MAX_NODE_COUNT
            if (depth < MAX_DEPTH) {
                for (i in 0 until childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(Pair(child, depth + 1))
                    }
                }
            }
        }

        return StructuralSnapshot(
            packageName = observedPackage,
            isForeground = isTargetApp,
            hasRootNode = true,
            totalNodeCount = totalNodes,
            maxDepth = maxObservedDepth,
            observedWidgetClasses = observedClasses,
            hasBottomNavigationBar = hasBottomNav,
            selectedBottomTabOrdinal = selectedTabOrdinal,
            hasVerticalPager = hasVerticalPager,
            hasHorizontalStoryList = hasHorizontalStoryList,
            hasTopAppBar = hasTopAppBar,
            hasChatInputBar = hasChatInput,
            hasMessageList = hasMessageList,
            hasStoryProgressIndicators = hasStoryProgress,
            hasMediaPlaybackContainer = hasMediaPlayback,
            hasProfileHeader = hasProfileHeaderStructure,
            detectedStructuralHints = hints
        )
    }
}
