package com.nothingwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, NothingWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            context.startForegroundService(Intent(context, WidgetUpdateService::class.java))
        }
    }
}
