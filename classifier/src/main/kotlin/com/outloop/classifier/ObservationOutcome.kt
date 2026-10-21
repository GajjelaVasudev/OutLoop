package com.outloop.classifier

/**
 * Standardized status indicating the outcome of an accessibility observation.
 *
 * Distinguishes system-level availability (missing root), incomplete evaluation
 * (truncated traversal or partial subtree), completed evaluation with no matching signals,
 * and successfully classified observations.
 */
enum class ObservationOutcome {
    /** Root node was unavailable from active window and event source (transient or background). */
    ROOT_UNAVAILABLE,

    /** Tree was partial or truncated due to node/depth traversal limits. */
    PARTIAL_OR_TRUNCATED,

    /** Complete tree was inspected, but no supported Instagram structural patterns matched. */
    NO_MATCHING_SIGNALS,

    /** Screen state was evaluated and classified with sufficient structural evidence. */
    CLASSIFIED
}
