package com.rdm.remote.desktop.manager.rdp

import android.graphics.Bitmap

enum class RdpConnectionStatus {
    DISCONNECTED,
    CONNECTING_TCP,
    TPKT_NEGOTIATING,
    SSL_CREDSSP_HANDSHAKE,
    AUTHENTICATING,
    ESTABLISHING_SESSION,
    CONNECTED,
    RECONNECTING,
    ERROR
}

enum class MouseInputMode {
    DIRECT_TOUCH, // Tap on screen clicks at exact coordinates
    TRACKPAD      // Screen acts as a laptop touchpad moving a mouse cursor
}

data class RdpSessionState(
    val status: RdpConnectionStatus = RdpConnectionStatus.DISCONNECTED,
    val statusMessage: String = "Отключено",
    val latencyMs: Long = 0,
    val fps: Int = 30,
    val serverName: String = "",
    val serverAddress: String = "",
    val desktopWidth: Int = 1920,
    val desktopHeight: Int = 1080,
    val cursorX: Float = 960f,
    val cursorY: Float = 540f,
    val mouseMode: MouseInputMode = MouseInputMode.TRACKPAD,
    val zoomScale: Float = 1.0f,
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val isCtrlActive: Boolean = false,
    val isAltActive: Boolean = false,
    val isShiftActive: Boolean = false,
    val isWinActive: Boolean = false,
    val isKeyboardVisible: Boolean = false,
    val isToolbarVisible: Boolean = true,
    val securityProtocol: String = "CredSSP / TLS 1.3 (NLA)",
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val bytesTransferred: Long = 0,
    val frameBitmap: Bitmap? = null
)
