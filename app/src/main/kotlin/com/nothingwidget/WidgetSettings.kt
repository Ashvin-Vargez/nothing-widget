package com.nothingwidget

import android.content.Context
import android.content.SharedPreferences

data class WidgetSettings(
    val startHour: Int = 7,
    val endHour: Int = 23,
) {
    companion object {
        private const val PREFS = "widget_prefs"
        private const val KEY_START = "start_hour"
        private const val KEY_END   = "end_hour"

        fun load(ctx: Context): WidgetSettings {
            val p = prefs(ctx)
            return WidgetSettings(
                startHour = p.getInt(KEY_START, 7),
                endHour   = p.getInt(KEY_END,   23),
            )
        }

        fun save(ctx: Context, s: WidgetSettings) {
            prefs(ctx).edit()
                .putInt(KEY_START, s.startHour)
                .putInt(KEY_END,   s.endHour)
                .apply()
        }

        private fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
