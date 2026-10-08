package com.rdm.remote.desktop.manager.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import java.io.File
import java.io.FileWriter
import java.net.URLEncoder

object RdpLauncher {

    const val PKG_AFREERDP = "com.freerdp.afreerdp"
    const val PKG_MS_RDC_1 = "com.microsoft.rdc.android"
    const val PKG_MS_RDC_2 = "com.microsoft.rdc.androidx"
    const val PKG_MS_RDC_BETA = "com.microsoft.rdc.android.beta"

    const val URL_AFREERDP_FDROID = "https://f-droid.org/packages/com.freerdp.afreerdp/"
    const val URL_AFREERDP_GITHUB = "https://github.com/FreeRDP/FreeRDP"

    data class InstalledClient(
        val packageName: String,
        val appName: String,
        val isInstalled: Boolean,
        val isOpenSource: Boolean = false
    )

    /**
     * Checks which known RDP clients are installed on device
     */
    fun checkInstalledClients(context: Context): List<InstalledClient> {
        val pm = context.packageManager
        return listOf(
            InstalledClient(
                packageName = PKG_AFREERDP,
                appName = "aFreeRDP (Open Source)",
                isInstalled = isAppInstalled(pm, PKG_AFREERDP),
                isOpenSource = true
            ),
            InstalledClient(
                packageName = PKG_MS_RDC_1,
                appName = "Microsoft Remote Desktop",
                isInstalled = isAppInstalled(pm, PKG_MS_RDC_1) || isAppInstalled(pm, PKG_MS_RDC_2) || isAppInstalled(pm, PKG_MS_RDC_BETA),
                isOpenSource = false
            )
        )
    }

    fun isAppInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Automatic seamless connection: launches server directly.
     * Prefers open-source aFreeRDP if installed, then MS RDC, then generic file / URI.
     */
    fun connectToServer(context: Context, server: ServerEntity) {
        val pm = context.packageManager
        val afreedpInstalled = isAppInstalled(pm, PKG_AFREERDP)

        if (afreedpInstalled) {
            if (launchFreeRdpDirect(context, server) || launchRdpFile(context, server, PKG_AFREERDP)) {
                return
            }
        }

        // Try MS Remote Desktop packages
        var launched = launchRdpFile(context, server, PKG_MS_RDC_1)
        if (!launched) launched = launchRdpFile(context, server, PKG_MS_RDC_2)
        if (!launched) launched = launchRdpFile(context, server, PKG_MS_RDC_BETA)

        // Try generic RDP file
        if (!launched) launched = launchRdpFile(context, server)

        // Try FreeRDP scheme
        if (!launched) launched = launchFreeRdpDirect(context, server)

        // Try rdp:// URI scheme
        if (!launched) launched = launchRdpUri(context, server)

        // If none installed, notify and offer aFreeRDP / Play Store
        if (!launched) {
            Toast.makeText(context, "Клиент RDP не найден. Установите aFreeRDP или MS Remote Desktop.", Toast.LENGTH_LONG).show()
            openUrl(context, URL_AFREERDP_FDROID)
        }
    }

    /**
     * Builds freerdp:// URI for native aFreeRDP SessionActivity
     * Syntax: freerdp://[user@]ip:port/connect?u=user&d=domain&p=password&sound=&clipboard=
     */
    fun buildFreeRdpUri(server: ServerEntity): Uri {
        val host = server.ip
        val port = if (server.port > 0) server.port else 3389
        val effectiveLogin = server.login.trim()
        val effectiveDomain = server.domain.trim()

        val userInfo = when {
            effectiveDomain.isNotBlank() && effectiveLogin.isNotBlank() -> {
                if (effectiveLogin.contains("\\")) effectiveLogin else "$effectiveDomain\\$effectiveLogin"
            }
            effectiveLogin.isNotBlank() -> effectiveLogin
            else -> null
        }

        val uriBuilder = Uri.Builder()
            .scheme("freerdp")
            .authority(if (userInfo != null) "$userInfo@$host:$port" else "$host:$port")
            .path("/connect")
            .appendQueryParameter("v", if (port != 3389) "$host:$port" else host)
            .appendQueryParameter("gdi", "sw")

        // Pass user explicitly as query param for robust aFreeRDP argument parsing (/u:)
        if (effectiveLogin.isNotBlank()) {
            val userParam = if (effectiveDomain.isNotBlank() && !effectiveLogin.contains("\\")) {
                "$effectiveDomain\\$effectiveLogin"
            } else {
                effectiveLogin
            }
            uriBuilder.appendQueryParameter("u", userParam)
        }

        // Pass domain explicitly (/d:)
        if (effectiveDomain.isNotBlank()) {
            uriBuilder.appendQueryParameter("d", effectiveDomain)
        }

        // Pass password explicitly (/p:)
        if (server.password.isNotBlank()) {
            uriBuilder.appendQueryParameter("p", server.password)
        }

        if (server.adminSession) {
            uriBuilder.appendQueryParameter("admin", "")
        }
        if (server.soundRedirection == 0) {
            uriBuilder.appendQueryParameter("sound", "sys:alsa")
        }
        uriBuilder.appendQueryParameter("clipboard", "")
        uriBuilder.appendQueryParameter("cert", "ignore")

        return uriBuilder.build()
    }

    /**
     * Launches session directly via aFreeRDP native scheme
     */
    fun launchFreeRdpDirect(context: Context, server: ServerEntity): Boolean {
        return try {
            val uri = buildFreeRdpUri(server)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(PKG_AFREERDP)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Package might not respond to scheme, fallback to .rdp file
            false
        }
    }

    /**
     * Generates .rdp configuration file content according to MS RDP / FreeRDP specifications
     */
    fun generateRdpFileContent(server: ServerEntity): String {
        val sb = StringBuilder()
        val address = if (server.port > 0 && server.port != 3389) "${server.ip}:${server.port}" else server.ip

        sb.appendLine("screen mode id:i:2")
        sb.appendLine("use multimon:i:0")

        val parts = server.resolution.split("x")
        if (parts.size == 2) {
            sb.appendLine("desktopwidth:i:${parts[0]}")
            sb.appendLine("desktopheight:i:${parts[1]}")
        } else {
            sb.appendLine("desktopwidth:i:1920")
            sb.appendLine("desktopheight:i:1080")
        }

        sb.appendLine("session bpp:i:32")
        sb.appendLine("winposstr:s:0,1,0,0,800,600")
        sb.appendLine("full address:s:$address")
        sb.appendLine("compression:i:1")
        sb.appendLine("keyboardhook:i:2")
        sb.appendLine("audiomode:i:${server.soundRedirection}")
        sb.appendLine("redirectprinters:i:0")
        sb.appendLine("redirectcomports:i:0")
        sb.appendLine("redirectsmartcards:i:0")
        sb.appendLine("redirectclipboard:i:1")
        sb.appendLine("redirectposdevices:i:0")
        sb.appendLine("autoreconnection enabled:i:1")
        sb.appendLine("authentication level:i:2")
        sb.appendLine("prompt for credentials:i:0")
        sb.appendLine("negotiate security layer:i:1")
        sb.appendLine("remoteapplicationmode:i:0")
        sb.appendLine("administrative session:i:${if (server.adminSession) 1 else 0}")

        if (server.login.isNotBlank()) {
            sb.appendLine("username:s:${server.login}")
        }
        if (server.domain.isNotBlank()) {
            sb.appendLine("domain:s:${server.domain}")
        }

        return sb.toString()
    }

    /**
     * Creates a temporary .rdp file and returns content URI via FileProvider
     */
    fun createRdpFile(context: Context, server: ServerEntity): Uri? {
        return try {
            val rdpDir = File(context.cacheDir, "rdp")
            if (!rdpDir.exists()) {
                rdpDir.mkdirs()
            }
            val sanitizedName = server.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val file = File(rdpDir, "${sanitizedName}_${server.id}.rdp")
            FileWriter(file).use { writer ->
                writer.write(generateRdpFileContent(server))
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Launches RDP session via FileProvider Intent (.rdp file)
     */
    fun launchRdpFile(context: Context, server: ServerEntity, targetPackage: String? = null): Boolean {
        val uri = createRdpFile(context, server) ?: return false

        val candidates = if (!targetPackage.isNullOrBlank()) {
            listOf(targetPackage)
        } else {
            listOf(
                PKG_AFREERDP,
                PKG_MS_RDC_1,
                PKG_MS_RDC_2,
                PKG_MS_RDC_BETA
            )
        }

        for (pkg in candidates) {
            try {
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: Exception) {}
        }

        val mimeTypes = listOf("application/x-rdp", "application/rdp", "*/*")
        for (mime in mimeTypes) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (!targetPackage.isNullOrBlank()) {
                        setPackage(targetPackage)
                    }
                }
                context.startActivity(intent)
                return true
            } catch (ignored: Exception) {}
        }

        return false
    }

    /**
     * Launches RDP session via rdp:// URI scheme
     */
    fun launchRdpUri(context: Context, server: ServerEntity): Boolean {
        return try {
            val address = server.formattedAddress()
            val encodedAddress = URLEncoder.encode(address, "UTF-8")
            val encodedUser = URLEncoder.encode(server.login, "UTF-8")
            val uriString = "rdp://full%20address=s:$encodedAddress&username=s:$encodedUser"
            val uri = Uri.parse(uriString)

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Opens Google Play Store
     */
    fun openPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    /**
     * Opens browser URL safely
     */
    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Helper to copy any text to clipboard
     */
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label скопирован", Toast.LENGTH_SHORT).show()
    }
}
