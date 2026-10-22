package com.outloop.prototype.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

enum class PlaybackRestrictionCause {
    DAILY_ALLOWANCE,
    CONTINUOUS_BREAK
}

enum class PlaybackControlResult {
    SEMANTIC_PAUSE_EXECUTED,
    FALLBACK_AUDIO_FOCUS_ONLY,
    REJECTED_NOT_TARGET_PACKAGE,
    REJECTED_NOT_REELS_CONTEXT
}

/**
 * Controller responsible for managing Reels video playback suppression and audio-focus lifecycle
 * when OutLoop restrictions or continuous break prompts are active.
 *
 * ARCHITECTURAL SAFETY & DESIGN GUARANTEES:
 * 1. Semantic Pause: Only executes genuine accessibility actions if an explicit semantic pause
 *    action or control (e.g. action label "pause" or dedicated pause viewId) is declared by Instagram.
 * 2. Zero Coordinate Guesses: Never taps blind coordinates, guesses screen positions, or uses synthetic gestures.
 * 3. Documented Audio Fallback: If no semantic pause action exists, utilizes transient media audio focus
 *    (USAGE_MEDIA) to cause video media players (such as ExoPlayer) to pause/silence background audio.
 * 4. Multi-Cause Retention: Preserves audio focus if one restriction is dismissed (e.g., continuous break prompt)
 *    while another remains active (e.g., daily allowance exhausted).
 * 5. Strict Audio Lifecycle: Immediately abandons audio focus upon overlay dismissal, window transition,
 *    or app departure so unrelated apps' audio is never permanently ducked or silenced.
 * 6. Target Package Guard: Revalidates that the active window belongs to com.instagram.android before
 *    performing any playback suppression.
 */
class ReelsPlaybackController(
    private val context: Context,
    private val targetPackage: String = "com.instagram.android",
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val logger: (String) -> Unit = { Log.d(TAG, it) }
) {
    companion object {
        private const val TAG = "OutLoopPlayback"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isAudioFocusHeld: Boolean = false
    private val activeCauses = mutableSetOf<PlaybackRestrictionCause>()

    val isHoldingAudioFocus: Boolean
        get() = isAudioFocusHeld

    val currentActiveCauses: Set<PlaybackRestrictionCause>
        get() = activeCauses.toSet()

    var lastResult: PlaybackControlResult? = null
        private set

    /**
     * Attempts to pause playback on the active Reels screen.
     * First checks if a reliable semantic pause control is exposed in the accessibility tree.
     * In either case, requests transient media audio focus to silence/pause background Reel playback.
     *
     * @param rootNode The accessibility root node of the active window.
     * @param isVerifiedTargetPackage True if the active window is verified to belong to [targetPackage].
     * @param isReelsContext True if the current screen is verified to be a Reels context.
     * @param cause The source of the restriction ([PlaybackRestrictionCause.DAILY_ALLOWANCE] or [PlaybackRestrictionCause.CONTINUOUS_BREAK]).
     * @return True if a genuine semantic pause action was found and invoked; false if falling back to audio focus.
     */
    fun suppressPlayback(
        rootNode: AccessibilityNodeInfo?,
        isVerifiedTargetPackage: Boolean,
        isReelsContext: Boolean = true,
        cause: PlaybackRestrictionCause = PlaybackRestrictionCause.DAILY_ALLOWANCE
    ): Boolean {
        if (!isVerifiedTargetPackage) {
            lastResult = PlaybackControlResult.REJECTED_NOT_TARGET_PACKAGE
            logger("Playback suppression rejected: active package is not $targetPackage")
            return false
        }
        if (!isReelsContext) {
            lastResult = PlaybackControlResult.REJECTED_NOT_REELS_CONTEXT
            logger("Playback suppression rejected: screen is not verified Reels context")
            return false
        }

        activeCauses.add(cause)

        // 1. Attempt genuine semantic pause if rootNode is available
        val semanticPauseInvoked = attemptSemanticPause(rootNode)
        if (semanticPauseInvoked) {
            lastResult = PlaybackControlResult.SEMANTIC_PAUSE_EXECUTED
            logger("Semantic pause control found and invoked on Reels player for cause: $cause.")
        } else {
            lastResult = PlaybackControlResult.FALLBACK_AUDIO_FOCUS_ONLY
            logger("No semantic pause control exposed by Instagram; applying transient media audio focus fallback for cause: $cause.")
        }

        // 2. Request transient audio focus to mute/pause media playback in ExoPlayer
        requestTransientAudioFocus()

        return semanticPauseInvoked
    }

    /**
     * Releases playback suppression for a specific restriction cause.
     * If no other restriction causes remain active, abandons audio focus.
     * If other causes remain (e.g. daily allowance exhausted while continuous break prompt dismissed),
     * audio focus is preserved.
     */
    fun releaseSuppression(cause: PlaybackRestrictionCause) {
        activeCauses.remove(cause)
        logger("Playback suppression released for cause: $cause. Remaining active causes: $activeCauses")
        if (activeCauses.isEmpty()) {
            abandonTransientAudioFocus()
        } else {
            logger("Audio focus preserved because other restriction causes are still active: $activeCauses")
        }
    }

    /**
     * Unconditionally releases all playback suppression causes and abandons audio focus.
     * Safe and idempotent.
     */
    fun releaseAllSuppression() {
        activeCauses.clear()
        logger("All playback suppression causes cleared.")
        abandonTransientAudioFocus()
    }

    /**
     * Requests transient media audio focus (USAGE_MEDIA, AUDIOFOCUS_GAIN_TRANSIENT).
     * ExoPlayer instances listening for audio focus loss will pause or silence playback.
     */
    fun requestTransientAudioFocus() {
        if (isAudioFocusHeld) {
            // Already holding focus; do not re-request repeatedly on every event
            return
        }

        try {
            if (sdkInt >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        logger("Audio focus change received: $focusChange")
                    }
                    .build()
                audioFocusRequest = focusRequest
                val result = audioManager?.requestAudioFocus(focusRequest)
                isAudioFocusHeld = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                isAudioFocusHeld = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }
            logger("Transient audio focus requested: granted=$isAudioFocusHeld")
        } catch (e: Exception) {
            logger("Error requesting audio focus: ${e.message}")
            isAudioFocusHeld = false
        }
    }

    /**
     * Immediately abandons transient audio focus and restores system audio routing.
     * Guaranteed safe and idempotent.
     */
    fun abandonTransientAudioFocus() {
        if (!isAudioFocusHeld && audioFocusRequest == null) return

        try {
            if (sdkInt >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
            logger("Transient audio focus abandoned. System audio restored.")
        } catch (e: Exception) {
            logger("Error abandoning audio focus: ${e.message}")
        } finally {
            isAudioFocusHeld = false
            audioFocusRequest = null
        }
    }

    /**
     * Traverses the node hierarchy looking for genuine semantic pause controls.
     * Strictly verifies node package matches [targetPackage].
     * Recycles all intermediate child nodes.
     */
    private fun attemptSemanticPause(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val pkg = rootNode.packageName?.toString()
        if (pkg != null && pkg != targetPackage) return false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0
        val maxNodes = 100
        var pauseActivated = false

        while (queue.isNotEmpty() && visited < maxNodes && !pauseActivated) {
            val node = queue.removeFirst()
            visited++

            val isEnabled = try { node.isEnabled } catch (_: Exception) { true }
            if (!isEnabled) {
                if (node !== rootNode) {
                    @Suppress("DEPRECATION")
                    node.recycle()
                }
                continue
            }

            // Check if node declares an explicit action labeled "pause"
            val actions = try { node.actionList } catch (_: Exception) { null }
            if (actions != null) {
                for (action in actions) {
                    val label = try { action.label?.toString() } catch (_: Exception) { null }
                    if (label != null && label.contains("pause", ignoreCase = true)) {
                        val result = try {
                            node.performAction(action.id)
                        } catch (_: Exception) { false }
                        if (result) {
                            pauseActivated = true
                            logger("Successfully executed pause accessibility action (id=${action.id}, label=$label)")
                            break
                        }
                    }
                }
            }

            // Check contentDescription for explicit "pause"
            if (!pauseActivated) {
                val contentDesc = try { node.contentDescription?.toString() } catch (_: Exception) { null }
                if (contentDesc != null && (contentDesc.equals("pause", ignoreCase = true) ||
                        contentDesc.equals("pause video", ignoreCase = true) ||
                        contentDesc.equals("pause reel", ignoreCase = true))) {
                    val isClickable = try { node.isClickable } catch (_: Exception) { false }
                    if (isClickable) {
                        val result = try {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        } catch (_: Exception) { false }
                        if (result) {
                            pauseActivated = true
                            logger("Successfully executed ACTION_CLICK on semantic pause control (desc=$contentDesc)")
                        }
                    }
                }
            }

            // Check viewId for dedicated pause control
            if (!pauseActivated) {
                val viewId = try { node.viewIdResourceName } catch (_: Exception) { null }
                if (viewId != null && (viewId.contains("playback_pause", ignoreCase = true) ||
                        viewId.contains("video_pause", ignoreCase = true) ||
                        viewId.contains("pause_button", ignoreCase = true))) {
                    val isClickable = try { node.isClickable } catch (_: Exception) { false }
                    if (isClickable) {
                        val result = try {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        } catch (_: Exception) { false }
                        if (result) {
                            pauseActivated = true
                            logger("Successfully executed ACTION_CLICK on dedicated pause viewId ($viewId)")
                        }
                    }
                }
            }

            // Queue children
            val childCount = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                queue.add(child)
            }

            if (node !== rootNode) {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }

        // Clean up any remaining nodes in queue
        while (queue.isNotEmpty()) {
            val rem = queue.removeFirst()
            if (rem !== rootNode) {
                @Suppress("DEPRECATION")
                rem.recycle()
            }
        }

        return pauseActivated
    }
}
