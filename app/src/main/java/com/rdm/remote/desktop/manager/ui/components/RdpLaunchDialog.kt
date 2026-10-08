package com.rdm.remote.desktop.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import com.rdm.remote.desktop.manager.utils.RdpLauncher

@Composable
fun RdpLaunchDialog(
    server: ServerEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val installedClients = remember { RdpLauncher.checkInstalledClients(context) }
    val isAfreeRdpInstalled = installedClients.firstOrNull { it.packageName == RdpLauncher.PKG_AFREERDP }?.isInstalled == true
    val isMsInstalled = installedClients.firstOrNull { it.packageName == RdpLauncher.PKG_MS_RDC_1 }?.isInstalled == true
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DesktopWindows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Подключение к серверу",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${server.name} (${server.formattedAddress()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Connection Info Summary Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Адрес: ${server.formattedAddress()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                            IconButton(
                                onClick = { RdpLauncher.copyToClipboard(context, "Адрес", server.formattedAddress()) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }
                        }
                        if (server.login.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Логин: ${server.formattedUsername()}", style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = { RdpLauncher.copyToClipboard(context, "Логин", server.formattedUsername()) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (server.password.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Пароль: ••••••••", style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = { RdpLauncher.copyToClipboard(context, "Пароль", server.password) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Выберите RDP-клиент:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Open Source FreeRDP (aFreeRDP)
                LaunchOptionCard(
                    title = "aFreeRDP (Open Source)",
                    subtitle = if (isAfreeRdpInstalled) "Запустить сеанс через открытый клиент FreeRDP" else "Открытый RDP-клиент не установлен (нажмите для скачивания с F-Droid)",
                    icon = Icons.Default.Terminal,
                    color = Color(0xFF107C41),
                    isFeatured = true,
                    badgeText = if (isAfreeRdpInstalled) "Open Source" else "Скачать F-Droid",
                    onClick = {
                        if (isAfreeRdpInstalled) {
                            if (!RdpLauncher.launchFreeRdpDirect(context, server)) {
                                RdpLauncher.launchRdpFile(context, server, RdpLauncher.PKG_AFREERDP)
                            }
                        } else {
                            RdpLauncher.openUrl(context, RdpLauncher.URL_AFREERDP_FDROID)
                        }
                        onDismiss()
                    }
                )

                // 2. Microsoft Remote Desktop (Official client)
                LaunchOptionCard(
                    title = "Microsoft Remote Desktop",
                    subtitle = if (isMsInstalled) "Запустить сеанс в клиенте Microsoft" else "Клиент Microsoft не установлен (нажмите для перехода в Google Play)",
                    icon = Icons.Default.Launch,
                    color = Color(0xFF0078D7),
                    badgeText = if (isMsInstalled) "Установлен" else "Google Play",
                    onClick = {
                        if (isMsInstalled) {
                            var launched = RdpLauncher.launchRdpFile(context, server, RdpLauncher.PKG_MS_RDC_1)
                            if (!launched) launched = RdpLauncher.launchRdpFile(context, server, RdpLauncher.PKG_MS_RDC_2)
                            if (!launched) launched = RdpLauncher.launchRdpFile(context, server, RdpLauncher.PKG_MS_RDC_BETA)
                            if (!launched) RdpLauncher.launchRdpUri(context, server)
                        } else {
                            RdpLauncher.openPlayStore(context, RdpLauncher.PKG_MS_RDC_1)
                        }
                        onDismiss()
                    }
                )

                // 3. Launch via generic .RDP file (Any other installed client)
                LaunchOptionCard(
                    title = "Любой системный RDP клиент",
                    subtitle = "Открыть сессию через файл конфигурации .rdp с выбором приложения",
                    icon = Icons.Outlined.OpenInNew,
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        RdpLauncher.launchRdpFile(context, server)
                        onDismiss()
                    }
                )

                // 4. Source code link to FreeRDP
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            RdpLauncher.openUrl(context, RdpLauncher.URL_AFREERDP_GITHUB)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GitHub исходный код FreeRDP / aFreeRDP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun LaunchOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    isFeatured: Boolean = false,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isFeatured) color.copy(alpha = 0.16f) else color.copy(alpha = 0.09f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = color,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = color
            )
        }
    }
}
