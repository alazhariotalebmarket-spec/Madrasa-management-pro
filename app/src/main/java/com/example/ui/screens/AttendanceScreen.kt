package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.AttendanceEntity
import com.example.data.entity.StudentEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    students: List<StudentEntity>,
    getAttendanceForDate: (String) -> Flow<List<AttendanceEntity>>,
    onSaveAttendance: (String, Map<Int, String>) -> Unit
) {
    val context = LocalContext.current
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var selectedDate by remember { mutableStateOf(todayStr) }
    val attendanceState = remember { mutableStateMapOf<Int, String>() }

    var selectedClassFilter by remember { mutableStateOf("সকল শ্রেণী") }
    var expandedClassDropdown by remember { mutableStateOf(false) }

    val classesList = remember(students) {
        listOf("সকল শ্রেণী") + students.map { it.cls }.distinct().filter { it.isNotBlank() }
    }

    val savedAttendance by remember(selectedDate) {
        getAttendanceForDate(selectedDate)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(selectedDate) {
        attendanceState.clear()
    }

    LaunchedEffect(selectedDate, savedAttendance, students) {
        val savedMap = savedAttendance.associate { it.studentId to it.status }
        students.forEach { s ->
            val persisted = savedMap[s.id]
            if (persisted != null) {
                attendanceState[s.id] = persisted
            } else if (!attendanceState.containsKey(s.id)) {
                attendanceState[s.id] = "উপস্থিত"
            }
        }
    }

    val filteredStudents = remember(students, selectedClassFilter) {
        val list = if (selectedClassFilter == "সকল শ্রেণী") students else students.filter { it.cls == selectedClassFilter }
        list.sortedBy { s -> s.roll.toIntOrNull() ?: 9999 }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "দৈনিক হাজিরা (শ্রেণী ও রোল অনুযায়ী)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                onSaveAttendance(selectedDate, attendanceState.toMap())
                                Toast.makeText(context, "হাজিরা সফলভাবে সংরক্ষিত হয়েছে ($selectedDate)!", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("save_attendance_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("সংরক্ষণ", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedDate,
                            onValueChange = { selectedDate = it },
                            label = { Text("তারিখ (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        ExposedDropdownMenuBox(
                            expanded = expandedClassDropdown,
                            onExpandedChange = { expandedClassDropdown = !expandedClassDropdown },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedClassFilter,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("শ্রেণী নির্বাচন") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClassDropdown) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedClassDropdown,
                                onDismissRequest = { expandedClassDropdown = false }
                            ) {
                                classesList.forEach { cls ->
                                    DropdownMenuItem(
                                        text = { Text(cls) },
                                        onClick = {
                                            selectedClassFilter = cls
                                            expandedClassDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (filteredStudents.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.FactCheck,
                    title = "কোন ছাত্র পাওয়া যায়নি",
                    subtitle = "এই শ্রেণীতে কোন ছাত্র ভর্তি করা হয়নি"
                )
            }
        } else {
            items(filteredStudents, key = { it.id }) { student ->
                var expanded by remember { mutableStateOf(false) }
                val currentStatus = attendanceState[student.id] ?: "উপস্থিত"
                val rollDisplay = if (student.roll.isNotBlank()) "রোল: ${student.roll}" else "রোল: -"

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
                            Text(text = student.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "${student.cls}  •  $rollDisplay",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.heightIn(min = 40.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = when (currentStatus) {
                                        "উপস্থিত" -> "✅ উপস্থিত"
                                        "অনুপস্থিত" -> "❌ অনুপস্থিত"
                                        else -> "🟡 ছুটি"
                                    },
                                    fontSize = 13.sp
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
        }
    }
}
