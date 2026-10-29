package de.matze.blackbox.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.GERMANY, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.GERMANY, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.GERMANY, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatDateTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return "Noch nie ausgeführt"
        val sdf = SimpleDateFormat("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMANY)
        return sdf.format(Date(timestampMs))
    }

    fun formatShortDate(timestampMs: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        return sdf.format(Date(timestampMs))
    }
}
