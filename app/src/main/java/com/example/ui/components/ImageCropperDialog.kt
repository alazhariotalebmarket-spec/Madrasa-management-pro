package com.example.ui.components

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    onCropCompleted: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "✂️ ছবি ক্রপ ও সাইজ নির্ধারণ",
    subtitle: String = "ছবিটি প্রয়োজনমতো ঘোরান, গোল বা চারকোনা করুন এবং জুম করুন",
    initialCircular: Boolean = false,
    lockCircularShape: Boolean = false,
    outputSizePx: Int = 512,
    filePrefix: String = "photo"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var rotationAngle by remember { mutableStateOf(0) } // 0, 90, 180, 270
    var isCircular by remember(initialCircular, lockCircularShape) {
        mutableStateOf(if (lockCircularShape) true else initialCircular)
    }
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSizePx by remember { mutableStateOf(0f) }

    // Load and auto-orient high-res bitmap on background dispatcher
    LaunchedEffect(imageUri, outputSizePx) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOnly)
                }

                var sample = 1
                val maxDim = maxOf(1600, outputSizePx * 2)
                while (boundsOnly.outWidth / sample > maxDim || boundsOnly.outHeight / sample > maxDim) {
                    sample *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                var rawBmp = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }

                if (rawBmp != null) {
                    try {
                        context.contentResolver.openInputStream(imageUri)?.use { exifStream ->
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
                                val oriented = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                                if (oriented != rawBmp) {
                                    rawBmp.recycle()
                                    rawBmp = oriented
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    loadedBitmap = rawBmp
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2621))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MadrasaGold
                )

                Spacer(modifier = Modifier.height(12.dp))

                val bmp = loadedBitmap
                if (isLoading || bmp == null) {
                    Box(
                        modifier = Modifier
                            .size(270.dp)
                            .background(Color.Black, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MadrasaPrimary)
                    }
                } else {
                    val composeImage = remember(bmp, rotationAngle) {
                        if (rotationAngle != 0) {
                            val matrix = Matrix().apply { postRotate(rotationAngle.toFloat()) }
                            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true).asImageBitmap()
                        } else {
                            bmp.asImageBitmap()
                        }
                    }

                    // Interactive Crop Viewport (supports 1-finger drag + 2-finger pinch zoom)
                    Box(
                        modifier = Modifier
                            .size(270.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .onSizeChanged { size ->
                                viewportSizePx = min(size.width, size.height).toFloat()
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                    panOffset += pan
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasW = size.width
                            val canvasH = size.height

                            val imgW = composeImage.width.toFloat()
                            val imgH = composeImage.height.toFloat()

                            // Base fit scale
                            val baseScale = min(canvasW / imgW, canvasH / imgH)
                            val finalScale = baseScale * zoomScale

                            val drawW = imgW * finalScale
                            val drawH = imgH * finalScale

                            val left = (canvasW - drawW) / 2f + panOffset.x
                            val top = (canvasH - drawH) / 2f + panOffset.y

                            drawImage(
                                image = composeImage,
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1))
                            )

                            // Crop Mask Scrim + Gold Border
                            val cropSize = min(canvasW, canvasH) * 0.88f
                            val cropLeft = (canvasW - cropSize) / 2f
                            val cropTop = (canvasH - cropSize) / 2f

                            val fullPath = Path().apply {
                                addRect(Rect(0f, 0f, canvasW, canvasH))
                            }
                            val cutoutPath = Path().apply {
                                if (isCircular) {
                                    addOval(Rect(cropLeft, cropTop, cropLeft + cropSize, cropTop + cropSize))
                                } else {
                                    addRect(Rect(cropLeft, cropTop, cropLeft + cropSize, cropTop + cropSize))
                                }
                            }
                            val overlayPath = Path.combine(PathOperation.Difference, fullPath, cutoutPath)
                            drawPath(path = overlayPath, color = Color.Black.copy(alpha = 0.62f))

                            if (isCircular) {
                                drawCircle(
                                    color = MadrasaGold,
                                    radius = cropSize / 2f,
                                    center = Offset(canvasW / 2f, canvasH / 2f),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            } else {
                                drawRect(
                                    color = MadrasaGold,
                                    topLeft = Offset(cropLeft, cropTop),
                                    size = Size(cropSize, cropSize),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { rotationAngle = (rotationAngle + 90) % 360 },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ঘোরান", fontSize = 12.sp)
                    }

                    if (lockCircularShape) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MadrasaGold.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MadrasaGold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "রাউন্ড হাই-রেস",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MadrasaGold
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isCircular = !isCircular },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                if (isCircular) Icons.Default.RadioButtonUnchecked else Icons.Default.CropSquare,
                                contentDescription = "Shape",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isCircular) "গোল" else "চারকোনা", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            rotationAngle = 0
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("রিসেট", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Zoom Slider (1.0x to 4.0x)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔍 জুম:", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = zoomScale,
                        onValueChange = { zoomScale = it },
                        valueRange = 1f..4f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MadrasaGold,
                            activeTrackColor = MadrasaPrimary
                        )
                    )
                    Text(
                        text = "%.1fx".format(zoomScale),
                        color = MadrasaGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Confirm / Cancel Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (!isSaving) onDismiss() },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                    ) {
                        Text("বাতিল")
                    }

                    Button(
                        onClick = {
                            val currentBmp = loadedBitmap
                            if (currentBmp != null && !isSaving) {
                                isSaving = true
                                scope.launch {
                                    val savedPath = withContext(Dispatchers.IO) {
                                        saveCroppedBitmap(
                                            context = context,
                                            src = currentBmp,
                                            rotation = rotationAngle,
                                            zoom = zoomScale,
                                            pan = panOffset,
                                            viewportDimPx = viewportSizePx.takeIf { it > 0f } ?: 720f,
                                            targetDim = outputSizePx,
                                            isCircle = isCircular,
                                            filePrefix = filePrefix
                                        )
                                    }
                                    isSaving = false
                                    if (savedPath != null) {
                                        Toast.makeText(context, "হাই-রেজুলেশন ছবি সফলভাবে সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                                        onCropCompleted(savedPath)
                                    } else {
                                        Toast.makeText(context, "ছবি সেভ করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isSaving && loadedBitmap != null,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isSaving) "সেভ হচ্ছে..." else "💾 ক্রপ ও সেভ",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun saveCroppedBitmap(
    context: Context,
    src: Bitmap,
    rotation: Int,
    zoom: Float,
    pan: Offset,
    viewportDimPx: Float,
    targetDim: Int,
    isCircle: Boolean,
    filePrefix: String
): String? {
    return try {
        val rotatedSrc = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } else {
            src
        }

        val cropSizePx = viewportDimPx * 0.88f
        val factor = targetDim.toFloat() / cropSizePx

        val srcW = rotatedSrc.width.toFloat()
        val srcH = rotatedSrc.height.toFloat()
        val baseScale = min(viewportDimPx / srcW, viewportDimPx / srcH)
        val finalScale = baseScale * zoom * factor

        val drawW = srcW * finalScale
        val drawH = srcH * finalScale

        val left = (targetDim - drawW) / 2f + (pan.x * factor)
        val top = (targetDim - drawH) / 2f + (pan.y * factor)

        val composed = Bitmap.createBitmap(targetDim, targetDim, Bitmap.Config.ARGB_8888)
        val compCanvas = Canvas(composed)
        compCanvas.drawColor(android.graphics.Color.WHITE)
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val dstRect = RectF(left, top, left + drawW, top + drawH)
        compCanvas.drawBitmap(rotatedSrc, null, dstRect, imgPaint)

        val finalBitmap = if (isCircle) {
            val masked = Bitmap.createBitmap(targetDim, targetDim, Bitmap.Config.ARGB_8888)
            val maskCanvas = Canvas(masked)
            val shader = BitmapShader(composed, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                this.shader = shader
            }
            val radius = targetDim / 2f
            maskCanvas.drawCircle(radius, radius, radius, circlePaint)
            composed.recycle()
            masked
        } else {
            composed
        }

        val safePrefix = filePrefix.ifBlank { "photo" }
        val file = File(context.filesDir, "${safePrefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        finalBitmap.recycle()
        if (rotatedSrc != src) rotatedSrc.recycle()

        Uri.fromFile(file).toString()
    } catch (_: Exception) {
        null
    }
}
