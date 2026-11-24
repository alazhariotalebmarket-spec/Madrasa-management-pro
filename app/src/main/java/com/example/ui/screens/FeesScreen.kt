package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.MadrasaInfo
import com.example.data.entity.FeeEntity
import com.example.data.entity.StudentEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.MadrasaRoundLogo
import com.example.ui.components.ThreeDotDeleteMenu
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaAccent
import com.example.ui.theme.MadrasaContainer
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.util.PdfPrintUtil
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeesScreen(
    students: List<StudentEntity>,
    fees: List<FeeEntity>,
    madrasaInfo: MadrasaInfo = MadrasaInfo(),
    logoVersion: Long = 0L,
    onAddFee: (Int, Double, String, String) -> Unit,
    onDeleteFee: (Int) -> Unit
) {
    val context = LocalContext.current
    if (students.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            MadrasaEmptyState(
                icon = Icons.Default.Payments,
                title = "প্রথমে কিছু ছাত্র ভর্তি করুন",
                subtitle = "ছাত্র ভর্তি করা হলে এখান থেকে বেতন ও বিদ্যুৎ বিল আদায় করতে পারবেন"
            )
        }
        return
    }

    var selectedClassFilter by remember { mutableStateOf("সকল শ্রেণী") }
    var expandedClassFilter by remember { mutableStateOf(false) }

    val classesList = remember(students) {
        listOf("সকল শ্রেণী") + students.map { it.cls }.distinct().filter { it.isNotBlank() }
    }

    val filteredStudents = remember(students, selectedClassFilter) {
        val list = if (selectedClassFilter == "সকল শ্রেণী") students else students.filter { it.cls == selectedClassFilter }
        list.sortedBy { s -> s.roll.toIntOrNull() ?: 9999 }
    }

    var selectedStudentId by remember(filteredStudents) { mutableStateOf(filteredStudents.firstOrNull()?.id ?: students.first().id) }
    var feeType by remember { mutableStateOf("মাসিক বেতন") }
    var feeMonth by remember { mutableStateOf("জানুয়ারি") }
    var feeYear by remember {
        mutableStateOf(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()))
    }

    val selectedStudent = students.find { it.id == selectedStudentId } ?: students.first()

    var amountStr by remember(selectedStudentId, feeType) {
        val initialAmt = when (feeType) {
            "মাসিক বেতন" -> selectedStudent.fee.toInt().toString()
            "মাসিক বিদ্যুৎ বিল" -> selectedStudent.electricBill.toInt().toString()
            "পরীক্ষার ফি" -> "200"
            else -> "500"
        }
        mutableStateOf(initialAmt)
    }

    var expandedStudent by remember { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf(false) }
    var expandedMonth by remember { mutableStateOf(false) }

    var activeReceiptFee by remember { mutableStateOf<FeeEntity?>(null) }

    // Report Period Filter
    var isYearlyReport by remember { mutableStateOf(false) }
    var filterMonth by remember { mutableStateOf("জানুয়ারি") }
    var filterYear by remember { mutableStateOf(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())) }
    var expandedFilterMonth by remember { mutableStateOf(false) }

    val feeTypes = listOf("মাসিক বেতন", "মাসিক বিদ্যুৎ বিল", "পরীক্ষার ফি", "ভর্তি ফি", "অন্যান্য")
    val months = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")

    val studentMap = remember(students) { students.associateBy { it.id } }
    val sortedFees = remember(fees) { fees.sortedByDescending { it.id } }

    val reportFees = remember(fees, isYearlyReport, filterMonth, filterYear) {
        fees.filter { fee ->
            val matchYear = fee.date.endsWith(filterYear) || fee.month.contains(filterYear)
            if (isYearlyReport) {
                matchYear
            } else {
                matchYear && fee.month.startsWith(filterMonth)
            }
        }
    }

    val totalSalaryCollected = remember(reportFees) { reportFees.filter { it.type == "মাসিক বেতন" }.sumOf { it.amount } }
    val totalElectricCollected = remember(reportFees) { reportFees.filter { it.type == "মাসিক বিদ্যুৎ বিল" }.sumOf { it.amount } }
    val totalExamCollected = remember(reportFees) { reportFees.filter { it.type == "পরীক্ষার ফি" }.sumOf { it.amount } }
    val totalOtherCollected = remember(reportFees) { reportFees.filter { it.type !in listOf("মাসিক বেতন", "মাসিক বিদ্যুৎ বিল", "পরীক্ষার ফি") }.sumOf { it.amount } }
    val totalCollected = remember(reportFees) { reportFees.sumOf { it.amount } }

    val expectedTuition = remember(students, isYearlyReport) {
        if (isYearlyReport) students.sumOf { it.fee * 12 } else students.sumOf { it.fee }
    }
    val expectedElectric = remember(students, isYearlyReport) {
        if (isYearlyReport) students.sumOf { it.electricBill * 12 } else students.sumOf { it.electricBill }
    }
    val totalExpected = expectedTuition + expectedElectric
    val totalDue = maxOf(0.0, totalExpected - (totalSalaryCollected + totalElectricCollected))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Fee Collection Card with Class & Roll Filtering
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = madrasaCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💰 বেতন ও বিদ্যুৎ বিল আদায় (শ্রেণী ও রোল অনুযায়ী)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Class Filter
                        ExposedDropdownMenuBox(
                            expanded = expandedClassFilter,
                            onExpandedChange = { expandedClassFilter = !expandedClassFilter },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedClassFilter,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("শ্রেণী ফিল্টার") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClassFilter) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedClassFilter,
                                onDismissRequest = { expandedClassFilter = false }
                            ) {
                                classesList.forEach { cls ->
                                    DropdownMenuItem(
                                        text = { Text(cls) },
                                        onClick = {
                                            selectedClassFilter = cls
                                            expandedClassFilter = false
                                            val nextList = if (cls == "সকল শ্রেণী") students else students.filter { it.cls == cls }
                                            nextList.firstOrNull()?.let { selectedStudentId = it.id }
                                        }
                                    )
                                }
                            }
                        }

                        // Student Dropdown (filtered by class, sorted by roll)
                        ExposedDropdownMenuBox(
                            expanded = expandedStudent,
                            onExpandedChange = { expandedStudent = !expandedStudent },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = "${selectedStudent.name} (রোল: ${selectedStudent.roll.ifEmpty { "-" }})",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("ছাত্র নির্বাচন") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStudent) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedStudent,
                                onDismissRequest = { expandedStudent = false }
                            ) {
                                filteredStudents.forEach { student ->
                                    DropdownMenuItem(
                                        text = { Text("${student.name} (রোল: ${student.roll.ifEmpty { "-" }}) — ${student.cls}") },
                                        onClick = {
                                            selectedStudentId = student.id
                                            expandedStudent = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expandedType,
                            onExpandedChange = { expandedType = !expandedType },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = feeType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("খাত") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                                feeTypes.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t) },
                                        onClick = {
                                            feeType = t
                                            expandedType = false
                                        }
                                    )
                                }
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = expandedMonth,
                            onExpandedChange = { expandedMonth = !expandedMonth },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = "$feeMonth $feeYear",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("মাস ও বছর") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonth) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                                months.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text("$m $feeYear") },
                                        onClick = {
                                            feeMonth = m
                                            expandedMonth = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("টাকার পরিমাণ *") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) return@Button
                            val monthStr = "$feeMonth $feeYear"
                            onAddFee(selectedStudent.id, amt, feeType, monthStr)
                            Toast.makeText(context, "${selectedStudent.name}-এর $feeType ($monthStr) সফলভাবে আদায় হয়েছে!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_fee_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ফি আদায় ও রসিদ তৈরি করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = madrasaCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 ফি আদায় রিপোর্ট ও সারাংশ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { isYearlyReport = false },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (!isYearlyReport) MadrasaPrimary.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("মাসিক", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { isYearlyReport = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isYearlyReport) MadrasaPrimary.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("বাৎসরিক", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isYearlyReport) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { expandedFilterMonth = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("মাস: $filterMonth", fontSize = 12.sp)
                                }
                                DropdownMenu(expanded = expandedFilterMonth, onDismissRequest = { expandedFilterMonth = false }) {
                                    months.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m) },
                                            onClick = {
                                                filterMonth = m
                                                expandedFilterMonth = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = filterYear,
                            onValueChange = { filterYear = it },
                            label = { Text("বছর") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("মাসিক বেতন আদায়: ${totalSalaryCollected.toInt()} ৳", fontSize = 13.sp, color = MadrasaGreen, fontWeight = FontWeight.SemiBold)
                    Text("বিদ্যুৎ বিল আদায়: ${totalElectricCollected.toInt()} ৳", fontSize = 13.sp, color = MadrasaGreen, fontWeight = FontWeight.SemiBold)
                    Text("পরীক্ষার ফি আদায়: ${totalExamCollected.toInt()} ৳", fontSize = 13.sp)
                    Text("অন্যান্য আদায়: ${totalOtherCollected.toInt()} ৳", fontSize = 13.sp)
                    Text("মোট আদায়: ${totalCollected.toInt()} ৳", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("মোট বকেয়া: ${totalDue.toInt()} ৳", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MadrasaRed)

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val titleStr = if (isYearlyReport) "বাৎসরিক ফি আদায় রিপোর্ট - $filterYear" else "মাসিক ফি আদায় রিপোর্ট - $filterMonth $filterYear"
                            PdfPrintUtil.generateAndShareFeesReportPdf(
                                context = context,
                                reportTitle = titleStr,
                                fees = reportFees,
                                students = students,
                                totalCollected = totalCollected,
                                totalDue = totalDue
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("download_fees_report_pdf_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isYearlyReport) "বাৎসরিক ফি রিপোর্ট PDF ডাউনলোড" else "মাসিক ফি রিপোর্ট PDF ডাউনলোড",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Recent Fees List
        item {
            Text(
                text = "সাম্প্রতিক ফি আদায়ের রশিদ (${fees.size} টি)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (fees.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.Payments,
                    title = "কোন ফি আদায়ের রেকর্ড নেই",
                    subtitle = "উপরের ফর্ম থেকে ফি আদায় করুন"
                )
            }
        } else {
            items(sortedFees, key = { it.id }) { fee ->
                val fStudent = studentMap[fee.studentId]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = madrasaCardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fStudent?.name ?: "অজ্ঞাত ছাত্র",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextDark
                            )
                            Text(
                                text = "খাত: ${fee.type} (${fee.month}) • তারিখ: ${fee.date}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${fee.amount.toInt()} ৳",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp,
                                color = MadrasaGreen
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { activeReceiptFee = fee }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "রসিদ", tint = MadrasaPrimary, modifier = Modifier.size(20.dp))
                            }
                            ThreeDotDeleteMenu(
                                onDelete = { onDeleteFee(fee.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    activeReceiptFee?.let { rFee ->
        val rStudent = studentMap[rFee.studentId]
        AlertDialog(
            onDismissRequest = { activeReceiptFee = null },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { PdfPrintUtil.generateAndShareFeeReceiptPdf(context, rStudent, rFee) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🖨️ PDF রসিদ ডাউনলোড ও শেয়ার", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { activeReceiptFee = null }, modifier = Modifier.align(Alignment.End)) {
                        Text("বন্ধ করুন")
                    }
                }
            },
            dismissButton = {},
            text = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MadrasaRoundLogo(
                            logoPath = madrasaInfo.logoPath,
                            logoVersion = logoVersion,
                            size = 52.dp,
                            borderWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = madrasaInfo.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MadrasaPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${madrasaInfo.address} • ${madrasaInfo.phone}",
                            fontSize = 11.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "অর্থ আদায় রসিদ • তারিখ: ${rFee.date}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE2E8F0))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "ছাত্রের নাম: ${rStudent?.name ?: "অজ্ঞাত"}", fontWeight = FontWeight.Bold, color = TextDark)
                            Text(text = "জামাত: ${rStudent?.cls} • রোল: ${rStudent?.roll.orEmpty().ifEmpty { "প্রযোজ্য নয়" }}", color = TextDark)
                            Text(text = "পিতা: ${rStudent?.father.orEmpty().ifEmpty { "প্রযোজ্য নয়" }}", color = TextDark)
                            Text(text = "খাত: ${rFee.type} (${rFee.month})", color = TextDark)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "পরিমাণ: ${rFee.amount.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MadrasaGreen)
                        }
                    }
                }
            }
        )
    }
}
