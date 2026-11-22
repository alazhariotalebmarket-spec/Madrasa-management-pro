package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.MadrasaInfoStore
import com.example.data.entity.NoticeEntity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

object NoticeImageUtil {

    private val imageExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val MADRASA_NAME = "ইসহাকিয়া কেরাতুল কোরআন নূরানী ও হাফেজিয়া মাদ্রাসা"
    private const val MADRASA_ADDRESS = "পশ্চিম তুষখালী, ছোটবিঘাই, পটুয়াখালী সদর, পটুয়াখালী"
    private const val MADRASA_PHONE = "+8801726541607"
    private const val DEVELOPER_FOOTER = "প্রস্তুতকারী: মোঃ ফেরদাউস মোল্লা (+8801934411199)"

    private fun buildLayout(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        spacingMult: Float = 1.3f,
        spacingAdd: Float = 8f
    ): StaticLayout {
        val safeWidth = widthPx.coerceAtLeast(40)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
                .setAlignment(alignment)
                .setLineSpacing(spacingAdd, spacingMult)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, safeWidth, alignment, spacingMult, spacingAdd, false)
        }
    }

    /**
     * Generates a high-resolution Notice poster bitmap (1080px wide, dynamic height >= 1440px)
     * with full text wrapping and safe margins so no text ever overflows or clips.
     */
    fun createNoticePosterBitmap(context: Context, notice: NoticeEntity): Bitmap {
        val width = 1080
        val safeMargin = 68f // ~12mm proportional margin on 1080px poster
        val innerLeft = safeMargin
        val innerRight = width - safeMargin
        val contentWidth = (innerRight - innerLeft).toInt()

        // Pre-measure header text
        val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 31f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE2E8F0.toInt()
            textSize = 22f
        }
        val madrasaInfo = MadrasaInfoStore.getInfo(context)
        val headerTextWidth = (width - 65f - 140f - 55f).toInt()
        val headerTitleLayout = buildLayout(madrasaInfo.name, headerTitlePaint, headerTextWidth, spacingMult = 1.15f, spacingAdd = 4f)
        val headerSubLayout = buildLayout(madrasaInfo.address, headerSubPaint, headerTextWidth, spacingMult = 1.15f, spacingAdd = 2f)

        val headerContentHeight = headerTitleLayout.height + headerSubLayout.height + 46f
        val headerBottom = maxOf(230f, 64f + headerContentHeight + 24f)
        val ribbonBottom = headerBottom + 56f
        val dateRowY = ribbonBottom + 48f
        val dividerY = dateRowY + 22f
        val titleBoxTop = dividerY + 22f

        // Pre-measure wrapped notice title
        val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A5C36.toInt()
            textSize = 29f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = buildLayout(
            notice.title.ifBlank { "জরুরি নোটিশ" },
            titleTextPaint,
            contentWidth - 40,
            Layout.Alignment.ALIGN_CENTER,
            1.2f,
            4f
        )
        val titleBoxBottom = titleBoxTop + maxOf(76f, titleLayout.height + 36f)
        val bodyTop = titleBoxBottom + 36f

        // Pre-measure wrapped notice body
        val bodyTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            textSize = 27f
        }
        val bodyLayout = buildLayout(
            notice.description.ifBlank { "বিস্তারিত তথ্য অফিস কক্ষ থেকে জেনে নিন।" },
            bodyTextPaint,
            contentWidth - 20,
            Layout.Alignment.ALIGN_NORMAL,
            1.35f,
            10f
        )

        // Compute dynamic height so long notices never clip or collide with signatures
        val minContentEnd = bodyTop + bodyLayout.height + 40f
        val height = maxOf(1440, (minContentEnd + 240f).toInt())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.WHITE })

        // 2. Outer Ornamental Borders
        val outerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A5C36.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawRoundRect(25f, 25f, width - 25f, height - 25f, 28f, 28f, outerBorder)

        val innerGoldBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD4AF37.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(35f, 35f, width - 35f, height - 35f, 22f, 22f, innerGoldBorder)

        // 3. Header Background
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A5C36.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, 40f, width - 40f, headerBottom, 18f, 18f, headerPaint)

        // Madrasa Logo
        try {
            val srcLogo = BitmapFactory.decodeResource(context.resources, R.drawable.madrasa_logo_round)
            if (srcLogo != null) {
                val logoSize = 128
                val scaledLogo = Bitmap.createScaledBitmap(srcLogo, logoSize, logoSize, true)
                val logoTop = 40f + (headerBottom - 40f - logoSize) / 2f
                canvas.drawBitmap(scaledLogo, 62f, logoTop, null)
                if (scaledLogo != srcLogo) srcLogo.recycle()
                scaledLogo.recycle()
            }
        } catch (_: Exception) {}

        // Header Text (Wrapped safely inside header bounds)
        var hy = 66f
        canvas.save()
        canvas.translate(208f, hy)
        headerTitleLayout.draw(canvas)
        canvas.restore()
        hy += headerTitleLayout.height + 6f

        canvas.save()
        canvas.translate(208f, hy)
        headerSubLayout.draw(canvas)
        canvas.restore()
        hy += headerSubLayout.height + 8f

        val headerPhonePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD4AF37.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("মোবাইল: ${madrasaInfo.phone}", 208f, hy + 18f, headerPhonePaint)

        // 4. Gold Ribbon Bar
        canvas.drawRect(40f, headerBottom, width - 40f, ribbonBottom, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD4AF37.toInt()
        })

        val ribbonText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF063D23.toInt()
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("📢 জরুরি নোটিশ / বিজ্ঞপ্তি 📢", width / 2f, headerBottom + 37f, ribbonText)

        // 5. Date & Notice No
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF444444.toInt()
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("স্মারক নং: #IKM-N${notice.id}", innerLeft, dateRowY, datePaint)

        val dateRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A5C36.toInt()
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("তারিখ: ${notice.date}", innerRight, dateRowY, dateRightPaint)

        canvas.drawLine(innerLeft, dividerY, innerRight, dividerY, Paint().apply {
            color = 0xFFE2E8F0.toInt()
            strokeWidth = 2f
        })

        // 6. Title Box (Wrapped)
        canvas.drawRoundRect(innerLeft, titleBoxTop, innerRight, titleBoxBottom, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEAF7EF.toInt()
        })
        canvas.drawRoundRect(innerLeft, titleBoxTop, innerRight, titleBoxBottom, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A5C36.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        })

        val titleDrawY = titleBoxTop + ((titleBoxBottom - titleBoxTop) - titleLayout.height) / 2f
        canvas.save()
        canvas.translate(innerLeft + 20f, titleDrawY)
        titleLayout.draw(canvas)
        canvas.restore()

        // 7. Notice Description (Wrapped safely inside inner margin)
        canvas.save()
        canvas.translate(innerLeft + 10f, bodyTop)
        bodyLayout.draw(canvas)
        canvas.restore()

        // 8. Signatures Block (Anchored safely at bottom of dynamic height)
        val sigLineY = height - 165f
        val sigLine = Paint().apply {
            color = 0xFF333333.toInt()
            strokeWidth = 2f
        }
        canvas.drawLine(100f, sigLineY, 360f, sigLineY, sigLine)
        canvas.drawLine(width - 360f, sigLineY, width - 100f, sigLineY, sigLine)

        val sigText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF222222.toInt()
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("আহ্বায়ক / প্রচার সম্পাদক", 230f, sigLineY + 34f, sigText)
        canvas.drawText("মুহতামিমের সীল ও স্বাক্ষর", width - 230f, sigLineY + 34f, sigText)

        // Seal Box
        val sealBox = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD4AF37.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(width - 320f, sigLineY - 100f, width - 140f, sigLineY - 14f, 12f, 12f, sealBox)

        val sealText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF996515.toInt()
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("মাদ্রাসার সীল", width - 230f, sigLineY - 52f, sealText)

        // 9. Footer
        val footerPaint = Paint().apply { color = 0xFF0A5C36.toInt() }
        canvas.drawRoundRect(40f, height - 75f, width - 40f, height - 40f, 10f, 10f, footerPaint)

        val footerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${madrasaInfo.name} • $DEVELOPER_FOOTER", width / 2f, height - 51f, footerText)

        return bitmap
    }

    /**
     * Saves the notice photo directly into Android MediaStore Pictures/MadrasaNotices (Gallery)
     * AND Downloads/MadrasaPro folder, then launches the system share/viewer chooser.
     */
    fun saveAndShareNoticePhoto(context: Context, notice: NoticeEntity) {
        val appContext = context.applicationContext
        Toast.makeText(context, "নোটিশ ফটো তৈরি হচ্ছে...", Toast.LENGTH_SHORT).show()
        imageExecutor.execute {
            var bitmap: Bitmap? = null
            try {
                bitmap = createNoticePosterBitmap(appContext, notice)
                val fileName = "Notice_${notice.id}_${notice.date.replace("/", "-")}.png"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MadrasaNotices")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = appContext.contentResolver
                    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val itemUri = resolver.insert(collection, values)
                    if (itemUri != null) {
                        resolver.openOutputStream(itemUri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(itemUri, values, null, null)
                    }
                }

                val cacheFile = File(appContext.cacheDir, fileName)
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                PdfPrintUtil.saveFileToDownloads(appContext, cacheFile, fileName, "image/png")

                val shareUri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    cacheFile
                )

                val madrasaInfo = MadrasaInfoStore.getInfo(appContext)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, "মাদ্রাসা নোটিশ - ${notice.title}")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${madrasaInfo.name}\nনোটিশ: ${notice.title}\nতারিখ: ${notice.date}\n\n${notice.description}"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                mainHandler.post {
                    Toast.makeText(
                        appContext,
                        "হাই-রেজুলেশন নোটিশ ফটো গ্যালারি ও Downloads ফোল্ডারে সেভ হয়েছে!",
                        Toast.LENGTH_LONG
                    ).show()
                    try {
                        val chooser = Intent.createChooser(shareIntent, "নোটিশ ফটো শেয়ার বা ওপেন করুন").apply {
                            if (context !is android.app.Activity) {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        context.startActivity(chooser)
                    } catch (_: Exception) {
                        try {
                            val chooser = Intent.createChooser(shareIntent, "নোটিশ ফটো শেয়ার বা ওপেন করুন")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            appContext.startActivity(chooser)
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (e: Throwable) {
                mainHandler.post {
                    Toast.makeText(appContext, "ফটো ডাউনলোড করতে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                try {
                    bitmap?.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }
}
