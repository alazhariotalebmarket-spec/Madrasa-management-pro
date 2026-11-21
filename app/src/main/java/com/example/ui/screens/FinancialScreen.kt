package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.FeeEntity
import com.example.data.entity.TeacherEntity
import com.example.data.entity.TeacherPaymentEntity
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaPrimaryDark
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaSky
import com.example.util.PdfPrintUtil
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialScreen(
    fees: List<FeeEntity>,
    teachers: List<TeacherEntity>,
    teacherPayments: List<TeacherPaymentEntity>,
    expenses: List<ExpenseEntity>
) {
    val context = LocalContext.current
    val months = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
    var isYearly by remember { mutableStateOf(false) }
    var filterMonth by remember {
        mutableStateOf(months.getOrElse(Calendar.getInstance().get(Calendar.MONTH)) { "জানুয়ারি" })
    }
    var filterYear by remember {
        mutableStateOf(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()))
    }
    var expandedFilterMonth by remember { mutableStateOf(false) }

    // Filter fees
    val periodFees = remember(fees, isYearly, filterMonth, filterYear) {
        fees.filter { f ->
            val matchYear = f.date.endsWith(filterYear) || f.month.contains(filterYear)
            if (isYearly) matchYear else (matchYear && f.month.startsWith(filterMonth))
        }
    }

    // Filter expenses
    val periodExpenses = remember(expenses, isYearly, filterMonth, filterYear) {
        expenses.filter { exp ->
            val matchYear = exp.date.endsWith(filterYear) || exp.date.isEmpty()
            val matchMonth = if (isYearly) true else {
                val parts = exp.date.split("/")
                if (parts.size >= 2) {
                    val mIndex = parts[1].toIntOrNull() ?: 1
                    months.getOrNull(mIndex - 1) == filterMonth
                } else true
            }
            matchYear && matchMonth
        }
    }

    // Filter teacher payments
    val periodTeacherPayments = remember(teacherPayments, teachers, isYearly, filterMonth, filterYear) {
        val payments = teacherPayments.filter { p ->
            val matchYear = p.year == filterYear || p.date.endsWith(filterYear)
            if (isYearly) matchYear else (matchYear && p.month.startsWith(filterMonth))
        }
        if (payments.isEmpty() && teachers.isNotEmpty()) {
            val multiplier = if (isYearly) 12.0 else 1.0
            teachers.map { TeacherPaymentEntity(teacherId = it.id, amount = it.salary * multiplier, month = filterMonth, year = filterYear, date = "") }
        } else {
            payments
        }
    }

    // 1. Income breakdown (আয়: ছাত্রদের বেতন, বিদ্যুৎ বিল, ভর্তি/পরীক্ষা/চাঁদা ও দান)
    val salaryIncome = periodFees.filter { it.type == "মাসিক বেতন" }.sumOf { it.amount }
    val electricIncome = periodFees.filter { it.type == "মাসিক বিদ্যুৎ বিল" }.sumOf { it.amount }
    val donationIncome = periodExpenses.filter { it.type.contains("দান") }.sumOf { it.amount } +
            periodFees.filter { it.type == "চাঁদা" || it.type == "ভর্তি ফি" || it.type == "পরীক্ষার ফি" || it.type == "অন্যান্য" }.sumOf { it.amount }

    val totalIncome = salaryIncome + electricIncome + donationIncome

    // 2. Expense breakdown (ব্যয়: মুহতামিম জমা + শিক্ষক বেতন + বিদ্যুৎ বিল + অন্যান্য খরচ)
    val muhtamimJomaExpense = periodExpenses.filter { it.type == "মুহতামিম জমা" }.sumOf { it.amount }
    val teacherSalaryExpense = periodTeacherPayments.sumOf { it.amount }
    val electricExpense = periodExpenses.filter {
        it.type == "খরচ" && (it.desc.contains("বিদ্যুৎ") || it.desc.contains("কারেন্ট"))
    }.sumOf { it.amount }
    val otherExpense = periodExpenses.filter {
        it.type == "খরচ" && !it.desc.contains("বিদ্যুৎ") && !it.desc.contains("কারেন্ট")
    }.sumOf { it.amount }

    val totalExpense = muhtamimJomaExpense + teacherSalaryExpense + electricExpense + otherExpense

    // 3. Final Summary
    val balance = maxOf(0.0, totalIncome - totalExpense)
    val deficit = if (totalExpense > totalIncome) totalExpense - totalIncome else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Filter Card
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
                        text = "📊 আর্থিক হিসাব বিবরণী",
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
                            selected = !isYearly,
                            onClick = { isYearly = false },
                            label = { Text("মাসিক হিসাব") }
                        )
                        FilterChip(
                            selected = isYearly,
                            onClick = { isYearly = true },
                            label = { Text("বাৎসরিক হিসাব") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isYearly) {
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

                    Spacer(modifier = Modifier.height(10.dp))
                    val periodTitle = if (isYearly) "$filterYear সাল (বাৎসরিক)" else "$filterMonth $filterYear (মাসিক)"
                    Button(
                        onClick = {
                            PdfPrintUtil.generateAndShareFinancialStatementPdf(
                                context = context,
                                period = periodTitle,
                                isYearly = isYearly,
                                salaryIncome = salaryIncome,
                                electricIncome = electricIncome,
                                donationIncome = donationIncome,
                                totalIncome = totalIncome,
                                muhtamimJomaExpense = muhtamimJomaExpense,
                                teacherSalaryExpense = teacherSalaryExpense,
                                electricExpense = electricExpense,
                                otherExpense = otherExpense,
                                totalExpense = totalExpense,
                                balance = balance,
                                deficit = deficit
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
                            text = if (isYearly) "📊 বাৎসরিক আর্থিক রিপোর্ট PDF ডাউনলোড" else "📄 মাসিক আর্থিক রিপোর্ট PDF ডাউনলোড",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section 1: আয় (Income)
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
                        text = "📥 আয় খাতসমূহ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MadrasaGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FinancialItemRow("১. মাসিক বেতন আদায়:", salaryIncome)
                    FinancialItemRow("২. মাসিক বিদ্যুৎ বিল আদায়:", electricIncome)
                    FinancialItemRow("৩. মাসিক চাঁদা, ভর্তি ও পরীক্ষার ফি:", donationIncome)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "মোট আয়:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(text = "${totalIncome.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MadrasaGreen)
                    }
                }
            }
        }

        // Section 2: ব্যয় (Expense) — includes মুহতামিম জমা
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
                        text = "📤 ব্যয় খাতসমূহ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MadrasaRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FinancialItemRow("১. মুহতামিম জমা (বেতন ও বিদ্যুৎ বিল):", muhtamimJomaExpense)
                    FinancialItemRow("২. মাসিক শিক্ষক বেতন:", teacherSalaryExpense)
                    FinancialItemRow("৩. মাসিক বিদ্যুৎ বিল খরচ:", electricExpense)
                    FinancialItemRow("৪. অন্যান্য ব্যয়:", otherExpense)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "মোট ব্যয়:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(text = "${totalExpense.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MadrasaRed)
                    }
                }
            }
        }

        // Section 3: সারসংক্ষেপ (Deep Ocean Teal Signature Card)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MadrasaPrimaryDark),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "⚖️ চূড়ান্ত আর্থিক সারসংক্ষেপ",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MadrasaGold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("মোট আয়:", color = Color.White, fontSize = 14.sp)
                        Text("${totalIncome.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF4ADE80))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("মোট ব্যয় (মুহতামিম জমাসহ):", color = Color.White, fontSize = 14.sp)
                        Text("${totalExpense.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFF87171))
                    }

                    HorizontalDivider(color = MadrasaSky.copy(alpha = 0.25f), modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("অবশিষ্ট (হাতে থাকা নগদ):", color = MadrasaSky, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${balance.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF4ADE80))
                    }

                    if (deficit > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("অতিরিক্ত খরচ (ঘাটতি):", color = Color(0xFFFEE2E2), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${deficit.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialItemRow(title: String, amount: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "${amount.toInt()} ৳", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
