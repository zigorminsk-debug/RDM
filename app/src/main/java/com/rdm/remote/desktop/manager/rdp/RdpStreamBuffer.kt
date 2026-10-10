package com.rdm.remote.desktop.manager.rdp

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RdpPacketWriter(initialCapacity: Int = 512) {
    private val bos = ByteArrayOutputStream(initialCapacity)

    fun writeByte(b: Int) {
        bos.write(b and 0xFF)
    }

    fun writeBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        bos.write(bytes, offset, length)
    }

    // Little-Endian (Used in RDP PDU bodies)
    fun writeUInt16LE(value: Int) {
        bos.write(value and 0xFF)
        bos.write((value shr 8) and 0xFF)
    }

    fun writeUInt32LE(value: Long) {
        bos.write((value and 0xFF).toInt())
        bos.write(((value shr 8) and 0xFF).toInt())
        bos.write(((value shr 16) and 0xFF).toInt())
        bos.write(((value shr 24) and 0xFF).toInt())
    }

    // Big-Endian (Used in TPKT and X.224 headers)
    fun writeUInt16BE(value: Int) {
        bos.write((value shr 8) and 0xFF)
        bos.write(value and 0xFF)
    }

    fun writeUInt32BE(value: Long) {
        bos.write(((value shr 24) and 0xFF).toInt())
        bos.write(((value shr 16) and 0xFF).toInt())
        bos.write(((value shr 8) and 0xFF).toInt())
        bos.write((value and 0xFF).toInt())
    }

    // Unicode string (UTF-16LE with null-terminator)
    fun writeUnicodeStringLE(str: String, nullTerminated: Boolean = true) {
        val bytes = str.toByteArray(Charsets.UTF_16LE)
        bos.write(bytes)
        if (nullTerminated) {
            writeByte(0)
            writeByte(0)
        }
    }

    // BER Length Encoding (ITU-T X.690)
    fun writeBerLength(length: Int) {
        if (length > 0x7F) {
            writeByte(0x82)
            writeUInt16BE(length)
        } else {
            writeByte(length)
        }
    }

    fun toByteArray(): ByteArray = bos.toByteArray()

    fun size(): Int = bos.size()

    // Wrap body with TPKT and X.224 Data headers
    fun toTpktX224DataPacket(): ByteArray {
        val body = toByteArray()
        val totalLength = 4 + 3 + body.size // TPKT (4) + X.224 (3) + body

        val p = RdpPacketWriter(totalLength)
        // TPKT Header
        p.writeByte(RdpProtocol.TPKT_VERSION)
        p.writeByte(0) // Reserved
        p.writeUInt16BE(totalLength)

        // X.224 Data Header
        p.writeByte(2) // Length indicator
        p.writeByte(RdpProtocol.X224_TPDU_DATA.toInt()) // Data TPDU
        p.writeByte(0x80) // EOT (End of transmission)

        p.writeBytes(body)
        return p.toByteArray()
    }
}

class RdpPacketReader(private val buffer: ByteArray, offset: Int = 0, length: Int = buffer.size) {
    private val byteBuffer = ByteBuffer.wrap(buffer, offset, length)

    init {
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    }

    fun setByteOrder(order: ByteOrder) {
        byteBuffer.order(order)
    }

    fun remaining(): Int = byteBuffer.remaining()

    fun position(): Int = byteBuffer.position()

    fun setPosition(newPos: Int) {
        byteBuffer.position(newPos)
    }

    fun readByte(): Int = byteBuffer.get().toInt() and 0xFF

    fun readBytes(length: Int): ByteArray {
        val dest = ByteArray(length)
        byteBuffer.get(dest)
        return dest
    }

    fun readUInt16LE(): Int = byteBuffer.short.toInt() and 0xFFFF

    fun readUInt32LE(): Long = byteBuffer.int.toLong() and 0xFFFFFFFFL

    fun readUInt16BE(): Int {
        val b1 = readByte()
        val b2 = readByte()
        return (b1 shl 8) or b2
    }

    fun readUInt32BE(): Long {
        val b1 = readByte().toLong()
        val b2 = readByte().toLong()
        val b3 = readByte().toLong()
        val b4 = readByte().toLong()
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    fun readUnicodeStringLE(charCount: Int): String {
        val byteLength = charCount * 2
        val bytes = readBytes(byteLength)
        return String(bytes, Charsets.UTF_16LE).trimEnd('\u0000')
    }

    companion object {
        /**
         * Reads full TPKT packet from input stream blocking until complete
         */
        fun readTpktPacket(inputStream: InputStream): RdpPacketReader? {
            val header = ByteArray(4)
            var read = 0
            while (read < 4) {
                val r = inputStream.read(header, read, 4 - read)
                if (r < 0) return null
                read += r
            }

            val version = header[0].toInt() and 0xFF
            if (version != RdpProtocol.TPKT_VERSION && (version and 0xC0) != 0) {
                // Fast-Path Header (first byte has 2 high bits != 0)
                val fastPathByte1 = version
                val lengthByte2 = header[1].toInt() and 0xFF
                val fastPathLength = if ((lengthByte2 and 0x80) != 0) {
                    val lengthByte3 = header[2].toInt() and 0xFF
                    ((lengthByte2 and 0x7F) shl 8) or lengthByte3
                } else {
                    lengthByte2
                }

                val payloadLength = fastPathLength - if ((lengthByte2 and 0x80) != 0) 3 else 2
                val payload = ByteArray(payloadLength)
                var pRead = 0
                while (pRead < payloadLength) {
                    val r = inputStream.read(payload, pRead, payloadLength - pRead)
                    if (r < 0) return null
                    pRead += r
                }
                return RdpPacketReader(payload)
            }

            val length = ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            if (length < 4) return null

            val bodyLength = length - 4
            val body = ByteArray(bodyLength)
            var bRead = 0
            while (bRead < bodyLength) {
                val r = inputStream.read(body, bRead, bodyLength - bRead)
                if (r < 0) return null
                bRead += r
            }

            return RdpPacketReader(body)
        }
    }
}
