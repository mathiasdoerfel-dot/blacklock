package de.matze.blackbox.tools.pocketlock

import android.content.Context

class BlackoutConfig(context: Context) {
    private val prefs = context.getSharedPreferences("blackbox_blackout_prefs", Context.MODE_PRIVATE)

    var showHintOnTap: Boolean
        get() = prefs.getBoolean("show_hint_on_tap", true)
        set(value) = prefs.edit().putBoolean("show_hint_on_tap", value).apply()

    var showSubtleClock: Boolean
        get() = prefs.getBoolean("show_subtle_clock", false)
        set(value) = prefs.edit().putBoolean("show_subtle_clock", value).apply()
}
