package com.rdm.remote.desktop.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import com.rdm.remote.desktop.manager.ui.theme.TagColors

@Composable
fun ServerEditDialog(
    server: ServerEntity,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        ip: String,
        port: Int,
        domain: String,
        login: String,
        password: String,
        notes: String,
        resolution: String,
        soundRedirection: Int,
        adminSession: Boolean,
        colorHex: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(server.name) }
    var ip by remember { mutableStateOf(server.ip) }
    var portText by remember { mutableStateOf(if (server.port > 0) server.port.toString() else "3389") }
    var domain by remember { mutableStateOf(server.domain) }
    var login by remember { mutableStateOf(server.login) }
    var password by remember { mutableStateOf(server.password) }
    var notes by remember { mutableStateOf(server.notes) }
    var resolution by remember { mutableStateOf(server.resolution.ifBlank { "1920x1080" }) }
    var soundRedirection by remember { mutableIntStateOf(server.soundRedirection) }
    var adminSession by remember { mutableStateOf(server.adminSession) }
    var selectedColor by remember { mutableStateOf(server.colorHex.ifBlank { "#0078D7" }) }

    var passwordVisible by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf(false) }
    var ipError by remember { mutableStateOf(false) }

    val resolutions = listOf("1920x1080", "1680x1050", "1440x900", "1366x768", "1280x720", "1024x768")
    val soundOptions = listOf("Воспроизводить на телефоне", "Оставить на сервере", "Не воспроизводить")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (server.id == 0L) "Добавить сервер" else "Редактировать сервер",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Server Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text("Название сервера *") },
                    placeholder = { Text("например: DC-01, SQL-Server, RDS") },
                    isError = nameError,
                    supportingText = { if (nameError) Text("Название обязательно") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 2. IP / Host and Port Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = {
                            ip = it
                            ipError = it.isBlank()
                        },
                        label = { Text("IP / Хост *") },
                        placeholder = { Text("192.168.1.100") },
                        isError = ipError,
                        supportingText = { if (ipError) Text("Укажите IP или хост") },
                        singleLine = true,
                        modifier = Modifier.weight(0.68f)
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Порт") },
                        placeholder = { Text("3389") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.32f)
                    )
                }

                // 3. Domain
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Домен (необязательно)") },
                    placeholder = { Text("CORP, WORKGROUP...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. Login
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин / Пользователь") },
                    placeholder = { Text("Administrator, user1...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    placeholder = { Text("Пароль для входа") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 6. Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметки") },
                    placeholder = { Text("Описание сервера, роли, регламент...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Color Selection Row
                Text(
                    text = "Цветовая метка:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(TagColors) { colorHex ->
                        val color = Color(android.graphics.Color.parseColor(colorHex))
                        val isSelected = colorHex.equals(selectedColor, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    } else Modifier
                                )
                                .clickable { selectedColor = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                // Advanced Settings Expander
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(if (showAdvanced) "▲ Скрыть доп. параметры" else "▼ Дополнительные параметры (RDP)")
                }

                if (showAdvanced) {
                    // Admin session toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { adminSession = !adminSession }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = adminSession,
                            onCheckedChange = { adminSession = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Сессия администратора (/admin)", style = MaterialTheme.typography.bodyMedium)
                            Text("Подключение к консоли сервера", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Resolution selector
                    Text("Разрешение:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(resolutions) { res ->
                            FilterChip(
                                selected = resolution == res,
                                onClick = { resolution = res },
                                label = { Text(res, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    // Sound Redirection
                    Text("Звук:", style = MaterialTheme.typography.labelMedium)
                    soundOptions.forEachIndexed { index, title ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { soundRedirection = index }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = soundRedirection == index,
                                onClick = { soundRedirection = index }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(title, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 3389
                    var hasError = false
                    if (name.isBlank()) {
                        nameError = true
                        hasError = true
                    }
                    if (ip.isBlank()) {
                        ipError = true
                        hasError = true
                    }
                    if (!hasError) {
                        onSave(
                            name,
                            ip,
                            port,
                            domain,
                            login,
                            password,
                            notes,
                            resolution,
                            soundRedirection,
                            adminSession,
                            selectedColor
                        )
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
