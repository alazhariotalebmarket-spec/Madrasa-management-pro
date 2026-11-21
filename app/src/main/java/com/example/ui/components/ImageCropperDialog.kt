package com.example.ui.components

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.media.ExifInterface
import com.example.ui.theme.MadrasaAccent
import com.example.ui.theme.MadrasaPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    onCropCompleted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var rotationAngle by remember { mutableStateOf(0) } // 0, 90, 180, 270
    var isCircular by remember { mutableStateOf(false) } // false = square, true = circle
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Load and auto-orient bitmap on background dispatcher
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                var stream: InputStream? = context.contentResolver.openInputStream(imageUri)
                val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, boundsOnly)
                stream?.close()

                var sample = 1
                val maxDim = 1600
                while (boundsOnly.outWidth / sample > maxDim || boundsOnly.outHeight / sample > maxDim) {
                    sample *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                stream = context.contentResolver.openInputStream(imageUri)
                var rawBmp = BitmapFactory.decodeStream(stream, null, decodeOpts)
                stream?.close()

                if (rawBmp != null) {
                    // Check orientation
                    try {
                        val exifStream = context.contentResolver.openInputStream(imageUri)
                        if (exifStream != null) {
                            val exif = ExifInterface(exifStream)
                            val orient = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                            exifStream.close()
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
        onDismissRequest = onDismiss,
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
                    text = "✂️ ছবি ক্রপ ও সাইজ নির্ধারণ",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ছবিটি প্রয়োজনমতো ঘোরান, গোল বা চারকোনা করুন এবং জুম করুন",
                    fontSize = 11.sp,
                    color = Color(0xFFD4AF37)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading || loadedBitmap == null) {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .background(Color.Black, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MadrasaPrimary)
                    }
                } else {
                    val bmp = loadedBitmap!!
                    val composeImage = remember(bmp, rotationAngle) {
                        if (rotationAngle != 0) {
                            val matrix = Matrix().apply { postRotate(rotationAngle.toFloat()) }
                            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true).asImageBitmap()
                        } else {
                            bmp.asImageBitmap()
                        }
                    }

                    // Interactive Viewport
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    panOffset += dragAmount
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
                                dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
                            )

                            // Draw Crop Mask Overlay
                            val cropSize = canvasW * 0.88f
                            val cropLeft = (canvasW - cropSize) / 2f
                            val cropTop = (canvasH - cropSize) / 2f

                            if (isCircular) {
                                val radius = cropSize / 2f
                                val center = Offset(canvasW / 2f, canvasH / 2f)

                                // Guide ring
                                drawCircle(
                                    color = Color(0xFFD4AF37),
                                    radius = radius,
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                )
                            } else {
                                // Square guide
                                drawRect(
                                    color = Color(0xFFD4AF37),
                                    topLeft = Offset(cropLeft, cropTop),
                                    size = androidx.compose.ui.geometry.Size(cropSize, cropSize),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
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

                // Zoom Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔍 জুম:", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = zoomScale,
                        onValueChange = { zoomScale = it },
                        valueRange = 1f..3f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFD4AF37),
                            activeTrackColor = MadrasaPrimary
                        )
                    )
                    Text(text = "%.1fx".format(zoomScale), color = Color(0xFFD4AF37), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Confirm / Cancel Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                    ) {
                        Text("বাতিল")
                    }

                    Button(
                        onClick = {
                            val bmp = loadedBitmap
                            if (bmp != null) {
                                val savedPath = saveCroppedBitmap(
                                    context = context,
                                    src = bmp,
                                    rotation = rotationAngle,
                                    zoom = zoomScale,
                                    pan = panOffset,
                                    isCircle = isCircular
                                )
                                if (savedPath != null) {
                                    Toast.makeText(context, "ছবি সফলভাবে ক্রপ ও সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                                    onCropCompleted(savedPath)
                                } else {
                                    Toast.makeText(context, "ছবি সেভ করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary)
                    ) {
                        Text("💾 ক্রপ ও সেভ", fontWeight = FontWeight.Bold, color = Color.White)
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
    isCircle: Boolean
): String? {
    return try {
        // Prepare rotated source
        val rotatedSrc = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } else {
            src
        }

        val targetDim = 512
        val output = Bitmap.createBitmap(targetDim, targetDim, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Viewport dimensions in canvas (260dp box equivalent)
        val viewportDim = 260f
        val cropSize = viewportDim * 0.88f
        val factor = targetDim.toFloat() / cropSize

        val srcW = rotatedSrc.width.toFloat()
        val srcH = rotatedSrc.height.toFloat()
        val baseScale = min(viewportDim / srcW, viewportDim / srcH)
        val finalScale = baseScale * zoom * factor

        val drawW = srcW * finalScale
        val drawH = srcH * finalScale

        val centerOffsetX = (viewportDim / 2f - cropSize / 2f) * factor
        val centerOffsetY = (viewportDim / 2f - cropSize / 2f) * factor

        val left = (targetDim - drawW) / 2f + (pan.x * factor)
        val top = (targetDim - drawH) / 2f + (pan.y * factor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        if (isCircle) {
            val path = Path().apply {
                addCircle(targetDim / 2f, targetDim / 2f, targetDim / 2f, Path.Direction.CCW)
            }
            canvas.clipPath(path)
        }

        canvas.drawColor(android.graphics.Color.WHITE)
        val dstRect = RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(rotatedSrc, null, dstRect, paint)

        val file = File(context.filesDir, "photo_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            output.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        output.recycle()
        if (rotatedSrc != src) rotatedSrc.recycle()

        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        null
    }
}
