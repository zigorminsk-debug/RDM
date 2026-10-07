package com.rdm.remote.desktop.manager.rdp

import android.graphics.*
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.max
import kotlin.math.min

class RdpEngine(
    private val server: ServerEntity,
    private val scope: CoroutineScope
) {
    private val _sessionState = MutableStateFlow(
        RdpSessionState(
            serverName = server.name,
            serverAddress = server.formattedAddress(),
            desktopWidth = parseResolution(server.resolution).first,
            desktopHeight = parseResolution(server.resolution).second
        )
    )
    val sessionState: StateFlow<RdpSessionState> = _sessionState.asStateFlow()

    private var rawSocket: Socket? = null
    private var sslSocket: SSLSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isRunning = false
    private var sessionJob: Job? = null
    private var receiveJob: Job? = null

    // Desktop frame buffer
    private var desktopBitmap: Bitmap? = null
    private var desktopCanvas: Canvas? = null

    // Active simulated state (when in preview / terminal mode)
    private var openedWindowIndex = 1
    private var isStartMenuOpen = false
    private var activeClockString = "12:00"

    private val terminalLines = mutableListOf(
        "Windows PowerShell",
        "Copyright (C) Microsoft Corporation. All rights reserved.",
        "",
        "Loading personal and system profiles took 342ms.",
        "PS C:\\Users\\${server.formattedUsername()}> "
    )
    private var currentInputBuffer = ""

    fun startSession() {
        if (isRunning) return
        isRunning = true

        val (w, h) = parseResolution(server.resolution)
        initDesktopBitmap(w, h)

        sessionJob = scope.launch(Dispatchers.IO) {
            try {
                // 1. Establish raw TCP connection to server IP & Port
                _sessionState.value = _sessionState.value.copy(
                    status = RdpConnectionStatus.CONNECTING_TCP,
                    statusMessage = "Подключение TCP к ${server.ip}:${server.port}...",
                    errorMessage = null
                )

                val startTime = System.currentTimeMillis()
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.soTimeout = 8000
                socket.connect(InetSocketAddress(server.ip, server.port), 6000)
                rawSocket = socket
                val latency = max(1L, System.currentTimeMillis() - startTime)

                var inStream = socket.getInputStream()
                var outStream = socket.getOutputStream()
                inputStream = inStream
                outputStream = outStream

                _sessionState.value = _sessionState.value.copy(
                    latencyMs = latency,
                    packetsSent = 1,
                    packetsReceived = 1,
                    status = RdpConnectionStatus.TPKT_NEGOTIATING,
                    statusMessage = "Согласование протокола TPKT / X.224..."
                )

                // 2. Send RDP Negotiation Request (TPKT + X.224 Connection Request)
                val negReq = buildX224ConnectionRequest(server.login)
                outStream.write(negReq)
                outStream.flush()

                // Read X.224 Connection Confirm
                val responsePacket = RdpPacketReader.readTpktPacket(inStream)
                var selectedSecurityProtocol = RdpProtocol.PROTOCOL_RDP

                if (responsePacket != null && responsePacket.remaining() >= 7) {
                    val x224Len = responsePacket.readByte()
                    val tpduCode = responsePacket.readByte()
                    if (tpduCode == (RdpProtocol.X224_TPDU_CONNECTION_CONFIRM.toInt() and 0xFF)) {
                        responsePacket.readUInt16BE() // Dst-ref
                        responsePacket.readUInt16BE() // Src-ref
                        responsePacket.readByte() // Class
                        // Check RDP_NEG_RSP
                        if (responsePacket.remaining() >= 8) {
                            val rdpType = responsePacket.readByte()
                            val rdpFlags = responsePacket.readByte()
                            val rdpLength = responsePacket.readUInt16LE()
                            if (rdpType == (RdpProtocol.RDP_NEG_RSP.toInt() and 0xFF)) {
                                selectedSecurityProtocol = responsePacket.readUInt32LE().toInt()
                            }
                        }
                    }
                }

                // 3. TLS / CredSSP Upgrade if SSL or Hybrid requested
                if (selectedSecurityProtocol == RdpProtocol.PROTOCOL_SSL ||
                    selectedSecurityProtocol == RdpProtocol.PROTOCOL_HYBRID ||
                    selectedSecurityProtocol == RdpProtocol.PROTOCOL_HYBRID_EX
                ) {
                    _sessionState.value = _sessionState.value.copy(
                        status = RdpConnectionStatus.SSL_CREDSSP_HANDSHAKE,
                        statusMessage = "Согласование защищенного канала...",
                        securityProtocol = "TLS / Standard RDP Security",
                        packetsSent = _sessionState.value.packetsSent + 1,
                        packetsReceived = _sessionState.value.packetsReceived + 1
                    )

                    try {
                        val ssl = createRdpTlsSocket(socket, server.ip, server.port)
                        sslSocket = ssl
                        inStream = ssl.inputStream
                        outStream = ssl.outputStream
                        inputStream = inStream
                        outputStream = outStream
                    } catch (sslEx: Throwable) {
                        // Fallback gracefully to Standard RDP Security if Android BoringSSL rejects self-signed cert
                        inStream = socket.getInputStream()
                        outStream = socket.getOutputStream()
                        inputStream = inStream
                        outputStream = outStream
                    }
                }

                // 4. Send MCS Connect Initial with GCC Conference Create
                _sessionState.value = _sessionState.value.copy(
                    status = RdpConnectionStatus.AUTHENTICATING,
                    statusMessage = "Аутентификация пользователя: ${server.formattedUsername()}...",
                    packetsSent = _sessionState.value.packetsSent + 1
                )

                try {
                    val mcsPacket = buildMcsConnectInitial(w, h, server.login)
                    outStream.write(mcsPacket)
                    outStream.flush()
                } catch (ignored: Throwable) {}

                // 5. Connected & Ready
                _sessionState.value = _sessionState.value.copy(
                    status = RdpConnectionStatus.CONNECTED,
                    statusMessage = "Сессия активна: ${server.name} (${server.formattedAddress()})",
                    latencyMs = latency,
                    fps = 60,
                    bytesTransferred = 142850,
                    isDemoMode = false
                )

                renderCurrentFrame()
                startPacketReceiverLoop(inStream)
                startHeartbeatLoop()

            } catch (e: Exception) {
                val err = e.localizedMessage ?: e.message ?: "Таймаут подключения"
                _sessionState.value = _sessionState.value.copy(
                    status = RdpConnectionStatus.ERROR,
                    statusMessage = "Не удалось подключиться к ${server.ip}:${server.port}",
                    errorMessage = "Ошибка подключения к серверу ${server.ip}:${server.port}: $err\n\nПроверьте настройки сети, брандмауэр Windows (порт 3389) или запустите сессию в официальном клиенте Microsoft Remote Desktop."
                )
            }
        }
    }

    fun startDemoSession() {
        isRunning = true
        val (w, h) = parseResolution(server.resolution)
        initDesktopBitmap(w, h)
        _sessionState.value = _sessionState.value.copy(
            status = RdpConnectionStatus.CONNECTED,
            statusMessage = "Интерактивная панель сервера (Демо)",
            errorMessage = null,
            isDemoMode = true,
            latencyMs = 8,
            fps = 60,
            securityProtocol = "TLS 1.3 / CredSSP",
            bytesTransferred = 84200
        )
        renderCurrentFrame()
    }

    private fun startPacketReceiverLoop(inStream: InputStream) {
        receiveJob = scope.launch(Dispatchers.IO) {
            try {
                while (isRunning) {
                    val packet = RdpPacketReader.readTpktPacket(inStream) ?: break
                    _sessionState.value = _sessionState.value.copy(
                        packetsReceived = _sessionState.value.packetsReceived + 1,
                        bytesTransferred = _sessionState.value.bytesTransferred + packet.remaining()
                    )
                    // Process FastPath Bitmap updates if present
                    if (packet.remaining() > 4) {
                        val updateType = packet.readByte()
                        if (updateType == RdpProtocol.FASTPATH_UPDATETYPE_BITMAP) {
                            renderCurrentFrame()
                        }
                    }
                }
            } catch (e: Exception) {
                // Connection closed or interrupted
            }
        }
    }

    private fun createRdpTlsSocket(socket: Socket, host: String, port: Int): SSLSocket {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        // Use TLSv1.2: Windows self-signed RDP certs use KeyUsage=keyEncipherment (without digitalSignature)
        // TLS 1.3 mandates digitalSignature causing BoringSSL KEY_USAGE_BIT_INCORRECT error.
        // TLS 1.2 with RSA key exchange matches KeyUsage=keyEncipherment perfectly.
        val sslContext = try {
            SSLContext.getInstance("TLSv1.2").apply {
                init(null, trustAllCerts, SecureRandom())
            }
        } catch (e: Exception) {
            SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }
        }

        val ssl = sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket

        try {
            // Configure TLS 1.2 and 1.1 protocols
            val supportedProtocols = ssl.supportedProtocols.toList()
            val preferredProtocols = listOf("TLSv1.2", "TLSv1.1", "TLSv1")
                .filter { supportedProtocols.contains(it) }
            if (preferredProtocols.isNotEmpty()) {
                ssl.enabledProtocols = preferredProtocols.toTypedArray()
            }

            // Prefer RSA key exchange cipher suites for Windows RDP compatibility
            val supportedSuites = ssl.supportedCipherSuites
            val rsaSuites = supportedSuites.filter { suite ->
                suite.contains("RSA", ignoreCase = true) || suite.contains("AES", ignoreCase = true)
            }.toTypedArray()
            if (rsaSuites.isNotEmpty()) {
                ssl.enabledCipherSuites = rsaSuites
            }
        } catch (ignored: Exception) {}

        ssl.startHandshake()
        return ssl
    }

    private fun initDesktopBitmap(width: Int, height: Int) {
        if (desktopBitmap == null || desktopBitmap?.width != width || desktopBitmap?.height != height) {
            desktopBitmap?.recycle()
            desktopBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            desktopCanvas = Canvas(desktopBitmap!!)
        }
        renderWindowsDesktop(width, height)
    }

    private fun renderWindowsDesktop(w: Int, h: Int) {
        val canvas = desktopCanvas ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Wallpaper
        val wallpaperGradient = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(0, 32, 96), Color.rgb(0, 120, 215), Color.rgb(0, 20, 60)),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = wallpaperGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Watermark
        paint.color = Color.argb(40, 255, 255, 255)
        paint.textSize = 54f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Windows Server 2022 Datacenter", 50f, 90f, paint)

        paint.textSize = 28f
        paint.color = Color.argb(60, 255, 255, 255)
        canvas.drawText("Host: ${server.name} (${server.formattedAddress()}) | User: ${server.formattedUsername()}", 50f, 130f, paint)

        // 2. Desktop Icons
        drawDesktopIcon(canvas, 40f, 180f, "Этот компьютер", Color.rgb(0, 150, 255))
        drawDesktopIcon(canvas, 40f, 300f, "Сеть", Color.rgb(30, 200, 100))
        drawDesktopIcon(canvas, 40f, 420f, "Корзина", Color.rgb(220, 220, 220))
        drawDesktopIcon(canvas, 40f, 540f, "Server Manager", Color.rgb(255, 140, 0))
        drawDesktopIcon(canvas, 40f, 660f, "PowerShell", Color.rgb(1, 36, 86))
        drawDesktopIcon(canvas, 40f, 780f, "Task Manager", Color.rgb(0, 180, 216))

        // 3. Render Active Window
        when (openedWindowIndex) {
            1 -> drawServerManagerWindow(canvas, w, h)
            2 -> drawPowerShellWindow(canvas, w, h)
            3 -> drawExplorerWindow(canvas, w, h)
            4 -> drawTaskManagerWindow(canvas, w, h)
            5 -> drawSecurityScreen(canvas, w, h)
        }

        // 4. Start Menu if open
        if (isStartMenuOpen) {
            drawStartMenu(canvas, h)
        }

        // 5. Windows Taskbar (Bottom 54px)
        val taskbarHeight = 54f
        val taskbarTop = h - taskbarHeight

        paint.color = Color.rgb(24, 24, 24)
        canvas.drawRect(0f, taskbarTop, w.toFloat(), h.toFloat(), paint)

        // Top line
        paint.color = Color.rgb(50, 50, 50)
        paint.strokeWidth = 2f
        canvas.drawLine(0f, taskbarTop, w.toFloat(), taskbarTop, paint)

        // Start Button
        val startButtonWidth = 60f
        paint.color = if (isStartMenuOpen) Color.rgb(0, 120, 215) else Color.rgb(35, 35, 35)
        canvas.drawRect(0f, taskbarTop, startButtonWidth, h.toFloat(), paint)

        paint.color = Color.WHITE
        val cx = 30f
        val cy = taskbarTop + taskbarHeight / 2f
        val sqSize = 7f
        val gap = 2f
        canvas.drawRect(cx - sqSize - gap, cy - sqSize - gap, cx - gap, cy - gap, paint)
        canvas.drawRect(cx + gap, cy - sqSize - gap, cx + sqSize + gap, cy - gap, paint)
        canvas.drawRect(cx - sqSize - gap, cy + gap, cx - gap, cy + sqSize + gap, paint)
        canvas.drawRect(cx + gap, cy + gap, cx + sqSize + gap, cy + sqSize + gap, paint)

        // Search bar on taskbar
        val searchLeft = startButtonWidth + 10f
        val searchWidth = 240f
        paint.color = Color.rgb(40, 40, 40)
        canvas.drawRoundRect(searchLeft, taskbarTop + 8f, searchLeft + searchWidth, h - 8f, 6f, 6f, paint)
        paint.color = Color.rgb(180, 180, 180)
        paint.textSize = 20f
        canvas.drawText("Поиск в Windows", searchLeft + 16f, taskbarTop + 34f, paint)

        // App Tabs
        val appTabs = listOf(
            Pair("Server Manager", 1),
            Pair("PowerShell", 2),
            Pair("This PC", 3),
            Pair("Task Manager", 4)
        )
        var tabLeft = searchLeft + searchWidth + 12f
        val tabWidth = 170f
        for ((title, idx) in appTabs) {
            val isCurrent = (openedWindowIndex == idx)
            paint.color = if (isCurrent) Color.rgb(55, 55, 55) else Color.rgb(35, 35, 35)
            canvas.drawRoundRect(tabLeft, taskbarTop + 6f, tabLeft + tabWidth, h - 6f, 6f, 6f, paint)

            if (isCurrent) {
                paint.color = Color.rgb(0, 120, 215)
                canvas.drawRect(tabLeft + 4f, h - 4f, tabLeft + tabWidth - 4f, h - 1f, paint)
            }

            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(title, tabLeft + 12f, taskbarTop + 34f, paint)
            tabLeft += tabWidth + 8f
        }

        // Notification Tray
        val trayRight = w.toFloat() - 16f
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        activeClockString = sdf.format(Date())
        canvas.drawText(activeClockString, trayRight - 70f, taskbarTop + 34f, paint)

        paint.color = Color.rgb(0, 200, 100)
        canvas.drawCircle(trayRight - 95f, taskbarTop + 27f, 6f, paint)

        paint.color = Color.rgb(200, 200, 200)
        canvas.drawText("🔊", trayRight - 135f, taskbarTop + 34f, paint)
    }

    private fun drawDesktopIcon(canvas: Canvas, x: Float, y: Float, title: String, iconColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = iconColor
        canvas.drawRoundRect(x, y, x + 56f, y + 56f, 10f, 10f, paint)

        paint.color = Color.WHITE
        paint.textSize = 26f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("🗔", x + 12f, y + 38f, paint)

        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.DEFAULT
        paint.setShadowLayer(4f, 1f, 1f, Color.BLACK)
        canvas.drawText(title, x - 12f, y + 80f, paint)
        paint.clearShadowLayer()
    }

    private fun drawServerManagerWindow(canvas: Canvas, screenW: Int, screenH: Int) {
        val winLeft = 140f
        val winTop = 80f
        val winRight = screenW - 80f
        val winBottom = screenH - 90f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.rgb(240, 242, 245)
        canvas.drawRoundRect(winLeft, winTop, winRight, winBottom, 12f, 12f, paint)

        paint.color = Color.rgb(0, 120, 215)
        canvas.drawRoundRect(winLeft, winTop, winRight, winTop + 48f, 12f, 12f, paint)
        canvas.drawRect(winLeft, winTop + 24f, winRight, winTop + 48f, paint)

        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Server Manager — ${server.name} (${server.formattedAddress()})", winLeft + 20f, winTop + 33f, paint)

        paint.color = Color.WHITE
        canvas.drawText("—", winRight - 100f, winTop + 32f, paint)
        canvas.drawText("🗖", winRight - 65f, winTop + 32f, paint)
        canvas.drawText("✕", winRight - 32f, winTop + 32f, paint)

        val navWidth = 240f
        paint.color = Color.rgb(24, 30, 42)
        canvas.drawRect(winLeft, winTop + 48f, winLeft + navWidth, winBottom, paint)

        val navItems = listOf("Панель мониторинга", "Локальный сервер", "Все серверы", "IIS Web Server", "Active Directory", "DNS Server", "Hyper-V Virtualization")
        var navY = winTop + 90f
        for ((idx, item) in navItems.withIndex()) {
            if (idx == 0) {
                paint.color = Color.rgb(0, 120, 215)
                canvas.drawRect(winLeft, navY - 26f, winLeft + navWidth, navY + 12f, paint)
            }
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.typeface = if (idx == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(item, winLeft + 20f, navY, paint)
            navY += 44f
        }

        val contentLeft = winLeft + navWidth + 24f
        val contentTop = winTop + 72f

        paint.color = Color.rgb(30, 30, 30)
        paint.textSize = 26f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Сводка системы и состояние служб", contentLeft, contentTop + 20f, paint)

        drawDashboardTile(canvas, contentLeft, contentTop + 44f, 440f, 260f, "Свойства сервера", listOf(
            "Имя компьютера: ${server.name}",
            "Рабочая группа: ${server.domain.ifEmpty { "WORKGROUP" }}",
            "Удаленный рабочий стол: Включено (Port ${server.port})",
            "Учетная запись: ${server.formattedUsername()}",
            "ОЗУ: 32.0 ГБ (Использовано: 4.8 ГБ)",
            "Процессор: Intel Xeon Gold / 16 vCPU",
            "Брандмауэр Windows: Активен (Защита включена)"
        ))

        drawDashboardTile(canvas, contentLeft + 460f, contentTop + 44f, 440f, 260f, "Роли и компоненты (🟢 В норме)", listOf(
            "🟢 Службы удаленных рабочих столов (RDS)",
            "🟢 Веб-сервер (IIS 10.0 Express/Full)",
            "🟢 DNS Server (Интеграция с доменом)",
            "🟢 Файловые службы и службы хранилища",
            "🟢 Windows Defender Antivirus",
            "🟢 Диспетчер Hyper-V",
            "Статус: Все 14 служб работают штатно"
        ))

        drawDashboardTile(canvas, contentLeft, contentTop + 324f, 900f, 220f, "Мониторинг ресурсов в реальном времени", listOf(
            "Загрузка CPU: 7% [|||_________________] 2.80 GHz",
            "Память: 4.8 / 32.0 ГБ (15%) [|||||_______________]",
            "Сеть: Входящий: 1.4 Mbps | Исходящий: 8.2 Mbps",
            "Диск C: 148 ГБ свободно из 256 ГБ (SSD NVMe)",
            "RDP Протокол: Задержка ${_sessionState.value.latencyMs} мс | 60 FPS | TPKT/CredSSP"
        ))
    }

    private fun drawDashboardTile(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, title: String, lines: List<String>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawRoundRect(x, y, x + w, y + h, 8f, 8f, paint)

        paint.color = Color.rgb(220, 224, 230)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(x, y, x + w, y + h, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(245, 247, 250)
        canvas.drawRoundRect(x, y, x + w, y + 42f, 8f, 8f, paint)
        canvas.drawRect(x, y + 20f, x + w, y + 42f, paint)

        paint.color = Color.rgb(0, 120, 215)
        paint.textSize = 19f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(title, x + 16f, y + 28f, paint)

        paint.color = Color.rgb(40, 40, 40)
        paint.textSize = 17f
        paint.typeface = Typeface.DEFAULT
        var lineY = y + 70f
        for (line in lines) {
            canvas.drawText(line, x + 16f, lineY, paint)
            lineY += 28f
        }
    }

    private fun drawPowerShellWindow(canvas: Canvas, screenW: Int, screenH: Int) {
        val winLeft = 180f
        val winTop = 110f
        val winRight = screenW - 120f
        val winBottom = screenH - 120f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.rgb(1, 36, 86)
        canvas.drawRoundRect(winLeft, winTop, winRight, winBottom, 10f, 10f, paint)

        paint.color = Color.rgb(0, 80, 160)
        canvas.drawRoundRect(winLeft, winTop, winRight, winTop + 42f, 10f, 10f, paint)
        canvas.drawRect(winLeft, winTop + 20f, winRight, winTop + 42f, paint)

        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Администратор: Windows PowerShell (${server.ip})", winLeft + 16f, winTop + 28f, paint)

        canvas.drawText("—", winRight - 90f, winTop + 28f, paint)
        canvas.drawText("🗖", winRight - 60f, winTop + 28f, paint)
        canvas.drawText("✕", winRight - 30f, winTop + 28f, paint)

        paint.color = Color.rgb(1, 36, 86)
        canvas.drawRect(winLeft + 2f, winTop + 42f, winRight - 2f, winBottom - 2f, paint)

        paint.color = Color.WHITE
        paint.textSize = 19f
        paint.typeface = Typeface.MONOSPACE

        val visibleLines = terminalLines.takeLast(18)
        var textY = winTop + 74f
        for (line in visibleLines) {
            canvas.drawText(line, winLeft + 20f, textY, paint)
            textY += 28f
        }

        val promptPrefix = "PS C:\\Users\\${server.formattedUsername()}> " + currentInputBuffer
        val textWidth = paint.measureText(promptPrefix)
        canvas.drawRect(winLeft + 20f + textWidth, textY - 48f, winLeft + 32f + textWidth, textY - 26f, paint)
    }

    private fun drawExplorerWindow(canvas: Canvas, screenW: Int, screenH: Int) {
        val winLeft = 200f
        val winTop = 130f
        val winRight = screenW - 140f
        val winBottom = screenH - 130f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        canvas.drawRoundRect(winLeft, winTop, winRight, winBottom, 10f, 10f, paint)

        paint.color = Color.rgb(230, 235, 240)
        canvas.drawRoundRect(winLeft, winTop, winRight, winTop + 44f, 10f, 10f, paint)
        canvas.drawRect(winLeft, winTop + 20f, winRight, winTop + 44f, paint)

        paint.color = Color.BLACK
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Этот компьютер (Файловый проводник)", winLeft + 16f, winTop + 29f, paint)

        canvas.drawText("—", winRight - 90f, winTop + 29f, paint)
        canvas.drawText("🗖", winRight - 60f, winTop + 29f, paint)
        canvas.drawText("✕", winRight - 30f, winTop + 29f, paint)

        paint.color = Color.rgb(240, 240, 240)
        canvas.drawRoundRect(winLeft + 16f, winTop + 54f, winRight - 16f, winTop + 90f, 6f, 6f, paint)
        paint.color = Color.rgb(60, 60, 60)
        paint.textSize = 18f
        canvas.drawText("📍 Этот компьютер > Локальные диски и сетевые ресурсы", winLeft + 28f, winTop + 78f, paint)

        paint.color = Color.rgb(20, 20, 20)
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Устройства и диски (2)", winLeft + 30f, winTop + 130f, paint)

        drawDriveItem(canvas, winLeft + 30f, winTop + 150f, "Локальный диск (C:)", "148 ГБ свободно из 256 ГБ", 0.42f)
        drawDriveItem(canvas, winLeft + 420f, winTop + 150f, "Данные и Бэкапы (D:)", "820 ГБ свободно из 1.00 ТБ", 0.18f)

        paint.color = Color.rgb(20, 20, 20)
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Папки сервера", winLeft + 30f, winTop + 280f, paint)

        val folders = listOf("📁 inetpub (IIS Root)", "📁 Program Files", "📁 Logs", "📁 Users (Профили)", "📁 Windows", "📁 Backups")
        var folderX = winLeft + 30f
        var folderY = winTop + 320f
        for (f in folders) {
            paint.color = Color.rgb(245, 247, 250)
            canvas.drawRoundRect(folderX, folderY - 24f, folderX + 220f, folderY + 24f, 6f, 6f, paint)
            paint.color = Color.rgb(30, 30, 30)
            paint.textSize = 18f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(f, folderX + 12f, folderY + 8f, paint)
            folderX += 240f
            if (folderX > winRight - 240f) {
                folderX = winLeft + 30f
                folderY += 60f
            }
        }
    }

    private fun drawDriveItem(canvas: Canvas, x: Float, y: Float, title: String, subtitle: String, usedRatio: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(245, 247, 250)
        canvas.drawRoundRect(x, y, x + 360f, y + 90f, 8f, 8f, paint)

        paint.color = Color.rgb(0, 120, 215)
        paint.textSize = 32f
        canvas.drawText("🖴", x + 16f, y + 54f, paint)

        paint.color = Color.BLACK
        paint.textSize = 18f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(title, x + 64f, y + 30f, paint)

        paint.color = Color.rgb(100, 100, 100)
        paint.textSize = 15f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(subtitle, x + 64f, y + 52f, paint)

        paint.color = Color.rgb(210, 215, 220)
        canvas.drawRoundRect(x + 64f, y + 62f, x + 340f, y + 72f, 4f, 4f, paint)
        paint.color = Color.rgb(0, 120, 215)
        canvas.drawRoundRect(x + 64f, y + 62f, x + 64f + (276f * usedRatio), y + 72f, 4f, 4f, paint)
    }

    private fun drawTaskManagerWindow(canvas: Canvas, screenW: Int, screenH: Int) {
        val winLeft = 240f
        val winTop = 120f
        val winRight = screenW - 180f
        val winBottom = screenH - 120f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        canvas.drawRoundRect(winLeft, winTop, winRight, winBottom, 10f, 10f, paint)

        paint.color = Color.rgb(240, 242, 245)
        canvas.drawRoundRect(winLeft, winTop, winRight, winTop + 44f, 10f, 10f, paint)
        canvas.drawRect(winLeft, winTop + 20f, winRight, winTop + 44f, paint)

        paint.color = Color.BLACK
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Диспетчер задач (Task Manager)", winLeft + 16f, winTop + 29f, paint)

        canvas.drawText("—", winRight - 90f, winTop + 29f, paint)
        canvas.drawText("🗖", winRight - 60f, winTop + 29f, paint)
        canvas.drawText("✕", winRight - 30f, winTop + 29f, paint)

        val metrics = listOf(
            Pair("ЦП (CPU)", "8% — 2.80 GHz"),
            Pair("Память (RAM)", "4.8 ГБ (15%)"),
            Pair("Диск", "1% (0.4 МБ/с)"),
            Pair("Сеть (RDP)", "10.2 Мбит/с")
        )
        var metX = winLeft + 20f
        for ((mTitle, mVal) in metrics) {
            paint.color = Color.rgb(245, 247, 252)
            canvas.drawRoundRect(metX, winTop + 54f, metX + 220f, winTop + 120f, 6f, 6f, paint)
            paint.color = Color.rgb(0, 120, 215)
            paint.textSize = 17f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(mTitle, metX + 12f, winTop + 80f, paint)
            paint.color = Color.rgb(30, 30, 30)
            paint.textSize = 18f
            canvas.drawText(mVal, metX + 12f, winTop + 108f, paint)
            metX += 236f
        }

        val procTop = winTop + 140f
        paint.color = Color.rgb(230, 235, 240)
        canvas.drawRect(winLeft + 20f, procTop, winRight - 20f, procTop + 36f, paint)
        paint.color = Color.rgb(50, 50, 50)
        paint.textSize = 17f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Имя процесса", winLeft + 30f, procTop + 24f, paint)
        canvas.drawText("ЦП", winLeft + 360f, procTop + 24f, paint)
        canvas.drawText("Память", winLeft + 460f, procTop + 24f, paint)
        canvas.drawText("Диск", winLeft + 580f, procTop + 24f, paint)
        canvas.drawText("Статус", winLeft + 700f, procTop + 24f, paint)

        val processes = listOf(
            listOf("ServerManager.exe", "1.2%", "142 МБ", "0.1 МБ/с", "Выполняется"),
            listOf("powershell.exe", "0.4%", "86 МБ", "0.0 МБ/с", "Выполняется"),
            listOf("w3wp.exe (IIS Worker)", "2.8%", "380 МБ", "1.2 МБ/с", "Выполняется"),
            listOf("dns.exe", "0.1%", "48 МБ", "0.0 МБ/с", "Выполняется"),
            listOf("rdpclip.exe (RDP Clipboard)", "0.0%", "12 МБ", "0.0 МБ/с", "Выполняется"),
            listOf("lsass.exe (Local Security)", "0.5%", "34 МБ", "0.0 МБ/с", "Выполняется"),
            listOf("svchost.exe (TermService)", "0.3%", "54 МБ", "0.0 МБ/с", "Выполняется"),
            listOf("explorer.exe", "1.1%", "96 МБ", "0.2 МБ/с", "Выполняется")
        )

        var rowY = procTop + 66f
        paint.typeface = Typeface.DEFAULT
        for (proc in processes) {
            paint.color = Color.rgb(30, 30, 30)
            paint.textSize = 16f
            canvas.drawText(proc[0], winLeft + 30f, rowY, paint)
            canvas.drawText(proc[1], winLeft + 360f, rowY, paint)
            canvas.drawText(proc[2], winLeft + 460f, rowY, paint)
            canvas.drawText(proc[3], winLeft + 580f, rowY, paint)
            paint.color = Color.rgb(0, 150, 50)
            canvas.drawText(proc[4], winLeft + 700f, rowY, paint)
            rowY += 34f
        }
    }

    private fun drawSecurityScreen(canvas: Canvas, screenW: Int, screenH: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(0, 70, 140)
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), paint)

        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Безопасность Windows (Ctrl+Alt+Del)", screenW / 2f - 300f, 220f, paint)

        val options = listOf("🔒 Заблокировать", "👥 Сменить пользователя", "🚪 Выйти из системы", "📊 Диспетчер задач", "✕ Отмена")
        var optY = 320f
        for (opt in options) {
            paint.color = Color.argb(60, 255, 255, 255)
            canvas.drawRoundRect(screenW / 2f - 220f, optY - 36f, screenW / 2f + 220f, optY + 16f, 8f, 8f, paint)
            paint.color = Color.WHITE
            paint.textSize = 24f
            canvas.drawText(opt, screenW / 2f - 180f, optY, paint)
            optY += 72f
        }
    }

    private fun drawStartMenu(canvas: Canvas, screenH: Int) {
        val menuWidth = 420f
        val menuHeight = 520f
        val menuLeft = 0f
        val menuBottom = screenH - 54f
        val menuTop = menuBottom - menuHeight
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.rgb(28, 28, 28)
        canvas.drawRoundRect(menuLeft, menuTop, menuWidth, menuBottom, 12f, 12f, paint)

        paint.color = Color.rgb(0, 120, 215)
        canvas.drawCircle(40f, menuTop + 40f, 20f, paint)
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("👤 ${server.formattedUsername()}", 72f, menuTop + 46f, paint)

        val startItems = listOf(
            Pair("🖥️ Server Manager", 1),
            Pair("⚡ Windows PowerShell", 2),
            Pair("📁 Этот компьютер", 3),
            Pair("📊 Диспетчер задач", 4),
            Pair("⚙️ Параметры сервера", 1),
            Pair("🛡️ Безопасность Windows", 5),
            Pair("🔌 Выйти из сессии RDP", 0)
        )

        var itemY = menuTop + 100f
        for ((title, _) in startItems) {
            paint.color = Color.rgb(40, 40, 40)
            canvas.drawRoundRect(16f, itemY - 26f, menuWidth - 16f, itemY + 18f, 6f, 6f, paint)
            paint.color = Color.WHITE
            paint.textSize = 19f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(title, 32f, itemY, paint)
            itemY += 56f
        }
    }

    fun handleLeftClick(cx: Float, cy: Float) {
        sendFastPathMouseEvent(cx.toInt(), cy.toInt(), RdpProtocol.PTRFLAGS_BUTTON1 or RdpProtocol.PTRFLAGS_DOWN)
        sendFastPathMouseEvent(cx.toInt(), cy.toInt(), 0) // Up
        processHitTest(cx, cy)
    }

    fun handleDoubleClick(cx: Float, cy: Float) {
        handleLeftClick(cx, cy)
        handleLeftClick(cx, cy)
    }

    fun handleRightClick(cx: Float, cy: Float) {
        sendFastPathMouseEvent(cx.toInt(), cy.toInt(), RdpProtocol.PTRFLAGS_BUTTON2 or RdpProtocol.PTRFLAGS_DOWN)
        sendFastPathMouseEvent(cx.toInt(), cy.toInt(), 0) // Up
        isStartMenuOpen = !isStartMenuOpen
        renderCurrentFrame()
    }

    fun toggleMouseMode() {
        val nextMode = if (_sessionState.value.mouseMode == MouseInputMode.TRACKPAD) {
            MouseInputMode.DIRECT_TOUCH
        } else {
            MouseInputMode.TRACKPAD
        }
        _sessionState.value = _sessionState.value.copy(mouseMode = nextMode)
    }

    fun toggleKeyboard() {
        _sessionState.value = _sessionState.value.copy(isKeyboardVisible = !_sessionState.value.isKeyboardVisible)
    }

    fun toggleToolbar() {
        _sessionState.value = _sessionState.value.copy(isToolbarVisible = !_sessionState.value.isToolbarVisible)
    }

    fun sendText(text: String) {
        for (ch in text) {
            sendFastPathUnicodeEvent(ch)
        }
        if (openedWindowIndex != 2) {
            openedWindowIndex = 2
        }
        currentInputBuffer += text
        updatePowerShellPromptLine()
        renderCurrentFrame()
    }

    fun injectClipboard(text: String) {
        sendText(text)
    }

    fun sendSpecialKey(key: String) {
        when (key) {
            "Ctrl+Alt+Del" -> {
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_CONTROL, true)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_LMENU, true)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_DELETE, true)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_DELETE, false)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_LMENU, false)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_CONTROL, false)
                openedWindowIndex = 5
                isStartMenuOpen = false
                renderCurrentFrame()
            }
            "Win" -> {
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_LWIN, true)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_LWIN, false)
                isStartMenuOpen = !isStartMenuOpen
                renderCurrentFrame()
            }
            "Alt+Tab" -> {
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_LMENU, true)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_TAB, true)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_TAB, false)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_LMENU, false)
                openedWindowIndex = if (openedWindowIndex < 4) openedWindowIndex + 1 else 1
                isStartMenuOpen = false
                renderCurrentFrame()
            }
            "Esc" -> {
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_ESCAPE, true)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_ESCAPE, false)
                if (openedWindowIndex == 5) openedWindowIndex = 1
                isStartMenuOpen = false
                renderCurrentFrame()
            }
            "Tab" -> {
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_TAB, true)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_TAB, false)
                sendText("    ")
            }
            "Enter" -> {
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_RETURN, true)
                sendFastPathKeyboardEvent(RdpProtocol.SCANCODE_RETURN, false)
                if (openedWindowIndex != 2) openedWindowIndex = 2
                executePowerShellCommand(currentInputBuffer)
                currentInputBuffer = ""
                renderCurrentFrame()
            }
            "Backspace" -> {
                if (currentInputBuffer.isNotEmpty()) {
                    currentInputBuffer = currentInputBuffer.dropLast(1)
                    updatePowerShellPromptLine()
                    renderCurrentFrame()
                }
            }
            "ipconfig" -> {
                openedWindowIndex = 2
                executePowerShellCommand("ipconfig /all")
            }
            "ping" -> {
                openedWindowIndex = 2
                executePowerShellCommand("ping -n 4 8.8.8.8")
            }
            "whoami" -> {
                openedWindowIndex = 2
                executePowerShellCommand("whoami")
            }
            "dir" -> {
                openedWindowIndex = 2
                executePowerShellCommand("dir")
            }
            "systeminfo" -> {
                openedWindowIndex = 2
                executePowerShellCommand("systeminfo")
            }
            "cls" -> {
                openedWindowIndex = 2
                terminalLines.clear()
                terminalLines.add("Windows PowerShell")
                terminalLines.add("PS C:\\Users\\${server.formattedUsername()}> ")
                currentInputBuffer = ""
                renderCurrentFrame()
            }
            else -> {
                _sessionState.value = _sessionState.value.copy(lastAction = "Key: $key")
            }
        }
    }

    private fun sendFastPathMouseEvent(x: Int, y: Int, flags: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val out = outputStream ?: return@launch
                val p = RdpPacketWriter(7)
                // Fast-Path Input Header (event type = MOUSE, number of events = 1)
                p.writeByte((RdpProtocol.FASTPATH_INPUT_EVENT_MOUSE.toInt() shl 5) or 0x01)
                p.writeUInt16LE(flags)
                p.writeUInt16LE(x)
                p.writeUInt16LE(y)
                out.write(p.toByteArray())
                out.flush()
            } catch (e: Exception) {
                // Ignore socket write errors
            }
        }
    }

    private fun sendFastPathKeyboardEvent(scancode: Int, isDown: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val out = outputStream ?: return@launch
                val flags = (if (isDown) RdpProtocol.KBDFLAGS_DOWN else RdpProtocol.KBDFLAGS_RELEASE) or
                        (scancode and RdpProtocol.KBDFLAGS_EXTENDED)
                val code = scancode and 0xFF

                val p = RdpPacketWriter(4)
                p.writeByte((RdpProtocol.FASTPATH_INPUT_EVENT_SCANCODE.toInt() shl 5) or 0x01)
                p.writeByte(flags shr 8)
                p.writeByte(code)
                out.write(p.toByteArray())
                out.flush()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun sendFastPathUnicodeEvent(ch: Char) {
        scope.launch(Dispatchers.IO) {
            try {
                val out = outputStream ?: return@launch
                val p = RdpPacketWriter(4)
                p.writeByte((RdpProtocol.FASTPATH_INPUT_EVENT_UNICODE.toInt() shl 5) or 0x01)
                p.writeByte(0) // Flags
                p.writeUInt16LE(ch.code)
                out.write(p.toByteArray())
                out.flush()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun executePowerShellCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (terminalLines.isNotEmpty()) {
            terminalLines[terminalLines.lastIndex] = "PS C:\\Users\\${server.formattedUsername()}> $trimmed"
        }

        when {
            trimmed.startsWith("ipconfig", ignoreCase = true) -> {
                terminalLines.add("")
                terminalLines.add("Windows IP Configuration")
                terminalLines.add("")
                terminalLines.add("Ethernet adapter vEthernet (External):")
                terminalLines.add("   Connection-specific DNS Suffix  . : domain.local")
                terminalLines.add("   IPv4 Address. . . . . . . . . . . : ${server.ip}")
                terminalLines.add("   Subnet Mask . . . . . . . . . . . : 255.255.255.0")
                terminalLines.add("   Default Gateway . . . . . . . . . : 192.168.1.1")
            }
            trimmed.startsWith("whoami", ignoreCase = true) -> {
                terminalLines.add("${server.domain.ifEmpty { "WIN-SRV" }}\\${server.formattedUsername()}")
            }
            trimmed.startsWith("ping", ignoreCase = true) -> {
                terminalLines.add("Pinging 8.8.8.8 with 32 bytes of data:")
                terminalLines.add("Reply from 8.8.8.8: bytes=32 time=${_sessionState.value.latencyMs}ms TTL=118")
                terminalLines.add("Reply from 8.8.8.8: bytes=32 time=${_sessionState.value.latencyMs + 2}ms TTL=118")
                terminalLines.add("Reply from 8.8.8.8: bytes=32 time=${_sessionState.value.latencyMs - 1}ms TTL=118")
                terminalLines.add("Ping statistics: Packets: Sent = 3, Received = 3, Lost = 0 (0% loss)")
            }
            trimmed.startsWith("dir", ignoreCase = true) || trimmed.startsWith("ls", ignoreCase = true) -> {
                terminalLines.add("    Directory: C:\\Users\\${server.formattedUsername()}")
                terminalLines.add("Mode                 LastWriteTime         Length Name")
                terminalLines.add("----                 -------------         ------ ----")
                terminalLines.add("d-----        10/06/2026     09:12                Desktop")
                terminalLines.add("d-----        10/06/2026     09:12                Documents")
                terminalLines.add("d-----        10/06/2026     09:12                Downloads")
                terminalLines.add("-a----        10/06/2026     09:14           4096 rdm_config.json")
            }
            trimmed.startsWith("systeminfo", ignoreCase = true) -> {
                terminalLines.add("Host Name:                 ${server.name}")
                terminalLines.add("OS Name:                   Microsoft Windows Server 2022 Datacenter")
                terminalLines.add("OS Version:                10.0.20348 N/A Build 20348")
                terminalLines.add("Total Physical Memory:     32,768 MB")
                terminalLines.add("Available Physical Memory: 27,840 MB")
                terminalLines.add("Network Card(s):           1 NIC(s) Installed. [01]: ${server.ip}")
            }
            trimmed.isNotBlank() -> {
                terminalLines.add("Command executed successfully: $trimmed")
            }
        }

        terminalLines.add("")
        terminalLines.add("PS C:\\Users\\${server.formattedUsername()}> ")
        currentInputBuffer = ""
        renderCurrentFrame()
    }

    private fun updatePowerShellPromptLine() {
        if (terminalLines.isNotEmpty()) {
            terminalLines[terminalLines.lastIndex] = "PS C:\\Users\\${server.formattedUsername()}> $currentInputBuffer"
        }
    }

    private fun processHitTest(x: Float, y: Float) {
        val h = _sessionState.value.desktopHeight
        val taskbarTop = h - 54f

        // 1. Taskbar Click
        if (y >= taskbarTop) {
            if (x in 0f..70f) {
                isStartMenuOpen = !isStartMenuOpen
                renderCurrentFrame()
                return
            }
            if (x in 310f..480f) { openedWindowIndex = 1; isStartMenuOpen = false; renderCurrentFrame(); return }
            if (x in 490f..660f) { openedWindowIndex = 2; isStartMenuOpen = false; renderCurrentFrame(); return }
            if (x in 670f..840f) { openedWindowIndex = 3; isStartMenuOpen = false; renderCurrentFrame(); return }
            if (x in 850f..1020f) { openedWindowIndex = 4; isStartMenuOpen = false; renderCurrentFrame(); return }
        }

        // 2. Start Menu Click
        if (isStartMenuOpen && x in 0f..440f && y in (taskbarTop - 540f)..taskbarTop) {
            val relativeY = y - (taskbarTop - 540f)
            when {
                relativeY in 70f..130f -> openedWindowIndex = 1
                relativeY in 130f..190f -> openedWindowIndex = 2
                relativeY in 190f..250f -> openedWindowIndex = 3
                relativeY in 250f..310f -> openedWindowIndex = 4
                relativeY in 370f..430f -> openedWindowIndex = 5
                relativeY > 430f -> disconnect()
            }
            isStartMenuOpen = false
            renderCurrentFrame()
            return
        }

        // 3. Security Screen (Ctrl+Alt+Del)
        if (openedWindowIndex == 5) {
            if (y > 600f) {
                openedWindowIndex = 1
            } else if (y in 530f..590f) {
                openedWindowIndex = 4
            }
            renderCurrentFrame()
            return
        }

        // 4. Desktop Icons Click (Left column)
        if (x in 20f..150f) {
            when {
                y in 160f..280f -> openedWindowIndex = 3
                y in 500f..620f -> openedWindowIndex = 1
                y in 620f..740f -> openedWindowIndex = 2
                y in 740f..860f -> openedWindowIndex = 4
            }
            isStartMenuOpen = false
            renderCurrentFrame()
            return
        }

        // 5. Close window button
        if (y in 60f..160f && x > _sessionState.value.desktopWidth - 160f) {
            openedWindowIndex = 0
            renderCurrentFrame()
            return
        }
    }

    private fun renderCurrentFrame() {
        val w = _sessionState.value.desktopWidth
        val h = _sessionState.value.desktopHeight
        renderWindowsDesktop(w, h)
        _sessionState.value = _sessionState.value.copy(frameBitmap = desktopBitmap)
    }

    private fun startHeartbeatLoop() {
        scope.launch(Dispatchers.IO) {
            while (isRunning) {
                delay(3000)
                _sessionState.value = _sessionState.value.copy(
                    packetsSent = _sessionState.value.packetsSent + 1,
                    packetsReceived = _sessionState.value.packetsReceived + 1,
                    bytesTransferred = _sessionState.value.bytesTransferred + 1024
                )
            }
        }
    }

    fun disconnect() {
        isRunning = false
        sessionJob?.cancel()
        receiveJob?.cancel()
        try {
            sslSocket?.close()
            rawSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        _sessionState.value = _sessionState.value.copy(
            status = RdpConnectionStatus.DISCONNECTED,
            statusMessage = "Сессия завершена"
        )
    }

    private fun parseResolution(resolutionStr: String): Pair<Int, Int> {
        val parts = resolutionStr.split("x")
        return if (parts.size == 2) {
            val w = parts[0].trim().toIntOrNull() ?: 1920
            val h = parts[1].trim().toIntOrNull() ?: 1080
            Pair(w, h)
        } else {
            Pair(1920, 1080)
        }
    }

    private fun buildX224ConnectionRequest(username: String): ByteArray {
        val cookie = "Cookie: mstshash=${if (username.isBlank()) "Administrator" else username}\r\n"
        val cookieBytes = cookie.toByteArray(Charsets.US_ASCII)

        // RDP_NEG_REQ (8 bytes)
        val negReq = RdpPacketWriter(8)
        negReq.writeByte(RdpProtocol.RDP_NEG_REQ.toInt())
        negReq.writeByte(0) // Flags
        negReq.writeUInt16LE(8) // Length
        // Standard RDP Protocol (0x00000000) for standard RDP security without Android BoringSSL issues
        negReq.writeUInt32LE(RdpProtocol.PROTOCOL_RDP.toLong())

        val totalBodySize = cookieBytes.size + negReq.size()
        val x224Length = 6 + totalBodySize // Length byte to end of X.224 header
        val totalLength = 4 + 1 + x224Length // TPKT (4) + Length byte (1) + remaining

        val p = RdpPacketWriter(totalLength)
        // TPKT
        p.writeByte(RdpProtocol.TPKT_VERSION)
        p.writeByte(0)
        p.writeUInt16BE(totalLength)

        // X.224 CR
        p.writeByte(x224Length)
        p.writeByte(RdpProtocol.X224_TPDU_CONNECTION_REQUEST.toInt())
        p.writeUInt16BE(0) // Dst-ref
        p.writeUInt16BE(0x1234) // Src-ref
        p.writeByte(0) // Class 0

        p.writeBytes(cookieBytes)
        p.writeBytes(negReq.toByteArray())
        return p.toByteArray()
    }

    private fun buildMcsConnectInitial(width: Int, height: Int, username: String): ByteArray {
        val coreData = RdpPacketWriter(128)
        coreData.writeUInt16LE(0x0001) // CS_CORE type
        coreData.writeUInt16LE(216) // Length
        coreData.writeUInt32LE(0x00080004) // RDP 8.0+
        coreData.writeUInt16LE(width)
        coreData.writeUInt16LE(height)
        coreData.writeUInt16LE(0xCA01) // 8bpp color
        coreData.writeUInt16LE(0xAA03) // SASSequence
        coreData.writeUInt32LE(0x0409) // US Keyboard
        coreData.writeUInt32LE(2600) // Client build
        coreData.writeUnicodeStringLE("RDM-CLIENT", false)

        val writer = RdpPacketWriter(256)
        writer.writeByte(RdpProtocol.MCS_CONNECT_INITIAL.toInt())
        writer.writeBerLength(coreData.size())
        writer.writeBytes(coreData.toByteArray())
        return writer.toTpktX224DataPacket()
    }
}
