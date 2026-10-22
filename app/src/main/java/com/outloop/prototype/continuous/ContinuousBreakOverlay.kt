package com.outloop.prototype.continuous

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Lightweight, native Android [WindowManager] attention-check overlay displayed
 * directly over Instagram when the configured Continuous Reels Break interval is reached.
 *
 * ARCHITECTURAL SAFETY & DESIGN:
 * 1. Uses [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY], native to AccessibilityService.
 * 2. Presents a simple, non-coercive attention check ("Still want to keep scrolling?") with two actions:
 *    - "Take a break": Dismisses the prompt smoothly without forced countdowns or redirects.
 *    - "Continue watching": Dismisses the prompt and allows the user to continue scrolling.
 * 3. Guaranteed single-prompt lifecycle per continuous session (requires fresh session before re-prompting).
 * 4. Zero impact on daily allowance accounting, extra time, or clock integrity.
 * 5. Fail-open try/catch protection on all window manager operations.
 */
class ContinuousBreakOverlay(
    private val service: AccessibilityService,
    private val playbackController: com.outloop.prototype.playback.ReelsPlaybackController = com.outloop.prototype.playback.ReelsPlaybackController(service)
) {

    companion object {
        private const val TAG = "OutLoopBreakDebug"

        // OutLoop Design System Color Tokens
        private const val COLOR_CANVAS_OVERLAY = 0xD9111315.toInt() // 85% opaque dark canvas
        private const val COLOR_CARD_BACKGROUND = 0xFF1C1E22.toInt()
        private const val COLOR_CARD_BORDER = 0xFF2C2E33.toInt()
        private const val COLOR_ACCENT_LIME = 0xFFB9F45A.toInt()
        private const val COLOR_ON_ACCENT_LIME = 0xFF1A2600.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFF0F1ED.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFF9DA19A.toInt()
        private const val COLOR_TEXT_MUTED = 0xFF737A65.toInt()
        private const val COLOR_BADGE_CONTAINER = 0x26B9F45A.toInt() // 15% Lime
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayRootView: FrameLayout? = null
    private var isAttached: Boolean = false
    var lastError: String? = null
        private set

    val isShowing: Boolean
        get() = isAttached

    /**
     * Shows the lightweight "Still want to keep scrolling?" attention check prompt over Instagram.
     */
    fun showPrompt(intervalMinutes: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showPrompt(intervalMinutes) }
            return
        }

        Log.d(TAG, "showPrompt() invoked for ${intervalMinutes}m interval (isAttached=$isAttached)")
        if (isAttached) {
            Log.d(TAG, "showPrompt() ignored: overlay is already attached.")
            return
        }
        if (windowManager == null) {
            lastError = "WindowManager service is null"
            Log.e(TAG, "showPrompt() failed: $lastError")
            ContinuousBreakManager.getInstance(service).updateOverlayStatus("ERROR: WindowManager null")
            return
        }

        try {
            val root = FrameLayout(service).apply {
                setBackgroundColor(COLOR_CANVAS_OVERLAY)
                val density = service.resources.displayMetrics.density
                val pad = (24 * density).toInt()
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
            }

            val card = createPromptCard(intervalMinutes)
            val cardParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            root.addView(card, cardParams)

            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.CENTER
            }

            windowManager.addView(root, params)
            overlayRootView = root
            isAttached = true
            lastError = null
            playbackController.suppressPlayback(
                rootNode = null,
                isVerifiedTargetPackage = true,
                isReelsContext = true,
                cause = com.outloop.prototype.playback.PlaybackRestrictionCause.CONTINUOUS_BREAK
            )
            Log.d(TAG, "Continuous break overlay attached to WindowManager successfully for ${intervalMinutes}m threshold.")
            ContinuousBreakManager.getInstance(service).updateOverlayStatus("ATTACHED")
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "WindowManager.addView failed: $lastError", e)
            ContinuousBreakManager.getInstance(service).updateOverlayStatus("ERROR: $lastError")
            isAttached = false
            overlayRootView = null
        }
    }

    /**
     * Dismisses the overlay immediately.
     */
    fun dismiss(reason: String = "Dismiss requested") {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss(reason) }
            return
        }

        if (!isAttached || overlayRootView == null || windowManager == null) {
            isAttached = false
            overlayRootView = null
            return
        }

        try {
            windowManager.removeView(overlayRootView)
            Log.d(TAG, "Continuous break overlay dismissed. Reason: $reason")
            ContinuousBreakManager.getInstance(service).updateOverlayStatus("DISMISSED ($reason)")
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "Exception dismissing continuous break overlay: $lastError", e)
            ContinuousBreakManager.getInstance(service).updateOverlayStatus("ERROR_DISMISS: $lastError")
        } finally {
            playbackController.releaseSuppression(com.outloop.prototype.playback.PlaybackRestrictionCause.CONTINUOUS_BREAK)
            isAttached = false
            overlayRootView = null
        }
    }

    enum class MindRefreshActivityType {
        PATTERN_RECALL,
        VISUAL_RESET,
        TINY_BRAIN_CHALLENGE
    }

    var activityTypeOverrideForTesting: MindRefreshActivityType? = null
    private var nextActivityType: MindRefreshActivityType = MindRefreshActivityType.PATTERN_RECALL

    private fun createPromptCard(intervalMinutes: Int): View {
        val density = service.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val cardDrawable = GradientDrawable().apply {
            setColor(COLOR_CARD_BACKGROUND)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), COLOR_CARD_BORDER)
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable
            setPadding(dp(22), dp(24), dp(22), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Header Row (Brand & Badge)
        val headerRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 1f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        val brand = TextView(service).apply {
            text = "OutLoop"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badge = TextView(service).apply {
            text = "ATTENTION CHECK"
            setTextColor(COLOR_ACCENT_LIME)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(COLOR_BADGE_CONTAINER)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), COLOR_ACCENT_LIME)
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        headerRow.addView(brand)
        headerRow.addView(badge)
        card.addView(headerRow)

        // Headline
        val title = TextView(service).apply {
            text = "Still want to keep scrolling?"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        card.addView(title)

        // Body explanation
        val body = TextView(service).apply {
            text = "You've been watching Reels continuously for $intervalMinutes minutes. Take a mindful pause to check in with your attention."
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dp(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }
        card.addView(body)

        // Primary Action: "Take a break" -> Launches Mind Refresh activity directly in overlay
        val breakBtn = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(COLOR_ACCENT_LIME)
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                showMindRefreshActivity()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }

            val btnText = TextView(service).apply {
                text = "Take a break"
                setTextColor(COLOR_ON_ACCENT_LIME)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(btnText)
        }
        card.addView(breakBtn)

        // Secondary Action: "Continue watching" -> Dismisses immediately & resets baseline
        val continueBtn = TextView(service).apply {
            text = "Continue watching"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismiss("User chose to continue watching")
                ContinuousBreakManager.getInstance(service).onContinueWatching()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(continueBtn)

        return card
    }

    /**
     * Replaces the prompt card with a Mind Refresh activity directly in the current overlay window.
     */
    fun showMindRefreshActivity() {
        val root = overlayRootView
        if (root == null || !isAttached) {
            ContinuousBreakManager.getInstance(service).onMindRefreshCompleted()
            return
        }

        root.removeAllViews()

        val selectedType = activityTypeOverrideForTesting ?: nextActivityType
        nextActivityType = when (selectedType) {
            MindRefreshActivityType.PATTERN_RECALL -> MindRefreshActivityType.VISUAL_RESET
            MindRefreshActivityType.VISUAL_RESET -> MindRefreshActivityType.TINY_BRAIN_CHALLENGE
            MindRefreshActivityType.TINY_BRAIN_CHALLENGE -> MindRefreshActivityType.PATTERN_RECALL
        }

        val activityCard = when (selectedType) {
            MindRefreshActivityType.PATTERN_RECALL -> createPatternRecallCard()
            MindRefreshActivityType.VISUAL_RESET -> createVisualResetCard()
            MindRefreshActivityType.TINY_BRAIN_CHALLENGE -> createTinyBrainChallengeCard()
        }

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        root.addView(activityCard, cardParams)
        Log.d(TAG, "Transitioned continuous break overlay to Mind Refresh: $selectedType")
    }

    /**
     * Mind Refresh Activity 0: Pattern Recall Interactive Mini-Game.
     * Features a 3-round progressive sequence memorization and recall challenge.
     * Accessible, one-handed touch targets (>=48dp), immediate feedback, and complete local execution.
     */
    fun createPatternRecallCard(): View {
        val density = service.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val cardDrawable = GradientDrawable().apply {
            setColor(COLOR_CARD_BACKGROUND)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), COLOR_CARD_BORDER)
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable
            setPadding(dp(22), dp(24), dp(22), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        data class ShapeSymbol(
            val id: Int,
            val icon: String,
            val name: String,
            val color: Int,
            val textColor: Int
        )

        val symbolPalette = listOf(
            ShapeSymbol(0, "★", "Star", COLOR_ACCENT_LIME, COLOR_ON_ACCENT_LIME),
            ShapeSymbol(1, "◆", "Diamond", 0xFF4ECDC4.toInt(), 0xFF00302E.toInt()),
            ShapeSymbol(2, "●", "Sun", 0xFFFFD166.toInt(), 0xFF332300.toInt()),
            ShapeSymbol(3, "▲", "Moon", 0xFFA09BE7.toInt(), 0xFF201B40.toInt())
        )

        // Rounds: Sequence lengths 3 -> 4 -> 5
        val roundSequences = listOf(
            listOf(0, 2, 1),             // Round 1: 3 steps
            listOf(3, 0, 2, 3),          // Round 2: 4 steps
            listOf(1, 2, 0, 3, 0)        // Round 3: 5 steps
        )

        var currentRoundIndex = 0
        val userInputs = mutableListOf<Int>()

        // Header Row
        val headerRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 1f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        val brand = TextView(service).apply {
            text = "OutLoop"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val roundBadge = TextView(service).apply {
            text = "ROUND 1 OF 3"
            setTextColor(COLOR_ACCENT_LIME)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(COLOR_BADGE_CONTAINER)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), COLOR_ACCENT_LIME)
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        headerRow.addView(brand)
        headerRow.addView(roundBadge)
        card.addView(headerRow)

        // Title and Subtitle
        val titleView = TextView(service).apply {
            text = "Pattern Recall"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        card.addView(titleView)

        val subtitleView = TextView(service).apply {
            text = "Memorize the sequence below:"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        }
        card.addView(subtitleView)

        // Dynamic interactive area
        val dynamicArea = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        card.addView(dynamicArea)

        // Bottom skip action (always accessible)
        val skipButton = TextView(service).apply {
            text = "Skip activity"
            setTextColor(COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismiss("Pattern recall skipped")
                ContinuousBreakManager.getInstance(service).onMindRefreshSkipped()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(skipButton)

        fun createChip(symbol: ShapeSymbol, isSlot: Boolean = false, isPlaceholder: Boolean = false): TextView {
            return TextView(service).apply {
                text = if (isPlaceholder) "?" else symbol.icon
                setTextColor(if (isPlaceholder) COLOR_TEXT_MUTED else symbol.textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (isPlaceholder) 0xFF25282F.toInt() else symbol.color)
                    cornerRadius = dp(12).toFloat()
                    if (isPlaceholder) {
                        setStroke(dp(1), COLOR_CARD_BORDER)
                    }
                }
                val sizeDp = if (isSlot) 44 else 48
                layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            }
        }

        lateinit var renderMemorizeState: () -> Unit
        lateinit var renderInputState: () -> Unit
        lateinit var renderFeedbackState: (Boolean) -> Unit
        lateinit var renderSuccessState: () -> Unit

        renderMemorizeState = {
            dynamicArea.removeAllViews()
            userInputs.clear()
            roundBadge.text = "ROUND ${currentRoundIndex + 1} OF 3"
            subtitleView.text = "Memorize this ${roundSequences[currentRoundIndex].size}-symbol pattern:"

            val sequenceRow = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(20) }
            }

            roundSequences[currentRoundIndex].forEach { symbolId ->
                sequenceRow.addView(createChip(symbolPalette[symbolId], isSlot = false))
            }
            dynamicArea.addView(sequenceRow)

            val readyBtn = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(COLOR_ACCENT_LIME)
                    cornerRadius = dp(14).toFloat()
                }
                setPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    renderInputState()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }

                val btnText = TextView(service).apply {
                    text = "I'm ready to recall"
                    setTextColor(COLOR_ON_ACCENT_LIME)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                addView(btnText)
            }
            dynamicArea.addView(readyBtn)
        }

        renderInputState = {
            dynamicArea.removeAllViews()
            val target = roundSequences[currentRoundIndex]
            subtitleView.text = "Tap the symbols in order (${userInputs.size}/${target.size}):"

            val slotsRow = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(18) }
            }

            for (i in target.indices) {
                if (i < userInputs.size) {
                    val enteredSymbol = symbolPalette[userInputs[i]]
                    slotsRow.addView(createChip(enteredSymbol, isSlot = true))
                } else {
                    slotsRow.addView(createChip(symbolPalette[0], isSlot = true, isPlaceholder = true))
                }
            }
            dynamicArea.addView(slotsRow)

            val keypad = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }

            val row1 = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            val row2 = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            fun createInputButton(symbol: ShapeSymbol): View {
                return LinearLayout(service).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(0xFF25282F.toInt())
                        cornerRadius = dp(14).toFloat()
                        setStroke(dp(1), COLOR_CARD_BORDER)
                    }
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    }

                    val iconView = TextView(service).apply {
                        text = symbol.icon
                        setTextColor(symbol.color)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                        setPadding(0, 0, dp(8), 0)
                    }
                    val labelView = TextView(service).apply {
                        text = symbol.name
                        setTextColor(COLOR_TEXT_PRIMARY)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    addView(iconView)
                    addView(labelView)

                    setOnClickListener {
                        userInputs.add(symbol.id)
                        if (userInputs.size == target.size) {
                            val isMatch = (userInputs == target)
                            renderFeedbackState(isMatch)
                        } else {
                            renderInputState()
                        }
                    }
                }
            }

            row1.addView(createInputButton(symbolPalette[0]))
            row1.addView(createInputButton(symbolPalette[1]))
            row2.addView(createInputButton(symbolPalette[2]))
            row2.addView(createInputButton(symbolPalette[3]))

            keypad.addView(row1)
            keypad.addView(row2)
            dynamicArea.addView(keypad)
        }

        renderFeedbackState = { isCorrect ->
            dynamicArea.removeAllViews()
            if (isCorrect) {
                roundBadge.text = "CORRECT"
                subtitleView.text = "Excellent recall! Round ${currentRoundIndex + 1} cleared."

                val nextBtn = LinearLayout(service).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(COLOR_ACCENT_LIME)
                        cornerRadius = dp(14).toFloat()
                    }
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (currentRoundIndex + 1 < roundSequences.size) {
                            currentRoundIndex++
                            renderMemorizeState()
                        } else {
                            renderSuccessState()
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(10)
                        bottomMargin = dp(8)
                    }

                    val btnText = TextView(service).apply {
                        text = if (currentRoundIndex + 1 < roundSequences.size) {
                            "Next Round (${currentRoundIndex + 2}/3)"
                        } else {
                            "See Results"
                        }
                        setTextColor(COLOR_ON_ACCENT_LIME)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    addView(btnText)
                }
                dynamicArea.addView(nextBtn)
            } else {
                roundBadge.text = "TRY AGAIN"
                subtitleView.text = "Pattern was slightly different. Let's look again!"

                val retryBtn = LinearLayout(service).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(0xFF25282F.toInt())
                        cornerRadius = dp(14).toFloat()
                        setStroke(dp(1), COLOR_ACCENT_LIME)
                    }
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        renderMemorizeState()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(10)
                        bottomMargin = dp(8)
                    }

                    val btnText = TextView(service).apply {
                        text = "Review Sequence"
                        setTextColor(COLOR_ACCENT_LIME)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    addView(btnText)
                }
                dynamicArea.addView(retryBtn)
            }
        }

        renderSuccessState = {
            dynamicArea.removeAllViews()
            roundBadge.text = "COMPLETE"
            titleView.text = "Mind Refreshed!"
            subtitleView.text = "You cleared all 3 pattern rounds. Your focus is reset and ready."

            val doneBtn = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(COLOR_ACCENT_LIME)
                    cornerRadius = dp(14).toFloat()
                }
                setPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dismiss("Pattern recall completed")
                    ContinuousBreakManager.getInstance(service).onMindRefreshCompleted()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }

                val btnText = TextView(service).apply {
                    text = "Done"
                    setTextColor(COLOR_ON_ACCENT_LIME)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                addView(btnText)
            }
            dynamicArea.addView(doneBtn)

            val restartBtn = TextView(service).apply {
                text = "Play again"
                setTextColor(COLOR_TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentRoundIndex = 0
                    titleView.text = "Pattern Recall"
                    renderMemorizeState()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            dynamicArea.addView(restartBtn)
        }

        renderMemorizeState()
        return card
    }

    /**
     * Mind Refresh Activity 1: Visual Reset.
     * Prompts the user to look away from screen and spot 3 objects with a common color or 3 room details.
     */
    fun createVisualResetCard(): View {
        val density = service.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val cardDrawable = GradientDrawable().apply {
            setColor(COLOR_CARD_BACKGROUND)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), COLOR_CARD_BORDER)
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable
            setPadding(dp(22), dp(24), dp(22), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Header
        val headerRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 1f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        val brand = TextView(service).apply {
            text = "OutLoop"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badge = TextView(service).apply {
            text = "VISUAL RESET"
            setTextColor(COLOR_ACCENT_LIME)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(COLOR_BADGE_CONTAINER)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), COLOR_ACCENT_LIME)
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        headerRow.addView(brand)
        headerRow.addView(badge)
        card.addView(headerRow)

        // Title
        val title = TextView(service).apply {
            text = "Rest Your Eyes"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        card.addView(title)

        // Instructions
        val body = TextView(service).apply {
            text = "Look up and away from your screen.\n\nNotice three objects around you that share the same color, or spot three interesting details in your surroundings."
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(dp(3).toFloat(), 1.1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(24) }
        }
        card.addView(body)

        // Primary Action: "Done"
        val doneBtn = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(COLOR_ACCENT_LIME)
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismiss("Visual reset completed")
                ContinuousBreakManager.getInstance(service).onMindRefreshCompleted()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }

            val btnText = TextView(service).apply {
                text = "Done"
                setTextColor(COLOR_ON_ACCENT_LIME)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(btnText)
        }
        card.addView(doneBtn)

        // Secondary Action: "Skip"
        val skipBtn = TextView(service).apply {
            text = "Skip"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismiss("Visual reset skipped")
                ContinuousBreakManager.getInstance(service).onMindRefreshSkipped()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(skipBtn)

        return card
    }

    /**
     * Mind Refresh Activity 2: Tiny Brain Challenge.
     * Shows 3 simple symbols briefly, asks user which symbol was in the middle.
     */
    fun createTinyBrainChallengeCard(): View {
        val density = service.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val cardDrawable = GradientDrawable().apply {
            setColor(COLOR_CARD_BACKGROUND)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), COLOR_CARD_BORDER)
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable
            setPadding(dp(22), dp(24), dp(22), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Header
        val headerRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 1f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        val brand = TextView(service).apply {
            text = "OutLoop"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badge = TextView(service).apply {
            text = "TINY BRAIN CHALLENGE"
            setTextColor(COLOR_ACCENT_LIME)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(COLOR_BADGE_CONTAINER)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), COLOR_ACCENT_LIME)
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        headerRow.addView(brand)
        headerRow.addView(badge)
        card.addView(headerRow)

        // Title
        val title = TextView(service).apply {
            text = "Quick Memory Check"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        card.addView(title)

        // Instruction / Step description
        val instruction = TextView(service).apply {
            text = "Remember this sequence in order:"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        card.addView(instruction)

        // Symbols Row
        val symbolsRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

        val symbols = listOf("★", "●", "▲")
        val correctMiddle = "●"

        fun createSymbolView(sym: String): TextView {
            return TextView(service).apply {
                text = sym
                setTextColor(COLOR_ACCENT_LIME)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(0xFF25282F.toInt())
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), COLOR_CARD_BORDER)
                }
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                    setMargins(dp(8), 0, dp(8), 0)
                }
            }
        }

        symbols.forEach { sym ->
            symbolsRow.addView(createSymbolView(sym))
        }
        card.addView(symbolsRow)

        // Feedback / Result TextView (hidden initially)
        val feedbackView = TextView(service).apply {
            visibility = View.GONE
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        card.addView(feedbackView)

        // Options Row (for Step 2, hidden initially)
        val optionsRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        card.addView(optionsRow)

        // Primary Action Button (I'm ready -> Done)
        val primaryBtn = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(COLOR_ACCENT_LIME)
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        val primaryBtnText = TextView(service).apply {
            text = "I'm ready"
            setTextColor(COLOR_ON_ACCENT_LIME)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        primaryBtn.addView(primaryBtnText)
        card.addView(primaryBtn)

        // Secondary Action: Skip
        val skipBtn = TextView(service).apply {
            text = "Skip"
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismiss("Tiny brain challenge skipped")
                ContinuousBreakManager.getInstance(service).onMindRefreshSkipped()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(skipBtn)

        var hasAnswered = false

        // Setup Step 2 options
        symbols.forEach { optionSym ->
            val optView = TextView(service).apply {
                text = optionSym
                setTextColor(COLOR_TEXT_PRIMARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(0xFF25282F.toInt())
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), COLOR_CARD_BORDER)
                }
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                    setMargins(dp(8), 0, dp(8), 0)
                }
                setOnClickListener {
                    if (hasAnswered) return@setOnClickListener
                    hasAnswered = true
                    val isCorrect = optionSym == correctMiddle
                    if (isCorrect) {
                        feedbackView.text = "Spot on! Great focus."
                        feedbackView.setTextColor(COLOR_ACCENT_LIME)
                    } else {
                        feedbackView.text = "Nice try! The circle (●) was in the middle."
                        feedbackView.setTextColor(COLOR_TEXT_SECONDARY)
                    }
                    feedbackView.visibility = View.VISIBLE
                    primaryBtnText.text = "Done"
                    primaryBtn.setOnClickListener {
                        dismiss("Tiny brain challenge completed")
                        ContinuousBreakManager.getInstance(service).onMindRefreshCompleted()
                    }
                }
            }
            optionsRow.addView(optView)
        }

        primaryBtn.setOnClickListener {
            // Transition to Step 2
            symbolsRow.visibility = View.GONE
            instruction.text = "Which symbol was in the middle?"
            optionsRow.visibility = View.VISIBLE
            primaryBtnText.text = "Done"
            primaryBtn.setOnClickListener {
                dismiss("Tiny brain challenge completed")
                ContinuousBreakManager.getInstance(service).onMindRefreshCompleted()
            }
        }

        return card
    }
}
