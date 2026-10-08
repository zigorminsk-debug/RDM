package com.rdm.remote.desktop.manager.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.rdm.remote.desktop.manager.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseAsset(
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    val size: Long,
    @SerializedName("content_type") val contentType: String?
)

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    val assets: List<GitHubReleaseAsset>?
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class UpdateAvailable(
        val newVersion: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val htmlUrl: String
    ) : UpdateStatus()
    data class Downloading(val progressPercent: Int) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

object AppUpdateManager {

    private const val GITHUB_REPO = "zigorminsk-debug/RDM"
    private const val GITHUB_API_LATEST = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    fun resetStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }

    /**
     * Checks GitHub API for the latest release and compares with current BuildConfig.VERSION_NAME
     */
    suspend fun checkForUpdates(silentIfUpToDate: Boolean = false): UpdateStatus = withContext(Dispatchers.IO) {
        _updateStatus.value = UpdateStatus.Checking

        try {
            val url = URL(GITHUB_API_LATEST)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "RDM-Android-App/${BuildConfig.VERSION_NAME}")
            }

            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val release = Gson().fromJson(json, GitHubRelease::class.java)

                val latestTag = release.tagName.trim()
                val currentVersion = BuildConfig.VERSION_NAME.trim()

                if (isNewerVersion(latestTag, currentVersion)) {
                    // Find APK asset or fallback to release page
                    val apkAsset = release.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    val downloadUrl = apkAsset?.browserDownloadUrl
                        ?: "https://github.com/$GITHUB_REPO/releases/download/$latestTag/RDM-$latestTag-signed.apk"

                    val status = UpdateStatus.UpdateAvailable(
                        newVersion = latestTag.removePrefix("v"),
                        releaseNotes = release.body ?: "Доступно новое обновление приложения.",
                        downloadUrl = downloadUrl,
                        htmlUrl = release.htmlUrl
                    )
                    _updateStatus.value = status
                    return@withContext status
                } else {
                    val status = if (silentIfUpToDate) UpdateStatus.Idle else UpdateStatus.UpToDate
                    _updateStatus.value = status
                    return@withContext status
                }
            } else {
                val status = UpdateStatus.Error("Сервер GitHub вернул код ${connection.responseCode}")
                _updateStatus.value = status
                return@withContext status
            }
        } catch (e: Exception) {
            val status = UpdateStatus.Error(e.localizedMessage ?: "Не удалось проверить обновления")
            _updateStatus.value = status
            return@withContext status
        }
    }

    /**
     * Downloads APK from GitHub release and triggers native Android package installer
     */
    suspend fun downloadAndInstallApk(context: Context, downloadUrl: String, versionName: String) = withContext(Dispatchers.IO) {
        _updateStatus.value = UpdateStatus.Downloading(0)

        try {
            val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val apkFile = File(updatesDir, "RDM-v$versionName-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RDM-Android-App/${BuildConfig.VERSION_NAME}")
            }

            // Follow redirect if 302/301 (GitHub asset redirects to AWS S3/Azure)
            var actualConn = connection
            if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                val redirectUrl = connection.getHeaderField("Location")
                if (!redirectUrl.isNullOrBlank()) {
                    actualConn = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "RDM-Android-App/${BuildConfig.VERSION_NAME}")
                    }
                }
            }

            val totalBytes = actualConn.contentLengthLong
            actualConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastPercent = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _updateStatus.value = UpdateStatus.Downloading(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }

            _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile)
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("Ошибка загрузки обновления: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Prompts the user to install the downloaded APK
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("Не удалось запустить установщик: ${e.localizedMessage}")
        }
    }

    /**
     * SemVer-like comparison between tags (e.g. v1.0.44 vs 1.0.43)
     */
    fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        val cleanLatest = latestTag.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val vLatest = latestParts.getOrElse(i) { 0 }
            val vCurrent = currentParts.getOrElse(i) { 0 }
            if (vLatest > vCurrent) return true
            if (vLatest < vCurrent) return false
        }
        return false
    }
}
