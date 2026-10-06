package com.rdm.remote.desktop.manager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import com.rdm.remote.desktop.manager.rdp.MouseInputMode
import com.rdm.remote.desktop.manager.rdp.RdpConnectionStatus
import com.rdm.remote.desktop.manager.rdp.RdpEngine
import com.rdm.remote.desktop.manager.ui.theme.ColorOnline
import kotlinx.coroutines.delay

@Composable
fun RdpSessionScreen(
    server: ServerEntity,
    onDisconnect: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val rdpEngine = remember(server.id) { RdpEngine(server, scope) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(server.id) {
        rdpEngine.startSession()
    }

    DisposableEffect(Unit) {
        onDispose {
            rdpEngine.disconnect()
        }
    }

    val state by rdpEngine.sessionState.collectAsState()
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSendTextDialog by remember { mutableStateOf(false) }
    var sendTextInput by remember { mutableStateOf("") }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3.5f)
        if (scale > 1f) {
            val maxOffsetX = (containerSize.width * (scale - 1f)) / 2f
            val maxOffsetY = (containerSize.height * (scale - 1f)) / 2f
            panOffset = Offset(
                x = (panOffset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (panOffset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            panOffset = Offset.Zero
        }
    }

    BackHandler {
        showDisconnectDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
    ) {
        // 1. Desktop Canvas & Frame Rendering
        if (state.frameBitmap != null) {
            val bitmap = state.frameBitmap!!

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = panOffset.x,
                        translationY = panOffset.y
                    )
                    .transformable(state = transformState)
                    .pointerInput(state.mouseMode, scale) {
                        if (state.mouseMode == MouseInputMode.TRACKPAD) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                rdpEngine.moveCursor(
                                    (dragAmount.x / scale) * 1.6f,
                                    (dragAmount.y / scale) * 1.6f
                                )
                            }
                        } else {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    if (containerSize.width > 0 && containerSize.height > 0) {
                                        val mappedX = (tapOffset.x / containerSize.width.toFloat()) * state.desktopWidth
                                        val mappedY = (tapOffset.y / containerSize.height.toFloat()) * state.desktopHeight
                                        rdpEngine.setCursorPosition(mappedX, mappedY)
                                        rdpEngine.handleLeftClick()
                                    }
                                },
                                onLongPress = { tapOffset ->
                                    if (containerSize.width > 0 && containerSize.height > 0) {
                                        val mappedX = (tapOffset.x / containerSize.width.toFloat()) * state.desktopWidth
                                        val mappedY = (tapOffset.y / containerSize.height.toFloat()) * state.desktopHeight
                                        rdpEngine.setCursorPosition(mappedX, mappedY)
                                        rdpEngine.handleRightClick()
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(state.mouseMode) {
                        if (state.mouseMode == MouseInputMode.TRACKPAD) {
                            detectTapGestures(
                                onTap = {
                                    rdpEngine.handleLeftClick()
                                },
                                onDoubleTap = {
                                    rdpEngine.handleLeftClick()
                                    rdpEngine.handleLeftClick()
                                },
                                onLongPress = {
                                    rdpEngine.handleRightClick()
                                }
                            )
                        }
                    }
            ) {
                // Desktop Frame Image
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "RDP Remote Desktop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Mouse Pointer Cursor Overlay (in Trackpad Mode)
                if (state.mouseMode == MouseInputMode.TRACKPAD && containerSize.width > 0 && containerSize.height > 0) {
                    val scaleX = containerSize.width.toFloat() / state.desktopWidth.toFloat()
                    val scaleY = containerSize.height.toFloat() / state.desktopHeight.toFloat()
                    val actualScale = minOf(scaleX, scaleY)

                    val offsetX = (containerSize.width - (state.desktopWidth * actualScale)) / 2f
                    val offsetY = (containerSize.height - (state.desktopHeight * actualScale)) / 2f

                    val screenCursorX = offsetX + (state.cursorX * actualScale)
                    val screenCursorY = offsetY + (state.cursorY * actualScale)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawMousePointer(screenCursorX, screenCursorY)
                    }
                }
            }
        }

        // 2. Connecting State Overlay
        if (state.status != RdpConnectionStatus.CONNECTED && state.status != RdpConnectionStatus.DISCONNECTED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Подключение к ${server.name}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${server.formattedAddress()} (${server.formattedUsername()})",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 3. Top Floating Toolbar
        AnimatedVisibility(
            visible = state.isToolbarVisible,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Disconnect / Back Button
                    IconButton(onClick = { showDisconnectDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    // Server Title & Status
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showInfoDialog = true }
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.status == RdpConnectionStatus.CONNECTED) ColorOnline
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${server.formattedAddress()} • ${state.latencyMs} мс • ${state.fps} FPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 1. Send Ctrl+Alt+Del
                    IconButton(onClick = { rdpEngine.sendSpecialKey("Ctrl+Alt+Del") }) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Ctrl+Alt+Del",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 2. Mouse Mode Toggle (Trackpad vs Direct Touch)
                    IconButton(onClick = { rdpEngine.toggleMouseMode() }) {
                        Icon(
                            imageVector = if (state.mouseMode == MouseInputMode.TRACKPAD) Icons.Default.Mouse else Icons.Default.TouchApp,
                            contentDescription = "Mouse Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 3. Send Text / Paste modal
                    IconButton(onClick = { showSendTextDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Send Text",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 4. Virtual Keyboard / Special Keys toggle
                    IconButton(onClick = { rdpEngine.toggleKeyboard() }) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Keyboard",
                            tint = if (state.isKeyboardVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 5. Session Info
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Toolbar expand/collapse handle button (when toolbar hidden)
        if (!state.isToolbarVisible) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(CircleShape)
                    .clickable { rdpEngine.toggleToolbar() }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Show Toolbar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }

        // 4. Virtual Keyboard & Function Keys Dock (Bottom)
        AnimatedVisibility(
            visible = state.isKeyboardVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick Terminal Commands Row
                    val quickCommands = listOf("ipconfig", "whoami", "ping", "dir", "systeminfo", "cls")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickCommands) { cmd ->
                            SuggestionChip(
                                onClick = { rdpEngine.sendSpecialKey(cmd) },
                                label = {
                                    Text(
                                        text = cmd,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            )
                        }
                    }

                    // Modifier & System Keys Row
                    val modifierKeys = listOf("Ctrl+Alt+Del", "Win", "Esc", "Tab", "Ctrl", "Alt", "Shift", "Alt+Tab", "Enter", "Backspace")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(modifierKeys) { key ->
                            val isActive = when (key) {
                                "Ctrl" -> state.isCtrlActive
                                "Alt" -> state.isAltActive
                                "Shift" -> state.isShiftActive
                                else -> false
                            }
                            FilterChip(
                                selected = isActive,
                                onClick = { rdpEngine.sendSpecialKey(key) },
                                label = {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            )
                        }
                    }

                    // Function keys Row: F1-F12
                    val fKeys = listOf("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(fKeys) { fKey ->
                            SuggestionChip(
                                onClick = { rdpEngine.sendSpecialKey(fKey) },
                                label = { Text(fKey, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // 5. On-Screen Floating Mouse Buttons in Trackpad mode
        if (state.mouseMode == MouseInputMode.TRACKPAD) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (state.isKeyboardVisible) 130.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Reset Zoom button if zoomed
                if (scale > 1f) {
                    FloatingActionButton(
                        onClick = {
                            scale = 1f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier.size(52.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom")
                    }
                }

                // Left Click Button
                FloatingActionButton(
                    onClick = { rdpEngine.handleLeftClick() },
                    modifier = Modifier.size(52.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Text("L", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                // Right Click Button
                FloatingActionButton(
                    onClick = { rdpEngine.handleRightClick() },
                    modifier = Modifier.size(52.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = CircleShape
                ) {
                    Text("R", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }

    // Send Text / Paste modal
    if (showSendTextDialog) {
        AlertDialog(
            onDismissRequest = { showSendTextDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Отправить текст в сессию")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Введите текст или команду для отправки в активное окно Windows / PowerShell:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = sendTextInput,
                        onValueChange = { sendTextInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Например: Get-Process, ipconfig...") },
                        singleLine = false,
                        maxLines = 3,
                        trailingIcon = {
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrEmpty()) {
                                    sendTextInput = clip
                                }
                            }) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sendTextInput.isNotBlank()) {
                            rdpEngine.sendText(sendTextInput)
                            rdpEngine.sendSpecialKey("Enter")
                            sendTextInput = ""
                        }
                        showSendTextDialog = false
                    }
                ) {
                    Text("Отправить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendTextDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Завершить RDP сессию?") },
            text = { Text("Вы действительно хотите отключиться от сервера «${server.name}» (${server.formattedAddress()})?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        rdpEngine.disconnect()
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Отключиться")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Остаться")
                }
            }
        )
    }

    // Session Info Modal
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Параметры RDP сессии")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Сервер", server.name)
                    InfoRow("Адрес", server.formattedAddress())
                    InfoRow("Пользователь", server.formattedUsername())
                    InfoRow("Разрешение", "${state.desktopWidth} x ${state.desktopHeight}")
                    InfoRow("Протокол", state.securityProtocol)
                    InfoRow("Задержка (Ping)", "${state.latencyMs} мс")
                    InfoRow("Кадров/сек", "${state.fps} FPS")
                    InfoRow("Передано байт", "${state.bytesTransferred / 1024} КБ")
                    InfoRow("Режим мыши", if (state.mouseMode == MouseInputMode.TRACKPAD) "Тачпад со стрелкой" else "Прямое касание")
                }
            },
            confirmButton = {
                Button(onClick = { showInfoDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMousePointer(x: Float, y: Float) {
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x, y + 22f)
        lineTo(x + 6f, y + 17f)
        lineTo(x + 11f, y + 26f)
        lineTo(x + 15f, y + 24f)
        lineTo(x + 10f, y + 15f)
        lineTo(x + 18f, y + 15f)
        close()
    }

    drawPath(
        path = path,
        color = Color.Black,
        style = Stroke(width = 3f)
    )
    drawPath(
        path = path,
        color = Color.White,
        style = Fill
    )
}
