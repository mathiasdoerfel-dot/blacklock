package de.matze.blackbox.core.updater

import java.io.File

data class UpdateInfo(
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val releaseNotes: String,
    val downloadUrl: String?,
    val fileSize: Long = 0L
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpToDate(val version: String) : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class PermissionRequired(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}
