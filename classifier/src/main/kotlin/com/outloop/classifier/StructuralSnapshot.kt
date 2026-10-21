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

    /** Resource ID entry name of the selected tab if identifiable (e.g. "feed_tab", "clips_tab", "profile_tab"). */
    val selectedTabId: String? = null,

    /** True if standard feed post action controls (like, comment, save, media group) were detected. */
    val hasFeedPostControls: Boolean = false,

    /** True if a full-screen vertical pagination container (e.g. ViewPager2 with vertical paging) was detected. */
    val hasVerticalPager: Boolean = false,

    /** True if a horizontal pagination container (e.g. standard ViewPager for Camera-Feed-DM) was detected. */
    val hasHorizontalPager: Boolean = false,

    /** True if a pager container was detected but its scroll orientation could not be reliably established. */
    val hasUnknownOrientationPager: Boolean = false,

    /** True if the inspected tree is a partial event-source subtree rather than a verified window root. */
    val isPartialTree: Boolean = false,

    /** Mechanism by which the root node was acquired ("ROOT_IN_ACTIVE_WINDOW", "EVENT_SOURCE_SUBTREE", "EVENT_SOURCE_WINDOW_ROOT", "NONE"). */
    val rootAcquisitionMethod: String = "NONE",

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

    /** True if a main scrollable feed container (continuous vertical list, not a page-by-page pager) was detected. */
    val hasScrollableFeed: Boolean = false,

    /** True if bottom navigation structure was specifically observable. */
    val bottomNavObserved: Boolean = false,

    /** Number of tab items detected in the bottom navigation bar. */
    val bottomNavChildCount: Int? = null,

    /** True if traversal reached the configured node limit (e.g. 250 nodes). */
    val reachedNodeLimit: Boolean = false,

    /** True if traversal reached the configured depth limit (e.g. 14 levels). */
    val reachedDepthLimit: Boolean = false,

    /** True if coordinate bounds were available and usable from the accessibility nodes. */
    val hasBounds: Boolean = true,

    /** Counts of canonical widget class names observed in the hierarchy. */
    val observedClassCounts: Map<String, Int> = emptyMap(),

    /** The incoming accessibility event type name (e.g. TYPE_WINDOW_STATE_CHANGED). */
    val eventType: String? = null,

    /** Privacy-safe structural classification hints. */
    val detectedStructuralHints: Set<String> = emptySet(),

    /** True if DM Inbox thread list (inbox_refreshable_thread_list_recyclerview) was detected on-screen. */
    val hasDmInboxList: Boolean = false,

    /** True if DM chat composer bar (message_composer_bar, row_thread_composer_edittext) was detected on-screen. */
    val hasDmComposer: Boolean = false,

    /** True if DM message list (message_list, thread_fragment_container) was detected on-screen. */
    val hasDmMessageList: Boolean = false,

    /** True if dedicated Clips viewer container (clips_viewer_view_pager) was detected on-screen. */
    val hasClipsViewer: Boolean = false,

    /** True if active Clips video/media components (clips_media_component, clips_single_media_component, etc.) were detected on-screen. */
    val hasClipsComponents: Boolean = false,

    /** True if explicit profile header stats (profile_header_post_count_front_familiar, followers, following) were detected on-screen. */
    val hasProfileHeaderStats: Boolean = false,

    /** True if profile-specific tabs/viewpager (profile_tab_layout, profile_viewpager, clips_grid_recyclerview) were detected on-screen. */
    val hasProfileTabs: Boolean = false,

    /** True if Story viewer root or components (reel_viewer_root, reel_viewer_header_container, story_progress) were detected on-screen. */
    val hasStoryViewer: Boolean = false,

    /** Optional detailed privacy-safe structural tree dump for diagnostic analysis. */
    val structuralTreeDump: List<StructuralNodeRecord> = emptyList()
)
