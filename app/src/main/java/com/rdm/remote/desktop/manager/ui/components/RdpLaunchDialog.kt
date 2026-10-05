package com.rdm.remote.desktop.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
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
    val isAnyClientInstalled = installedClients.any { it.isInstalled }

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
                    text = "Способ запуска:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Primary: Launch MS Remote Desktop (or System Default)
                LaunchOptionCard(
                    title = "Запустить в MS Remote Desktop",
                    subtitle = "Открыть через официальный клиент Microsoft",
                    icon = Icons.Default.Launch,
                    color = Color(0xFF0078D7),
                    onClick = {
                        val launched = RdpLauncher.launchRdpFile(context, server, RdpLauncher.PKG_MS_RDC_1)
                        if (!launched) {
                            // Fallback to generic RDP file viewer or URI
                            val uriLaunched = RdpLauncher.launchRdpUri(context, server)
                            if (!uriLaunched) {
                                RdpLauncher.launchRdpFile(context, server)
                            }
                        }
                        onDismiss()
                    }
                )

                // 2. Launch in aFreeRDP / Generic RDP
                LaunchOptionCard(
                    title = "Запустить через .RDP файл",
                    subtitle = "Открыть в любом установленном RDP клиенте",
                    icon = Icons.Outlined.OpenInNew,
                    color = Color(0xFF107C41),
                    onClick = {
                        RdpLauncher.launchRdpFile(context, server)
                        onDismiss()
                    }
                )

                // 3. If no client is installed, prompt to download MS Remote Desktop from Play Store
                if (!isAnyClientInstalled) {
                    LaunchOptionCard(
                        title = "Установить MS Remote Desktop",
                        subtitle = "Скачать из Google Play для прямого подключения",
                        icon = Icons.Default.Download,
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = {
                            RdpLauncher.openPlayStore(context, RdpLauncher.PKG_MS_RDC_1)
                            onDismiss()
                        }
                    )
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
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = color
            )
        }
    }
}
