package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ResultEntity
import com.example.data.entity.StudentEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaSapphire
import com.example.util.CombinedResultHelper
import com.example.util.EXAM_ANNUAL
import com.example.util.EXAM_COMBINED_THREE
import com.example.util.EXAM_FIRST_TERM
import com.example.util.EXAM_MONTHLY
import com.example.util.EXAM_SECOND_TERM
import com.example.util.MeritPdfEntry
import com.example.util.PdfPrintUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeritListScreen(
    students: List<StudentEntity>,
    results: List<ResultEntity>,
    gradeCalculator: (Double) -> String
) {
    val context = LocalContext.current
    val examOptions = listOf(
        EXAM_FIRST_TERM,
        EXAM_SECOND_TERM,
        EXAM_ANNUAL,
        EXAM_COMBINED_THREE,
        EXAM_MONTHLY
    )
    var selectedExam by remember { mutableStateOf(EXAM_FIRST_TERM) }
    var expandedExam by remember { mutableStateOf(false) }

    val classOptions = remember(students) {
        listOf("সকল শ্রেণী") + students.map { it.cls }.filter { it.isNotBlank() }.distinct()
    }
    var selectedClass by remember { mutableStateOf("সকল শ্রেণী") }
    var expandedClass by remember { mutableStateOf(false) }

    val studentMap = remember(students) { students.associateBy { it.id } }

    val rankedList = remember(students, results, selectedExam, selectedClass) {
        if (selectedExam == EXAM_COMBINED_THREE) {
            val resultsByStudent = results.groupBy { it.studentId }
            val combinedEntries = students.filter { st ->
                selectedClass == "সকল শ্রেণী" || st.cls == selectedClass
            }.mapNotNull { st ->
                val stResults = resultsByStudent[st.id].orEmpty()
                val combined = CombinedResultHelper.computeForStudent(st.id, stResults) ?: return@mapNotNull null
                st to combined
            }.sortedWith(
                compareByDescending<Pair<StudentEntity, com.example.util.CombinedStudentResult>> { it.second.isOverallPass }
                    .thenByDescending { it.second.grandTotal }
                    .thenByDescending { it.second.overallAvg }
            )

            combinedEntries.mapIndexed { index, (st, combined) ->
                MeritPdfEntry(
                    rankLabel = calculateRankBengali(index + 1),
                    roll = st.roll,
                    name = st.name,
                    cls = "${st.cls} (${combined.examCount}/৩ পরীক্ষা)",
                    totalMarks = combined.grandTotal,
                    maxMarks = combined.maxPossible,
                    avg = combined.overallAvg,
                    grade = if (combined.isOverallPass) gradeCalculator(combined.overallAvg) else "F",
                    passed = combined.isOverallPass
                )
            }
        } else {
            val matchingResults = results.filter { res ->
                val st = studentMap[res.studentId]
                val examMatches = when (selectedExam) {
                    EXAM_SECOND_TERM -> CombinedResultHelper.isSecondTerm(res.exam)
                    else -> res.exam == selectedExam
                }
                val classMatches = selectedClass == "সকল শ্রেণী" || st?.cls == selectedClass
                examMatches && classMatches && st != null
            }.sortedWith(
                compareByDescending<ResultEntity> { it.isAllPassed(33.0) }
                    .thenByDescending { it.total }
                    .thenByDescending { it.avg }
            )

            matchingResults.mapIndexedNotNull { index, res ->
                val st = studentMap[res.studentId] ?: return@mapIndexedNotNull null
                val passed = res.isAllPassed(33.0)
                val maxMarks = (res.getSubjectList().size * 100).coerceAtLeast(100)
                MeritPdfEntry(
                    rankLabel = calculateRankBengali(index + 1),
                    roll = st.roll,
                    name = st.name,
                    cls = st.cls,
                    totalMarks = res.total,
                    maxMarks = maxMarks,
                    avg = res.avg,
                    grade = if (passed) gradeCalculator(res.avg) else "F",
                    passed = passed
                )
            }
        }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MadrasaGold, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "পরীক্ষার মেধাতালিকা",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = expandedExam,
                            onExpandedChange = { expandedExam = !expandedExam },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedExam,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("পরীক্ষা") },
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
                                label = { Text("শ্রেণী") },
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

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            PdfPrintUtil.generateAndShareMeritListPdf(
                                context = context,
                                title = "$selectedExam ($selectedClass)",
                                entries = rankedList
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("download_merit_list_pdf_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("মেধাতালিকা PDF ডাউনলোড (${rankedList.size} জন)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (rankedList.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.EmojiEvents,
                    title = "এই পরীক্ষা ও শ্রেণীর কোন রেজাল্ট পাওয়া যায়নি",
                    subtitle = "রেজাল্ট অপশনে নম্বর যুক্ত করলে মেধাতালিকা স্বয়ংক্রিয়ভাবে তৈরি হবে"
                )
            }
        } else {
            itemsIndexed(
                items = rankedList,
                key = { index, entry -> "${index}_${entry.name}_${entry.cls}_${entry.roll}" }
            ) { index, entry ->
                val rankBg = when (index) {
                    0 -> MadrasaGold
                    1 -> Color(0xFF64748B)
                    2 -> Color(0xFFB45309)
                    else -> MadrasaSapphire
                }
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(rankBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = entry.rankLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val rollTxt = if (entry.roll.isNotBlank()) " • রোল: ${entry.roll}" else ""
                            Text("${entry.cls}$rollTxt", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "মোট: ${entry.totalMarks.toInt()}/${entry.maxMarks} • গড়: ${"%.1f".format(entry.avg)}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            color = if (entry.passed) MadrasaGreen.copy(alpha = 0.14f) else MadrasaRed.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (entry.passed) "পাশ (${entry.grade})" else "ফেল (F)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.passed) MadrasaGreen else MadrasaRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
