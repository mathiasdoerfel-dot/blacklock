package de.matze.blackbox.tools.mediacleaner

import android.content.Context
import android.os.Environment
import java.io.File

enum class ScheduleMode(val id: String, val label: String, val intervalDays: Long) {
    MANUAL("MANUAL", "Nur manuell (Standard / 0% Hintergrund)", 0L),
    DAILY("DAILY", "Täglich", 1L),
    EVERY_3_DAYS("EVERY_3_DAYS", "Alle 3 Tage", 3L),
    WEEKLY("WEEKLY", "Wöchentlich", 7L),
    EVERY_14_DAYS("EVERY_14_DAYS", "Alle 14 Tage", 14L);

    companion object {
        fun fromId(id: String?): ScheduleMode {
            return entries.find { it.id == id } ?: MANUAL
        }
    }
}

data class WatchedFolderDef(
    val id: String,
    val title: String,
    val defaultRelativePath: String
)

class CleanerConfig(context: Context) {
    private val prefs = context.getSharedPreferences("blackbox_cleaner_prefs", Context.MODE_PRIVATE)

    companion object {
        val DEFAULT_FOLDERS = listOf(
            WatchedFolderDef(
                id = "wa_images_private",
                title = "WhatsApp Images · Private",
                defaultRelativePath = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Private"
            ),
            WatchedFolderDef(
                id = "wa_video_private",
                title = "WhatsApp Video · Private",
                defaultRelativePath = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Private"
            ),
            WatchedFolderDef(
                id = "quick_share",
                title = "Quick Share",
                defaultRelativePath = "Download/Quick Share"
            )
        )

        fun normalizeRelativePath(rawPath: String): String {
            val rootPath = Environment.getExternalStorageDirectory().absolutePath
            var cleaned = rawPath.trim()
            if (cleaned.startsWith(rootPath)) {
                cleaned = cleaned.removePrefix(rootPath)
            }
            return cleaned.trimStart('/', '\\').replace('\\', '/')
        }
    }

    fun isFolderEnabled(folderId: String): Boolean {
        return prefs.getBoolean("folder_enabled_$folderId", true)
    }

    fun setFolderEnabled(folderId: String, enabled: Boolean) {
        prefs.edit().putBoolean("folder_enabled_$folderId", enabled).apply()
    }

    fun getFolderPath(folderId: String): String {
        val defaultPath = DEFAULT_FOLDERS.find { it.id == folderId }?.defaultRelativePath ?: ""
        return prefs.getString("folder_path_$folderId", defaultPath) ?: defaultPath
    }

    fun setFolderPath(folderId: String, path: String) {
        val normalized = normalizeRelativePath(path)
        prefs.edit().putString("folder_path_$folderId", normalized).apply()
    }

    fun resetFolderPath(folderId: String) {
        prefs.edit().remove("folder_path_$folderId").apply()
    }

    fun isCustomPath(folderId: String): Boolean {
        val defaultPath = DEFAULT_FOLDERS.find { it.id == folderId }?.defaultRelativePath ?: ""
        return getFolderPath(folderId) != defaultPath
    }

    fun resolveFolderFile(folderId: String): File {
        val relPath = getFolderPath(folderId)
        val root = Environment.getExternalStorageDirectory()
        val primary = File(root, relPath)
        if (!primary.exists() && !isCustomPath(folderId)) {
            // Auto-fallback for legacy WhatsApp path (Android 10 and below or alternate storage)
            if (relPath.startsWith("Android/media/com.whatsapp/WhatsApp/")) {
                val legacyRelPath = relPath.removePrefix("Android/media/com.whatsapp/")
                val legacy = File(root, legacyRelPath)
                if (legacy.exists()) return legacy
            }
        }
        return primary
    }

    var scheduleMode: ScheduleMode
        get() = ScheduleMode.fromId(prefs.getString("schedule_mode", ScheduleMode.MANUAL.id))
        set(value) {
            prefs.edit().putString("schedule_mode", value.id).apply()
            if (value != ScheduleMode.MANUAL) {
                hasConfiguredSchedule = true
            }
        }

    var hasConfiguredSchedule: Boolean
        get() = prefs.getBoolean("has_configured_schedule", false)
        set(value) = prefs.edit().putBoolean("has_configured_schedule", value).apply()

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
