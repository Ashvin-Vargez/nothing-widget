package com.nothingwidget

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class WidgetUpdateService : Service() {

    private val handler  = Handler(Looper.getMainLooper())
    private val game     = DinoGameState()
    private val renderer = WidgetRenderer()

    private lateinit var settings: WidgetSettings
    private lateinit var manager: AppWidgetManager
    private lateinit var provider: ComponentName

    // ── Frame loop (~15 fps = 66 ms)
    private val frameLoop = object : Runnable {
        override fun run() {
            game.update()
            pushFrame()
            handler.postDelayed(this, 66L)
        }
    }

    // ── Lifecycle

    override fun onCreate() {
        super.onCreate()
        manager  = AppWidgetManager.getInstance(this)
        provider = ComponentName(this, NothingWidgetProvider::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        settings = WidgetSettings.load(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        handler.removeCallbacks(frameLoop)
        handler.post(frameLoop)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(frameLoop)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Rendering

    private fun pushFrame() {
        val ids = manager.getAppWidgetIds(provider)
        if (ids.isEmpty()) { stopSelf(); return }

        // Use the first widget's option bundle to get the pixel size
        val opts = manager.getAppWidgetOptions(ids[0])
        val density = resources.displayMetrics.density
        val widthDp  = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,  280)
        val heightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 280)
        val sqDp     = minOf(widthDp, heightDp).coerceAtLeast(90)
        val px       = (sqDp * density).toInt()

        val bmp: Bitmap = renderer.render(px, settings, game)

        val views = RemoteViews(packageName, R.layout.widget_layout)
        views.setImageViewBitmap(R.id.widget_image, bmp)
        manager.updateAppWidget(ids, views)
    }

    // ── Notification (required for foreground service on Android 8+)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Widget Animation",
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nothing Widget")
            .setContentText("Running")
            .setSmallIcon(R.drawable.ic_widget)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "nw_channel"
        private const val NOTIF_ID   = 1
    }
}
