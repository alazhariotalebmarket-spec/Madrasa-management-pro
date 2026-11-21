package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.StudentEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaSapphire
import com.example.util.PdfPrintUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmitCardScreen(
    students: List<StudentEntity>
) {
    val context = LocalContext.current
    val examOptions = listOf("১ম সাময়িক", "২য় সাময়িক", "বার্ষিক পরীক্ষা", "মাসিক পরীক্ষা")
    var selectedExam by remember { mutableStateOf("১ম সাময়িক") }
    var expandedExam by remember { mutableStateOf(false) }

    val classOptions = remember(students) {
        listOf("সব শ্রেণী") + students.map { it.cls }.filter { it.isNotBlank() }.distinct()
    }
    var selectedClass by remember { mutableStateOf("সব শ্রেণী") }
    var expandedClass by remember { mutableStateOf(false) }

    var scheduleNote by remember { mutableStateOf("পরীক্ষা শুরু: সকাল ১০:০০ টা • পরীক্ষা শুরুর ১৫ মিনিট পূর্বে আসন গ্রহণ করতে হবে।") }

    val filteredStudents = remember(students, selectedClass) {
        if (selectedClass == "সব শ্রেণী") students else students.filter { it.cls == selectedClass }
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
                        text = "🪪 পরীক্ষার প্রবেশ পত্র (Admit Card)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expandedExam,
                            onExpandedChange = { expandedExam = !expandedExam },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedExam,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("পরীক্ষার নাম") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedExam) },
                                modifier = Modifier.menuAnchor(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedExam,
                                onDismissRequest = { expandedExam = false }
                            ) {
                                examOptions.forEach { exam ->
                                    DropdownMenuItem(
                                        text = { Text(exam) },
                                        onClick = {
                                            selectedExam = exam
                                            expandedExam = false
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
                                value = selectedClass,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("শ্রেণী / জামাত") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClass) },
                                modifier = Modifier.menuAnchor(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedClass,
                                onDismissRequest = { expandedClass = false }
                            ) {
                                classOptions.forEach { cls ->
                                    DropdownMenuItem(
                                        text = { Text(cls) },
                                        onClick = {
                                            selectedClass = cls
                                            expandedClass = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scheduleNote,
                        onValueChange = { scheduleNote = it },
                        label = { Text("পরীক্ষার সময়সূচী / নির্দেশনা") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            PdfPrintUtil.generateAndShareAdmitCardsPdf(
                                context = context,
                                examName = selectedExam,
                                examScheduleNote = scheduleNote,
                                students = filteredStudents
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("download_all_admit_cards_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("সকল ছাত্রের প্রবেশ পত্র PDF (${filteredStudents.size} জন)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (filteredStudents.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.AssignmentInd,
                    title = "প্রবেশ পত্রের জন্য কোন ছাত্র পাওয়া যায়নি",
                    subtitle = "আগে ভর্তি অপশন থেকে ছাত্র যুক্ত করুন অথবা শ্রেণীর ফিল্টার পরিবর্তন করুন"
                )
            }
        } else {
            items(filteredStudents, key = { it.id }) { student ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = madrasaCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (student.photoUri.isNotEmpty()) {
                            AsyncImage(
                                model = student.photoUri,
                                contentDescription = student.name,
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(student.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val rollTxt = if (student.roll.isNotBlank()) " • রোল: ${student.roll}" else ""
                            Text("${student.cls}$rollTxt", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                PdfPrintUtil.generateAndShareAdmitCardsPdf(
                                    context = context,
                                    examName = selectedExam,
                                    examScheduleNote = scheduleNote,
                                    students = listOf(student)
                                )
                            },
                            modifier = Modifier.heightIn(min = 42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaSapphire, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
