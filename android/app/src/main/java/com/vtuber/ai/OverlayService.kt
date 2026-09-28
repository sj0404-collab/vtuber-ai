package com.vtuber.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.Gravity as WindowGravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service(), AvatarWebView.Listener, VoiceChatManager.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var avatar: AvatarWebView
    private lateinit var container: FrameLayout
    private lateinit var statusLabel: TextView
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var scaleDetector: ScaleGestureDetector? = null

    private var baseWidth = BASE_WIDTH
    private var baseHeight = BASE_HEIGHT
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var scaling = false
    private var holdFrom = 0L
    private var lastTapAt = 0L
    private var privacy = false

    private val hideStatus = Runnable { statusLabel.visibility = View.GONE }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        baseWidth = (BASE_WIDTH * prefs.avatarScale).roundToInt()
        baseHeight = (BASE_HEIGHT * prefs.avatarScale).roundToInt()
        createChannel()
        buildWindow()
        VoiceChatManager.listener = this
        VoiceChatManager.init(this)
        privacy = prefs.privacyMode
        if (privacy) {
            avatar.dim(0.75f)
            avatar.mood("sleep")
        }
        startForegroundNotification()
    }

    private fun buildWindow() {
        avatar = AvatarWebView(this).apply {
            listener = this@OverlayService
            setOnTouchListener(touchListener)
        }
        statusLabel = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(18, 8, 18, 8)
            visibility = View.GONE
        }
        container = FrameLayout(this).apply {
            addView(
                avatar,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                statusLabel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                )
            )
        }

        val metrics = resources.displayMetrics
        params = WindowManager.LayoutParams(
            baseWidth,
            baseHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = WindowGravity.TOP or WindowGravity.START
            if (prefs.overlayX != Int.MIN_VALUE) {
                x = prefs.overlayX
                y = prefs.overlayY
            } else {
                x = metrics.widthPixels - baseWidth - (16 * metrics.density).roundToInt()
                y = (metrics.heightPixels * 0.45f).roundToInt()
            }
        }

        scaleDetector = ScaleGestureDetector(this, scaleListener)
        avatar.load(prefs.avatarModel)
        runCatching { windowManager?.addView(container, params) }
            .onFailure { Log.e(TAG, "cannot add overlay window", it) }
    }

    private val touchListener = View.OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = params?.x ?: 0
                startY = params?.y ?: 0
                holdFrom = System.currentTimeMillis()
                dragging = false
                scaling = false
                true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                scaling = true
                true
            }

            MotionEvent.ACTION_MOVE -> {
                scaleDetector?.onTouchEvent(event)
                if (scaling) return@OnTouchListener true
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && abs(dx) + abs(dy) > touchSlop) {
                    dragging = true
                    avatar.tilt(dx.coerceIn(-140f, 140f) * 0.2f)
                }
                if (dragging) {
                    params?.x = startX + dx.roundToInt()
                    params?.y = startY + dy.roundToInt()
                    updateLayout()
                }
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scaleDetector?.onTouchEvent(event)
                when {
                    dragging -> {
                        avatar.tilt(0f)
                        snapToEdge()
                        savePosition()
                    }

                    scaling -> Unit

                    System.currentTimeMillis() - holdFrom > HOLD_MS -> togglePrivacy()

                    else -> onTap()
                }
                dragging = false
                scaling = false
                true
            }

            else -> false
        }
    }

    private val touchSlop: Int
        get() = ViewConfiguration.get(this).scaledTouchSlop

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val target = (baseWidth * detector.scaleFactor).roundToInt().coerceIn(MIN_SIZE, MAX_SIZE)
            val ratio = target.toFloat() / baseWidth
            baseHeight = (baseHeight * ratio).roundToInt().coerceIn(MIN_SIZE, MAX_SIZE)
            baseWidth = target
            params?.width = baseWidth
            params?.height = baseHeight
            updateLayout()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaling = false
            prefs.avatarScale = (baseWidth.toFloat() / BASE_WIDTH).coerceIn(0.5f, 2f)
        }
    }

    private fun onTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapAt < DOUBLE_TAP_MS) {
            lastTapAt = 0
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        lastTapAt = now
        avatar.pulse()
        if (VoiceChatManager.isListening() || VoiceChatManager.isSpeaking()) {
            VoiceChatManager.stop()
            return
        }
        if (!VoiceChatManager.hasPermission(this)) {
            showStatus("Нужен доступ к микрофону")
            return
        }
        VoiceChatManager.start(this)
    }

    private fun togglePrivacy() {
        applyPrivacy(!privacy)
    }

    private fun applyPrivacy(enabled: Boolean) {
        privacy = enabled
        prefs.privacyMode = enabled
        if (enabled) {
            avatar.dim(0.75f)
            avatar.mood("sleep")
            showStatus("Приватный режим")
        } else {
            avatar.dim(0f)
            avatar.mood("idle")
            showStatus("Приватный режим выключен")
        }
    }

    private fun snapToEdge() {
        val metrics = resources.displayMetrics
        val current = params ?: return
        val margin = (12 * metrics.density).roundToInt()
        val maxX = metrics.widthPixels - current.width - margin
        val maxY = metrics.heightPixels - current.height - margin
        current.x = current.x.coerceIn(margin, maxOf(margin, maxX))
        current.y = current.y.coerceIn(margin, maxOf(margin, maxY))
        val nearLeft = abs(current.x - margin) < metrics.widthPixels * 0.12f
        val nearRight = abs(current.x - maxX) < metrics.widthPixels * 0.12f
        if (nearLeft) current.x = margin
        if (nearRight) current.x = maxX
        updateLayout()
    }

    private fun savePosition() {
        params?.let {
            prefs.overlayX = it.x
            prefs.overlayY = it.y
        }
    }

    private fun updateLayout() {
        val layout = params ?: return
        runCatching { windowManager?.updateViewLayout(container, layout) }
    }

    private fun showStatus(text: String) {
        handler.removeCallbacks(hideStatus)
        statusLabel.text = text
        statusLabel.visibility = View.VISIBLE
        handler.postDelayed(hideStatus, STATUS_MS)
    }

    override fun onModelReady(model: String) {
        showStatus("Аватар $model готов")
        if (privacy) avatar.mood("sleep")
    }

    override fun onModelError(message: String) {
        Log.e(TAG, "model error: $message")
        showStatus("Ошибка модели: $message")
    }

    override fun onAvatarTap(x: Float, y: Float) {
        if (avatar.width == 0 || avatar.height == 0) return
        avatar.gaze(
            ((x / avatar.width) - 0.5f) * 2f,
            ((y / avatar.height) - 0.5f) * 2f
        )
    }

    override fun onMood(mood: String) {
        if (privacy && mood != "sleep") return
        avatar.mood(mood)
        if (mood == "thinking") showStatus("Думаю…")
    }

    override fun onCaption(text: String) {
        avatar.caption(text)
    }

    override fun onLevel(level: Float) {
        avatar.level(level)
    }

    override fun onHeard(text: String) {
        showStatus("Ты: ${text.take(42)}")
    }

    override fun onError(message: String) {
        showStatus(message)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Аватар", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Аватар поверх других приложений"
                setShowBadge(false)
            }
        )
    }

    private fun startForegroundNotification() {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vtuber AI поверх приложений")
            .setContentText("Тап — разговор, двойной тап — настройки, долгий тап — приватный режим")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Скрыть", stop)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        VoiceChatManager.listener = null
        VoiceChatManager.destroy()
        prefs.avatarScale = (baseWidth.toFloat() / BASE_WIDTH).coerceIn(0.5f, 2f)
        runCatching { windowManager?.removeViewImmediate(container) }
        avatar.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "vtuber_avatar"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_STOP = "com.vtuber.ai.action.STOP"
        private const val BASE_WIDTH = 320
        private const val BASE_HEIGHT = 400
        private const val MIN_SIZE = 180
        private const val MAX_SIZE = 900
        private const val DOUBLE_TAP_MS = 320L
        private const val HOLD_MS = 550L
        private const val STATUS_MS = 2600L
    }
}
