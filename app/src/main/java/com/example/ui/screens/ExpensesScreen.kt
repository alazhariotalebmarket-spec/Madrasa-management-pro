package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
    var selectedTab by remember { mutableStateOf("খরচ") } // "খরচ" or "মুহতামিম জমা"

    var desc by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var entryDate by remember {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
    }

    // Muhtamim Jama sub-heads
    val defaultMuhtamimHeads = remember { mutableStateListOf("বেতন", "বিদ্যুৎ বিল", "ভর্তি ফি", "পরীক্ষার ফি") }
    var selectedMuhtamimHead by remember { mutableStateOf("বেতন") }
    var showAddHeadDialog by remember { mutableStateOf(false) }
    var customHeadInput by remember { mutableStateOf("") }

    val months = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
    var isYearly by remember { mutableStateOf(false) }
    var filterMonth by remember {
        mutableStateOf(months.getOrElse(Calendar.getInstance().get(Calendar.MONTH)) { "জানুয়ারি" })
    }
    var filterYear by remember {
        mutableStateOf(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()))
    }
    var expandedFilterMonth by remember { mutableStateOf(false) }

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
        // Tab Selector
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
                        text = if (selectedTab == "খরচ") "🧾 নতুন খরচ এন্ট্রি (ব্যয়)" else "📤 মুহতামিম জমা এন্ট্রি (বেতন, বিদ্যুৎ, ভর্তি, পরীক্ষা)",
                        fontSize = 16.sp,
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
                                    text = "মুহতামিম জমার বিবরণ: বেতন, বিদ্যুৎ বিল, ভর্তি ফি, পরীক্ষার ফি ও টাকার পরিমাণ সহ এন্ট্রি করুন। নতুন খাত যোগ করতে '+' বাটন চাপুন।",
                                    fontSize = 12.sp,
                                    color = Color(0xFF7F1D1D),
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "বাকি জমা দেওয়ার মতো সংগ্রহ: ${remainingToHandover.toInt()} ৳",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MadrasaPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Muhtamim 4+ Heads Selector with '+' Button
                        Text("খাত নির্বাচন:", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            defaultMuhtamimHeads.forEach { head ->
                                OutlinedButton(
                                    onClick = { selectedMuhtamimHead = head },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selectedMuhtamimHead == head) MadrasaRed.copy(alpha = 0.15f) else Color.Transparent,
                                        contentColor = if (selectedMuhtamimHead == head) MadrasaRed else TextDark
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(head, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            IconButton(
                                onClick = { showAddHeadDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MadrasaPrimary, CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "নতুন খাত যোগ করুন", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = {
                            Text(
                                if (selectedTab == "খরচ")
                                    "খরচের বিবরণ (যেমন: চাল, ডাল, মেরামত)"
                                else
                                    "বিবরণ / নোট (খাত: $selectedMuhtamimHead)"
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
                            label = { Text("টাকার পরিমাণ *") },
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
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) return@Button
                            val finalDesc = if (selectedTab == "মুহতামিম জমা") {
                                "[$selectedMuhtamimHead] ${desc.trim()}".trim()
                            } else {
                                desc.trim()
                            }
                            if (finalDesc.isBlank()) return@Button

                            onAddExpense(selectedTab, finalDesc, amt, entryDate.trim())
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
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 মাসিক ও বাৎসরিক হিসাব রিপোর্ট",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { isYearly = false },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (!isYearly) MadrasaPrimary.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("মাসিক", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { isYearly = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isYearly) MadrasaPrimary.copy(alpha = 0.15f) else Color.Transparent
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
                        if (!isYearly) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { expandedFilterMonth = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("মাস: $filterMonth", fontSize = 12.sp)
                                }
                                DropdownMenu(
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
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("নির্বাচিত সময়কালের মোট আয় (বেতন + বিদ্যুৎ): ${periodSalaryAndElectricIncome.toInt()} ৳", fontSize = 13.sp, color = MadrasaGreen, fontWeight = FontWeight.SemiBold)
                    Text("নির্বাচিত সময়কালের মোট সাধারণ খরচ: ${periodGeneralExpense.toInt()} ৳", fontSize = 13.sp, color = TextDark)
                    Text("নির্বাচিত সময়কালের মোট মুহতামিম জমা: ${periodMuhtamimJoma.toInt()} ৳", fontSize = 13.sp, color = MadrasaRed, fontWeight = FontWeight.SemiBold)
                    Text("মোট ব্যয়: ${periodTotalExpense.toInt()} ৳", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val titleStr = if (isYearly) "বাৎসরিক হিসাব রিপোর্ট - $filterYear" else "মাসিক হিসাব রিপোর্ট - $filterMonth $filterYear"
                            PdfPrintUtil.generateAndShareExpensesReportPdf(
                                context = context,
                                periodTitle = titleStr,
                                expenses = allPeriodExpenses,
                                totalIncome = periodSalaryAndElectricIncome,
                                totalExpense = periodTotalExpense,
                                netBalance = periodSalaryAndElectricIncome - periodTotalExpense
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("download_expenses_pdf_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isYearly) "বাৎসরিক হিসাব PDF ডাউনলোড" else "মাসিক হিসাব PDF ডাউনলোড",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedTab তালিকা (${filteredList.size} টি) — মোট: ${totalTabAmount.toInt()} ৳",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (filteredList.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = "কোন $selectedTab এন্ট্রি নেই",
                    subtitle = "উপরের ফরম থেকে নতুন এন্ট্রি যুক্ত করুন"
                )
            }
        } else {
            items(filteredList, key = { it.id }) { exp ->
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
                                text = exp.desc,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "তারিখ: ${exp.date}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${exp.amount.toInt()} ৳",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp,
                                color = if (selectedTab == "খরচ") MadrasaPrimary else MadrasaRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { onDeleteExpense(exp.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = MadrasaRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddHeadDialog) {
        AlertDialog(
            onDismissRequest = { showAddHeadDialog = false },
            title = { Text("নতুন মুহতামিম জমা খাত যোগ করুন", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = customHeadInput,
                    onValueChange = { customHeadInput = it },
                    label = { Text("খাতের নাম (যেমন: অনুদান, কিতাব বিক্রয়)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customHeadInput.isNotBlank()) {
                            val newHead = customHeadInput.trim()
                            if (!defaultMuhtamimHeads.contains(newHead)) {
                                defaultMuhtamimHeads.add(newHead)
                            }
                            selectedMuhtamimHead = newHead
                            customHeadInput = ""
                            showAddHeadDialog = false
                            Toast.makeText(context, "নতুন খাত যোগ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary)
                ) {
                    Text("যোগ করুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddHeadDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
