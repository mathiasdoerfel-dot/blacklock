package de.matze.blackbox.tools.mediacleaner

import android.content.Context
import android.os.Environment
import de.matze.blackbox.core.FormatUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.ceil

data class FolderStat(
    val def: WatchedFolderDef,
    val enabled: Boolean,
    val currentRelativePath: String,
    val absolutePath: String,
    val exists: Boolean,
    val isCustom: Boolean,
    val fileCount: Int,
    val totalBytes: Long
)

data class TrashItem(
    val id: String,
    val fileName: String,
    val sourceFolderId: String,
    val sourceFolderTitle: String,
    val originalPath: String,
    val trashFilePath: String,
    val sizeBytes: Long,
    val trashedAt: Long,
    val originalLastModified: Long
) {
    val isVideo: Boolean
        get() {
            val lower = fileName.lowercase()
            return lower.endsWith(".mp4") || lower.endsWith(".mov") ||
                lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".3gp")
        }

    val isImage: Boolean
        get() {
            val lower = fileName.lowercase()
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.endsWith(".heic")
        }

    fun daysRemaining(retentionDays: Int, nowMs: Long = System.currentTimeMillis()): Int {
        val expireAt = trashedAt + retentionDays * 86_400_000L
        val diffMs = expireAt - nowMs
        if (diffMs <= 0L) return 0
        return ceil(diffMs.toDouble() / 86_400_000.0).toInt()
    }
}

data class CleanupRunResult(
    val movedCount: Int,
    val movedBytes: Long,
    val purgedCount: Int,
    val errors: List<String>
)

class CleanerRepository(private val context: Context) {
    val config = CleanerConfig(context)
    private val manifestFile = File(context.filesDir, "trash_manifest.json")

    fun hasAllFilesAccess(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun getTrashDirectory(): File {
        val root = Environment.getExternalStorageDirectory()
        val trashDir = File(root, "Android/media/de.matze.blackbox/Trash")
        if (!trashDir.exists()) {
            trashDir.mkdirs()
        }
        val noMedia = File(trashDir, ".nomedia")
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile()
            } catch (_: Exception) {
            }
        }
        return trashDir
    }

    fun getFolderStats(): List<FolderStat> {
        return CleanerConfig.DEFAULT_FOLDERS.map { def ->
            val enabled = config.isFolderEnabled(def.id)
            val currentRelPath = config.getFolderPath(def.id)
            val isCustom = config.isCustomPath(def.id)
            val dir = config.resolveFolderFile(def.id)
            val exists = dir.exists() && dir.isDirectory
            var count = 0
            var bytes = 0L
            if (exists && hasAllFilesAccess()) {
                val files = dir.listFiles().orEmpty()
                for (f in files) {
                    if (f.isFile && !f.name.equals(".nomedia", ignoreCase = true) && !f.name.startsWith(".trashed")) {
                        count++
                        bytes += f.length()
                    }
                }
            }
            FolderStat(
                def = def,
                enabled = enabled,
                currentRelativePath = currentRelPath,
                absolutePath = dir.absolutePath,
                exists = exists,
                isCustom = isCustom,
                fileCount = count,
                totalBytes = bytes
            )
        }
    }

    @Synchronized
    fun getTrashItems(): List<TrashItem> {
        val rawList = readManifestInternal()
        val validList = mutableListOf<TrashItem>()
        var changed = false
        for (item in rawList) {
            val f = File(item.trashFilePath)
            if (f.exists() && f.isFile) {
                validList.add(item)
            } else {
                changed = true
            }
        }
        if (changed) {
            writeManifestInternal(validList)
        }
        return validList.sortedByDescending { it.trashedAt }
    }

    @Synchronized
    fun runCleanup(ignoreMinAge: Boolean = false): CleanupRunResult {
        val errors = mutableListOf<String>()
        if (!hasAllFilesAccess()) {
            return CleanupRunResult(0, 0L, 0, listOf("Berechtigung 'Zugriff auf alle Dateien' fehlt."))
        }

        val now = System.currentTimeMillis()
        val minAgeMs = if (ignoreMinAge) 0L else config.minFileAgeDays * 86_400_000L
        val trashDir = getTrashDirectory()
        val manifest = getTrashItems().toMutableList()

        var movedCount = 0
        var movedBytes = 0L

        for (def in CleanerConfig.DEFAULT_FOLDERS) {
            if (!config.isFolderEnabled(def.id)) continue
            val folder = config.resolveFolderFile(def.id)
            if (!folder.exists() || !folder.isDirectory) continue

            val files = folder.listFiles().orEmpty()
            for (file in files) {
                if (!file.isFile) continue
                if (file.name.equals(".nomedia", ignoreCase = true) || file.name.startsWith(".trashed")) continue

                val ageMs = now - file.lastModified()
                if (ageMs < minAgeMs) continue

                val itemId = UUID.randomUUID().toString().take(8)
                val safeName = file.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val targetFile = File(trashDir, "${now}_${itemId}_$safeName")

                val fileSize = file.length()
                val origModified = file.lastModified()
                val origPath = file.absolutePath
                val origName = file.name

                try {
                    val moved = moveFileRobust(file, targetFile)
                    if (moved) {
                        manifest.add(
                            TrashItem(
                                id = itemId,
                                fileName = origName,
                                sourceFolderId = def.id,
                                sourceFolderTitle = def.title,
                                originalPath = origPath,
                                trashFilePath = targetFile.absolutePath,
                                sizeBytes = fileSize,
                                trashedAt = now,
                                originalLastModified = origModified
                            )
                        )
                        movedCount++
                        movedBytes += fileSize
                    } else {
                        errors.add("Konnte ${file.name} nicht verschieben.")
                    }
                } catch (e: Exception) {
                    errors.add("Fehler bei ${file.name}: ${e.message}")
                }
            }
        }

        writeManifestInternal(manifest)
        val purgedCount = purgeExpiredInternal(now)

        config.lastRunTimestamp = now
        val movedPart = "$movedCount Dateien (${FormatUtils.formatBytes(movedBytes)}) in Papierkorb"
        val purgedPart = if (purgedCount > 0) " · $purgedCount abgelaufene gelöscht" else ""
        config.lastRunSummary = movedPart + purgedPart

        return CleanupRunResult(
            movedCount = movedCount,
            movedBytes = movedBytes,
            purgedCount = purgedCount,
            errors = errors
        )
    }

    @Synchronized
    fun restoreItem(id: String): Result<TrashItem> {
        return try {
            val items = getTrashItems().toMutableList()
            val item = items.find { it.id == id }
                ?: return Result.failure(IllegalArgumentException("Datei nicht im Papierkorb gefunden."))

            val trashFile = File(item.trashFilePath)
            if (!trashFile.exists()) {
                items.removeAll { it.id == id }
                writeManifestInternal(items)
                return Result.failure(IllegalStateException("Papierkorb-Datei existiert nicht mehr."))
            }

            var targetFile = File(item.originalPath)
            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            if (targetFile.exists()) {
                val dotIndex = item.fileName.lastIndexOf('.')
                val restoredName = if (dotIndex > 0) {
                    val base = item.fileName.substring(0, dotIndex)
                    val ext = item.fileName.substring(dotIndex)
                    "${base}_restored_${System.currentTimeMillis() % 10000}$ext"
                } else {
                    "${item.fileName}_restored"
                }
                targetFile = File(parent ?: getTrashDirectory(), restoredName)
            }

            val ok = moveFileRobust(trashFile, targetFile)
            if (!ok) {
                return Result.failure(IllegalStateException("Wiederherstellung fehlgeschlagen."))
            }
            if (item.originalLastModified > 0L) {
                try {
                    targetFile.setLastModified(item.originalLastModified)
                } catch (_: Exception) {
                }
            }

            items.removeAll { it.id == id }
            writeManifestInternal(items)
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Synchronized
    fun restoreAll(): Int {
        val items = getTrashItems()
        var restored = 0
        for (item in items) {
            if (restoreItem(item.id).isSuccess) {
                restored++
            }
        }
        return restored
    }

    @Synchronized
    fun deletePermanently(id: String): Boolean {
        val items = getTrashItems().toMutableList()
        val item = items.find { it.id == id } ?: return false
        val f = File(item.trashFilePath)
        val deleted = !f.exists() || f.delete()
        if (deleted) {
            items.removeAll { it.id == id }
            writeManifestInternal(items)
        }
        return deleted
    }

    @Synchronized
    fun emptyTrash(): Int {
        val items = getTrashItems()
        var deletedCount = 0
        for (item in items) {
            val f = File(item.trashFilePath)
            if (!f.exists() || f.delete()) {
                deletedCount++
            }
        }
        writeManifestInternal(emptyList())
        return deletedCount
    }

    @Synchronized
    private fun purgeExpiredInternal(nowMs: Long = System.currentTimeMillis()): Int {
        val retentionMs = config.trashRetentionDays * 86_400_000L
        val items = getTrashItems()
        val remaining = mutableListOf<TrashItem>()
        var purged = 0
        for (item in items) {
            if (nowMs - item.trashedAt >= retentionMs) {
                val f = File(item.trashFilePath)
                if (!f.exists() || f.delete()) {
                    purged++
                } else {
                    remaining.add(item)
                }
            } else {
                remaining.add(item)
            }
        }
        if (purged > 0) {
            writeManifestInternal(remaining)
        }
        return purged
    }

    private fun moveFileRobust(source: File, target: File): Boolean {
        if (source.renameTo(target)) {
            return true
        }
        return try {
            source.copyTo(target, overwrite = true)
            if (target.exists() && target.length() == source.length()) {
                source.delete()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readManifestInternal(): List<TrashItem> {
        if (!manifestFile.exists()) return emptyList()
        return try {
            val content = manifestFile.readText(Charsets.UTF_8)
            val arr = JSONArray(content)
            val list = mutableListOf<TrashItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TrashItem(
                        id = obj.optString("id"),
                        fileName = obj.optString("fileName"),
                        sourceFolderId = obj.optString("sourceFolderId"),
                        sourceFolderTitle = obj.optString("sourceFolderTitle"),
                        originalPath = obj.optString("originalPath"),
                        trashFilePath = obj.optString("trashFilePath"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        trashedAt = obj.optLong("trashedAt", 0L),
                        originalLastModified = obj.optLong("originalLastModified", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeManifestInternal(items: List<TrashItem>) {
        try {
            val arr = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("fileName", item.fileName)
                    put("sourceFolderId", item.sourceFolderId)
                    put("sourceFolderTitle", item.sourceFolderTitle)
                    put("originalPath", item.originalPath)
                    put("trashFilePath", item.trashFilePath)
                    put("sizeBytes", item.sizeBytes)
                    put("trashedAt", item.trashedAt)
                    put("originalLastModified", item.originalLastModified)
                }
                arr.put(obj)
            }
            manifestFile.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}
