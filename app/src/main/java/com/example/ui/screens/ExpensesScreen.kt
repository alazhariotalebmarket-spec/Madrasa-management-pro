package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.FeeEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaRedSoft
import com.example.ui.theme.TextDark
import com.example.util.PdfPrintUtil
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    expenses: List<ExpenseEntity>,
    fees: List<FeeEntity> = emptyList(),
    onAddExpense: (String, String, Double, String) -> Unit,
    onDeleteExpense: (Int) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("খরচ") } // "খরচ" or "মুহতামিম জমা" (both are ব্যয়/outflows)

    var desc by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var entryDate by remember {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
    }

    val months = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
    var isYearly by remember { mutableStateOf(false) }
    var filterMonth by remember {
        mutableStateOf(months.getOrElse(Calendar.getInstance().get(Calendar.MONTH)) { "জানুয়ারি" })
    }
    var filterYear by remember {
        mutableStateOf(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()))
    }
    var expandedFilterMonth by remember { mutableStateOf(false) }

    // Filtered expenses for the selected tab
    val filteredList = remember(expenses, selectedTab, isYearly, filterMonth, filterYear) {
        expenses.filter { exp ->
            val matchType = exp.type == selectedTab
            val matchYear = exp.date.endsWith(filterYear) || exp.date.isEmpty()
            val matchMonth = if (isYearly) true else {
                val parts = exp.date.split("/")
                if (parts.size >= 2) {
                    val mIndex = parts[1].toIntOrNull() ?: 1
                    months.getOrNull(mIndex - 1) == filterMonth
                } else true
            }
            matchType && matchYear && matchMonth
        }
    }

    val totalTabAmount = filteredList.sumOf { it.amount }

    // Period expense totals (Both সাধারণ খরচ and মুহতামিম জমা are ব্যয়)
    val allPeriodExpenses = remember(expenses, isYearly, filterMonth, filterYear) {
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

    // Period collected student monthly salary + electricity bill (আয়)
    val periodSalaryAndElectricIncome = remember(fees, isYearly, filterMonth, filterYear) {
        fees.filter { f ->
            val isFeeOrElectric = f.type == "মাসিক বেতন" || f.type == "মাসিক বিদ্যুৎ বিল"
            val matchYear = f.date.endsWith(filterYear) || f.month.contains(filterYear)
            val matchMonth = if (isYearly) true else f.month.startsWith(filterMonth)
            isFeeOrElectric && matchYear && matchMonth
        }.sumOf { it.amount }
    }

    val periodGeneralExpense = allPeriodExpenses.filter { it.type == "খরচ" }.sumOf { it.amount }
    val periodMuhtamimJoma = allPeriodExpenses.filter { it.type == "মুহতামিম জমা" }.sumOf { it.amount }
    val periodTotalExpense = periodGeneralExpense + periodMuhtamimJoma
    val remainingToHandover = maxOf(0.0, periodSalaryAndElectricIncome - periodMuhtamimJoma)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tab Selector: ১. খরচ vs ২. মুহতামিম জমা (ব্যয়)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = madrasaCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedTab = "খরচ" },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("expense_tab_general"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == "খরচ") MadrasaPrimary else Color.Transparent,
                            contentColor = if (selectedTab == "খরচ") Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("১. সাধারণ খরচ", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }

                    Button(
                        onClick = { selectedTab = "মুহতামিম জমা" },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("expense_tab_muhtamim"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == "মুহতামিম জমা") MadrasaRed else Color.Transparent,
                            contentColor = if (selectedTab == "মুহতামিম জমা") Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("২. মুহতামিম জমা (ব্যয়)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Entry Form Card
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
                        text = if (selectedTab == "খরচ") "🧾 নতুন খরচ এন্ট্রি (ব্যয়)" else "📤 মুহতামিম জমা এন্ট্রি (ব্যয়)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == "খরচ") MaterialTheme.colorScheme.primary else MadrasaRed
                    )

                    if (selectedTab == "মুহতামিম জমা") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = MadrasaRedSoft,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "ছাত্র-ছাত্রীদের আদায়কৃত মাসিক বেতন ও বিদ্যুৎ বিল মুহতামিম নিয়ে গেলে এখানে এন্ট্রি করুন। এটি মাদ্রাসার হিসাব থেকে ব্যয় হিসেবে বাদ যাবে।",
                                    fontSize = 12.sp,
                                    color = Color(0xFF7F1D1D),
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "আদায়কৃত বেতন ও বিদ্যুৎ বিল: ${periodSalaryAndElectricIncome.toInt()} ৳  •  জমা হয়েছে: ${periodMuhtamimJoma.toInt()} ৳  •  বাকি: ${remainingToHandover.toInt()} ৳",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MadrasaPrimary
                                )
                                if (remainingToHandover > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            desc = if (isYearly) {
                                                "$filterYear সালের ছাত্রদের মাসিক বেতন ও বিদ্যুৎ বিল বাবদ মুহতামিম জমা"
                                            } else {
                                                "$filterMonth $filterYear-এর ছাত্রদের মাসিক বেতন ও বিদ্যুৎ বিল বাবদ মুহতামিম জমা"
                                            }
                                            amountStr = remainingToHandover.toInt().toString()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "অবশিষ্ট বেতন ও বিদ্যুৎ বিল (${remainingToHandover.toInt()} ৳) বসান",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MadrasaRed
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = {
                            Text(
                                if (selectedTab == "খরচ")
                                    "খরচের বিবরণ (যেমন: চাল, ডাল, কারেন্ট বিল, মেরামত)"
                                else
                                    "মুহতামিম জমার বিবরণ (যেমন: মাসিক বেতন ও বিদ্যুৎ বিল বাবদ প্রদান)"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
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
                        OutlinedTextField(
                            value = entryDate,
                            onValueChange = { entryDate = it },
                            label = { Text("তারিখ (দিন/মাস/বছর)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (desc.isBlank()) return@Button
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) return@Button
                            onAddExpense(selectedTab, desc.trim(), amt, entryDate.trim())
                            desc = ""
                            amountStr = ""
                        },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_expense_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == "খরচ") MadrasaPrimary else MadrasaRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (selectedTab == "খরচ") "খরচ সেভ করুন" else "মুহতামিম জমা (ব্যয়) সেভ করুন",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Monthly & Yearly Calculations & PDF Card
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
                        text = "📊 $selectedTab - মাসিক ও বাৎসরিক হিসাব (ব্যয়)",
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
                    Surface(
                        color = MadrasaRedSoft,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("নির্বাচিত ট্যাবে মোট ($selectedTab):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDark)
                                Text("${totalTabAmount.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MadrasaRed)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFFECACA))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ছাত্রদের মাসিক বেতন ও বিদ্যুৎ বিল (আয়):", fontSize = 12.sp, color = Color(0xFF334155))
                                Text("${periodSalaryAndElectricIncome.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MadrasaGreen)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("মুহতামিম জমা (ব্যয়):", fontSize = 12.sp, color = Color(0xFF334155))
                                Text("${periodMuhtamimJoma.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MadrasaRed)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("সাধারণ খরচ (ব্যয়):", fontSize = 12.sp, color = Color(0xFF334155))
                                Text("${periodGeneralExpense.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MadrasaRed)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFFECACA))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("মোট ব্যয় (সাধারণ খরচ + মুহতামিম জমা):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                Text("${periodTotalExpense.toInt()} ৳", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MadrasaRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val periodTitle = if (isYearly) "$filterYear সাল (বাৎসরিক)" else "$filterMonth $filterYear (মাসিক)"
                            PdfPrintUtil.generateAndShareExpenseDepositReportPdf(
                                context = context,
                                reportType = selectedTab,
                                period = periodTitle,
                                isYearly = isYearly,
                                list = filteredList,
                                totalAmount = totalTabAmount
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
                            text = if (isYearly) "📊 বাৎসরিক $selectedTab (ব্যয়) রিপোর্ট PDF" else "📄 মাসিক $selectedTab (ব্যয়) রিপোর্ট PDF",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // List of entries
        item {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$selectedTab তালিকা (${filteredList.size} টি)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (filteredList.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = "কোন হিসাব নেই",
                    subtitle = "উপরের ফর্ম থেকে নতুন এন্ট্রি যোগ করুন"
                )
            }
        } else {
            items(filteredList.reversed()) { exp ->
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
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = exp.desc, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "${if (exp.date.isNotBlank()) "তারিখ: ${exp.date} • " else ""}${exp.type} (ব্যয়)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "- ${exp.amount.toInt()} ৳",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MadrasaRed
                            )
                            IconButton(onClick = { onDeleteExpense(exp.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MadrasaRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
