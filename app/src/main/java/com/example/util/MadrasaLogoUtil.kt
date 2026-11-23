package com.example.util

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.R
import com.example.data.MadrasaInfoStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

object MadrasaLogoUtil {

    private fun openLogoStream(context: Context, logoPath: String): InputStream? {
        if (logoPath.isBlank()) return null
        return try {
            val uri = Uri.parse(logoPath)
            if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
                val path = uri.path ?: logoPath
                val file = File(path)
                if (file.exists() && file.length() > 0L) FileInputStream(file) else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun isCustomLogoAvailable(context: Context, logoPath: String): Boolean {
        if (logoPath.isBlank()) return false
        return try {
            val uri = Uri.parse(logoPath)
            if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
                val file = File(uri.path ?: logoPath)
                file.exists() && file.length() > 0L
            } else {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Loads the Madrasa logo (custom edited logo if present, otherwise built-in emblem)
     * and masks it into an anti-aliased high-resolution circular Bitmap with transparent corners.
     */
    fun loadCircularLogoBitmap(
        context: Context,
        logoPath: String = MadrasaInfoStore.getInfo(context).logoPath,
        targetSizePx: Int = 512,
        fallbackResId: Int = R.drawable.img_mpro_emblem
    ): Bitmap? {
        val safeSize = targetSizePx.coerceIn(48, 1536)
        val rawBitmap = loadCustomBitmap(context, logoPath, safeSize)
            ?: loadFallbackBitmap(context, fallbackResId)
            ?: return null

        val circular = toCircularBitmap(rawBitmap, safeSize)
        if (rawBitmap != circular) {
            try { rawBitmap.recycle() } catch (_: Exception) {}
        }
        return circular
    }

    private fun loadCustomBitmap(context: Context, logoPath: String, targetSizePx: Int): Bitmap? {
        if (logoPath.isBlank()) return null
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openLogoStream(context, logoPath)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            } ?: return null

            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            val maxDecode = (targetSizePx * 2).coerceAtLeast(512)
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDecode ||
                boundsOpts.outHeight / sampleSize > maxDecode
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bmp = openLogoStream(context, logoPath)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            // Auto-apply EXIF rotation if needed
            try {
                openLogoStream(context, logoPath)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    val orient = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    val matrix = Matrix()
                    when (orient) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    }
                    if (!matrix.isIdentity) {
                        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                        if (rotated != bmp) {
                            bmp.recycle()
                            bmp = rotated
                        }
                    }
                }
            } catch (_: Throwable) {}

            bmp
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadFallbackBitmap(context: Context, fallbackResId: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeResource(context.resources, fallbackResId, opts)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Center-crops to a square, scales smoothly to [targetSizePx], and applies an
     * anti-aliased BitmapShader circle mask so edges are crisp and corners are transparent.
     */
    fun toCircularBitmap(src: Bitmap, targetSizePx: Int): Bitmap {
        val side = min(src.width, src.height)
        val xOff = (src.width - side) / 2
        val yOff = (src.height - side) / 2

        val squareSrc = if (src.width != src.height) {
            Bitmap.createBitmap(src, xOff, yOff, side, side)
        } else {
            src
        }

        val scaled = if (squareSrc.width != targetSizePx || squareSrc.height != targetSizePx) {
            Bitmap.createScaledBitmap(squareSrc, targetSizePx, targetSizePx, true)
        } else {
            squareSrc
        }

        // Composite over white inside the circle so transparent edges render cleanly on dark headers
        val composed = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val compCanvas = Canvas(composed)
        compCanvas.drawColor(Color.WHITE)
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        compCanvas.drawBitmap(scaled, 0f, 0f, imgPaint)

        val output = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(output)
        val shader = BitmapShader(composed, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            this.shader = shader
        }
        val radius = targetSizePx / 2f
        outCanvas.drawCircle(radius, radius, radius, circlePaint)

        if (squareSrc != src) {
            try { squareSrc.recycle() } catch (_: Exception) {}
        }
        if (scaled != squareSrc && scaled != src) {
            try { scaled.recycle() } catch (_: Exception) {}
        }
        try { composed.recycle() } catch (_: Exception) {}

        return output
    }

    fun cleanupOldLogoFiles(context: Context, keepLogoPath: String) {
        try {
            val keepFileName = if (keepLogoPath.isNotBlank()) {
                File(Uri.parse(keepLogoPath).path ?: keepLogoPath).name
            } else {
                ""
            }
            context.filesDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("madrasa_logo_") && f.name.endsWith(".png") && f.name != keepFileName) {
                    f.delete()
                }
            }
        } catch (_: Throwable) {}
    }

    fun readLogoAsBase64(logoPath: String): String {
        if (logoPath.isBlank()) return ""
        return try {
            val path = Uri.parse(logoPath).path ?: logoPath
            val file = File(path)
            if (!file.exists() || file.length() <= 0L || file.length() > 3L * 1024L * 1024L) {
                ""
            } else {
                Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            }
        } catch (_: Throwable) {
            ""
        }
    }

    fun readLogoAsCompactBase64(logoPath: String, maxBase64Chars: Int = 450_000): String {
        val raw = readLogoAsBase64(logoPath)
        if (raw.isBlank() || raw.length <= maxBase64Chars) return raw
        return try {
            val path = Uri.parse(logoPath).path ?: logoPath
            val src = BitmapFactory.decodeFile(path) ?: return ""
            var size = 512
            var result = ""
            while (size >= 192) {
                val circular = toCircularBitmap(src, size)
                val baos = java.io.ByteArrayOutputStream()
                circular.compress(Bitmap.CompressFormat.PNG, 90, baos)
                if (circular != src) {
                    try { circular.recycle() } catch (_: Exception) {}
                }
                val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                if (encoded.length <= maxBase64Chars) {
                    result = encoded
                    break
                }
                size -= 96
            }
            if (result.isBlank()) {
                val circular = toCircularBitmap(src, 384)
                val baos = java.io.ByteArrayOutputStream()
                circular.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                if (circular != src) {
                    try { circular.recycle() } catch (_: Exception) {}
                }
                val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                if (encoded.length <= maxBase64Chars) result = encoded
            }
            try { src.recycle() } catch (_: Exception) {}
            result
        } catch (_: Throwable) {
            ""
        }
    }

    fun saveLogoFromBase64(context: Context, base64: String): String {
        if (base64.isBlank()) return ""
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes.isEmpty()) return ""
            val outFile = File(context.filesDir, "madrasa_logo_restored_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { it.write(bytes) }
            Uri.fromFile(outFile).toString()
        } catch (_: Throwable) {
            ""
        }
    }
}
