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

    const val PKG_MS_RDC_1 = "com.microsoft.rdc.android"
    const val PKG_MS_RDC_2 = "com.microsoft.rdc.androidx"
    const val PKG_MS_RDC_BETA = "com.microsoft.rdc.android.beta"
    const val PKG_AFREERDP = "com.freerdp.afreerdp"

    data class InstalledClient(
        val packageName: String,
        val appName: String,
        val isInstalled: Boolean
    )

    /**
     * Checks which known RDP clients are installed on device
     */
    fun checkInstalledClients(context: Context): List<InstalledClient> {
        val pm = context.packageManager
        val knownClients = listOf(
            InstalledClient(PKG_MS_RDC_1, "Microsoft Remote Desktop", isAppInstalled(pm, PKG_MS_RDC_1) || isAppInstalled(pm, PKG_MS_RDC_2) || isAppInstalled(pm, PKG_MS_RDC_BETA)),
            InstalledClient(PKG_AFREERDP, "aFreeRDP", isAppInstalled(pm, PKG_AFREERDP))
        )
        return knownClients
    }

    private fun isAppInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Automatic seamless connection (Как в RDM): launches server in best available RDP client
     */
    fun connectToServer(context: Context, server: ServerEntity) {
        // 1. Try official Microsoft Remote Desktop
        var launched = launchRdpFile(context, server, PKG_MS_RDC_1)
        if (!launched) {
            launched = launchRdpFile(context, server, PKG_MS_RDC_2)
        }
        if (!launched) {
            launched = launchRdpFile(context, server, PKG_MS_RDC_BETA)
        }
        // 2. Try aFreeRDP
        if (!launched) {
            launched = launchRdpFile(context, server, PKG_AFREERDP)
        }
        // 3. Try generic .rdp file association
        if (!launched) {
            launched = launchRdpFile(context, server)
        }
        // 4. Try rdp:// URI scheme
        if (!launched) {
            launched = launchRdpUri(context, server)
        }
        // 5. If no client installed, prompt to install from Play Store
        if (!launched) {
            Toast.makeText(context, "Установите MS Remote Desktop для прямого подключения", Toast.LENGTH_LONG).show()
            openPlayStore(context, PKG_MS_RDC_1)
        }
    }

    /**
     * Generates .rdp configuration file content according to MS RDP specifications
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
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/x-rdp")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                if (!targetPackage.isNullOrBlank()) {
                    setPackage(targetPackage)
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/rdp")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    if (!targetPackage.isNullOrBlank()) {
                        setPackage(targetPackage)
                    }
                }
                context.startActivity(fallbackIntent)
                true
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }
        }
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
     * Opens Google Play Store to install Microsoft Remote Desktop or aFreeRDP
     */
    fun openPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
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
