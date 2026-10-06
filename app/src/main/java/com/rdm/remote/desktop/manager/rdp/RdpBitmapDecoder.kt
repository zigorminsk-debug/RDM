package com.rdm.remote.desktop.manager.rdp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object RdpBitmapDecoder {

    /**
     * Updates an area of the target desktop bitmap with raw uncompressed or RLE decoded pixels
     */
    fun updateBitmapRect(
        targetBitmap: Bitmap,
        destLeft: Int,
        destTop: Int,
        width: Int,
        height: Int,
        bitsPerPixel: Int,
        pixelData: ByteArray,
        isCompressed: Boolean
    ) {
        if (width <= 0 || height <= 0) return
        val clampedWidth = minOf(width, targetBitmap.width - destLeft)
        val clampedHeight = minOf(height, targetBitmap.height - destTop)
        if (clampedWidth <= 0 || clampedHeight <= 0) return

        val pixelArray = IntArray(clampedWidth * clampedHeight)

        if (!isCompressed && bitsPerPixel == 32) {
            var srcIdx = 0
            for (y in 0 until clampedHeight) {
                for (x in 0 until clampedWidth) {
                    if (srcIdx + 3 < pixelData.size) {
                        val b = pixelData[srcIdx].toInt() and 0xFF
                        val g = pixelData[srcIdx + 1].toInt() and 0xFF
                        val r = pixelData[srcIdx + 2].toInt() and 0xFF
                        val a = 0xFF
                        pixelArray[y * clampedWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        srcIdx += 4
                    }
                }
            }
        } else if (!isCompressed && bitsPerPixel == 24) {
            var srcIdx = 0
            for (y in 0 until clampedHeight) {
                for (x in 0 until clampedWidth) {
                    if (srcIdx + 2 < pixelData.size) {
                        val b = pixelData[srcIdx].toInt() and 0xFF
                        val g = pixelData[srcIdx + 1].toInt() and 0xFF
                        val r = pixelData[srcIdx + 2].toInt() and 0xFF
                        val a = 0xFF
                        pixelArray[y * clampedWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        srcIdx += 3
                    }
                }
            }
        } else if (!isCompressed && bitsPerPixel == 16) {
            var srcIdx = 0
            for (y in 0 until clampedHeight) {
                for (x in 0 until clampedWidth) {
                    if (srcIdx + 1 < pixelData.size) {
                        val low = pixelData[srcIdx].toInt() and 0xFF
                        val high = pixelData[srcIdx + 1].toInt() and 0xFF
                        val raw16 = (high shl 8) or low
                        val r = ((raw16 shr 11) and 0x1F) * 255 / 31
                        val g = ((raw16 shr 5) and 0x3F) * 255 / 63
                        val b = (raw16 and 0x1F) * 255 / 31
                        pixelArray[y * clampedWidth + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        srcIdx += 2
                    }
                }
            }
        } else {
            // Compressed or synthetic RLE
            decodeRleBitmap(pixelData, pixelArray, clampedWidth, clampedHeight, bitsPerPixel)
        }

        try {
            targetBitmap.setPixels(pixelArray, 0, clampedWidth, destLeft, destTop, clampedWidth, clampedHeight)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun decodeRleBitmap(
        compressed: ByteArray,
        outputPixels: IntArray,
        width: Int,
        height: Int,
        bpp: Int
    ) {
        var src = 0
        var dst = 0
        val total = width * height

        while (src < compressed.size && dst < total) {
            val control = compressed[src++].toInt() and 0xFF
            val count = (control and 0x0F) + 1
            val code = (control and 0xF0) shr 4

            if (code == 0) {
                // Literal run
                for (i in 0 until count) {
                    if (dst < total && src < compressed.size) {
                        outputPixels[dst++] = Color.rgb(
                            compressed[src++].toInt() and 0xFF,
                            (if (src < compressed.size) compressed[src++].toInt() and 0xFF else 0),
                            (if (src < compressed.size) compressed[src++].toInt() and 0xFF else 0)
                        )
                    }
                }
            } else {
                // Repeat run
                val prev = if (dst > 0) outputPixels[dst - 1] else Color.BLACK
                for (i in 0 until count) {
                    if (dst < total) {
                        outputPixels[dst++] = prev
                    }
                }
            }
        }
    }
}
