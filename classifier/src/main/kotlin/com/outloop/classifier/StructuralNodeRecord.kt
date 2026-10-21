package com.outloop.classifier

/**
 * A privacy-safe structural representation of a single Android accessibility node.
 *
 * CRITICAL PRIVACY ASSURANCES:
 * - NEVER captures node text (`node.text`).
 * - NEVER captures content descriptions (`node.contentDescription`).
 * - NEVER captures user input, messages, usernames, or image contents.
 * - Captures ONLY class names, bounds, structural flags, and hierarchy linkage.
 */
data class StructuralNodeRecord(
    /** Sequential 0-indexed traversal visit identifier. */
    val nodeIndex: Int,

    /** Index of the parent node in the traversal list, or null if root. */
    val parentIndex: Int?,

    /** Hierarchy depth (root = 0). */
    val depth: Int,

    /** Canonical or raw Android accessibility class name. */
    val className: String,

    /** Resource ID entry name if available, or null if stripped/unavailable. */
    val resourceId: String?,

    /** Number of direct children on this node. */
    val childCount: Int,

    /** Whether the node responds to clicks. */
    val isClickable: Boolean,

    /** Whether the node is scrollable. */
    val isScrollable: Boolean,

    /** Whether the node is selected. */
    val isSelected: Boolean,

    /** Whether the node is visible to the user. */
    val isVisibleToUser: Boolean,

    /** Screen coordinates [left, top][right, bottom]. */
    val bounds: String,

    /** Collection metadata (e.g. "rows=5,cols=1") if available. */
    val collectionInfo: String? = null,

    /** Explicitly supported pager orientation ("VERTICAL", "HORIZONTAL", or null). */
    val pagerOrientation: String? = null,

    /** Traversal status ("VISITED", "CHILDREN_CAPPED(3 of N)", "DEPTH_LIMIT", "NODE_LIMIT"). */
    val traversalStatus: String = "VISITED"
)
