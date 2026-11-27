/*
 * Copyright (C) 2026 GameSpace BurnIn Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.burnin

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import io.chaldeaprjkt.gamespace.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Center of the OLED burn-in guard. Lives in the singleton graph for the whole
 * game session (owned by SessionService, reached from the game bar through the
 * ServiceViewEntryPoint) so no extra foreground service is needed.
 *
 * Mode 1 (Galaxy style): a full-screen black TYPE_APPLICATION_OVERLAY scrim
 * with a dim clock/battery/lock ring at the center, the screen kept awake, and
 * the content position jittering every minute to spread pixel wear.
 * Mode 2 (Xiaomi style): the same touch-swallowing window but transparent,
 * while the panel backlight is driven to 0 through root; when the node cannot
 * be written it transparently degrades into mode 1.
 *
 * Release paths: lock-handle drag-out, volume double-press / long press, five
 * quick taps, the game bar tile toggle, session end, or the watchdog timeout.
 */
@Singleton
class BurnInGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: BurnInSettings,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val backlight = BacklightController()
    private val clocks = ClockLimiter()
    private val refresh = RefreshRateLimiter()

    @Volatile
    private var guarded = false

    /** True while either mode is holding the panel. */
    val isActive: Boolean get() = guarded

    private var window: LockWindow? = null
    private var sessionActive = false
    private var idleJob: Job? = null

    @Volatile
    private var lastTouch = SystemClock.elapsedRealtime()

    private val watchdog = Runnable {
        Log.i(TAG, "watchdog: releasing the guard after max lock time")
        deactivate()
    }

    // ---------------------------------------------------------------- session

    /** Called by SessionService once the game bar is up: arms the idle timer. */
    fun onSessionStarted() {
        sessionActive = true
        lastTouch = SystemClock.elapsedRealtime()
        val watcherLive = watcher?.start() == true
        if (!watcherLive) {
            Log.i(TAG, "input monitor unavailable; idle clock falls back to dumpsys power")
        }
        idleJob?.cancel()
        idleJob = ioScope.launch {
            // Without the input monitor the only touch evidence left is the
            // power service clock; if even that dump is unreadable we have no
            // signal and must not auto-activate while someone is playing.
            var clockMisses = 0
            while (isActive && clockMisses < 3) {
                delay(IDLE_POLL_MS)
                val minutes = settings.idleMinutes
                if (minutes > 0 && settings.mode > 0 && !guarded) {
                    if (!watcherLive) {
                        val act = PowerActivityClock.lastUserActivityElapsed()
                        if (act == null) {
                            clockMisses++
                            continue
                        }
                        if (act > lastTouch) lastTouch = act
                    }
                    if (SystemClock.elapsedRealtime() - lastTouch >= minutes * 60_000L) {
                        activate(settings.mode)
                    }
                }
            }
        }
    }

    /** Called by SessionService on teardown: releases everything. */
    fun onSessionEnded() {
        sessionActive = false
        idleJob?.cancel()
        idleJob = null
        watcher?.stop()
        watcher = null
        deactivate()
    }

    // ------------------------------------------------------------- activation

    /** Tile entry point: on -> off, off -> configured mode (overlay by default). */
    fun toggle() {
        if (guarded) deactivate() else activate(settings.mode.takeIf { it > 0 } ?: MODE_OVERLAY)
    }

    fun activate(preferredMode: Int) {
        handler.post { activateOnMain(preferredMode) }
    }

    fun deactivate() {
        handler.post { deactivateOnMain() }
    }

    private fun activateOnMain(preferredMode: Int) {
        if (guarded || !sessionActive) return
        guarded = true
        val mode = preferredMode.coerceIn(MODE_OVERLAY, MODE_BACKLIGHT)

        val lockWindow = LockWindow(mode) { deactivate() }
        lockWindow.show()
        window = lockWindow

        if (mode == MODE_BACKLIGHT) {
            ioScope.launch {
                if (!backlight.turnOff()) {
                    Log.w(TAG, "backlight node unusable, falling back to overlay mode")
                    handler.post { window?.switchToOverlayLook() }
                }
            }
        }
        ioScope.launch {
            clocks.limit(settings.cpuLimitPercent)
            if (settings.lockRefreshRate) {
                refresh.lock()
            }
        }
        handler.postDelayed(watchdog, settings.maxLockMinutes.coerceAtLeast(1) * 60_000L)
        Log.i(TAG, "burn-in guard active (mode=$mode)")
    }

    private fun deactivateOnMain() {
        handler.removeCallbacks(watchdog)
        if (!guarded) return
        guarded = false
        window?.dismiss()
        window = null
        lastTouch = SystemClock.elapsedRealtime()
        ioScope.launch {
            backlight.restore()
            clocks.restore()
            refresh.restore()
        }
        Log.i(TAG, "burn-in guard released")
    }

    // ------------------------------------------------------------ lock window

    /**
     * Full-screen focusable overlay: swallows every touch, keeps the screen on,
     * and owns the volume keys so the release gestures cannot leak into the
     * game. Built programmatically so the feature adds no layout resources.
     */
    private inner class LockWindow(
        private val mode: Int,
        private val onUnlock: () -> Unit,
    ) {
        private val density = context.resources.displayMetrics.density
        private fun dp(v: Int) = (v * density).toInt()

        private val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        private var scrim: View? = null
        private var batteryReceiver: BroadcastReceiver? = null
        private var added = false

        private val jitter = object : Runnable {
            private val random = Random()
            override fun run() {
                val spread = dp(48)
                content.translationX = (random.nextInt(2 * spread + 1) - spread).toFloat()
                content.translationY = (random.nextInt(2 * spread + 1) - spread).toFloat()
                handler.postDelayed(this, JITTER_MS)
            }
        }

        private var lastVolumePressAt = 0L
        private var taps = 0
        private var firstTapAt = 0L

        @SuppressLint("ClickableViewAccessibility")
        fun show() {
            val root = object : FrameLayout(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                        event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                    ) {
                        handleVolumeKey(event)
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }

                override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                    if (ev.action == MotionEvent.ACTION_UP) {
                        handleTap()
                    }
                    return super.dispatchTouchEvent(ev)
                }
            }
            scrim = root
            root.isClickable = true
            root.setBackgroundColor(
                if (mode == MODE_BACKLIGHT) Color.TRANSPARENT else overlayColor()
            )

            val clock = TextClock(context).apply {
                setFormat24Hour("kk:mm")
                setFormat12Hour("kk:mm")
                setTextColor(Color.rgb(170, 170, 170))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            }
            content.addView(clock, matchWrap())

            val battery = TextView(context).apply {
                setTextColor(Color.rgb(150, 150, 150))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, dp(10))
            }
            content.addView(battery, matchWrap())
            content.addView(createRing(), matchWrap())

            root.addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
            )

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
            try {
                windowManager.addView(root, params)
                added = true
                root.isFocusableInTouchMode = true
                root.requestFocus()
            } catch (e: Exception) {
                Log.e(TAG, "cannot add the overlay window", e)
                added = false
            }

            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    if (level >= 0) battery.text = "${level * 100 / scale}%"
                }
            }.also {
                context.registerReceiver(it, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
            handler.postDelayed(jitter, JITTER_MS)
        }

        fun dismiss() {
            handler.removeCallbacks(jitter)
            batteryReceiver?.let { runCatching { context.unregisterReceiver(it) } }
            batteryReceiver = null
            if (added) runCatching { windowManager.removeView(scrim) }
            added = false
        }

        /** Mode 2 -> mode 1 fallback when the backlight write failed. */
        fun switchToOverlayLook() {
            scrim?.setBackgroundColor(overlayColor())
        }

        private fun overlayColor(): Int =
            Color.argb((settings.overlayOpacity.coerceIn(80, 100) * 255 / 100), 0, 0, 0)

        private fun matchWrap() = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }

        /** Ring with a draggable lock handle: drag it out to unlock. */
        @SuppressLint("ClickableViewAccessibility")
        private fun createRing(): View {
            val ringSize = dp(80)
            val radius = dp(32).toFloat()
            val ring = FrameLayout(context)
            ring.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.rgb(90, 90, 90))
            }
            val handle = ImageView(context).apply {
                setImageResource(R.drawable.ic_burnin)
                setColorFilter(Color.rgb(140, 140, 140))
            }
            val handleSize = dp(44)
            ring.addView(
                handle,
                FrameLayout.LayoutParams(handleSize, handleSize, Gravity.CENTER)
            )
            var dragging = false
            var baseX = 0f
            var baseY = 0f
            handle.setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragging = true
                        baseX = v.translationX
                        baseY = v.translationY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> if (dragging) {
                        v.translationX =
                            (baseX + ev.x - ring.width / 2f).coerceIn(-radius, radius)
                        v.translationY =
                            (baseY + ev.y - ring.height / 2f).coerceIn(-radius, radius)
                        true
                    } else false
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        dragging = false
                        val distance = Math.hypot(v.translationX.toDouble(), v.translationY.toDouble())
                        v.animate().translationX(0f).translationY(0f).setDuration(120).start()
                        if (distance > radius) onUnlock()
                        true
                    }
                    else -> false
                }
            }
            ring.layoutParams = LinearLayout.LayoutParams(ringSize, ringSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            }
            return ring
        }

        private fun handleVolumeKey(event: KeyEvent) {
            val now = SystemClock.elapsedRealtime()
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                    if (now - lastVolumePressAt <= DOUBLE_PRESS_MS) {
                        onUnlock()
                    } else {
                        lastVolumePressAt = now
                    }
                }
                KeyEvent.ACTION_UP -> if (event.eventTime - event.downTime >= LONG_PRESS_MS) {
                    onUnlock()
                }
            }
        }

        private fun handleTap() {
            val now = SystemClock.elapsedRealtime()
            if (now - firstTapAt > TAP_WINDOW_MS) {
                taps = 0
                firstTapAt = now
            }
            taps++
            if (taps >= TAPS_TO_UNLOCK) {
                taps = 0
                onUnlock()
            }
        }
    }

    private var watcher: IdleInputWatcher? = IdleInputWatcher(context) {
        lastTouch = SystemClock.elapsedRealtime()
    }

    companion object {
        private const val TAG = "GameSpaceBurnIn"
        const val MODE_OVERLAY = 1
        const val MODE_BACKLIGHT = 2
        private const val JITTER_MS = 60_000L
        private const val IDLE_POLL_MS = 5_000L
        private const val DOUBLE_PRESS_MS = 500L
        private const val LONG_PRESS_MS = 800L
        private const val TAP_WINDOW_MS = 2_000L
        private const val TAPS_TO_UNLOCK = 5
    }
}
