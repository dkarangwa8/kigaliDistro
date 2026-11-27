package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ProofMediaUtils {

    private const val MAX_DIMENSION = 320
    private const val MAX_BASE64_CHARS = 80_000

    fun bitmapToCompactBase64(bitmap: Bitmap): String {
        val scaled = scaleBitmap(bitmap, MAX_DIMENSION)
        var quality = 72
        var encoded: String
        do {
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            encoded = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            quality -= 12
        } while (encoded.length > MAX_BASE64_CHARS && quality >= 24)
        return encoded.take(MAX_BASE64_CHARS)
    }

    fun uriToCompactBase64(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val raw = BitmapFactory.decodeStream(input) ?: return null
                bitmapToCompactBase64(raw)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmap(source: Bitmap, maxEdge: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxEdge && height <= maxEdge) return source
        val scale = maxEdge.toFloat() / max(width, height).toFloat()
        val targetW = max(1, (width * scale).roundToInt())
        val targetH = max(1, (height * scale).roundToInt())
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    /**
     * Serializes normalized strokes (0..1 coordinates) as compact "x,y x,y;x,y x,y" string
     * bounded under 14,000 chars for Firestore cross-device proof-of-delivery rendering.
     */
    fun serializeSignatureStrokes(
        strokes: List<List<Offset>>,
        canvasWidth: Float,
        canvasHeight: Float
    ): String {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return ""
        val sb = StringBuilder()
        strokes.forEachIndexed { strokeIdx, stroke ->
            if (stroke.isEmpty()) return@forEachIndexed
            if (strokeIdx > 0 && sb.isNotEmpty()) sb.append(';')
            // Downsample dense points to keep payload crisp and compact
            val step = max(1, stroke.size / 40)
            stroke.forEachIndexed { idx, pt ->
                if (idx % step == 0 || idx == stroke.lastIndex) {
                    val nx = min(1f, max(0f, pt.x / canvasWidth))
                    val ny = min(1f, max(0f, pt.y / canvasHeight))
                    if (idx > 0 && sb.isNotEmpty() && sb.last() != ';') sb.append(' ')
                    sb.append(String.format(Locale.US, "%.3f,%.3f", nx, ny))
                }
            }
        }
        return sb.toString().take(14_000)
    }

    fun parseSignatureStrokes(serialized: String): List<List<Offset>> {
        if (serialized.isBlank()) return emptyList()
        return serialized.split(';').mapNotNull { strokeStr ->
            val pts = strokeStr.trim().split(' ').mapNotNull { pair ->
                val parts = pair.split(',')
                if (parts.size == 2) {
                    val x = parts[0].toFloatOrNull()
                    val y = parts[1].toFloatOrNull()
                    if (x != null && y != null) Offset(x, y) else null
                } else null
            }
            pts.takeIf { it.isNotEmpty() }
        }
    }
}
