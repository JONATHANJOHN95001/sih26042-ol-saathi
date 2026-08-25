package `in`.gov.tribalfln.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import `in`.gov.tribalfln.NipunEducatorDashboardActivity
import `in`.gov.tribalfln.util.TelemetryMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SihTelemetryOverlayService — Non-intrusive floating hardware HUD for SIH jury live proof.
 *
 * Visualizes:
 * 1. Current Heap RAM (e.g., "RAM: 142 MB / 180 MB") @ 500ms intervals
 * 2. Real-time AI Pipeline Latency (e.g., "VAD + Search + TTS: 2.1s")
 * 3. Dynamic Network Status (e.g., "Air-Gapped / Offline")
 */
class SihTelemetryOverlayService : Service() {

    companion object {
        const val ACTION_START = "in.gov.tribalfln.ACTION_START_TELEMETRY"
        const val ACTION_STOP = "in.gov.tribalfln.ACTION_STOP_TELEMETRY"
        const val ACTION_TOGGLE = "in.gov.tribalfln.ACTION_TOGGLE_TELEMETRY"

        private const val NOTIFICATION_CHANNEL_ID = "sih_telemetry_overlay_channel"
        private const val NOTIFICATION_ID = 2026

        fun start(context: Context) {
            val intent = Intent(context, SihTelemetryOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SihTelemetryOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private lateinit var tvRamMetric: TextView
    private lateinit var tvLatencyMetric: TextView
    private lateinit var tvNetworkMetric: TextView
    private lateinit var statusBadge: View

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        setupOverlayView()
        startMetricsCollector()
        TelemetryMonitor.setOverlayActive(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                if (overlayView?.visibility == View.VISIBLE) {
                    overlayView?.visibility = View.GONE
                    TelemetryMonitor.setOverlayActive(false)
                } else {
                    overlayView?.visibility = View.VISIBLE
                    TelemetryMonitor.setOverlayActive(true)
                }
            }
            else -> {
                overlayView?.visibility = View.VISIBLE
                TelemetryMonitor.setOverlayActive(true)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SIH Hardware Telemetry HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays live on-device RAM, AI latency, and air-gapped status."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, NipunEducatorDashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TribalFLN Hardware Telemetry")
            .setContentText("Monitoring live RAM, edge AI latency, and zero-network state")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupOverlayView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dpToPx(16)
            y = dpToPx(72)
        }

        // Programmatic sleek dark HUD card
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            background = createCardBackground()
            elevation = dpToPx(8).toFloat()
        }

        // Header Row (Title + Close Button)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(6) }
        }

        // Live pulse indicator
        statusBadge = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)).apply {
                marginEnd = dpToPx(6)
            }
            background = createCircleDrawable(Color.parseColor("#10B981")) // Emerald Green
        }

        val tvTitle = TextView(this).apply {
            text = "SIH LIVE TELEMETRY"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#9CA3AF")) // Gray 400
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val btnClose = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#9CA3AF"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(18), dpToPx(18))
            setOnClickListener {
                stopSelf()
            }
        }

        headerRow.addView(statusBadge)
        headerRow.addView(tvTitle)
        headerRow.addView(btnClose)

        // 1. RAM Metric Row
        tvRamMetric = TextView(this).apply {
            text = "RAM: -- MB / 180 MB"
            textSize = 12.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#34D399")) // Emerald 400
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        // 2. AI Pipeline Latency Row
        tvLatencyMetric = TextView(this).apply {
            text = "VAD + Search + TTS: 2.1s"
            textSize = 12.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#38BDF8")) // Sky 400
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        // 3. Network Status Row
        tvNetworkMetric = TextView(this).apply {
            text = "Air-Gapped / Offline"
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#FBBF24")) // Amber 400
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        container.addView(headerRow)
        container.addView(tvRamMetric)
        container.addView(tvLatencyMetric)
        container.addView(tvNetworkMetric)

        // Enable touch-drag to reposition on screen
        attachDragListener(container)

        overlayView = container
        windowManager?.addView(overlayView, layoutParams)
    }

    private fun attachDragListener(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun startMetricsCollector() {
        pollingJob = serviceScope.launch {
            // Subscribe to TelemetryMonitor updates
            launch {
                TelemetryMonitor.telemetryState.collect { state ->
                    tvLatencyMetric.text = state.lastInferenceDescription
                    tvNetworkMetric.text = state.networkStatusLabel

                    if (state.isAirGapped) {
                        tvNetworkMetric.setTextColor(Color.parseColor("#34D399")) // Green if strictly air-gapped
                    } else {
                        tvNetworkMetric.setTextColor(Color.parseColor("#F87171")) // Red if connected
                    }
                }
            }

            // 500ms ticker for live JVM Heap memory & Network interface polling
            while (isActive) {
                val runtime = Runtime.getRuntime()
                val usedHeapBytes = runtime.totalMemory() - runtime.freeMemory()
                val usedHeapMb = usedHeapBytes / (1024 * 1024)
                val maxMb = TelemetryMonitor.HEAP_CEILING_MB

                TelemetryMonitor.updateHeapUsage(usedHeapMb, maxMb)
                TelemetryMonitor.checkAndUpdateNetworkStatus(applicationContext)

                tvRamMetric.text = "RAM: $usedHeapMb MB / $maxMb MB"

                // Color code RAM usage against 180MB ceiling
                when {
                    usedHeapMb >= 160 -> tvRamMetric.setTextColor(Color.parseColor("#EF4444")) // Red
                    usedHeapMb >= 135 -> tvRamMetric.setTextColor(Color.parseColor("#F59E0B")) // Amber
                    else -> tvRamMetric.setTextColor(Color.parseColor("#34D399")) // Emerald Green
                }

                delay(500)
            }
        }
    }

    private fun createCardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(Color.parseColor("#E60F172A")) // Slate 900 with 90% opacity
            setStroke(dpToPx(1), Color.parseColor("#334155")) // Slate 700 border
        }
    }

    private fun createCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
        TelemetryMonitor.setOverlayActive(false)
        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (_: Exception) {}
            overlayView = null
        }
    }
}
