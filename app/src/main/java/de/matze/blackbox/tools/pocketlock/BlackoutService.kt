package de.matze.blackbox.tools.pocketlock

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import de.matze.blackbox.MainActivity
import de.matze.blackbox.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlackoutService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var hintTextView: TextView? = null
    private var clockTextView: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hideHintRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBlackout()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        showOverlay()

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val stopIntent = Intent(this, BlackoutService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            102,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OLED Blackout aktiv")
            .setContentText("Display stromlos (0W) · Doppeltippen zum Entsperren")
            .setSmallIcon(R.drawable.ic_blackout_tile)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_blackout_tile, "Beenden", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (overlayView != null) return

        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val config = BlackoutConfig(this)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        }

        // Optional Clock in center
        if (config.showSubtleClock) {
            val clock = TextView(this).apply {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                text = timeFormat.format(Date())
                setTextColor(Color.parseColor("#44FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
                gravity = Gravity.CENTER
            }
            val clockParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            frame.addView(clock, clockParams)
            clockTextView = clock
        }

        // Hint Text at bottom
        val hint = TextView(this).apply {
            text = "Doppeltippen zum Entsperren"
            setTextColor(Color.parseColor("#88FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            alpha = 0f
        }
        val hintParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = (60 * resources.displayMetrics.density).toInt()
        }
        frame.addView(hint, hintParams)
        hintTextView = hint

        // Gesture detector for Double-Tap
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                stopBlackout()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (config.showHintOnTap) {
                    showHintBriefly()
                }
                return true
            }
        })

        frame.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true // Absorb all touches to prevent accidental interaction in pocket
        }

        try {
            wm.addView(frame, params)
            overlayView = frame
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun showHintBriefly() {
        val hint = hintTextView ?: return
        hideHintRunnable?.let { mainHandler.removeCallbacks(it) }

        hint.animate().alpha(1f).setDuration(150).start()

        val runnable = Runnable {
            hint.animate().alpha(0f).setDuration(400).start()
        }
        hideHintRunnable = runnable
        mainHandler.postDelayed(runnable, 1500)
    }

    private fun removeOverlay() {
        hideHintRunnable?.let { mainHandler.removeCallbacks(it) }
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {}
        overlayView = null
        hintTextView = null
        clockTextView = null
    }

    private fun stopBlackout() {
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Blackout Dienst"
            val descriptionText = "Benachrichtigung während aktivem OLED Blackout"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "blackbox_blackout_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "de.matze.blackbox.ACTION_STOP_BLACKOUT"

        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BlackoutService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BlackoutService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
