package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MadrasaInfo
import com.example.data.entity.FeeEntity
import com.example.data.entity.StudentEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    students: List<StudentEntity>,
    fees: List<FeeEntity>,
    madrasaInfo: MadrasaInfo = MadrasaInfo()
) {
    val context = LocalContext.current

    var selectedClassFilter by remember { mutableStateOf("সকল শ্রেণী") }
    var expandedClassFilter by remember { mutableStateOf(false) }

    val classesList = remember(students) {
        listOf("সকল শ্রেণী") + students.map { it.cls }.distinct().filter { it.isNotBlank() }
    }

    val filteredStudents = remember(students, selectedClassFilter) {
        val list = if (selectedClassFilter == "সকল শ্রেণী") students else students.filter { it.cls == selectedClassFilter }
        list.sortedBy { s -> s.roll.toIntOrNull() ?: 9999 }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = madrasaCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 কল ও এসএমএস (শ্রেণী ও রোল অনুযায়ী)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    ExposedDropdownMenuBox(
                        expanded = expandedClassFilter,
                        onExpandedChange = { expandedClassFilter = !expandedClassFilter },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedClassFilter,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("শ্রেণী নির্বাচন করে ফিল্টার করুন") },
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
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filteredStudents.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.PeopleOutline,
                    title = "কোন ছাত্র পাওয়া যায়নি",
                    subtitle = "এই শ্রেণীতে কোন ছাত্র ভর্তি করা হয়নি"
                )
            }
        } else {
            items(filteredStudents, key = { it.id }) { student ->
                val paidTotal = fees.filter { it.studentId == student.id }.sumOf { it.amount }
                val due = maxOf(0.0, student.fee - paidTotal)
                val isDue = due > 0
                val rollStr = if (student.roll.isNotBlank()) "রোল: ${student.roll}" else "রোল: -"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = if (isDue) androidx.compose.foundation.BorderStroke(1.dp, MadrasaRed.copy(alpha = 0.35f)) else madrasaCardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = student.name, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                                Text(
                                    text = "${student.cls} • $rollStr • ফোন: ${student.phone.ifEmpty { "নেই" }}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isDue) MadrasaRed.copy(alpha = 0.12f) else MadrasaGreen.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (isDue) "বাকি ${due.toInt()} ৳" else "✅ পরিশোধ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isDue) MadrasaRed else MadrasaGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (student.phone.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.phone}"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("কল করুন", fontSize = 12.5.sp)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        val message = "আসসালামু আলাইকুম, ${madrasaInfo.name}-এর পক্ষ থেকে জানানো যাচ্ছে যে, আপনার সন্তান ${student.name}-এর বকেয়া বেতন ${due.toInt()} টাকা বাকি রয়েছে। দয়া করে পরিশোধ করার অনুরোধ করা হলো।"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:${student.phone}")).apply {
                                            putExtra("sms_body", message)
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Message, contentDescription = "SMS", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("এসএমএস", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
