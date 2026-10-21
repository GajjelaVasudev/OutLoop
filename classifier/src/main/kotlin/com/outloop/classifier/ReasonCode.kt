package com.outloop.classifier

/**
 * Standardized diagnostic reason codes explaining classifier decisions.
 */
enum class ReasonCode(val description: String) {
    /** Distinct layout pattern matched known structural signature. */
    KNOWN_STRUCTURE_MATCH("Known structural layout pattern matched"),

    /** Multiple independent structural cues corroborate candidate state. */
    MULTIPLE_SIGNALS_MATCH("Multiple independent structural signals matched"),

    /** Structure is ambiguous (e.g. video container present without dedicated tab cues). */
    AMBIGUOUS_STRUCTURE("Ambiguous layout structure detected"),

    /** No accessibility root node was available from the system window. */
    MISSING_ROOT("No active window root node available"),

    /** Layout contains no recognizable navigation or structural signals. */
    NO_SUPPORTED_SIGNALS("No supported structural signals detected"),

    /** Target application is not currently active in the foreground window. */
    APP_NOT_FOREGROUND("Target app is not the active foreground window"),

    /** The layout hierarchy structure is unrecognized or unsupported. */
    UNSUPPORTED_STRUCTURE("Unsupported hierarchy structure"),

    /** Detected cues contradict each other (e.g. bottom navigation + chat composer). */
    CONFLICTING_SIGNALS("Conflicting structural signals detected"),

    /** Insufficient structural nodes or depth to make a reliable determination. */
    INSUFFICIENT_EVIDENCE("Insufficient structural evidence"),

    /** Hierarchy traversal limit reached before complete structure could be observed. */
    TRUNCATED_TRAVERSAL("Hierarchy traversal limit reached before complete structure could be observed")
}

