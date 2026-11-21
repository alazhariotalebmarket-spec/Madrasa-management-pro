package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    var selectedStudentId by remember { mutableStateOf(students.first().id) }
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

    // Filtered fees for calculations
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

    val totalSalaryCollected = reportFees.filter { it.type == "মাসিক বেতন" }.sumOf { it.amount }
    val totalElectricCollected = reportFees.filter { it.type == "মাসিক বিদ্যুৎ বিল" }.sumOf { it.amount }
    val totalExamCollected = reportFees.filter { it.type == "পরীক্ষার ফি" }.sumOf { it.amount }
    val totalOtherCollected = reportFees.filter { it.type !in listOf("মাসিক বেতন", "মাসিক বিদ্যুৎ বিল", "পরীক্ষার ফি") }.sumOf { it.amount }
    val totalCollected = reportFees.sumOf { it.amount }

    // Expected calculation
    val expectedTuition = if (isYearlyReport) students.sumOf { it.fee * 12 } else students.sumOf { it.fee }
    val expectedElectric = if (isYearlyReport) students.sumOf { it.electricBill * 12 } else students.sumOf { it.electricBill }
    val totalExpected = expectedTuition + expectedElectric
    val totalDue = maxOf(0.0, totalExpected - (totalSalaryCollected + totalElectricCollected))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Fee Collection Card
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
                        text = "💰 বেতন ও ফি আদায় এন্ট্রি",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "ছাত্র নির্বাচন:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExposedDropdownMenuBox(
                        expanded = expandedStudent,
                        onExpandedChange = { expandedStudent = !expandedStudent }
                    ) {
                        OutlinedTextField(
                            value = "${selectedStudent.name} (${selectedStudent.cls})",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStudent) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedStudent,
                            onDismissRequest = { expandedStudent = false }
                        ) {
                            students.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${s.name} - ${s.cls} (বেতন: ${s.fee.toInt()}৳, কারেন্ট: ${s.electricBill.toInt()}৳)")
                                    },
                                    onClick = {
                                        selectedStudentId = s.id
                                        expandedStudent = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MadrasaContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("মাসিক বেতন: ${selectedStudent.fee.toInt()} ৳", fontSize = 12.sp, color = MadrasaPrimary, fontWeight = FontWeight.Bold)
                            Text("মাসিক বিদ্যুৎ বিল: ${selectedStudent.electricBill.toInt()} ৳", fontSize = 12.sp, color = MadrasaAccent, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "খাত নির্বাচন:", fontSize = 13.sp, color = Color.Gray)
                    ExposedDropdownMenuBox(
                        expanded = expandedType,
                        onExpandedChange = { expandedType = !expandedType }
                    ) {
                        OutlinedTextField(
                            value = feeType,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedType,
                            onDismissRequest = { expandedType = false }
                        ) {
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            label = { Text("টাকার পরিমাণ") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = expandedMonth,
                            onExpandedChange = { expandedMonth = !expandedMonth },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = feeMonth,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("মাস") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonth) },
                                modifier = Modifier.menuAnchor(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedMonth,
                                onDismissRequest = { expandedMonth = false }
                            ) {
                                months.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = {
                                            feeMonth = m
                                            expandedMonth = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) return@Button
                            onAddFee(selectedStudentId, amt, feeType, feeMonth)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("টাকা জমা করুন", fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active Receipt View
        if (activeReceiptFee != null) {
            val rFee = activeReceiptFee!!
            val rStudent = students.find { it.id == rFee.studentId }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.madrasa_logo_round),
                            contentDescription = "Logo",
                            modifier = Modifier.size(50.dp).clip(CircleShape)
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
                            Text(text = "জমার পরিমাণ: ${rFee.amount.toInt()} ৳", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MadrasaPrimary)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE2E8F0))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    PdfPrintUtil.generateAndShareFeeReceiptPdf(context, rStudent, rFee)
                                },
                                modifier = Modifier.heightIn(min = 44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("🖨️ PDF ডাউনলোড / সেভ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { activeReceiptFee = null },
                                modifier = Modifier.heightIn(min = 44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MadrasaAccent, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("বন্ধ", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Monthly & Yearly Report Section
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
                        text = "📊 মাসিক ও বাৎসরিক হিসাব ও রিপোর্ট",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isYearlyReport,
                            onClick = { isYearlyReport = false },
                            label = { Text("মাসিক হিসাব") }
                        )
                        FilterChip(
                            selected = isYearlyReport,
                            onClick = { isYearlyReport = true },
                            label = { Text("বাৎসরিক হিসাব") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isYearlyReport) {
                            ExposedDropdownMenuBox(
                                expanded = expandedFilterMonth,
                                onExpandedChange = { expandedFilterMonth = !expandedFilterMonth },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = filterMonth,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("মাস") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFilterMonth) },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedFilterMonth,
                                    onDismissRequest = { expandedFilterMonth = false }
                                ) {
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
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    // Summary Box
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MadrasaContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("মোট ধার্য:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDark)
                                Text("${totalExpected.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MadrasaPrimary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("সর্বমোট জমা:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDark)
                                Text("${totalCollected.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MadrasaGreen)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("সর্বমোট বাকি:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDark)
                                Text("${totalDue.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MadrasaRed)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MadrasaPrimary.copy(alpha = 0.2f))
                            Text(
                                text = "• মাসিক বেতন আদায়: ${totalSalaryCollected.toInt()} ৳\n" +
                                        "• বিদ্যুৎ বিল আদায়: ${totalElectricCollected.toInt()} ৳\n" +
                                        "• পরীক্ষার ফি আদায়: ${totalExamCollected.toInt()} ৳",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val periodTitle = if (isYearlyReport) "$filterYear সাল (বাৎসরিক)" else "$filterMonth $filterYear (মাসিক)"
                            PdfPrintUtil.generateAndShareFeeReportPdf(
                                context = context,
                                period = periodTitle,
                                isYearly = isYearlyReport,
                                students = students,
                                fees = reportFees
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isYearlyReport) "📊 বাৎসরিক রিপোর্ট PDF ডাউনলোড" else "📄 মাসিক রিপোর্ট PDF ডাউনলোড",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Recent Transactions List
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "সাম্প্রতিক আদায় তালিকা (${fees.size} টি)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (fees.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.Payments,
                    title = "কোন ফি জমা হয়নি",
                    subtitle = "উপরের ফর্ম থেকে ছাত্রের বেতন বা বিদ্যুৎ বিল জমা দিন"
                )
            }
        } else {
            items(fees.reversed()) { fee ->
                val s = students.find { it.id == fee.studentId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = madrasaCardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${s?.name ?: "ছাত্র"} - ${fee.type} (${fee.month})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(text = "${fee.date}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "${fee.amount.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(onClick = { activeReceiptFee = fee }) {
                                Text("রশিদ")
                            }
                            IconButton(onClick = { onDeleteFee(fee.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MadrasaRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
