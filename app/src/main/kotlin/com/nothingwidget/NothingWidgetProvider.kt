package com.nothingwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class NothingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        startService(context)
    }

    override fun onEnabled(context: Context) {
        startService(context)
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, WidgetUpdateService::class.java))
    }

    private fun startService(context: Context) {
        val intent = Intent(context, WidgetUpdateService::class.java)
        try {
            context.startForegroundService(intent)
        } catch (_: Exception) {
            context.startService(intent)
        }
    }
}
