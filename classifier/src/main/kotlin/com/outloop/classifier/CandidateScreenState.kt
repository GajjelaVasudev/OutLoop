package com.outloop.classifier

/**
 * Candidate screen states for Instagram interface detection.
 *
 * Designed to selectively distinguish Instagram's dedicated Reels tab from
 * communication (DMs), user content (Stories, Profile), and the ordinary Home Feed,
 * without collecting or inspecting private user content.
 */
enum class CandidateScreenState(val displayName: String) {
    /** The dedicated full-screen Reels browsing tab. */
    REELS_TAB("Dedicated Reels Tab"),

    /** The direct messaging inbox / conversation list. */
    DM_INBOX("DM Inbox"),

    /** An individual direct message chat / thread. */
    DM_CONVERSATION("DM Conversation"),

    /** Instagram Stories viewing interface. */
    STORIES("Stories"),

    /** User profile screen. */
    PROFILE("Profile"),

    /** The standard main home feed. */
    HOME_FEED("Home Feed"),

    /**
     * A full-screen video interface that does not exhibit dedicated Reels tab signals
     * (e.g. a Reel or video opened from a DM conversation or Explore).
     */
    FULL_SCREEN_VIDEO("Full-Screen Video"),

    /** Screen state cannot be determined with sufficient confidence. */
    UNKNOWN("Unknown Screen")
}
