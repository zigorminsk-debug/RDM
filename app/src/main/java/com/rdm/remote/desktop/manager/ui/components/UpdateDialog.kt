package com.rdm.remote.desktop.manager.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rdm.remote.desktop.manager.BuildConfig
import com.rdm.remote.desktop.manager.utils.UpdateStatus

@Composable
fun UpdateDialog(
    status: UpdateStatus,
    onDismiss: () -> Unit,
    onDownloadAndInstall: (String, String) -> Unit,
    onInstallLocalFile: () -> Unit = {}
) {
    val context = LocalContext.current

    when (status) {
        is UpdateStatus.Checking -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Проверка обновлений") },
                text = {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Поиск свежей версии на GitHub...")
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateStatus.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("У вас последняя версия") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Установлена актуальная версия приложения RDM.")
                        Text(
                            text = "Версия: ${BuildConfig.VERSION_NAME} (Сборка ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("Отлично")
                    }
                }
            )
        }

        is UpdateStatus.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                },
                title = {
                    Text(
                        text = "Доступно обновление v${status.newVersion}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Текущая: v${BuildConfig.VERSION_NAME} → Новая: v${status.newVersion}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            Text(
                                text = status.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }

                        Text(
                            text = "Обновление установится поверх текущей версии с сохранением всех серверов и настроек.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDownloadAndInstall(status.downloadUrl, status.newVersion)
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Обновить сейчас")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(status.htmlUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                onDismiss()
                            }
                        ) {
                            Text("GitHub")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Позже")
                        }
                    }
                }
            )
        }

        is UpdateStatus.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Загрузка обновления...") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { status.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Загрузка APK с GitHub...")
                            Text("${status.progressPercent}%", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateStatus.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("Обновление готово к установке") },
                text = {
                    Text("APK успешно скачан. Нажмите кнопку ниже для запуска установки поверх.")
                },
                confirmButton = {
                    Button(onClick = onInstallLocalFile) {
                        Text("Установить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }
            )
        }

        is UpdateStatus.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("Ошибка обновления") },
                text = {
                    Text(status.message)
                },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("Понятно")
                    }
                }
            )
        }

        is UpdateStatus.Idle -> {
            // Nothing to show
        }
    }
}
