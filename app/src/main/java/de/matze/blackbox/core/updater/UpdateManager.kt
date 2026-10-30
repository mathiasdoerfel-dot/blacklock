package de.matze.blackbox.core.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.matze.blackbox.core.ChangelogData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val GITHUB_REPO_API = "https://api.github.com/repos/mathiasdoerfel-dot/blacklock/releases/latest"

    /**
     * Checks GitHub for the latest release and compares it with the current app version.
     */
    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_REPO_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "BlackBox-App")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("Server antwortete mit Statuscode: ${connection.responseCode}")
                )
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val rawTag = json.optString("tag_name", "").trim()
            val latestVersion = rawTag.removePrefix("v").removePrefix("V")
            val releaseNotes = json.optString("body", "").trim()

            var apkDownloadUrl: String? = null
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", null)
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val currentVersion = getCurrentVersion(context)
            val isNewer = isVersionNewer(latestVersion, currentVersion)

            Result.success(
                UpdateInfo(
                    latestVersion = if (isNewer) latestVersion else currentVersion,
                    isUpdateAvailable = isNewer && apkDownloadUrl != null,
                    releaseNotes = releaseNotes.ifEmpty { "Keine Release-Notes vorhanden." },
                    downloadUrl = apkDownloadUrl,
                    fileSize = apkSize
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file to the app's cache directory while following any redirects.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetVersion: String,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Remove previous updates if present
            updatesDir.listFiles()?.forEach { it.delete() }

            val targetFile = File(updatesDir, "BlackBox-v$targetVersion.apk")

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0
            val maxRedirects = 6

            // Follow HTTP redirects (GitHub releases redirect to AWS S3/CDN)
            while (true) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "BlackBox-App")
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location != null && redirectCount < maxRedirects) {
                        currentUrl = location
                        redirectCount++
                        continue
                    } else {
                        return@withContext Result.failure(Exception("Zu viele Weiterleitungen beim Download."))
                    }
                }
                break
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100 || downloadedBytes == totalBytes) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if the app can trigger installation, otherwise redirects to the system permission setting.
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Directs the user to Android Settings to allow installing unknown apps.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Triggers the Android package installer for the downloaded APK file.
     */
    fun installApk(context: Context, apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Returns the current versionName from PackageManager or fallback to ChangelogData.
     */
    fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: ChangelogData.CURRENT_VERSION
        } catch (_: Exception) {
            ChangelogData.CURRENT_VERSION
        }
    }

    /**
     * Compares two semantic version strings (e.g., "0.0.2" > "0.0.1").
     */
    fun isVersionNewer(remote: String, local: String): Boolean {
        if (remote.isBlank() || local.isBlank()) return false
        val remoteParts = remote.split(".").mapNotNull { it.trim().toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.trim().toIntOrNull() }

        // Ignore legacy releases from before the 0.0.x version reboot (e.g. old v1.0.0)
        if ((remoteParts.firstOrNull() ?: 0) >= 1 && (localParts.firstOrNull() ?: 0) == 0) {
            return false
        }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
