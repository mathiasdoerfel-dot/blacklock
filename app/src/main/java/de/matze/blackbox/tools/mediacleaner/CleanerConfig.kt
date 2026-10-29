package de.matze.blackbox.tools.mediacleaner

import android.content.Context
import android.os.Environment
import java.io.File

enum class ScheduleMode(val id: String, val label: String, val intervalDays: Long) {
    MANUAL("MANUAL", "Nur manuell", 0L),
    DAILY("DAILY", "Täglich", 1L),
    EVERY_3_DAYS("EVERY_3_DAYS", "Alle 3 Tage", 3L),
    WEEKLY("WEEKLY", "Wöchentlich (Standard)", 7L),
    EVERY_14_DAYS("EVERY_14_DAYS", "Alle 14 Tage", 14L);

    companion object {
        fun fromId(id: String?): ScheduleMode {
            return entries.find { it.id == id } ?: WEEKLY
        }
    }
}

data class WatchedFolderDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val relativePath: String
) {
    fun resolveFile(): File {
        val root = Environment.getExternalStorageDirectory()
        return File(root, relativePath)
    }
}

class CleanerConfig(context: Context) {
    private val prefs = context.getSharedPreferences("blackbox_cleaner_prefs", Context.MODE_PRIVATE)

    companion object {
        val DEFAULT_FOLDERS = listOf(
            WatchedFolderDef(
                id = "wa_images_private",
                title = "WhatsApp Images · Private",
                subtitle = "Android/media/com.whatsapp/.../WhatsApp Images/Private",
                relativePath = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Private"
            ),
            WatchedFolderDef(
                id = "wa_video_private",
                title = "WhatsApp Video · Private",
                subtitle = "Android/media/com.whatsapp/.../WhatsApp Video/Private",
                relativePath = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Private"
            ),
            WatchedFolderDef(
                id = "quick_share",
                title = "Quick Share",
                subtitle = "Download/Quick Share",
                relativePath = "Download/Quick Share"
            )
        )
    }

    fun isFolderEnabled(folderId: String): Boolean {
        return prefs.getBoolean("folder_enabled_$folderId", true)
    }

    fun setFolderEnabled(folderId: String, enabled: Boolean) {
        prefs.edit().putBoolean("folder_enabled_$folderId", enabled).apply()
    }

    var scheduleMode: ScheduleMode
        get() = ScheduleMode.fromId(prefs.getString("schedule_mode", ScheduleMode.WEEKLY.id))
        set(value) = prefs.edit().putString("schedule_mode", value.id).apply()

    var minFileAgeDays: Int
        get() = prefs.getInt("min_file_age_days", 0)
        set(value) = prefs.edit().putInt("min_file_age_days", value).apply()

    var trashRetentionDays: Int
        get() = prefs.getInt("trash_retention_days", 30)
        set(value) = prefs.edit().putInt("trash_retention_days", value).apply()

    var requireCharging: Boolean
        get() = prefs.getBoolean("require_charging", false)
        set(value) = prefs.edit().putBoolean("require_charging", value).apply()

    var lastRunTimestamp: Long
        get() = prefs.getLong("last_run_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_run_timestamp", value).apply()

    var lastRunSummary: String
        get() = prefs.getString("last_run_summary", "Noch kein Durchlauf ausgeführt") ?: "Noch kein Durchlauf ausgeführt"
        set(value) = prefs.edit().putString("last_run_summary", value).apply()
}
