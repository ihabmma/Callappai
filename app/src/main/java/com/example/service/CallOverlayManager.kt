package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.domain.model.LiveCallState

/**
 * Optional floating call status & quick control pill shown over other apps ONLY during active calls
 * when the user explicitly enables floatingOverlayEnabled and grants SYSTEM_ALERT_WINDOW.
 */
class CallOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "CallOverlayManager"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager? by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    private var overlayView: View? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun showOrUpdate(
        state: LiveCallState,
        onHangupClick: () -> Unit
    ) {
        if (!canDrawOverlays()) return

        mainHandler.post {
            try {
                if (overlayView == null) {
                    createAndAttachOverlay(onHangupClick)
                }
                val callerTitle = state.callerName.ifBlank {
                    state.callerNumber.ifBlank { "مكالمة نشطة" }
                }
                val modeTag = if (state.isOfflineActive) "أوفلاين" else "أونلاين"
                titleView?.text = "المجيب الذكي • $callerTitle"
                subtitleView?.text = "${state.detectedCategory.arabicLabel} • $modeTag • ${state.statusNote}"
            } catch (e: Exception) {
                Log.w(TAG, "Unable to show/update floating overlay", e)
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            try {
                overlayView?.let { view ->
                    windowManager?.removeViewImmediate(view)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to dismiss floating overlay", e)
            } finally {
                overlayView = null
                titleView = null
                subtitleView = null
                layoutParams = null
            }
        }
    }

    private fun createAndAttachOverlay(onHangupClick: () -> Unit) {
        val wm = windowManager ?: return
        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0E1E28"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#38D399"))
            }
        }

        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(context).apply {
            setTextColor(Color.parseColor("#B7CCD2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(3), 0, dp(8))
        }

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val openBtn = Button(context).apply {
            text = "فتح التطبيق"
            setTextColor(Color.parseColor("#00241A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#38D399"))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(openIntent)
            }
        }

        val hangupBtn = Button(context).apply {
            text = "إنهاء"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D94A4A"))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                onHangupClick()
                dismiss()
            }
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        }

        actionsRow.addView(openBtn)
        actionsRow.addView(spacer)
        actionsRow.addView(hangupBtn)

        container.addView(title)
        container.addView(subtitle)
        container.addView(actionsRow)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }

        // Allow dragging the overlay pill smoothly
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    try {
                        wm.updateViewLayout(container, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(container, params)
        overlayView = container
        titleView = title
        subtitleView = subtitle
        layoutParams = params
    }
}
