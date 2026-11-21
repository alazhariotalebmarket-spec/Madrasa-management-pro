package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
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
import com.example.data.entity.ClassRoutineEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaSapphire
import com.example.util.PdfPrintUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassRoutineScreen(
    routines: List<ClassRoutineEntity>,
    onAddRoutine: (String, String, String, String, String) -> Unit,
    onDeleteRoutine: (Int) -> Unit
) {
    val context = LocalContext.current
    val days = listOf("শনিবার", "রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার")
    val classPresets = listOf("নূরানী - শিশু", "নূরানী - প্রথম", "নূরানী - দ্বিতীয়", "নূরানী - তৃতীয়", "নাজেরা", "হিফজ", "কিতাব বিভাগ")

    var selectedDay by remember { mutableStateOf("শনিবার") }
    var expandedDay by remember { mutableStateOf(false) }

    var className by remember { mutableStateOf("নূরানী - প্রথম") }
    var expandedClass by remember { mutableStateOf(false) }

    var timeSlot by remember { mutableStateOf("সকাল ৮:০০ - ৯:০০") }
    var subject by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf("") }

    var filterClass by remember { mutableStateOf("সকল শ্রেণী") }
    var expandedFilter by remember { mutableStateOf(false) }

    val availableFilterClasses = remember(routines) {
        listOf("সকল শ্রেণী") + (classPresets + routines.map { it.className }).distinct()
    }

    val filteredRoutines = remember(routines, filterClass) {
        if (filterClass == "সকল শ্রেণী") routines else routines.filter { it.className == filterClass }
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
                    Text(
                        text = "📅 ক্লাস রুটিন যুক্ত করুন",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = expandedDay,
                            onExpandedChange = { expandedDay = !expandedDay },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedDay,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("বার") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDay) },
                                modifier = Modifier.menuAnchor(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedDay,
                                onDismissRequest = { expandedDay = false }
                            ) {
                                days.forEach { d ->
                                    DropdownMenuItem(
                                        text = { Text(d) },
                                        onClick = {
                                            selectedDay = d
                                            expandedDay = false
                                        }
                                    )
                                }
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = expandedClass,
                            onExpandedChange = { expandedClass = !expandedClass },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = className,
                                onValueChange = { className = it },
                                label = { Text("শ্রেণী") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClass) },
                                modifier = Modifier.menuAnchor(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedClass,
                                onDismissRequest = { expandedClass = false }
                            ) {
                                classPresets.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c) },
                                        onClick = {
                                            className = c
                                            expandedClass = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = timeSlot,
                        onValueChange = { timeSlot = it },
                        label = { Text("সময় (যেমন: সকাল ৮:০০ - ৯:০০)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("বিষয় / কিতাব *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = teacherName,
                            onValueChange = { teacherName = it },
                            label = { Text("শিক্ষকের নাম") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (subject.isBlank()) return@Button
                            onAddRoutine(
                                className.trim(),
                                selectedDay,
                                timeSlot.trim(),
                                subject.trim(),
                                teacherName.trim()
                            )
                            subject = ""
                            teacherName = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_routine_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("রুটিনে যুক্ত করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = madrasaCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedFilter,
                        onExpandedChange = { expandedFilter = !expandedFilter }
                    ) {
                        OutlinedTextField(
                            value = filterClass,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("শ্রেণী অনুযায়ী ফিল্টার") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFilter) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFilter,
                            onDismissRequest = { expandedFilter = false }
                        ) {
                            availableFilterClasses.forEach { fc ->
                                DropdownMenuItem(
                                    text = { Text(fc) },
                                    onClick = {
                                        filterClass = fc
                                        expandedFilter = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            PdfPrintUtil.generateAndShareClassRoutinePdf(
                                context = context,
                                classFilterTitle = filterClass,
                                routines = filteredRoutines
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("download_routine_pdf_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaSapphire, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ক্লাস রুটিন PDF ডাউনলোড (${filteredRoutines.size} টি)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (filteredRoutines.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.CalendarMonth,
                    title = "কোন রুটিন যুক্ত করা হয়নি",
                    subtitle = "উপরের ফর্ম থেকে সাপ্তাহিক ক্লাস রুটিন যুক্ত করুন"
                )
            }
        } else {
            items(filteredRoutines, key = { it.id }) { item ->
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
                            Text(
                                "${item.dayOfWeek} • ${item.timeSlot}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("বিষয়: ${item.subject} (${item.className})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (item.teacherName.isNotBlank()) {
                                Text("শিক্ষক: ${item.teacherName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onDeleteRoutine(item.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = MadrasaRed)
                        }
                    }
                }
            }
        }
    }
}
