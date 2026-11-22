package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.MadrasaInfoStore
import com.example.data.entity.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

data class MeritPdfEntry(
    val rankLabel: String,
    val roll: String,
    val name: String,
    val cls: String,
    val totalMarks: Double,
    val maxMarks: Int,
    val avg: Double,
    val grade: String,
    val passed: Boolean
)

object PdfPrintUtil {

    private const val MADRASA_NAME = "ইসহাকিয়া কেরাতুল কোরআন নূরানী ও হাফেজিয়া মাদ্রাসা"
    private const val MADRASA_ADDRESS = "পশ্চিম তুষখালী, ছোটবিঘাই, পটুয়াখালী সদর, পটুয়াখালী"
    private const val MADRASA_PHONE = "+8801726541607"
    private const val DEVELOPER_FOOTER = "প্রস্তুতকারী: মোঃ ফেরদাউস মোল্লা (+8801934411199)"

    // Standard A4 (210mm x 297mm) at 72 DPI = 595 x 842 pt
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    // 12mm margin = (12 / 25.4) * 72 = 34.01 pt
    private const val MARGIN_12MM = 34f
    private const val LEFT = MARGIN_12MM
    private const val RIGHT = A4_WIDTH - MARGIN_12MM // 561f
    private const val CONTENT_W = RIGHT - LEFT       // 527f
    private const val MAX_CONTENT_Y = A4_HEIGHT - MARGIN_12MM - 30f // 778f (room for footer)
    private const val FOOTER_Y = A4_HEIGHT - MARGIN_12MM - 8f       // 800f

    // CR80 ID Card dimensions:
    // Trim: 54mm x 85.6mm = 2.125" x 3.375" = 638 x 1012 px at 300 DPI
    // Bleed: 2mm per side (~24 px at 300 DPI) => Full bleed: 58mm x 89.6mm = 686 x 1060 px at 300 DPI
    private const val CR80_BLEED_PX = 24f
    private const val CR80_FULL_W_PX = 686
    private const val CR80_FULL_H_PX = 1060
    private const val CR80_TRIM_W_PX = 638f
    private const val CR80_TRIM_H_PX = 1012f
    // In 72 DPI points: 58mm = 164.4pt (~165pt), 89.6mm = 254pt
    private const val CR80_PDF_W_PT = 165
    private const val CR80_PDF_H_PT = 254

    private val highQualityBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    private fun loadHighResMadrasaLogo(context: Context, maxPx: Int = 512): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val src = BitmapFactory.decodeResource(context.resources, R.drawable.madrasa_logo_round, opts)
                ?: return null
            if (src.width > maxPx || src.height > maxPx) {
                Bitmap.createScaledBitmap(src, maxPx, maxPx, true)
            } else {
                src
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadMadrasaLogo(context: Context, sizePx: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val src = BitmapFactory.decodeResource(context.resources, R.drawable.madrasa_logo_round, opts)
            src?.let { Bitmap.createScaledBitmap(it, sizePx, sizePx, true) }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadPhotoBitmap(context: Context, photoUriStr: String, sizePx: Int): Bitmap? {
        if (photoUriStr.isBlank()) return null
        return try {
            val uri = Uri.parse(photoUriStr)
            val stream: InputStream? = if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
                FileInputStream(File(uri.path ?: photoUriStr))
            } else {
                context.contentResolver.openInputStream(uri)
            }
            val src = BitmapFactory.decodeStream(stream)
            stream?.close()
            src?.let { Bitmap.createScaledBitmap(it, sizePx, sizePx, true) }
        } catch (_: Exception) {
            null
        }
    }

    private fun createCircularBitmap(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, null, rect, paint)
        return output
    }

    private fun makeStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        lineSpacingMult: Float = 1.15f,
        lineSpacingAdd: Float = 1.5f
    ): StaticLayout {
        val safeW = widthPx.coerceAtLeast(24)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, safeW)
                .setAlignment(alignment)
                .setLineSpacing(lineSpacingAdd, lineSpacingMult)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, safeW, alignment, lineSpacingMult, lineSpacingAdd, false)
        }
    }

    private fun Canvas.drawLayoutAt(layout: StaticLayout, x: Float, y: Float) {
        save()
        translate(x, y)
        layout.draw(this)
        restore()
    }

    /**
     * Multi-page A4 PDF Cursor enforcing 12mm margins, wrapped text, repeating headers, and auto page breaks.
     */
    private class A4PageCursor(
        private val context: Context,
        private val doc: PdfDocument,
        private val reportBannerTitle: String
    ) {
        var pageNum = 0
            private set
        lateinit var page: PdfDocument.Page
            private set
        lateinit var canvas: Canvas
            private set
        var y = MARGIN_12MM

        private val logoBmp = loadHighResMadrasaLogo(context, 480)
        private val madrasaInfo = MadrasaInfoStore.getInfo(context)

        init {
            startNextPage(isFirstPage = true)
        }

        private fun drawFooterOnCurrentPage() {
            val linePaint = Paint().apply {
                color = 0xFFD0D7DE.toInt()
                strokeWidth = 0.7f
            }
            canvas.drawLine(LEFT, FOOTER_Y - 12f, RIGHT, FOOTER_Y - 12f, linePaint)

            val footLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF64748B.toInt()
                textSize = 8.5f
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText(DEVELOPER_FOOTER, LEFT, FOOTER_Y, footLeft)

            val footRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A5C36.toInt()
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("পৃষ্ঠা $pageNum", RIGHT, FOOTER_Y, footRight)
        }

        private fun startNextPage(isFirstPage: Boolean) {
            pageNum++
            val info = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create()
            page = doc.startPage(info)
            canvas = page.canvas

            // White Page Background
            canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), A4_HEIGHT.toFloat(), Paint().apply { color = Color.WHITE })

            // Subtle outer border strictly outside 12mm content margin (at 24pt, leaving 10pt padding to 34pt margin)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A5C36.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
            }
            canvas.drawRoundRect(24f, 24f, A4_WIDTH - 24f, A4_HEIGHT - 24f, 10f, 10f, borderPaint)

            if (isFirstPage) {
                drawFullHeader()
            } else {
                drawContinuationHeader()
            }
        }

        private fun drawFullHeader() {
            val headerTop = MARGIN_12MM
            val headerTextStart = if (logoBmp != null) LEFT + 64f else LEFT + 12f
            val headerTextW = (RIGHT - headerTextStart - 12f).toInt()

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 14.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val addrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE2E8F0.toInt()
                textSize = 10f
            }
            val titleLayout = makeStaticLayout(madrasaInfo.name, titlePaint, headerTextW, Layout.Alignment.ALIGN_CENTER, 1.1f, 1f)
            val addrLayout = makeStaticLayout(madrasaInfo.address, addrPaint, headerTextW, Layout.Alignment.ALIGN_CENTER, 1.1f, 1f)

            val headerHeight = maxOf(68f, titleLayout.height + addrLayout.height + 34f)
            val headerBottom = headerTop + headerHeight

            canvas.drawRoundRect(LEFT, headerTop, RIGHT, headerBottom, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A5C36.toInt()
            })

            if (logoBmp != null) {
                val logoLeft = LEFT + 10f
                val logoTop = headerTop + (headerHeight - 48f) / 2f
                canvas.drawBitmap(logoBmp, null, RectF(logoLeft, logoTop, logoLeft + 48f, logoTop + 48f), highQualityBitmapPaint)
            }

            var ty = headerTop + 10f
            canvas.drawLayoutAt(titleLayout, headerTextStart, ty)
            ty += titleLayout.height + 3f
            canvas.drawLayoutAt(addrLayout, headerTextStart, ty)
            ty += addrLayout.height + 4f

            val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFD4AF37.toInt()
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("মোবাইল: ${madrasaInfo.phone}", headerTextStart + headerTextW / 2f, ty + 9f, phonePaint)

            // Gold Subtitle Ribbon (Wrapped)
            val ribbonPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF063D23.toInt()
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val ribbonLayout = makeStaticLayout(
                reportBannerTitle,
                ribbonPaint,
                (CONTENT_W - 20f).toInt(),
                Layout.Alignment.ALIGN_CENTER,
                1.1f,
                1f
            )
            val ribbonTop = headerBottom + 4f
            val ribbonH = maxOf(24f, ribbonLayout.height + 10f)
            val ribbonBottom = ribbonTop + ribbonH

            canvas.drawRoundRect(LEFT, ribbonTop, RIGHT, ribbonBottom, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFD4AF37.toInt()
            })
            canvas.drawLayoutAt(ribbonLayout, LEFT + 10f, ribbonTop + (ribbonH - ribbonLayout.height) / 2f)

            y = ribbonBottom + 12f
        }

        private fun drawContinuationHeader() {
            val top = MARGIN_12MM
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A5C36.toInt()
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val layout = makeStaticLayout(
                "${madrasaInfo.name} — $reportBannerTitle (পৃষ্ঠা $pageNum)",
                textPaint,
                (CONTENT_W - 16f).toInt(),
                Layout.Alignment.ALIGN_CENTER,
                1.1f,
                1f
            )
            val boxH = maxOf(24f, layout.height + 10f)
            canvas.drawRoundRect(LEFT, top, RIGHT, top + boxH, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFEAF7EF.toInt()
            })
            canvas.drawLayoutAt(layout, LEFT + 8f, top + (boxH - layout.height) / 2f)
            y = top + boxH + 10f
        }

        fun ensureSpace(neededHeight: Float, onNewPage: (() -> Unit)? = null) {
            if (y + neededHeight > MAX_CONTENT_Y) {
                drawFooterOnCurrentPage()
                doc.finishPage(page)
                startNextPage(isFirstPage = false)
                onNewPage?.invoke()
            }
        }

        fun drawSignatures(leftLabel: String, rightLabel: String) {
            ensureSpace(70f)
            y += 32f
            val sigLine = Paint().apply {
                color = 0xFF333333.toInt()
                strokeWidth = 1f
            }
            canvas.drawLine(LEFT + 20f, y, LEFT + 170f, y, sigLine)
            canvas.drawLine(RIGHT - 170f, y, RIGHT - 20f, y, sigLine)

            val sigText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF333333.toInt()
                textSize = 10.5f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(leftLabel, LEFT + 95f, y + 15f, sigText)
            canvas.drawText(rightLabel, RIGHT - 95f, y + 15f, sigText)
            y += 28f
        }

        fun finishDocument() {
            drawFooterOnCurrentPage()
            doc.finishPage(page)
        }
    }

    private data class TableCol(
        val title: String,
        val width: Float,
        val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    )

    private fun drawTableHeader(cursor: A4PageCursor, cols: List<TableCol>, bgColor: Int = 0xFF0A5C36.toInt()) {
        val thPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val layouts = cols.map { col ->
            makeStaticLayout(col.title, thPaint, (col.width - 8f).toInt(), col.alignment, 1.05f, 0f)
        }
        val rowH = maxOf(26f, (layouts.maxOfOrNull { it.height } ?: 14) + 12f)
        cursor.ensureSpace(rowH)

        cursor.canvas.drawRect(LEFT, cursor.y, RIGHT, cursor.y + rowH, Paint().apply { color = bgColor })
        var x = LEFT
        cols.forEachIndexed { idx, col ->
            val l = layouts[idx]
            val drawY = cursor.y + (rowH - l.height) / 2f
            cursor.canvas.drawLayoutAt(l, x + 4f, drawY)
            x += col.width
        }
        cursor.y += rowH
    }

    private fun drawWrappedTableRow(
        cursor: A4PageCursor,
        cols: List<TableCol>,
        values: List<String>,
        textColors: List<Int?> = emptyList(),
        boldCols: Set<Int> = emptySet(),
        isEvenRow: Boolean,
        repeatHeader: Boolean = true
    ) {
        val layouts = cols.mapIndexed { idx, col ->
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColors.getOrNull(idx) ?: 0xFF1F2937.toInt()
                textSize = 10f
                if (boldCols.contains(idx)) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            makeStaticLayout(values.getOrElse(idx) { "" }, tp, (col.width - 8f).toInt(), col.alignment, 1.1f, 1f)
        }

        val rowH = maxOf(22f, (layouts.maxOfOrNull { it.height } ?: 14) + 10f)
        cursor.ensureSpace(rowH) {
            if (repeatHeader) {
                drawTableHeader(cursor, cols)
            }
        }

        val bg = if (isEvenRow) 0xFFF8FBF9.toInt() else Color.WHITE
        cursor.canvas.drawRect(LEFT, cursor.y, RIGHT, cursor.y + rowH, Paint().apply { color = bg })

        val borderPaint = Paint().apply {
            color = 0xFFDDE5DF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        cursor.canvas.drawRect(LEFT, cursor.y, RIGHT, cursor.y + rowH, borderPaint)

        var x = LEFT
        cols.forEachIndexed { idx, col ->
            if (idx > 0) {
                cursor.canvas.drawLine(x, cursor.y, x, cursor.y + rowH, borderPaint)
            }
            val l = layouts[idx]
            val drawY = cursor.y + (rowH - l.height) / 2f
            cursor.canvas.drawLayoutAt(l, x + 4f, drawY)
            x += col.width
        }
        cursor.y += rowH
    }

    // -------------------------------------------------------------------------
    // 1. Single Exam Result Card PDF (A4, 12mm margin, wrap_text, auto page-break)
    // -------------------------------------------------------------------------
    fun generateAndShareResultPdf(
        context: Context,
        student: StudentEntity?,
        result: ResultEntity,
        meritRank: String = "১ম",
        gradeCalculator: (Double) -> String
    ) {
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "${result.exam} - মার্কশিট ও রেজাল্ট কার্ড (মেধাস্থান: $meritRank)")

            // Student Info Card (wrapped)
            drawStudentInfoCard(cursor, context, student, "মেধাস্থান: $meritRank  •  তারিখ: ${result.date}")

            val cols = listOf(
                TableCol("বিষয়", 247f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("পূর্ণমান (পাশ ৩৩)", 110f, Layout.Alignment.ALIGN_CENTER),
                TableCol("প্রাপ্ত নম্বর", 95f, Layout.Alignment.ALIGN_CENTER),
                TableCol("গ্রেড", 75f, Layout.Alignment.ALIGN_CENTER)
            )
            drawTableHeader(cursor, cols)

            val subjects = result.getSubjectList()
            subjects.forEachIndexed { i, sub ->
                val subPass = sub.mark >= 33.0
                val grade = gradeCalculator(sub.mark)
                val markColor = if (subPass) 0xFF0A5C36.toInt() else 0xFFE74C3C.toInt()
                drawWrappedTableRow(
                    cursor = cursor,
                    cols = cols,
                    values = listOf(sub.name, "১০০", "${sub.mark.toInt()}", grade),
                    textColors = listOf(0xFF1F2937.toInt(), 0xFF475569.toInt(), markColor, markColor),
                    boldCols = setOf(2, 3),
                    isEvenRow = i % 2 == 0
                )
            }

            // Summary Box
            cursor.y += 12f
            val isPass = result.isAllPassed(33.0)
            val overallGrade = if (isPass) gradeCalculator(result.avg) else "F"
            val maxMarks = (subjects.size * 100).coerceAtLeast(100)
            val passText = if (isPass) "উত্তীর্ণ (PASS) • $overallGrade" else "অনুত্তীর্ণ (FAIL) • $overallGrade"

            drawWrappedSummaryBanner(
                cursor = cursor,
                title = "মোট প্রাপ্ত নম্বর: ${result.total.toInt()} / $maxMarks  •  মেধাস্থান: $meritRank",
                subtitle = "গড় নম্বর: ${"%.1f".format(result.avg)}%  •  পাশ মার্ক: ৩৩",
                badgeText = passText,
                isSuccess = isPass
            )

            cursor.drawSignatures("শ্রেণী শিক্ষকের স্বাক্ষর", "মুহতামিমের সীল ও স্বাক্ষর")
            cursor.finishDocument()

            val safeName = (student?.name ?: "student").replace("\\s+".toRegex(), "_")
            val pdfFile = File(context.cacheDir, "Result_${safeName}_${result.exam}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "রেজাল্ট কার্ড - ${student?.name}")
        } catch (e: Exception) {
            Toast.makeText(context, "পিডিএফ তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 2. 3-Exam Combined Result Sheet PDF
    // -------------------------------------------------------------------------
    fun generateAndShareCombinedResultPdf(
        context: Context,
        student: StudentEntity?,
        results: List<ResultEntity>,
        gradeCalculator: (Double) -> String
    ) {
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "সম্মিলিত ফলাফল ও বার্ষিক প্রোগ্রেস রিপোর্ট (১ম, ২য় ও বার্ষিক পরীক্ষা)")

            drawStudentInfoCard(cursor, context, student, "মোবাইল: ${student?.phone?.ifEmpty { "নেই" } ?: "নেই"}")

            val exam1 = results.find { it.exam.contains("১ম") }
            val exam2 = results.find { it.exam.contains("২য়") }
            val exam3 = results.find { it.exam.contains("বার্ষিক") } ?: results.firstOrNull { it != exam1 && it != exam2 }

            val subjectNames = linkedSetOf<String>()
            exam1?.getSubjectList()?.forEach { subjectNames.add(it.name) }
            exam2?.getSubjectList()?.forEach { subjectNames.add(it.name) }
            exam3?.getSubjectList()?.forEach { subjectNames.add(it.name) }
            if (subjectNames.isEmpty()) {
                subjectNames.addAll(listOf("কুরআন", "আরবি", "বাংলা", "গণিত", "ইংরেজি"))
            }

            // Total width = 527f
            val cols = listOf(
                TableCol("বিষয়", 142f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("১ম সাময়িক", 70f, Layout.Alignment.ALIGN_CENTER),
                TableCol("২য় সাময়িক", 70f, Layout.Alignment.ALIGN_CENTER),
                TableCol("বার্ষিক", 65f, Layout.Alignment.ALIGN_CENTER),
                TableCol("মোট", 65f, Layout.Alignment.ALIGN_CENTER),
                TableCol("গড়", 60f, Layout.Alignment.ALIGN_CENTER),
                TableCol("গ্রেড", 55f, Layout.Alignment.ALIGN_CENTER)
            )
            drawTableHeader(cursor, cols)

            var grandTotal = 0.0
            var validExamCount = 0
            if (exam1 != null) validExamCount++
            if (exam2 != null) validExamCount++
            if (exam3 != null) validExamCount++
            val divisor = maxOf(1, validExamCount)
            var allSubjectsPassed = true

            subjectNames.forEachIndexed { i, subName ->
                val m1 = exam1?.getSubjectList()?.find { it.name == subName }?.mark
                val m2 = exam2?.getSubjectList()?.find { it.name == subName }?.mark
                val m3 = exam3?.getSubjectList()?.find { it.name == subName }?.mark

                val subSum = (m1 ?: 0.0) + (m2 ?: 0.0) + (m3 ?: 0.0)
                val subAvg = subSum / divisor
                grandTotal += subSum
                if (subAvg < 33.0) allSubjectsPassed = false

                val grade = gradeCalculator(subAvg)
                val gradeColor = if (subAvg >= 33.0) 0xFF0A5C36.toInt() else 0xFFE74C3C.toInt()

                drawWrappedTableRow(
                    cursor = cursor,
                    cols = cols,
                    values = listOf(
                        subName,
                        if (m1 != null) "${m1.toInt()}" else "-",
                        if (m2 != null) "${m2.toInt()}" else "-",
                        if (m3 != null) "${m3.toInt()}" else "-",
                        "${subSum.toInt()}",
                        "%.1f".format(subAvg),
                        grade
                    ),
                    textColors = listOf(null, null, null, null, 0xFF0A5C36.toInt(), null, gradeColor),
                    boldCols = setOf(4, 6),
                    isEvenRow = i % 2 == 0
                )
            }

            cursor.y += 12f
            val maxPossible = subjectNames.size * 100 * divisor
            val overallAvg = if (maxPossible > 0) (grandTotal / maxPossible) * 100 else 0.0
            val isOverallPass = allSubjectsPassed && overallAvg >= 33.0
            val overallGrade = if (isOverallPass) gradeCalculator(overallAvg) else "F"

            drawWrappedSummaryBanner(
                cursor = cursor,
                title = "সর্বমোট নম্বর: ${grandTotal.toInt()} / $maxPossible  •  গড়: ${"%.1f".format(overallAvg)}%",
                subtitle = "পরীক্ষা সংখ্যা: $validExamCount টি  •  ফলাফল: $overallGrade (পাশ মার্ক: ৩৩)",
                badgeText = if (isOverallPass) "উত্তীর্ণ (PASS) • $overallGrade" else "অনুত্তীর্ণ (FAIL) • $overallGrade",
                isSuccess = isOverallPass
            )

            cursor.drawSignatures("শ্রেণী শিক্ষকের স্বাক্ষর", "মুহতামিমের সীল ও স্বাক্ষর")
            cursor.finishDocument()

            val safeName = (student?.name ?: "student").replace("\\s+".toRegex(), "_")
            val pdfFile = File(context.cacheDir, "Combined_Result_${safeName}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "সমন্বিত রেজাল্ট - ${student?.name}")
        } catch (e: Exception) {
            Toast.makeText(context, "সমন্বিত রেজাল্ট তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 3. Monthly & Yearly Fee Report PDF (ALL students, multi-page, wrapped)
    // -------------------------------------------------------------------------
    fun generateAndShareFeeReportPdf(
        context: Context,
        period: String,
        isYearly: Boolean,
        students: List<StudentEntity>,
        fees: List<FeeEntity>
    ) {
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "ছাত্র-ছাত্রীদের বেতন ও বিদ্যুৎ বিল হিসাব বিবরণী • $period")

            // Total width = 527f
            val cols = listOf(
                TableCol("রোল", 42f, Layout.Alignment.ALIGN_CENTER),
                TableCol("ছাত্রের নাম", 125f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("শ্রেণী", 95f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("বেতন", 50f, Layout.Alignment.ALIGN_CENTER),
                TableCol("বিদ্যুৎ", 50f, Layout.Alignment.ALIGN_CENTER),
                TableCol("মোট ধার্য", 55f, Layout.Alignment.ALIGN_CENTER),
                TableCol("পরিশোধ", 55f, Layout.Alignment.ALIGN_CENTER),
                TableCol("বকেয়া", 55f, Layout.Alignment.ALIGN_CENTER)
            )
            drawTableHeader(cursor, cols)

            var sumExpected = 0.0
            var sumPaid = 0.0
            var sumDue = 0.0
            val mult = if (isYearly) 12.0 else 1.0

            if (students.isEmpty()) {
                drawWrappedTableRow(
                    cursor = cursor,
                    cols = listOf(TableCol("তথ্য", CONTENT_W, Layout.Alignment.ALIGN_CENTER)),
                    values = listOf("কোন ছাত্রের রেকর্ড পাওয়া যায়নি।"),
                    isEvenRow = true
                )
            } else {
                students.forEachIndexed { index, student ->
                    val studentFees = fees.filter { it.studentId == student.id }
                    val paid = studentFees.sumOf { it.amount }
                    val expected = (student.fee + student.electricBill) * mult
                    val due = maxOf(0.0, expected - paid)

                    sumExpected += expected
                    sumPaid += paid
                    sumDue += due

                    val dueColor = if (due > 0) 0xFFE74C3C.toInt() else 0xFF0A5C36.toInt()
                    drawWrappedTableRow(
                        cursor = cursor,
                        cols = cols,
                        values = listOf(
                            student.roll.ifEmpty { "${index + 1}" },
                            student.name,
                            student.cls,
                            "${student.fee.toInt()}",
                            "${student.electricBill.toInt()}",
                            "${expected.toInt()}",
                            "${paid.toInt()}",
                            "${due.toInt()}"
                        ),
                        textColors = listOf(null, null, null, null, null, null, 0xFF0A5C36.toInt(), dueColor),
                        boldCols = setOf(6, 7),
                        isEvenRow = index % 2 == 0
                    )
                }
            }

            cursor.y += 12f
            val rate = if (sumExpected > 0) "%.1f%%".format((sumPaid / sumExpected) * 100) else "০%"
            drawWrappedSummaryBanner(
                cursor = cursor,
                title = "সর্বমোট ধার্য: ${sumExpected.toInt()} ৳  •  সর্বমোট আদায়: ${sumPaid.toInt()} ৳  •  সর্বমোট বকেয়া: ${sumDue.toInt()} ৳",
                subtitle = "মোট শিক্ষার্থী: ${students.size} জন  •  আদায় হার: $rate",
                badgeText = if (sumDue <= 0) "১০০% পরিশোধ" else "বকেয়া ${sumDue.toInt()} ৳",
                isSuccess = sumDue <= 0
            )

            cursor.drawSignatures("হিসাবরক্ষকের স্বাক্ষর", "মুহতামিমের সীল ও স্বাক্ষর")
            cursor.finishDocument()

            val pdfFile = File(context.cacheDir, "Fee_Report_${period.replace("\\s+".toRegex(), "_")}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "বেতন ও ফি হিসাব রিপোর্ট - $period")
        } catch (e: Exception) {
            Toast.makeText(context, "রিপোর্ট তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 4. Teacher Salary Statement PDF
    // -------------------------------------------------------------------------
    fun generateAndShareTeacherSalaryStatementPdf(
        context: Context,
        teacher: TeacherEntity,
        payments: List<TeacherPaymentEntity>,
        period: String,
        isYearly: Boolean
    ) {
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "শিক্ষক বেতন ও আর্থিক বিবরণী • $period")

            drawGenericInfoCard(
                cursor = cursor,
                context = context,
                photoUri = teacher.photoUri,
                primaryTitle = "শিক্ষকের নাম: ${teacher.name}",
                secondaryTitle = "পদবী / বিষয়: ${teacher.subject}",
                line1 = "মাসিক নির্ধারিত বেতন: ${teacher.salary.toInt()} ৳",
                line2 = "মোবাইল: ${teacher.phone.ifEmpty { "নেই" }}  •  যোগদান: ${teacher.joinDate.ifEmpty { "নেই" }}",
                line3 = if (teacher.address.isNotBlank()) "ঠিকানা: ${teacher.address}" else ""
            )

            // Total width = 527f
            val cols = listOf(
                TableCol("ক্রমিক", 52f, Layout.Alignment.ALIGN_CENTER),
                TableCol("তারিখ", 95f, Layout.Alignment.ALIGN_CENTER),
                TableCol("মাস / বছর", 120f, Layout.Alignment.ALIGN_CENTER),
                TableCol("পরিশোধিত টাকা", 110f, Layout.Alignment.ALIGN_CENTER),
                TableCol("মন্তব্য / রশিদ", 150f, Layout.Alignment.ALIGN_NORMAL)
            )
            drawTableHeader(cursor, cols)

            var totalPaid = 0.0
            if (payments.isEmpty()) {
                drawWrappedTableRow(
                    cursor = cursor,
                    cols = listOf(TableCol("বিবরণ", CONTENT_W, Layout.Alignment.ALIGN_CENTER)),
                    values = listOf("কোন বেতন প্রদানের রেকর্ড পাওয়া যায়নি।"),
                    isEvenRow = true
                )
            } else {
                payments.forEachIndexed { idx, p ->
                    totalPaid += p.amount
                    drawWrappedTableRow(
                        cursor = cursor,
                        cols = cols,
                        values = listOf(
                            "${idx + 1}",
                            p.date.ifEmpty { "-" },
                            "${p.month} ${p.year}",
                            "${p.amount.toInt()} ৳",
                            p.note.ifEmpty { "নগদ পরিশোধ" }
                        ),
                        textColors = listOf(null, null, null, 0xFF0A5C36.toInt(), null),
                        boldCols = setOf(3),
                        isEvenRow = idx % 2 == 0
                    )
                }
            }

            cursor.y += 12f
            val expectedSalary = if (isYearly) teacher.salary * 12 else teacher.salary
            val due = maxOf(0.0, expectedSalary - totalPaid)

            drawWrappedSummaryBanner(
                cursor = cursor,
                title = "মোট প্রদেয় বেতন: ${expectedSalary.toInt()} ৳  •  মোট পরিশোধ: ${totalPaid.toInt()} ৳",
                subtitle = "বকেয়া বেতন: ${due.toInt()} ৳  •  সময়কাল: $period",
                badgeText = if (due <= 0) "বেতন পরিশোধিত" else "বকেয়া ${due.toInt()} ৳",
                isSuccess = due <= 0
            )

            cursor.drawSignatures("শিক্ষকের স্বাক্ষর", "মুহতামিমের সীল ও স্বাক্ষর")
            cursor.finishDocument()

            val safeName = teacher.name.replace("\\s+".toRegex(), "_")
            val pdfFile = File(context.cacheDir, "Teacher_Statement_${safeName}_${period.replace("\\s+".toRegex(), "_")}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "শিক্ষক বেতন হিসাব - ${teacher.name}")
        } catch (e: Exception) {
            Toast.makeText(context, "শিক্ষক রিপোর্ট তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 5. Expense / Muhtamim Deposit Report PDF (Multi-page, wrapped)
    // -------------------------------------------------------------------------
    fun generateAndShareExpenseDepositReportPdf(
        context: Context,
        reportType: String,
        period: String,
        isYearly: Boolean,
        list: List<ExpenseEntity>,
        totalAmount: Double
    ) {
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "মাদ্রাসা $reportType (ব্যয়) হিসাব বিবরণী • $period")

            // Total width = 527f
            val cols = listOf(
                TableCol("ক্রমিক", 52f, Layout.Alignment.ALIGN_CENTER),
                TableCol("তারিখ", 95f, Layout.Alignment.ALIGN_CENTER),
                TableCol("$reportType বিবরণ", 270f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("পরিমাণ (টাকা)", 110f, Layout.Alignment.ALIGN_CENTER)
            )
            drawTableHeader(cursor, cols, bgColor = 0xFFC0392B.toInt())

            if (list.isEmpty()) {
                drawWrappedTableRow(
                    cursor = cursor,
                    cols = listOf(TableCol("তথ্য", CONTENT_W, Layout.Alignment.ALIGN_CENTER)),
                    values = listOf("কোন $reportType এন্ট্রি পাওয়া যায়নি।"),
                    isEvenRow = true
                )
            } else {
                val amtColor = 0xFFE74C3C.toInt()
                list.forEachIndexed { idx, item ->
                    drawWrappedTableRow(
                        cursor = cursor,
                        cols = cols,
                        values = listOf(
                            "${idx + 1}",
                            item.date.ifEmpty { "-" },
                            item.desc,
                            "${item.amount.toInt()} ৳"
                        ),
                        textColors = listOf(null, null, null, amtColor),
                        boldCols = setOf(3),
                        isEvenRow = idx % 2 == 0
                    )
                }
            }

            cursor.y += 12f
            drawWrappedSummaryBanner(
                cursor = cursor,
                title = "সর্বমোট $reportType (ব্যয়): ${totalAmount.toInt()} ৳",
                subtitle = "মোট এন্ট্রি সংখ্যা: ${list.size} টি  •  সময়কাল: $period",
                badgeText = "ব্যয় ${totalAmount.toInt()} ৳",
                isSuccess = false
            )

            cursor.drawSignatures("কোষাধ্যক্ষের স্বাক্ষর", "মুহতামিমের সীল ও স্বাক্ষর")
            cursor.finishDocument()

            val pdfFile = File(context.cacheDir, "${reportType}_Report_${period.replace("\\s+".toRegex(), "_")}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "$reportType রিপোর্ট - $period")
        } catch (e: Exception) {
            Toast.makeText(context, "$reportType রিপোর্ট তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 6. Complete Financial Statement PDF
    // -------------------------------------------------------------------------
    fun generateAndShareFinancialStatementPdf(
        context: Context,
        period: String,
        isYearly: Boolean,
        salaryIncome: Double,
        electricIncome: Double,
        donationIncome: Double,
        totalIncome: Double,
        muhtamimJomaExpense: Double = 0.0,
        teacherSalaryExpense: Double,
        electricExpense: Double,
        otherExpense: Double,
        totalExpense: Double,
        balance: Double,
        deficit: Double
    ) {
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "আর্থিক আয়-ব্যয় ও লাভ-লোকসান হিসাব বিবরণী • $period")

            val cols = listOf(
                TableCol("খাতের বিবরণ", 377f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("টাকার পরিমাণ", 150f, Layout.Alignment.ALIGN_CENTER)
            )

            // Income
            drawTableHeader(cursor, listOf(TableCol("১. আয় খাতসমূহ (INCOME)", 377f), TableCol("টাকা", 150f, Layout.Alignment.ALIGN_CENTER)), bgColor = 0xFF0A5C36.toInt())
            drawWrappedTableRow(cursor, cols, listOf("১. মাসিক বেতন আদায়", "${salaryIncome.toInt()} ৳"), isEvenRow = false, repeatHeader = false)
            drawWrappedTableRow(cursor, cols, listOf("২. মাসিক বিদ্যুৎ বিল আদায়", "${electricIncome.toInt()} ৳"), isEvenRow = true, repeatHeader = false)
            drawWrappedTableRow(cursor, cols, listOf("৩. মাসিক চাঁদা, ভর্তি, পরীক্ষার ফি ও দান", "${donationIncome.toInt()} ৳"), isEvenRow = false, repeatHeader = false)
            drawWrappedTableRow(
                cursor, cols,
                listOf("সর্বমোট আয়", "${totalIncome.toInt()} ৳"),
                textColors = listOf(0xFF0A5C36.toInt(), 0xFF0A5C36.toInt()),
                boldCols = setOf(0, 1),
                isEvenRow = true,
                repeatHeader = false
            )

            cursor.y += 14f
            // Expense
            drawTableHeader(cursor, listOf(TableCol("২. ব্যয় খাতসমূহ (EXPENSE)", 377f), TableCol("টাকা", 150f, Layout.Alignment.ALIGN_CENTER)), bgColor = 0xFFC0392B.toInt())
            drawWrappedTableRow(cursor, cols, listOf("১. মুহতামিম জমা (বেতন ও বিদ্যুৎ বিল প্রদান)", "${muhtamimJomaExpense.toInt()} ৳"), isEvenRow = false, repeatHeader = false)
            drawWrappedTableRow(cursor, cols, listOf("২. মাসিক শিক্ষক বেতন", "${teacherSalaryExpense.toInt()} ৳"), isEvenRow = true, repeatHeader = false)
            drawWrappedTableRow(cursor, cols, listOf("৩. মাসিক বিদ্যুৎ বিল খরচ", "${electricExpense.toInt()} ৳"), isEvenRow = false, repeatHeader = false)
            drawWrappedTableRow(cursor, cols, listOf("৪. অন্যান্য ব্যয়", "${otherExpense.toInt()} ৳"), isEvenRow = true, repeatHeader = false)
            drawWrappedTableRow(
                cursor, cols,
                listOf("সর্বমোট ব্যয়", "${totalExpense.toInt()} ৳"),
                textColors = listOf(0xFFC0392B.toInt(), 0xFFC0392B.toInt()),
                boldCols = setOf(0, 1),
                isEvenRow = false,
                repeatHeader = false
            )

            cursor.y += 14f
            drawWrappedSummaryBanner(
                cursor = cursor,
                title = "৩. চূড়ান্ত আর্থিক সারসংক্ষেপ • মোট আয়: ${totalIncome.toInt()} ৳  •  মোট ব্যয়: ${totalExpense.toInt()} ৳",
                subtitle = if (deficit > 0) "অতিরিক্ত খরচ (ঘাটতি): ${deficit.toInt()} ৳" else "অবশিষ্ট (হাতে থাকা নগদ / তহবিল উদ্বৃত্ত): ${balance.toInt()} ৳",
                badgeText = if (deficit > 0) "ঘাটতি ${deficit.toInt()} ৳" else "উদ্বৃত্ত ${balance.toInt()} ৳",
                isSuccess = deficit <= 0
            )

            cursor.drawSignatures("হিসাব নিরীক্ষকের স্বাক্ষর", "মুহতামিমের সীল ও স্বাক্ষর")
            cursor.finishDocument()

            val pdfFile = File(context.cacheDir, "Financial_Statement_${period.replace("\\s+".toRegex(), "_")}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "আর্থিক হিসাব বিবরণী - $period")
        } catch (e: Exception) {
            Toast.makeText(context, "আর্থিক রিপোর্ট তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 7. ID CARD: Portrait CR80 (54mm x 85.6mm, 2.125" x 3.375", 638x1012px @300DPI + 2mm bleed)
    // -------------------------------------------------------------------------
    private fun drawCr80IdCardCanvas(
        canvas: Canvas,
        context: Context,
        isStudent: Boolean,
        badgeLabel: String,
        name: String,
        subtitle: String,
        rows: List<Pair<String, String>>,
        leftSig: String,
        rightSig: String,
        photoUri: String
    ) {
        val w = CR80_FULL_W_PX.toFloat()  // 686px
        val h = CR80_FULL_H_PX.toFloat()  // 1060px
        val bleed = CR80_BLEED_PX         // 24px (2mm @ 300DPI)
        val safePad = bleed + 22f         // inner safe margin inside trim line
        val safeLeft = safePad
        val safeRight = w - safePad
        val safeW = (safeRight - safeLeft).toInt()

        // Full bleed background
        canvas.drawRect(0f, 0f, w, h, Paint().apply { color = Color.WHITE })

        // Top Header background extended into top/left/right bleed so edge cuts cleanly!
        val headerColor = if (isStudent) 0xFF0A5C36.toInt() else 0xFF0B4F6C.toInt()
        val accentGold = 0xFFD4AF37.toInt()
        val headerBottom = bleed + 215f

        canvas.drawRect(0f, 0f, w, headerBottom, Paint().apply { color = headerColor })

        // Gold Ribbon full bleed width
        val ribbonBottom = headerBottom + 46f
        canvas.drawRect(0f, headerBottom, w, ribbonBottom, Paint().apply { color = accentGold })

        val madrasaInfo = MadrasaInfoStore.getInfo(context)

        // Madrasa Logo + Header Texts (Strictly inside safe area!)
        val logoBmp = loadHighResMadrasaLogo(context, 360)
        if (logoBmp != null) {
            val logoLeft = (w - 92f) / 2f
            val logoTop = bleed + 14f
            canvas.drawBitmap(logoBmp, null, RectF(logoLeft, logoTop, logoLeft + 92f, logoTop + 92f), highQualityBitmapPaint)
        }

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val nameLayout = makeStaticLayout(madrasaInfo.name, namePaint, safeW, Layout.Alignment.ALIGN_CENTER, 1.08f, 1f)
        canvas.drawLayoutAt(nameLayout, safeLeft, bleed + 112f)

        val addrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE2E8F0.toInt()
            textSize = 16.5f
        }
        val addrLayout = makeStaticLayout(madrasaInfo.address, addrPaint, safeW, Layout.Alignment.ALIGN_CENTER, 1.05f, 1f)
        canvas.drawLayoutAt(addrLayout, safeLeft, bleed + 168f)

        // Ribbon Badge Text
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF062C1B.toInt()
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(badgeLabel, w / 2f, headerBottom + 31f, badgePaint)

        // Photo Ring & Avatar
        val photoSize = 176
        val photoTop = ribbonBottom + 24f
        val photoCenterX = w / 2f
        val photoCenterY = photoTop + photoSize / 2f

        // Gold circular frame around photo
        canvas.drawCircle(photoCenterX, photoCenterY, photoSize / 2f + 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentGold
        })
        canvas.drawCircle(photoCenterX, photoCenterY, photoSize / 2f + 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        })

        val loadedPhoto = loadPhotoBitmap(context, photoUri, photoSize)
        if (loadedPhoto != null) {
            val circlePhoto = createCircularBitmap(loadedPhoto)
            canvas.drawBitmap(circlePhoto, photoCenterX - photoSize / 2f, photoTop, null)
        } else {
            canvas.drawCircle(photoCenterX, photoCenterY, photoSize / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isStudent) 0xFFEAF7EF.toInt() else 0xFFE6F4FA.toInt()
            })
            val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = headerColor
                textSize = 64f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(if (isStudent) "👨‍🎓" else "👳", photoCenterX, photoCenterY + 22f, avatarPaint)
        }

        // Person Name (Wrapped)
        var curY = photoTop + photoSize + 20f
        val personNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = headerColor
            textSize = 29f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val personNameLayout = makeStaticLayout(name, personNamePaint, safeW, Layout.Alignment.ALIGN_CENTER, 1.08f, 1f)
        canvas.drawLayoutAt(personNameLayout, safeLeft, curY)
        curY += personNameLayout.height + 8f

        // Class / Designation Pill
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = headerColor
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subLayout = makeStaticLayout(subtitle, subPaint, safeW - 28, Layout.Alignment.ALIGN_CENTER, 1.05f, 1f)
        val pillH = maxOf(36f, subLayout.height + 14f)
        canvas.drawRoundRect(safeLeft + 10f, curY, safeRight - 10f, curY + pillH, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStudent) 0xFFEAF7EF.toInt() else 0xFFE6F4FA.toInt()
        })
        canvas.drawLayoutAt(subLayout, safeLeft + 14f, curY + (pillH - subLayout.height) / 2f)
        curY += pillH + 16f

        // Info Rows (Wrapped inside safe zone, never overflowing)
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt()
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F172A.toInt()
            textSize = 19.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowDivider = Paint().apply { color = 0xFFE2E8F0.toInt(); strokeWidth = 1.5f }
        val labelW = 195f
        val valW = (safeW - labelW - 8f).toInt()

        val maxRowBottom = h - bleed - 118f
        for ((lbl, value) in rows) {
            val lLayout = makeStaticLayout(lbl, labelPaint, labelW.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.05f, 0f)
            val vLayout = makeStaticLayout(value, valPaint, valW, Layout.Alignment.ALIGN_NORMAL, 1.05f, 0f)
            val rh = maxOf(lLayout.height, vLayout.height) + 12f
            if (curY + rh > maxRowBottom) break

            canvas.drawLayoutAt(lLayout, safeLeft, curY)
            canvas.drawLayoutAt(vLayout, safeLeft + labelW, curY)
            canvas.drawLine(safeLeft, curY + rh - 4f, safeRight, curY + rh - 4f, rowDivider)
            curY += rh + 2f
        }

        // Bottom Footer (Full bleed width at bottom)
        val footerTop = h - bleed - 42f
        canvas.drawRect(0f, footerTop, w, h, Paint().apply { color = headerColor })
        val footText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("জরুরি যোগাযোগ: ${madrasaInfo.phone}", w / 2f, footerTop + 28f, footText)

        // Signatures right above footer inside safe margin
        val sigY = footerTop - 28f
        val sigLine = Paint().apply { color = 0xFF334155.toInt(); strokeWidth = 1.8f }
        canvas.drawLine(safeLeft + 10f, sigY, safeLeft + 210f, sigY, sigLine)
        canvas.drawLine(safeRight - 210f, sigY, safeRight - 10f, sigY, sigLine)

        val sigText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF334155.toInt()
            textSize = 16.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(leftSig, safeLeft + 110f, sigY + 19f, sigText)
        canvas.drawText(rightSig, safeRight - 110f, sigY + 19f, sigText)

        // Subtle Printer Crop/Trim Marks marking exact 54mm x 85.6mm (638 x 1012 px) boundary inside 2mm bleed!
        val trimLeft = bleed
        val trimTop = bleed
        val trimRight = bleed + CR80_TRIM_W_PX
        val trimBottom = bleed + CR80_TRIM_H_PX
        val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8064748B.toInt()
            strokeWidth = 1.4f
        }
        val mark = 14f
        // Top-Left
        canvas.drawLine(0f, trimTop, mark, trimTop, cropPaint)
        canvas.drawLine(trimLeft, 0f, trimLeft, mark, cropPaint)
        // Top-Right
        canvas.drawLine(w - mark, trimTop, w, trimTop, cropPaint)
        canvas.drawLine(trimRight, 0f, trimRight, mark, cropPaint)
        // Bottom-Left
        canvas.drawLine(0f, trimBottom, mark, trimBottom, cropPaint)
        canvas.drawLine(trimLeft, h - mark, trimLeft, h, cropPaint)
        // Bottom-Right
        canvas.drawLine(w - mark, trimBottom, w, trimBottom, cropPaint)
        canvas.drawLine(trimRight, h - mark, trimRight, h, cropPaint)
    }

    fun generateAndShareIdCardPdf(context: Context, student: StudentEntity) {
        try {
            val doc = PdfDocument()
            // 58mm x 89.6mm (54mm x 85.6mm CR80 Portrait + 2mm bleed on all sides)
            val pageInfo = PdfDocument.PageInfo.Builder(CR80_PDF_W_PT, CR80_PDF_H_PT, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            canvas.save()
            canvas.scale(CR80_PDF_W_PT.toFloat() / CR80_FULL_W_PX, CR80_PDF_H_PT.toFloat() / CR80_FULL_H_PX)

            val rollInfo = if (student.roll.isNotBlank()) " • রোল: ${student.roll}" else ""
            val rows = buildList {
                add("আইডি নং:" to "#IKM-${student.id}")
                if (student.roll.isNotBlank()) add("রোল নম্বর:" to student.roll)
                add("পিতার নাম:" to student.father.ifEmpty { "প্রযোজ্য নয়" })
                add("মোবাইল নং:" to student.phone.ifEmpty { "নেই" })
                add("মাসিক বেতন:" to "${student.fee.toInt()} ৳")
                add("বিদ্যুৎ বিল:" to "${student.electricBill.toInt()} ৳")
            }

            drawCr80IdCardCanvas(
                canvas = canvas,
                context = context,
                isStudent = true,
                badgeLabel = "স্টুডেন্ট আইডি কার্ড (CR80)",
                name = student.name,
                subtitle = "জামাত: ${student.cls}$rollInfo",
                rows = rows,
                leftSig = "শ্রেণী শিক্ষকের স্বাক্ষর",
                rightSig = "মুহতামিমের স্বাক্ষর",
                photoUri = student.photoUri
            )
            canvas.restore()

            doc.finishPage(page)

            val safeName = student.name.replace("\\s+".toRegex(), "_")
            val pdfFile = File(context.cacheDir, "Student_ID_CR80_${safeName}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "স্টুডেন্ট আইডি কার্ড (CR80 54×85.6mm) - ${student.name}")
        } catch (e: Exception) {
            Toast.makeText(context, "আইডি কার্ড তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun generateAndShareTeacherIdCardPdf(context: Context, teacher: TeacherEntity) {
        try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(CR80_PDF_W_PT, CR80_PDF_H_PT, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            canvas.save()
            canvas.scale(CR80_PDF_W_PT.toFloat() / CR80_FULL_W_PX, CR80_PDF_H_PT.toFloat() / CR80_FULL_H_PX)

            val rows = listOf(
                "আইডি নং:" to "#IKM-T${teacher.id}",
                "পদবী / বিষয়:" to teacher.subject,
                "মোবাইল নং:" to teacher.phone.ifEmpty { "নেই" },
                "যোগদান:" to teacher.joinDate.ifEmpty { "প্রযোজ্য নয়" },
                "ঠিকানা:" to teacher.address.ifEmpty { "মাদ্রাসা ক্যাম্পাস" }
            )

            drawCr80IdCardCanvas(
                canvas = canvas,
                context = context,
                isStudent = false,
                badgeLabel = "শিক্ষক পরিচিতি কার্ড (ID CARD)",
                name = teacher.name,
                subtitle = "পদবী: ${teacher.subject}",
                rows = rows,
                leftSig = "শিক্ষকের স্বাক্ষর",
                rightSig = "মুহতামিমের স্বাক্ষর",
                photoUri = teacher.photoUri
            )
            canvas.restore()

            doc.finishPage(page)

            val safeName = teacher.name.replace("\\s+".toRegex(), "_")
            val pdfFile = File(context.cacheDir, "Teacher_ID_CR80_${safeName}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "শিক্ষক আইডি কার্ড (CR80 54×85.6mm) - ${teacher.name}")
        } catch (e: Exception) {
            Toast.makeText(context, "শিক্ষক আইডি কার্ড তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Generates a 300-DPI CR80 PNG (638x1012 px trim + 2mm bleed = 686x1060 px) for direct print/gallery.
     */
    fun generateAndShareCr80IdCardPng(
        context: Context,
        student: StudentEntity? = null,
        teacher: TeacherEntity? = null
    ) {
        try {
            val bitmap = Bitmap.createBitmap(CR80_FULL_W_PX, CR80_FULL_H_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            if (student != null) {
                val rollInfo = if (student.roll.isNotBlank()) " • রোল: ${student.roll}" else ""
                val rows = buildList {
                    add("আইডি নং:" to "#IKM-${student.id}")
                    if (student.roll.isNotBlank()) add("রোল নম্বর:" to student.roll)
                    add("পিতার নাম:" to student.father.ifEmpty { "প্রযোজ্য নয়" })
                    add("মোবাইল নং:" to student.phone.ifEmpty { "নেই" })
                    add("মাসিক বেতন:" to "${student.fee.toInt()} ৳")
                    add("বিদ্যুৎ বিল:" to "${student.electricBill.toInt()} ৳")
                }
                drawCr80IdCardCanvas(
                    canvas = canvas,
                    context = context,
                    isStudent = true,
                    badgeLabel = "স্টুডেন্ট আইডি কার্ড (CR80)",
                    name = student.name,
                    subtitle = "জামাত: ${student.cls}$rollInfo",
                    rows = rows,
                    leftSig = "শ্রেণী শিক্ষকের স্বাক্ষর",
                    rightSig = "মুহতামিমের স্বাক্ষর",
                    photoUri = student.photoUri
                )
            } else if (teacher != null) {
                val rows = listOf(
                    "আইডি নং:" to "#IKM-T${teacher.id}",
                    "পদবী / বিষয়:" to teacher.subject,
                    "মোবাইল নং:" to teacher.phone.ifEmpty { "নেই" },
                    "যোগদান:" to teacher.joinDate.ifEmpty { "প্রযোজ্য নয়" },
                    "ঠিকানা:" to teacher.address.ifEmpty { "মাদ্রাসা ক্যাম্পাস" }
                )
                drawCr80IdCardCanvas(
                    canvas = canvas,
                    context = context,
                    isStudent = false,
                    badgeLabel = "শিক্ষক পরিচিতি কার্ড (ID CARD)",
                    name = teacher.name,
                    subtitle = "পদবী: ${teacher.subject}",
                    rows = rows,
                    leftSig = "শিক্ষকের স্বাক্ষর",
                    rightSig = "মুহতামিমের স্বাক্ষর",
                    photoUri = teacher.photoUri
                )
            } else return

            val personName = (student?.name ?: teacher?.name ?: "ID").replace("\\s+".toRegex(), "_")
            val fileName = "ID_CR80_300DPI_${personName}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MadrasaIDCards")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                }
            }

            val cacheFile = File(context.cacheDir, fileName)
            FileOutputStream(cacheFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            saveFileToDownloads(context, cacheFile, fileName, "image/png")

            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            Toast.makeText(context, "৩০০ DPI CR80 (638×1012 + 2mm ব্লিড) ছবি গ্যালারিতে সেভ হয়েছে!", Toast.LENGTH_LONG).show()

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, "আইডি কার্ড - $personName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "আইডি কার্ড ছবি শেয়ার বা প্রিন্ট করুন"))
        } catch (e: Exception) {
            Toast.makeText(context, "ছবি তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 8. Fee Receipt PDF (Wrapped text)
    // -------------------------------------------------------------------------
    fun generateAndShareFeeReceiptPdf(context: Context, student: StudentEntity?, fee: FeeEntity) {
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "অর্থ আদায় রসিদ • তারিখ: ${fee.date}")

            val rows = listOf(
                "ছাত্রের নাম:" to (student?.name ?: "অজ্ঞাত"),
                "জামাত ও রোল:" to "${student?.cls ?: ""}${if (!student?.roll.isNullOrBlank()) " (রোল: ${student?.roll})" else ""}",
                "পিতার নাম:" to (student?.father?.ifEmpty { "প্রযোজ্য নয়" } ?: "প্রযোজ্য নয়"),
                "খাত / বিবরণ:" to "${fee.type} (${fee.month})",
                "জমার তারিখ:" to fee.date
            )

            val cols = listOf(
                TableCol("বিবরণ", 180f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("তথ্য", CONTENT_W - 180f, Layout.Alignment.ALIGN_NORMAL)
            )
            drawTableHeader(cursor, cols)
            rows.forEachIndexed { idx, (k, v) ->
                drawWrappedTableRow(cursor, cols, listOf(k, v), boldCols = setOf(1), isEvenRow = idx % 2 == 0)
            }

            cursor.y += 14f
            drawWrappedSummaryBanner(
                cursor = cursor,
                title = "জমার পরিমাণ: ${fee.amount.toInt()} ৳",
                subtitle = "রসিদ নং: #REC-${fee.id}  •  খাত: ${fee.type}",
                badgeText = "পরিশোধিত",
                isSuccess = true
            )

            cursor.drawSignatures("আদায়কারীর স্বাক্ষর", "মুহতামিমের সীল ও স্বাক্ষর")
            cursor.finishDocument()

            val safeName = (student?.name ?: "student").replace("\\s+".toRegex(), "_")
            val pdfFile = File(context.cacheDir, "Receipt_${safeName}_${fee.month.replace("\\s+".toRegex(), "_")}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "বেতন রসিদ - ${student?.name}")
        } catch (e: Exception) {
            Toast.makeText(context, "রসিদ তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 9. Admit Card (প্রবেশ পত্র) A4 PDF (Supports single or batch list, auto page-break)
    // -------------------------------------------------------------------------
    fun generateAndShareAdmitCardsPdf(
        context: Context,
        examName: String,
        examScheduleNote: String,
        students: List<StudentEntity>
    ) {
        if (students.isEmpty()) {
            Toast.makeText(context, "প্রবেশপত্র তৈরির জন্য কোন ছাত্র পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "পরীক্ষার প্রবেশ পত্র (ADMIT CARD) • $examName")

            students.forEachIndexed { index, student ->
                val studentPhoto = loadPhotoBitmap(context, student.photoUri, 54)
                val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF1E293B.toInt()
                    textSize = 11f
                }
                val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF0A5C36.toInt()
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                val infoW = (CONTENT_W - 96f).toInt()
                val rollStr = if (student.roll.isNotBlank()) student.roll else "${student.id}"
                val nameL = makeStaticLayout("পরীক্ষার্থীর নাম: ${student.name}", boldPaint, infoW)
                val detailsText = buildString {
                    append("জামাত: ${student.cls}   |   রোল নং: $rollStr\n")
                    append("পিতার নাম: ${student.father.ifEmpty { "প্রযোজ্য নয়" }}   |   মোবাইল: ${student.phone.ifEmpty { "নেই" }}\n")
                    append("পরীক্ষার নাম: $examName")
                    if (examScheduleNote.isNotBlank()) append("\nসময়সূচী / নির্দেশনা: $examScheduleNote")
                }
                val detailsL = makeStaticLayout(detailsText, infoPaint, infoW, lineSpacingMult = 1.25f)
                val cardH = maxOf(140f, nameL.height + detailsL.height + 76f)

                cursor.ensureSpace(cardH + 14f)

                val top = cursor.y
                val bottom = top + cardH
                cursor.canvas.drawRoundRect(LEFT, top, RIGHT, bottom, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFF8FBF9.toInt()
                })
                cursor.canvas.drawRoundRect(LEFT, top, RIGHT, bottom, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF0A5C36.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 1.2f
                })

                // Top strip of admit card
                cursor.canvas.drawRoundRect(LEFT, top, RIGHT, top + 24f, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF0A5C36.toInt()
                })
                val cardHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 10.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                cursor.canvas.drawText("প্রবেশ পত্র #IKM-ADM-${student.id}  •  $examName", LEFT + 12f, top + 16f, cardHead)

                if (studentPhoto != null) {
                    cursor.canvas.drawBitmap(studentPhoto, LEFT + 12f, top + 34f, null)
                } else {
                    cursor.canvas.drawRoundRect(LEFT + 12f, top + 34f, LEFT + 66f, top + 88f, 6f, 6f, Paint().apply {
                        color = 0xFFE2E8F0.toInt()
                    })
                    val noPic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF64748B.toInt()
                        textSize = 9f
                        textAlign = Paint.Align.CENTER
                    }
                    cursor.canvas.drawText("ছবি", LEFT + 39f, top + 64f, noPic)
                }

                val tx = LEFT + 80f
                cursor.canvas.drawLayoutAt(nameL, tx, top + 34f)
                cursor.canvas.drawLayoutAt(detailsL, tx, top + 38f + nameL.height)

                val sigY = bottom - 22f
                val sigLine = Paint().apply { color = 0xFF475569.toInt(); strokeWidth = 0.8f }
                cursor.canvas.drawLine(RIGHT - 165f, sigY, RIGHT - 18f, sigY, sigLine)
                val sigTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF334155.toInt()
                    textSize = 9f
                    textAlign = Paint.Align.CENTER
                }
                cursor.canvas.drawText("মুহতামিমের সীল ও স্বাক্ষর", RIGHT - 91f, sigY + 12f, sigTxt)

                cursor.y = bottom + 14f
            }

            cursor.finishDocument()
            val fileName = if (students.size == 1) {
                "Admit_${students.first().name.replace("\\s+".toRegex(), "_")}.pdf"
            } else {
                "Admit_Cards_${examName.replace("\\s+".toRegex(), "_")}.pdf"
            }
            val pdfFile = File(context.cacheDir, fileName)
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "প্রবেশ পত্র - $examName")
        } catch (e: Exception) {
            Toast.makeText(context, "প্রবেশ পত্র তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 10. Question Paper (প্রশ্নপত্র) Landscape A4 (297mm x 210mm) 2-Column PDF
    //     Left & Right identical copies separated by a center dotted cut line
    // -------------------------------------------------------------------------
    private sealed class QpItem {
        data class Spacer(val height: Float) : QpItem()
        data class TextBlock(val layout: StaticLayout, val bottomGap: Float) : QpItem()
    }

    fun generateAndShareQuestionPaperPdf(context: Context, paper: QuestionPaperEntity) {
        try {
            // Standard A4 Landscape (297mm x 210mm) at 72 DPI = 842 x 595 pt
            val pageW = A4_HEIGHT // 842
            val pageH = A4_WIDTH  // 595
            val halfW = pageW / 2f // 421f

            // Symmetric margins inside each 421pt half (20pt outer frame margin, 30pt content margin)
            val borderLeft = 20f
            val borderRight = halfW - 20f // 401f
            val borderTop = 20f
            val borderBottom = pageH - 20f // 575f

            val colLeft = 30f
            val colRight = halfW - 30f // 391f
            val colW = colRight - colLeft // 361f
            val colTopStart = 30f
            val footerY = pageH - 30f // 565f
            val maxContentY = footerY - 22f // 543f

            val logoBmp = loadHighResMadrasaLogo(context, 512)

            // Pre-build First-Page Header & Continuation Header layouts once (shared by both halves)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val addrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE2E8F0.toInt()
                textSize = 8.2f
            }
            val headerTextStart = if (logoBmp != null) colLeft + 48f else colLeft + 8f
            val headerTextW = (colRight - headerTextStart - 8f).toInt()
            val madrasaInfo = MadrasaInfoStore.getInfo(context)
            val titleLayout = makeStaticLayout(madrasaInfo.name, titlePaint, headerTextW, Layout.Alignment.ALIGN_CENTER, 1.1f, 0.5f)
            val addrLayout = makeStaticLayout(madrasaInfo.address, addrPaint, headerTextW, Layout.Alignment.ALIGN_CENTER, 1.1f, 0.5f)
            val headerBoxH = maxOf(50f, titleLayout.height + addrLayout.height + 24f)

            val ribbonPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A3656.toInt()
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val ribbonLayout = makeStaticLayout(
                "${paper.examName} — প্রশ্নপত্র",
                ribbonPaint,
                (colW - 14f).toInt(),
                Layout.Alignment.ALIGN_CENTER,
                1.1f,
                0.5f
            )
            val ribbonH = maxOf(20f, ribbonLayout.height + 8f)

            val metaText = "শ্রেণী/জামাত: ${paper.className}  •  বিষয়: ${paper.subject}\nসময়: ${paper.duration}  •  পূর্ণমান: ${paper.fullMarks}  •  তারিখ: ${paper.date}"
            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A3656.toInt()
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val metaLayout = makeStaticLayout(metaText, metaPaint, (colW - 14f).toInt(), Layout.Alignment.ALIGN_CENTER, 1.2f, 1f)
            val metaH = metaLayout.height + 12f

            val instLayout = if (paper.instructions.isNotBlank()) {
                val instPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF475569.toInt()
                    textSize = 8.8f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                }
                makeStaticLayout("বিশেষ নির্দেশনা: ${paper.instructions}", instPaint, colW.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.18f, 1f)
            } else {
                null
            }

            val firstPageBodyStartY = colTopStart +
                headerBoxH + 4f +
                ribbonH + 7f +
                metaH + 7f +
                (instLayout?.let { it.height + 7f } ?: 0f) +
                8f

            val contHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A3656.toInt()
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val contHeaderLayout = makeStaticLayout(
                "${madrasaInfo.name} — ${paper.examName} (${paper.subject})",
                contHeaderPaint,
                (colW - 12f).toInt(),
                Layout.Alignment.ALIGN_CENTER,
                1.1f,
                0.5f
            )
            val contHeaderH = maxOf(20f, contHeaderLayout.height + 8f)
            val contPageBodyStartY = colTopStart + contHeaderH + 8f

            // Pre-wrap and paginate all question lines for the 361pt column width
            val qPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF111827.toInt()
                textSize = 10f
            }
            val pages = mutableListOf<List<QpItem>>()
            var currentItems = mutableListOf<QpItem>()
            var cursorY = firstPageBodyStartY

            for (rawLine in paper.questionsText.split("\n")) {
                if (rawLine.isBlank()) {
                    val gap = 6f
                    if (cursorY + gap <= maxContentY) {
                        currentItems.add(QpItem.Spacer(gap))
                        cursorY += gap
                    }
                    continue
                }
                val layout = makeStaticLayout(rawLine, qPaint, colW.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.25f, 1.5f)
                val needed = layout.height + 5f
                if (cursorY + needed > maxContentY && currentItems.isNotEmpty()) {
                    pages.add(currentItems)
                    currentItems = mutableListOf()
                    cursorY = contPageBodyStartY
                }
                currentItems.add(QpItem.TextBlock(layout, 5f))
                cursorY += needed
            }
            if (currentItems.isNotEmpty() || pages.isEmpty()) {
                pages.add(currentItems)
            }

            fun drawSingleCopyOnHalf(canvas: Canvas, pageIndex: Int, totalPages: Int, items: List<QpItem>) {
                val pageNum = pageIndex + 1

                // Outer frame inside each half
                canvas.drawRoundRect(
                    borderLeft, borderTop, borderRight, borderBottom,
                    8f, 8f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0B4F6C.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 1.2f
                    }
                )

                var y = colTopStart
                if (pageIndex == 0) {
                    // Header banner (Deep Ocean Teal)
                    canvas.drawRoundRect(
                        colLeft, y, colRight, y + headerBoxH,
                        6f, 6f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A3656.toInt() }
                    )
                    if (logoBmp != null) {
                        val logoLeft = colLeft + 8f
                        val logoTop = y + (headerBoxH - 36f) / 2f
                        canvas.drawBitmap(logoBmp, null, RectF(logoLeft, logoTop, logoLeft + 36f, logoTop + 36f), highQualityBitmapPaint)
                    }
                    var ty = y + 7f
                    canvas.drawLayoutAt(titleLayout, headerTextStart, ty)
                    ty += titleLayout.height + 2f
                    canvas.drawLayoutAt(addrLayout, headerTextStart, ty)
                    ty += addrLayout.height + 3f

                    val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFD4AF37.toInt()
                        textSize = 8f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("মোবাইল: ${madrasaInfo.phone}", headerTextStart + headerTextW / 2f, ty + 7f, phonePaint)
                    y += headerBoxH + 4f

                    // Gold exam title ribbon
                    canvas.drawRoundRect(
                        colLeft, y, colRight, y + ribbonH,
                        4f, 4f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD4AF37.toInt() }
                    )
                    canvas.drawLayoutAt(ribbonLayout, colLeft + 7f, y + (ribbonH - ribbonLayout.height) / 2f)
                    y += ribbonH + 7f

                    // Meta bar (Class, Subject, Time, Marks)
                    canvas.drawRoundRect(
                        colLeft, y, colRight, y + metaH,
                        6f, 6f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE6F4FA.toInt() }
                    )
                    canvas.drawRoundRect(
                        colLeft, y, colRight, y + metaH,
                        6f, 6f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF0B4F6C.toInt()
                            style = Paint.Style.STROKE
                            strokeWidth = 0.8f
                        }
                    )
                    canvas.drawLayoutAt(metaLayout, colLeft + 7f, y + 6f)
                    y += metaH + 7f

                    if (instLayout != null) {
                        canvas.drawLayoutAt(instLayout, colLeft, y)
                        y += instLayout.height + 7f
                    }

                    canvas.drawLine(colLeft, y, colRight, y, Paint().apply {
                        color = 0xFFCBD5E1.toInt()
                        strokeWidth = 0.8f
                    })
                    y += 8f
                } else {
                    // Continuation header on Page 2+
                    canvas.drawRoundRect(
                        colLeft, y, colRight, y + contHeaderH,
                        5f, 5f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE6F4FA.toInt() }
                    )
                    canvas.drawLayoutAt(contHeaderLayout, colLeft + 6f, y + (contHeaderH - contHeaderLayout.height) / 2f)
                    y += contHeaderH + 8f
                }

                // Draw questions inside this half
                for (item in items) {
                    when (item) {
                        is QpItem.Spacer -> y += item.height
                        is QpItem.TextBlock -> {
                            canvas.drawLayoutAt(item.layout, colLeft, y)
                            y += item.layout.height + item.bottomGap
                        }
                    }
                }

                // Footer inside each half
                canvas.drawLine(colLeft, footerY - 10f, colRight, footerY - 10f, Paint().apply {
                    color = 0xFFD0D7DE.toInt()
                    strokeWidth = 0.6f
                })
                canvas.drawText(
                    madrasaInfo.name,
                    colLeft,
                    footerY,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF64748B.toInt()
                        textSize = 7.5f
                        textAlign = Paint.Align.LEFT
                    }
                )
                canvas.drawText(
                    "পৃষ্ঠা $pageNum / $totalPages",
                    colRight,
                    footerY,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0A3656.toInt()
                        textSize = 7.5f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.RIGHT
                    }
                )
            }

            val doc = PdfDocument()
            val cutLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF64748B.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1f
                pathEffect = DashPathEffect(floatArrayOf(3f, 4f), 0f)
            }
            val cutPath = Path().apply {
                moveTo(halfW, 12f)
                lineTo(halfW, pageH - 12f)
            }

            pages.forEachIndexed { idx, items ->
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                val page = doc.startPage(info)
                val canvas = page.canvas

                // White page background
                canvas.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), Paint().apply { color = Color.WHITE })

                // Left Side Copy
                canvas.save()
                drawSingleCopyOnHalf(canvas, idx, pages.size, items)
                canvas.restore()

                // Center Dotted Cut Line
                canvas.drawPath(cutPath, cutLinePaint)

                // Right Side Identical Copy
                canvas.save()
                canvas.translate(halfW, 0f)
                drawSingleCopyOnHalf(canvas, idx, pages.size, items)
                canvas.restore()

                doc.finishPage(page)
            }

            val safeSub = paper.subject.replace("\\s+".toRegex(), "_")
            val pdfFile = File(context.cacheDir, "Question_${safeSub}_${paper.className.replace("\\s+".toRegex(), "_")}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "প্রশ্নপত্র (২-কপি ল্যান্ডস্কেপ) - ${paper.subject} (${paper.className})")
        } catch (e: Exception) {
            Toast.makeText(context, "প্রশ্নপত্র তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 11. Class Routine (ক্লাস রুটিন) Multi-page A4 Wrapped PDF
    // -------------------------------------------------------------------------
    fun generateAndShareClassRoutinePdf(
        context: Context,
        classFilterTitle: String,
        routines: List<ClassRoutineEntity>
    ) {
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "সাপ্তাহিক ক্লাস রুটিন • $classFilterTitle")

            val cols = listOf(
                TableCol("বার / দিন", 85f, Layout.Alignment.ALIGN_CENTER),
                TableCol("শ্রেণী / জামাত", 105f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("সময়", 102f, Layout.Alignment.ALIGN_CENTER),
                TableCol("বিষয় / কিতাব", 120f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("শিক্ষক", 115f, Layout.Alignment.ALIGN_NORMAL)
            )
            drawTableHeader(cursor, cols)

            if (routines.isEmpty()) {
                drawWrappedTableRow(
                    cursor,
                    listOf(TableCol("বিবরণ", CONTENT_W, Layout.Alignment.ALIGN_CENTER)),
                    listOf("কোন ক্লাস রুটিন যুক্ত করা হয়নি।"),
                    isEvenRow = true
                )
            } else {
                routines.forEachIndexed { idx, r ->
                    drawWrappedTableRow(
                        cursor = cursor,
                        cols = cols,
                        values = listOf(r.dayOfWeek, r.className, r.timeSlot, r.subject, r.teacherName.ifEmpty { "-" }),
                        boldCols = setOf(0, 3),
                        isEvenRow = idx % 2 == 0
                    )
                }
            }

            cursor.drawSignatures("রুটিন প্রস্তুতকারক", "মুহতামিমের সীল ও স্বাক্ষর")
            cursor.finishDocument()

            val pdfFile = File(context.cacheDir, "Class_Routine_${classFilterTitle.replace("\\s+".toRegex(), "_")}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "ক্লাস রুটিন - $classFilterTitle")
        } catch (e: Exception) {
            Toast.makeText(context, "রুটিন তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // 12. Merit List (মেধাতালিকা) Multi-page A4 Wrapped PDF
    // -------------------------------------------------------------------------
    fun generateAndShareMeritListPdf(
        context: Context,
        title: String,
        entries: List<MeritPdfEntry>
    ) {
        try {
            val doc = PdfDocument()
            val cursor = A4PageCursor(context, doc, "মেধাতালিকা • $title")

            val cols = listOf(
                TableCol("মেধাস্থান", 62f, Layout.Alignment.ALIGN_CENTER),
                TableCol("রোল", 45f, Layout.Alignment.ALIGN_CENTER),
                TableCol("ছাত্রের নাম", 150f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("শ্রেণী", 90f, Layout.Alignment.ALIGN_NORMAL),
                TableCol("মোট নম্বর", 70f, Layout.Alignment.ALIGN_CENTER),
                TableCol("গড় %", 55f, Layout.Alignment.ALIGN_CENTER),
                TableCol("গ্রেড", 55f, Layout.Alignment.ALIGN_CENTER)
            )
            drawTableHeader(cursor, cols)

            if (entries.isEmpty()) {
                drawWrappedTableRow(
                    cursor,
                    listOf(TableCol("তথ্য", CONTENT_W, Layout.Alignment.ALIGN_CENTER)),
                    listOf("এই পরীক্ষা বা শ্রেণীর কোন ফলাফল পাওয়া যায়নি।"),
                    isEvenRow = true
                )
            } else {
                entries.forEachIndexed { idx, e ->
                    val statusColor = if (e.passed) 0xFF0A5C36.toInt() else 0xFFE74C3C.toInt()
                    drawWrappedTableRow(
                        cursor = cursor,
                        cols = cols,
                        values = listOf(
                            e.rankLabel,
                            e.roll.ifEmpty { "-" },
                            e.name,
                            e.cls,
                            "${e.totalMarks.toInt()} / ${e.maxMarks}",
                            "%.1f%%".format(e.avg),
                            e.grade
                        ),
                        textColors = listOf(0xFF0A5C36.toInt(), null, null, null, null, null, statusColor),
                        boldCols = setOf(0, 4, 6),
                        isEvenRow = idx % 2 == 0
                    )
                }
            }

            cursor.drawSignatures("পরীক্ষা নিয়ন্ত্রক", "মুহতামিমের সীল ও স্বাক্ষর")
            cursor.finishDocument()

            val pdfFile = File(context.cacheDir, "Merit_List_${title.replace("\\s+".toRegex(), "_")}.pdf")
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
            doc.close()

            shareOrOpenPdf(context, pdfFile, "মেধাতালিকা - $title")
        } catch (e: Exception) {
            Toast.makeText(context, "মেধাতালিকা তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // Shared Student/Generic Info Cards & Summary Banner (Wrapped with 12mm margin)
    // -------------------------------------------------------------------------
    private fun drawStudentInfoCard(
        cursor: A4PageCursor,
        context: Context,
        student: StudentEntity?,
        extraRightInfo: String
    ) {
        val rollStr = if (!student?.roll.isNullOrBlank()) " (রোল: ${student?.roll})" else ""
        drawGenericInfoCard(
            cursor = cursor,
            context = context,
            photoUri = student?.photoUri.orEmpty(),
            primaryTitle = "ছাত্রের নাম: ${student?.name ?: "অজ্ঞাত"}",
            secondaryTitle = "জামাত: ${student?.cls ?: ""}$rollStr",
            line1 = "পিতার নাম: ${student?.father?.ifEmpty { "প্রযোজ্য নয়" } ?: "প্রযোজ্য নয়"}",
            line2 = extraRightInfo,
            line3 = ""
        )
    }

    private fun drawGenericInfoCard(
        cursor: A4PageCursor,
        context: Context,
        photoUri: String,
        primaryTitle: String,
        secondaryTitle: String,
        line1: String,
        line2: String,
        line3: String
    ) {
        val photoBmp = loadPhotoBitmap(context, photoUri, 54)
        val textStart = if (photoBmp != null) LEFT + 76f else LEFT + 14f
        val textW = (RIGHT - textStart - 12f).toInt()

        val p1 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A5C36.toInt()
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val p2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF334155.toInt()
            textSize = 10.5f
        }

        val combinedTop = "$primaryTitle  •  $secondaryTitle"
        val topL = makeStaticLayout(combinedTop, p1, textW, Layout.Alignment.ALIGN_NORMAL, 1.15f, 1f)
        val detailsStr = buildString {
            append(line1)
            if (line2.isNotBlank()) append("\n$line2")
            if (line3.isNotBlank()) append("\n$line3")
        }
        val detL = makeStaticLayout(detailsStr, p2, textW, Layout.Alignment.ALIGN_NORMAL, 1.2f, 1f)

        val boxH = maxOf(if (photoBmp != null) 70f else 54f, topL.height + detL.height + 24f)
        cursor.ensureSpace(boxH + 10f)

        val top = cursor.y
        val bottom = top + boxH
        cursor.canvas.drawRoundRect(LEFT, top, RIGHT, bottom, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEAF7EF.toInt()
        })
        cursor.canvas.drawRoundRect(LEFT, top, RIGHT, bottom, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A5C36.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        })

        if (photoBmp != null) {
            cursor.canvas.drawBitmap(photoBmp, LEFT + 12f, top + (boxH - 54f) / 2f, null)
        }

        cursor.canvas.drawLayoutAt(topL, textStart, top + 10f)
        cursor.canvas.drawLayoutAt(detL, textStart, top + 14f + topL.height)

        cursor.y = bottom + 12f
    }

    private fun drawWrappedSummaryBanner(
        cursor: A4PageCursor,
        title: String,
        subtitle: String,
        badgeText: String,
        isSuccess: Boolean
    ) {
        val badgeW = 155f
        val textW = (CONTENT_W - badgeW - 30f).toInt()
        val tPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F172A.toInt()
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt()
            textSize = 10.5f
        }
        val bPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val tLayout = makeStaticLayout(title, tPaint, textW, Layout.Alignment.ALIGN_NORMAL, 1.15f, 1f)
        val sLayout = makeStaticLayout(subtitle, sPaint, textW, Layout.Alignment.ALIGN_NORMAL, 1.15f, 1f)
        val bLayout = makeStaticLayout(badgeText, bPaint, (badgeW - 14f).toInt(), Layout.Alignment.ALIGN_CENTER, 1.1f, 0f)

        val boxH = maxOf(60f, tLayout.height + sLayout.height + 24f)
        cursor.ensureSpace(boxH + 10f)

        val top = cursor.y
        val bottom = top + boxH
        cursor.canvas.drawRoundRect(LEFT, top, RIGHT, bottom, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSuccess) 0xFFEAF7EF.toInt() else 0xFFFFF0F0.toInt()
        })
        cursor.canvas.drawRoundRect(LEFT, top, RIGHT, bottom, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSuccess) 0xFF0A5C36.toInt() else 0xFFE74C3C.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        })

        cursor.canvas.drawLayoutAt(tLayout, LEFT + 12f, top + 10f)
        cursor.canvas.drawLayoutAt(sLayout, LEFT + 12f, top + 14f + tLayout.height)

        val badgeH = maxOf(32f, bLayout.height + 12f)
        val badgeTop = top + (boxH - badgeH) / 2f
        val badgeLeft = RIGHT - badgeW - 10f
        cursor.canvas.drawRoundRect(badgeLeft, badgeTop, RIGHT - 10f, badgeTop + badgeH, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSuccess) 0xFF16A34A.toInt() else 0xFFDC2626.toInt()
        })
        cursor.canvas.drawLayoutAt(bLayout, badgeLeft + 7f, badgeTop + (badgeH - bLayout.height) / 2f)

        cursor.y = bottom + 10f
    }

    fun saveFileToDownloads(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MadrasaPro")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val target = File(downloadsDir, displayName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(target).use { out -> input.copyTo(out) }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun shareOrOpenPdf(context: Context, file: File, title: String) {
        try {
            val savedUri = saveFileToDownloads(context, file, file.name, "application/pdf")
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val toastMsg = if (savedUri != null) {
                "পিডিএফ Downloads/MadrasaPro ফোল্ডারে সেভ হয়েছে: ${file.name}"
            } else {
                "পিডিএফ ফাইল প্রস্তুত হয়েছে!"
            }
            Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "$title - PDF শেয়ার বা ওপেন করুন"))
        } catch (e: Exception) {
            Toast.makeText(context, "পিডিএফ ওপেন করতে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateAndShareExpensesReportPdf(
        context: Context,
        periodTitle: String,
        expenses: List<ExpenseEntity>,
        totalIncome: Double,
        totalExpense: Double,
        netBalance: Double
    ) {
        generateAndShareExpenseDepositReportPdf(
            context = context,
            reportType = "খরচ ও জমা",
            period = periodTitle,
            isYearly = false,
            list = expenses,
            totalAmount = totalExpense
        )
    }

    fun generateAndShareFeesReportPdf(
        context: Context,
        reportTitle: String,
        fees: List<FeeEntity>,
        students: List<StudentEntity>,
        totalCollected: Double,
        totalDue: Double
    ) {
        generateAndShareFeeReportPdf(
            context = context,
            period = reportTitle,
            isYearly = false,
            students = students,
            fees = fees
        )
    }

    fun generateAndShareStudentIdCardPdf(context: Context, student: StudentEntity) {
        generateAndShareIdCardPdf(context, student)
    }
}

