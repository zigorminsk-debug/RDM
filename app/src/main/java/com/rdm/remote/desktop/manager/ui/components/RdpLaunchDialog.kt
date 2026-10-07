package com.rdm.remote.desktop.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    onLaunchInApp: (ServerEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val installedClients = remember { RdpLauncher.checkInstalledClients(context) }
    val isAnyClientInstalled = installedClients.any { it.isInstalled }
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
                    text = "Способ подключения:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. #1 Primary: In-App Embedded RDP Client (Встроенный RDP-клиент)
                LaunchOptionCard(
                    title = "Встроенный RDP-клиент (In-App)",
                    subtitle = "Прямой интерактивный сеанс в приложении: виртуальный тачпад, клавиатура, горячие клавиши и зум",
                    icon = Icons.Default.Devices,
                    color = MaterialTheme.colorScheme.primary,
                    isFeatured = true,
                    badgeText = "Встроенный RDP",
                    onClick = {
                        onDismiss()
                        onLaunchInApp(server)
                    }
                )

                // 2. Launch MS Remote Desktop (Real remote Windows server session)
                LaunchOptionCard(
                    title = "Подключиться (MS Remote Desktop)",
                    subtitle = "Прямой запуск сессии в официальном клиенте Microsoft Remote Desktop",
                    icon = Icons.Default.Launch,
                    color = Color(0xFF0078D7),
                    onClick = {
                        val launched = RdpLauncher.launchRdpFile(context, server, RdpLauncher.PKG_MS_RDC_1)
                        if (!launched) {
                            val uriLaunched = RdpLauncher.launchRdpUri(context, server)
                            if (!uriLaunched) {
                                RdpLauncher.launchRdpFile(context, server)
                            }
                        }
                        onDismiss()
                    }
                )

                // 3. Launch via generic .RDP file (aFreeRDP / Any installed client)
                LaunchOptionCard(
                    title = "Открыть через .RDP файл",
                    subtitle = "Экспорт файла настроек в aFreeRDP или любой сторонний клиент",
                    icon = Icons.Outlined.OpenInNew,
                    color = Color(0xFF107C41),
                    onClick = {
                        RdpLauncher.launchRdpFile(context, server)
                        onDismiss()
                    }
                )

                // 4. If no client is installed, offer Play Store install option
                if (!isAnyClientInstalled) {
                    LaunchOptionCard(
                        title = "Установить MS Remote Desktop",
                        subtitle = "Скачать из Google Play для внешнего подключения",
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
            .then(
                if (isFeatured) {
                    Modifier.border(1.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
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
