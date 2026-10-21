package com.outloop.classifier

/**
 * Confidence categories representing structural evidence strength.
 *
 * Rules:
 * - HIGH: Supported by clear, repeatable structural evidence observed and validated on a real device.
 * - MEDIUM: Relevant structural evidence exists, but alternative interpretations remain plausible.
 * - LOW: Evidence is weak, incomplete, conflicting, or not yet validated on a real device.
 */
enum class Confidence(val label: String) {
    HIGH("High Confidence"),
    MEDIUM("Medium Confidence"),
    LOW("Low Confidence")
}
