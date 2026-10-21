package com.outloop.classifier

/**
 * A privacy-minimized, purely structural snapshot of an application screen.
 *
 * PRIVACY GUARANTEES:
 * - Contains NO raw text (never captures `node.text`).
 * - Contains NO content descriptions (never captures `node.contentDescription`).
 * - Contains NO user-generated content, usernames, IDs, or message contents.
 * - Retains NO references to Android framework AccessibilityNodeInfo instances.
 * - Exclusively records structural layout flags and sanitized widget class names.
 */
data class StructuralSnapshot(
    /** The package name of the inspected window (e.g. "com.instagram.android"). */
    val packageName: String?,

    /** Whether the target application is currently active in the foreground. */
    val isForeground: Boolean = true,

    /** Whether an accessibility root node was successfully obtained from the system. */
    val hasRootNode: Boolean = true,

    /** Total count of inspected nodes (bounded to prevent unbounded hierarchy scans). */
    val totalNodeCount: Int = 0,

    /** Maximum hierarchy depth traversed (bounded). */
    val maxDepth: Int = 0,

    /**
     * Set of sanitized, canonical widget class names observed in the hierarchy.
     * (e.g. "ViewPager2", "RecyclerView", "EditText", "Button", "FrameLayout", "SurfaceView").
     */
    val observedWidgetClasses: Set<String> = emptySet(),

    /** True if a bottom-anchored navigation bar containing multiple tab items was detected. */
    val hasBottomNavigationBar: Boolean = false,

    /**
     * 0-indexed ordinal of the currently selected bottom navigation tab, if identifiable.
     * Typically: 0 = Home, 1 = Search, 2 = Create, 3 = Reels, 4 = Profile.
     */
    val selectedBottomTabOrdinal: Int? = null,

    /** True if a full-screen vertical pagination container (e.g. ViewPager2) was detected. */
    val hasVerticalPager: Boolean = false,

    /** True if a horizontal stories tray was detected near the top of the interface. */
    val hasHorizontalStoryList: Boolean = false,

    /** True if a top action/app bar was detected. */
    val hasTopAppBar: Boolean = false,

    /** True if a chat/message input composer (e.g. EditText at bottom) was detected. */
    val hasChatInputBar: Boolean = false,

    /** True if a conversation message list / thread list was detected. */
    val hasMessageList: Boolean = false,

    /** True if segmented story progress indicator bars were detected at the top. */
    val hasStoryProgressIndicators: Boolean = false,

    /** True if a video rendering surface (SurfaceView/TextureView/Player container) was detected. */
    val hasMediaPlaybackContainer: Boolean = false,

    /** True if a profile header structure (avatar + stats container) was detected. */
    val hasProfileHeader: Boolean = false,

    /** Privacy-safe structural classification hints. */
    val detectedStructuralHints: Set<String> = emptySet()
)
