package com.rdm.remote.desktop.manager.rdp

object RdpProtocol {

    // TPKT (RFC 1006)
    const val TPKT_VERSION = 3

    // X.224 (ISO 8073) Codes
    const val X224_TPDU_CONNECTION_REQUEST: Byte = 0xE0.toByte()
    const val X224_TPDU_CONNECTION_CONFIRM: Byte = 0xD0.toByte()
    const val X224_TPDU_DISCONNECT_REQUEST: Byte = 0x80.toByte()
    const val X224_TPDU_DATA: Byte = 0xF0.toByte()

    // RDP Negotiation Protocol Request / Response Types (MS-RDPBCGR 2.2.1.1)
    const val RDP_NEG_REQ: Byte = 0x01
    const val RDP_NEG_RSP: Byte = 0x02
    const val RDP_NEG_FAILURE: Byte = 0x03

    // Security Protocols
    const val PROTOCOL_RDP = 0x00000000
    const val PROTOCOL_SSL = 0x00000001
    const val PROTOCOL_HYBRID = 0x00000002 // CredSSP / NLA
    const val PROTOCOL_RDSTLS = 0x00000004
    const val PROTOCOL_HYBRID_EX = 0x00000008

    // MCS (T.125) PDU Types
    const val MCS_CONNECT_INITIAL: Byte = 0x65
    const val MCS_CONNECT_RESPONSE: Byte = 0x66
    const val MCS_ATTACH_USER_REQUEST: Byte = 0x28 // (10 << 2)
    const val MCS_ATTACH_USER_CONFIRM: Byte = 0x2C // (11 << 2)
    const val MCS_CHANNEL_JOIN_REQUEST: Byte = 0x38 // (14 << 2)
    const val MCS_CHANNEL_JOIN_CONFIRM: Byte = 0x3C // (15 << 2)
    const val MCS_SEND_DATA_REQUEST: Byte = 0x64 // (25 << 2)
    const val MCS_SEND_DATA_INDICATION: Byte = 0x68 // (26 << 2)

    // Fast-Path Header (MS-RDPBCGR 2.2.9.1.2)
    const val FASTPATH_OUTPUT_ACTION_FASTPATH = 0x00
    const val FASTPATH_UPDATETYPE_ORDERS = 0x00
    const val FASTPATH_UPDATETYPE_BITMAP = 0x01
    const val FASTPATH_UPDATETYPE_PALETTE = 0x02
    const val FASTPATH_UPDATETYPE_SYNCHRONIZE = 0x03
    const val FASTPATH_UPDATETYPE_SURFCMDS = 0x04
    const val FASTPATH_UPDATETYPE_PTR_NULL = 0x05
    const val FASTPATH_UPDATETYPE_PTR_DEFAULT = 0x06
    const val FASTPATH_UPDATETYPE_PTR_POSITION = 0x08
    const val FASTPATH_UPDATETYPE_COLOR = 0x09
    const val FASTPATH_UPDATETYPE_CACHED = 0x0A
    const val FASTPATH_UPDATETYPE_POINTER = 0x0B

    // Fast-Path Input Events (MS-RDPBCGR 2.2.8.1.2)
    const val FASTPATH_INPUT_EVENT_SCANCODE: Byte = 0x00
    const val FASTPATH_INPUT_EVENT_MOUSE: Byte = 0x01
    const val FASTPATH_INPUT_EVENT_MOUSEX: Byte = 0x02
    const val FASTPATH_INPUT_EVENT_SYNC: Byte = 0x03
    const val FASTPATH_INPUT_EVENT_UNICODE: Byte = 0x04
    const val FASTPATH_INPUT_EVENT_QOE_TIMESTAMP: Byte = 0x06

    // Pointer Flags (MS-RDPBCGR 2.2.8.1.1.3.1.1)
    const val PTRFLAGS_MOVE = 0x0800
    const val PTRFLAGS_BUTTON1 = 0x1000 // Left button
    const val PTRFLAGS_BUTTON2 = 0x2000 // Right button
    const val PTRFLAGS_BUTTON3 = 0x4000 // Middle button
    const val PTRFLAGS_DOWN = 0x8000
    const val PTRFLAGS_WHEEL = 0x0200
    const val PTRFLAGS_WHEEL_NEGATIVE = 0x0100

    // Keyboard Scancodes (IBM PC AT)
    const val KBDFLAGS_EXTENDED = 0x0100
    const val KBDFLAGS_DOWN = 0x4000
    const val KBDFLAGS_RELEASE = 0x8000

    const val SCANCODE_ESCAPE = 0x0001
    const val SCANCODE_TAB = 0x000F
    const val SCANCODE_RETURN = 0x001C
    const val SCANCODE_CONTROL = 0x001D
    const val SCANCODE_LSHIFT = 0x002A
    const val SCANCODE_RSHIFT = 0x0036
    const val SCANCODE_LMENU = 0x0038 // Alt
    const val SCANCODE_SPACE = 0x0039
    const val SCANCODE_CAPSLOCK = 0x003A
    const val SCANCODE_F1 = 0x003B
    const val SCANCODE_F2 = 0x003C
    const val SCANCODE_F3 = 0x003D
    const val SCANCODE_F4 = 0x003E
    const val SCANCODE_F5 = 0x003F
    const val SCANCODE_F6 = 0x0040
    const val SCANCODE_F7 = 0x0041
    const val SCANCODE_F8 = 0x0042
    const val SCANCODE_F9 = 0x0043
    const val SCANCODE_F10 = 0x0044
    const val SCANCODE_F11 = 0x0057
    const val SCANCODE_F12 = 0x0058
    const val SCANCODE_LWIN = 0x005B or KBDFLAGS_EXTENDED
    const val SCANCODE_DELETE = 0x0053 or KBDFLAGS_EXTENDED
    const val SCANCODE_UP = 0x0048 or KBDFLAGS_EXTENDED
    const val SCANCODE_LEFT = 0x004B or KBDFLAGS_EXTENDED
    const val SCANCODE_RIGHT = 0x004D or KBDFLAGS_EXTENDED
    const val SCANCODE_DOWN = 0x0050 or KBDFLAGS_EXTENDED
}
