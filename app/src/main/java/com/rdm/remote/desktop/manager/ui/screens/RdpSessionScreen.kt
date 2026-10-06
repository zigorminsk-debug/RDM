package com.rdm.remote.desktop.manager.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import com.rdm.remote.desktop.manager.rdp.MouseInputMode
import com.rdm.remote.desktop.manager.rdp.RdpConnectionStatus
import com.rdm.remote.desktop.manager.rdp.RdpEngine
import com.rdm.remote.desktop.manager.ui.theme.ColorOnline
import com.rdm.remote.desktop.manager.utils.RdpLauncher

@Composable
fun RdpSessionScreen(
    server: ServerEntity,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rdpEngine = remember(server.id) { RdpEngine(server, scope) }
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textFocusRequester = remember { FocusRequester() }

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
    var directTypeInput by remember { mutableStateOf("") }
    var isDirectTypingActive by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Standalone hardware cursor position decoupled from bitmap state (ZERO screen flickering)
    var cursorPosition by remember { mutableStateOf(Offset(960f, 540f)) }

    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

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
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = panOffsetX,
                        translationY = panOffsetY
                    )
                    .pointerInput(state.mouseMode, zoomScale, panOffsetX, panOffsetY) {
                        if (state.mouseMode == MouseInputMode.TRACKPAD) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newX = (cursorPosition.x + (dragAmount.x / zoomScale) * 1.5f)
                                        .coerceIn(0f, state.desktopWidth.toFloat())
                                    val newY = (cursorPosition.y + (dragAmount.y / zoomScale) * 1.5f)
                                        .coerceIn(0f, state.desktopHeight.toFloat())
                                    cursorPosition = Offset(newX, newY)
                                }
                            )
                        } else {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (zoomScale > 1f) {
                                        panOffsetX += dragAmount.x
                                        panOffsetY += dragAmount.y
                                    } else {
                                        if (containerSize.width > 0 && containerSize.height > 0) {
                                            val mappedX = (change.position.x / containerSize.width.toFloat()) * state.desktopWidth
                                            val mappedY = (change.position.y / containerSize.height.toFloat()) * state.desktopHeight
                                            cursorPosition = Offset(
                                                mappedX.coerceIn(0f, state.desktopWidth.toFloat()),
                                                mappedY.coerceIn(0f, state.desktopHeight.toFloat())
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(state.mouseMode, zoomScale) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                if (state.mouseMode == MouseInputMode.DIRECT_TOUCH) {
                                    if (containerSize.width > 0 && containerSize.height > 0) {
                                        val mappedX = (tapOffset.x / containerSize.width.toFloat()) * state.desktopWidth
                                        val mappedY = (tapOffset.y / containerSize.height.toFloat()) * state.desktopHeight
                                        val clampedX = mappedX.coerceIn(0f, state.desktopWidth.toFloat())
                                        val clampedY = mappedY.coerceIn(0f, state.desktopHeight.toFloat())
                                        cursorPosition = Offset(clampedX, clampedY)
                                        rdpEngine.handleLeftClick(clampedX, clampedY)
                                    }
                                } else {
                                    rdpEngine.handleLeftClick(cursorPosition.x, cursorPosition.y)
                                }
                            },
                            onDoubleTap = { tapOffset ->
                                if (state.mouseMode == MouseInputMode.DIRECT_TOUCH) {
                                    if (containerSize.width > 0 && containerSize.height > 0) {
                                        val mappedX = (tapOffset.x / containerSize.width.toFloat()) * state.desktopWidth
                                        val mappedY = (tapOffset.y / containerSize.height.toFloat()) * state.desktopHeight
                                        val clampedX = mappedX.coerceIn(0f, state.desktopWidth.toFloat())
                                        val clampedY = mappedY.coerceIn(0f, state.desktopHeight.toFloat())
                                        cursorPosition = Offset(clampedX, clampedY)
                                        rdpEngine.handleDoubleClick(clampedX, clampedY)
                                    }
                                } else {
                                    rdpEngine.handleDoubleClick(cursorPosition.x, cursorPosition.y)
                                }
                            },
                            onLongPress = { tapOffset ->
                                if (state.mouseMode == MouseInputMode.DIRECT_TOUCH) {
                                    if (containerSize.width > 0 && containerSize.height > 0) {
                                        val mappedX = (tapOffset.x / containerSize.width.toFloat()) * state.desktopWidth
                                        val mappedY = (tapOffset.y / containerSize.height.toFloat()) * state.desktopHeight
                                        val clampedX = mappedX.coerceIn(0f, state.desktopWidth.toFloat())
                                        val clampedY = mappedY.coerceIn(0f, state.desktopHeight.toFloat())
                                        cursorPosition = Offset(clampedX, clampedY)
                                        rdpEngine.handleRightClick(clampedX, clampedY)
                                    }
                                } else {
                                    rdpEngine.handleRightClick(cursorPosition.x, cursorPosition.y)
                                }
                            }
                        )
                    }
            ) {
                // Desktop Frame Image (Static texture - does NOT recreate on cursor move)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "RDP Remote Desktop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Mouse Pointer Cursor Overlay (Smooth hardware canvas overlay)
                if (state.mouseMode == MouseInputMode.TRACKPAD && containerSize.width > 0 && containerSize.height > 0) {
                    val scaleX = containerSize.width.toFloat() / state.desktopWidth.toFloat()
                    val scaleY = containerSize.height.toFloat() / state.desktopHeight.toFloat()
                    val actualScale = minOf(scaleX, scaleY)

                    val offsetX = (containerSize.width - (state.desktopWidth * actualScale)) / 2f
                    val offsetY = (containerSize.height - (state.desktopHeight * actualScale)) / 2f

                    val screenCursorX = offsetX + (cursorPosition.x * actualScale)
                    val screenCursorY = offsetY + (cursorPosition.y * actualScale)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawMousePointer(screenCursorX, screenCursorY)
                    }
                }
            }
        }

        // 2. Connecting State Overlay
        if (state.status != RdpConnectionStatus.CONNECTED && state.status != RdpConnectionStatus.DISCONNECTED && state.status != RdpConnectionStatus.ERROR) {
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

        // 3. Error Dialog / Unreachable Server Overlay
        if (state.status == RdpConnectionStatus.ERROR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Ошибка подключения",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Text(
                            text = state.errorMessage ?: "Не удалось установить RDP соединение с ${server.formattedAddress()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        HorizontalDivider()

                        // Action 1: Open in MS Remote Desktop
                        Button(
                            onClick = {
                                val launched = RdpLauncher.launchRdpFile(context, server, RdpLauncher.PKG_MS_RDC_1)
                                if (!launched) {
                                    val uriLaunched = RdpLauncher.launchRdpUri(context, server)
                                    if (!uriLaunched) {
                                        RdpLauncher.launchRdpFile(context, server)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0078D7))
                        ) {
                            Icon(Icons.Default.Launch, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Открыть в MS Remote Desktop")
                        }

                        // Action 2: Start Interactive Desktop Demo Session
                        OutlinedButton(
                            onClick = { rdpEngine.startDemoSession() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Интерактивный тест элементов (Демо)")
                        }

                        // Action 3: Retry or Disconnect
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Назад")
                            }
                            Button(
                                onClick = { rdpEngine.startSession() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Повторить")
                            }
                        }
                    }
                }
            }
        }

        // 4. Top Floating Toolbar
        AnimatedVisibility(
            visible = state.isToolbarVisible,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
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
                        .padding(horizontal = 6.dp, vertical = 4.dp),
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
                                text = server.name + if (state.isDemoMode) " (Демо)" else "",
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

                    // Quick Launch in official MS Remote Desktop
                    IconButton(onClick = {
                        val launched = RdpLauncher.launchRdpFile(context, server, RdpLauncher.PKG_MS_RDC_1)
                        if (!launched) {
                            val uriLaunched = RdpLauncher.launchRdpUri(context, server)
                            if (!uriLaunched) {
                                RdpLauncher.launchRdpFile(context, server)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open in MS Remote Desktop",
                            tint = Color(0xFF0078D7)
                        )
                    }

                    // Paste Clipboard button
                    IconButton(onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            rdpEngine.injectClipboard(clip)
                            Toast.makeText(context, "Вставлен буфер: $clip", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = "Paste Clipboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Mouse Mode Toggle (Trackpad vs Direct Touch)
                    IconButton(onClick = {
                        rdpEngine.toggleMouseMode()
                        val modeName = if (state.mouseMode == MouseInputMode.TRACKPAD) "Прямое касание" else "Тачпад со стрелкой"
                        Toast.makeText(context, "Режим: $modeName", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = if (state.mouseMode == MouseInputMode.TRACKPAD) Icons.Default.Mouse else Icons.Default.TouchApp,
                            contentDescription = "Mouse Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Send Text / Custom Command
                    IconButton(onClick = { showSendTextDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Send Text",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Virtual Keyboard / Special Keys toggle
                    IconButton(onClick = {
                        rdpEngine.toggleKeyboard()
                        isDirectTypingActive = !isDirectTypingActive
                    }) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Keyboard",
                            tint = if (state.isKeyboardVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Pull handle when toolbar hidden
        if (!state.isToolbarVisible) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
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

        // 5. Direct Keyboard Input Bar (When keyboard active)
        if (isDirectTypingActive) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (state.isToolbarVisible) 64.dp else 12.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = directTypeInput,
                        onValueChange = { directTypeInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(textFocusRequester),
                        placeholder = { Text("Печатайте здесь для ввода в сессию...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (directTypeInput.isNotBlank()) {
                                    rdpEngine.sendText(directTypeInput)
                                    rdpEngine.sendSpecialKey("Enter")
                                    directTypeInput = ""
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (directTypeInput.isNotBlank()) {
                                rdpEngine.sendText(directTypeInput)
                                rdpEngine.sendSpecialKey("Enter")
                                directTypeInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            LaunchedEffect(Unit) {
                textFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }

        // 6. Virtual Keyboard & Function Keys Dock (Bottom)
        AnimatedVisibility(
            visible = state.isKeyboardVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 6.dp, start = 6.dp, end = 6.dp)
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
                                onClick = {
                                    rdpEngine.sendSpecialKey(cmd)
                                    Toast.makeText(context, "Команда: $cmd", Toast.LENGTH_SHORT).show()
                                },
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
                                onClick = {
                                    rdpEngine.sendSpecialKey(key)
                                },
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

        // 7. On-Screen Floating Mouse & Zoom Control Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = if (state.isKeyboardVisible) 140.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zoom In Button
            FloatingActionButton(
                onClick = {
                    zoomScale = (zoomScale + 0.5f).coerceAtMost(3.5f)
                },
                modifier = Modifier.size(46.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
            }

            // Zoom Out / Reset Button
            if (zoomScale > 1f) {
                FloatingActionButton(
                    onClick = {
                        zoomScale = 1f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    },
                    modifier = Modifier.size(46.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom", modifier = Modifier.size(20.dp))
                }
            }

            // Left Click Button (L)
            FloatingActionButton(
                onClick = { rdpEngine.handleLeftClick(cursorPosition.x, cursorPosition.y) },
                modifier = Modifier.size(54.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Text("L", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            // Double Click Button (2x)
            FloatingActionButton(
                onClick = { rdpEngine.handleDoubleClick(cursorPosition.x, cursorPosition.y) },
                modifier = Modifier.size(46.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape
            ) {
                Text("2x", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // Right Click Button (R)
            FloatingActionButton(
                onClick = { rdpEngine.handleRightClick(cursorPosition.x, cursorPosition.y) },
                modifier = Modifier.size(54.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape
            ) {
                Text("R", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
    }

    // Send Text Modal
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
                        text = "Введите текст, пароль или команду для вставки в Windows / PowerShell:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = sendTextInput,
                        onValueChange = { sendTextInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Например: ipconfig, whoami, Get-Process...") },
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
