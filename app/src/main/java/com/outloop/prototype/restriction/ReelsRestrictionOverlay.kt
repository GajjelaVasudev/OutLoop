package com.outloop.prototype.restriction

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
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
import com.outloop.prototype.ui.MainActivity

/**
 * Native Android [WindowManager] overlay displayed when the user's daily Reels allowance is exhausted.
 *
 * ARCHITECTURAL SAFETY & POLICY GUARANTEES:
 * 1. Trusted Window: Uses [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY], native to AccessibilityService.
 * 2. Fail-Open Architecture: If any layout error, classification ambiguity, or window exception occurs,
 *    the overlay fails open (remains removed) to prevent accidental lockouts.
 * 3. Instant Teardown: Dismissed immediately upon detection of non-Reels screens (DMs, Stories, Profiles, Feed),
 *    package transitions, service disconnection, or service unbinding.
 * 4. Transparent Disclosure: Unambiguously branded as OutLoop; informs the user why the restriction appeared
 *    and clearly reminds them that DMs, Stories, and Feeds remain accessible.
 * 5. Zero Obstruction: Does not prevent users from disabling or revoking accessibility in system settings.
 */
class ReelsRestrictionOverlay(
    private val service: AccessibilityService
) {

    companion object {
        private const val TAG = "OutLoopRestriction"

        // OutLoop Design System Color Tokens
        private const val COLOR_CANVAS_OVERLAY = 0xE6111315.toInt() // 90% opaque dark canvas
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

    private var overlayRootView: View? = null
    private var isAttached: Boolean = false

    val isShowing: Boolean
        get() = isAttached

    /**
     * Shows the restriction overlay with the specified allowance and usage numbers.
     * Must be called on the main looper thread.
     */
    fun show(allowanceMinutes: Long, accumulatedMs: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(allowanceMinutes, accumulatedMs) }
            return
        }

        if (isAttached || windowManager == null) return

        try {
            val view = createOverlayView(allowanceMinutes, accumulatedMs)
            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.CENTER
            }

            windowManager.addView(view, params)
            overlayRootView = view
            isAttached = true
            Log.d(TAG, "Restriction overlay attached successfully for exhausted allowance ($allowanceMinutes mins).")
        } catch (e: Exception) {
            // Fail-open: catch any window manager exceptions and ensure no stale state
            Log.e(TAG, "Fail-open safety: exception attaching restriction overlay: ${e.message}", e)
            isAttached = false
            overlayRootView = null
        }
    }

    /**
     * Removes the restriction overlay immediately.
     */
    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }

        if (!isAttached || overlayRootView == null || windowManager == null) {
            isAttached = false
            overlayRootView = null
            return
        }

        try {
            windowManager.removeView(overlayRootView)
            Log.d(TAG, "Restriction overlay dismissed.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception dismissing restriction overlay: ${e.message}", e)
        } finally {
            isAttached = false
            overlayRootView = null
        }
    }

    private fun createOverlayView(allowanceMinutes: Long, accumulatedMs: Long): View {
        val context = service
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        // 1. Root Container (Full Screen Scrim)
        val root = FrameLayout(context).apply {
            setBackgroundColor(COLOR_CANVAS_OVERLAY)
            setPadding(dp(24), dp(32), dp(24), dp(32))
            isClickable = true
            isFocusable = true
        }

        // 2. Card Container
        val cardDrawable = GradientDrawable().apply {
            setColor(COLOR_CARD_BACKGROUND)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), COLOR_CARD_BORDER)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable
            setPadding(dp(22), dp(24), dp(22), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // 3. Top Branding Row (OutLoop Logo + Badge)
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 1f
        }
        val headerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(16)
        }

        val brandTitle = TextView(context).apply {
            text = "OutLoop"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badgeDrawable = GradientDrawable().apply {
            setColor(COLOR_BADGE_CONTAINER)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), COLOR_ACCENT_LIME)
        }
        val badge = TextView(context).apply {
            text = "ALLOWANCE USED"
            setTextColor(COLOR_ACCENT_LIME)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = badgeDrawable
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        headerRow.addView(brandTitle)
        headerRow.addView(badge)
        card.addView(headerRow, headerParams)

        // 4. Headline
        val titleView = TextView(context).apply {
            text = "Daily Reels Allowance Reached"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.START
        }
        val titleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        }
        card.addView(titleView, titleParams)

        // 5. Body explanation
        val usedMinutes = maxOf(allowanceMinutes, accumulatedMs / 60000L)
        val bodyView = TextView(context).apply {
            text = "You've reached your daily limit of $usedMinutes minutes on Instagram Reels. Your allowance resets at midnight."
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        val bodyParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(16)
        }
        card.addView(bodyView, bodyParams)

        // 6. "What stays accessible" feature guarantees
        val featuresContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x14FFFFFF) // subtle container
            background = GradientDrawable().apply {
                setColor(0x0AFFFFFF)
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val featuresParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(20)
        }

        fun createFeatureLine(text: String): TextView {
            return TextView(context).apply {
                this.text = text
                setTextColor(COLOR_TEXT_PRIMARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(3), 0, dp(3))
            }
        }

        featuresContainer.addView(createFeatureLine("✓ Direct Messages are accessible"))
        featuresContainer.addView(createFeatureLine("✓ Stories are accessible"))
        featuresContainer.addView(createFeatureLine("✓ Profiles and Home Feed are accessible"))
        card.addView(featuresContainer, featuresParams)

        // 7. Action Button 1: Back to Feed / Previous Screen
        val btnBackDrawable = GradientDrawable().apply {
            setColor(COLOR_ACCENT_LIME)
            cornerRadius = dp(14).toFloat()
        }
        val btnBack = TextView(context).apply {
            text = "Back to Previous Screen"
            setTextColor(COLOR_ON_ACCENT_LIME)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = btnBackDrawable
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            setOnClickListener {
                dismiss()
                // Safely perform global back action so Instagram navigates away from Reels
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
        }
        val btnBackParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        }
        card.addView(btnBack, btnBackParams)

        // 8. Action Button 2: Open OutLoop Dashboard
        val btnDashboardDrawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), COLOR_CARD_BORDER)
        }
        val btnDashboard = TextView(context).apply {
            text = "Open OutLoop Dashboard"
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = btnDashboardDrawable
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            setOnClickListener {
                dismiss()
                val intent = Intent(service, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                service.startActivity(intent)
            }
        }
        val btnDashboardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        card.addView(btnDashboard, btnDashboardParams)

        // 9. Add card to root
        root.addView(card, cardParams)
        return root
    }
}
