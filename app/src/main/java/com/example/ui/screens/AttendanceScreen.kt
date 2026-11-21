package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.StudentEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    students: List<StudentEntity>,
    onSaveAttendance: (String, Map<Int, String>) -> Unit
) {
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var selectedDate by remember { mutableStateOf(todayStr) }
    val attendanceState = remember { mutableStateMapOf<Int, String>() }
    var summaryText by remember { mutableStateOf("") }

    LaunchedEffect(students) {
        students.forEach { s ->
            if (!attendanceState.containsKey(s.id)) {
                attendanceState[s.id] = "উপস্থিত"
            }
        }
    }

    val statusOptions = listOf("উপস্থিত", "অনুপস্থিত", "ছুটি")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Text(
                        text = "দৈনিক হাজিরা",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = selectedDate,
                        onValueChange = { selectedDate = it },
                        label = { Text("তারিখ (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        if (students.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.FactCheck,
                    title = "কোন ছাত্র পাওয়া যায়নি",
                    subtitle = "হাজিরা নেওয়ার জন্য আগে ভর্তি অপশন থেকে ছাত্র যুক্ত করুন"
                )
            }
        } else {
            items(students) { student ->
                var expanded by remember { mutableStateOf(false) }
                val currentStatus = attendanceState[student.id] ?: "উপস্থিত"

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
                            Text(text = student.name, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                            Text(
                                text = student.cls,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.heightIn(min = 42.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = when (currentStatus) {
                                        "উপস্থিত" -> "✅ উপস্থিত"
                                        "অনুপস্থিত" -> "❌ অনুপস্থিত"
                                        else -> "🟡 ছুটি"
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                statusOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (opt) {
                                                    "উপস্থিত" -> "✅ উপস্থিত"
                                                    "অনুপস্থিত" -> "❌ অনুপস্থিত"
                                                    else -> "🟡 ছুটি"
                                                }
                                            )
                                        },
                                        onClick = {
                                            attendanceState[student.id] = opt
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        onSaveAttendance(selectedDate, attendanceState.toMap())
                        val presentCount = attendanceState.values.count { it == "উপস্থিত" }
                        summaryText = "আজ উপস্থিত: $presentCount জন, মোট: ${students.size} জন"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("হাজিরা সেভ করুন", fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                }
                if (summaryText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MadrasaGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = summaryText,
                            color = MadrasaGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
