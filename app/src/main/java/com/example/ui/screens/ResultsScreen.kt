package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.MadrasaInfo
import com.example.data.entity.ResultEntity
import com.example.data.entity.StudentEntity
import com.example.data.entity.SubjectMark
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaAccent
import com.example.ui.theme.MadrasaContainer
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaGreenSoft
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaRedSoft
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.util.PdfPrintUtil

private data class EditableSubjectRow(
    val name: String,
    val mark: String
)

private fun defaultSubjectRows(): List<EditableSubjectRow> = listOf(
    EditableSubjectRow("কুরআন", "80"),
    EditableSubjectRow("আরবি", "75"),
    EditableSubjectRow("বাংলা", "70"),
    EditableSubjectRow("গণিত", "85"),
    EditableSubjectRow("ইংরেজি", "78")
)

fun calculateRankBengali(rankNum: Int): String {
    return when (rankNum) {
        1 -> "১ম"
        2 -> "২য়"
        3 -> "৩য়"
        4 -> "৪র্থ"
        5 -> "৫ম"
        6 -> "৬ষ্ঠ"
        7 -> "৭ম"
        8 -> "৮ম"
        9 -> "৯ম"
        10 -> "১০ম"
        else -> "$rankNum তম"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    students: List<StudentEntity>,
    results: List<ResultEntity>,
    madrasaInfo: MadrasaInfo = MadrasaInfo(),
    onSaveResult: (Int, Int, String, List<SubjectMark>, String?) -> Unit,
    onDeleteResult: (Int) -> Unit,
    gradeCalculator: (Double) -> String
) {
    val context = LocalContext.current
    if (students.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            MadrasaEmptyState(
                icon = Icons.Default.School,
                title = "প্রথমে ছাত্র ভর্তি করুন",
                subtitle = "ছাত্র ভর্তি করা হলে পরীক্ষার রেজাল্ট ও মার্কশিট তৈরি করতে পারবেন"
            )
        }
        return
    }

    var editingResultId by remember { mutableStateOf<Int?>(null) }
    var editingResultDate by remember { mutableStateOf<String?>(null) }
    var selectedStudentId by remember { mutableStateOf(students.first().id) }
    var examType by remember { mutableStateOf("১ম সাময়িক") }

    val subjectRows = remember {
        mutableStateListOf<EditableSubjectRow>().apply {
            addAll(defaultSubjectRows())
        }
    }

    var expandedStudent by remember { mutableStateOf(false) }
    var expandedExam by remember { mutableStateOf(false) }

    var activeResultId by remember { mutableStateOf<Int?>(null) }
    val activeResultSheet = results.find { it.id == activeResultId }

    val examOptions = listOf("১ম সাময়িক", "২য় সাময়িক", "বার্ষিক পরীক্ষা", "মাসিক পরীক্ষা")
    val selectedStudent = students.find { it.id == selectedStudentId } ?: students.first()

    fun loadResultForEdit(res: ResultEntity) {
        editingResultId = res.id
        editingResultDate = res.date
        selectedStudentId = res.studentId
        examType = res.exam
        subjectRows.clear()
        val parsed = res.getSubjectList()
        if (parsed.isNotEmpty()) {
            parsed.forEach { s ->
                val markStr = if (s.mark % 1.0 == 0.0) s.mark.toInt().toString() else s.mark.toString()
                subjectRows.add(EditableSubjectRow(s.name, markStr))
            }
        } else {
            subjectRows.addAll(defaultSubjectRows())
        }
    }

    fun resetForm() {
        editingResultId = null
        editingResultDate = null
        subjectRows.clear()
        subjectRows.addAll(defaultSubjectRows())
    }

    // Helper to calculate merit rank for a student in an exam
    fun getMeritRank(r: ResultEntity, s: StudentEntity?): String {
        if (s == null) return "১ম"
        val classStudentIds = students.filter { it.cls == s.cls }.map { it.id }.toSet()
        val examResults = results.filter { it.exam == r.exam && it.studentId in classStudentIds }
        val higherScoresCount = examResults.count { it.total > r.total }
        return calculateRankBengali(higherScoresCount + 1)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Result Entry Card
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
                            text = if (editingResultId != null) "✏️ রেজাল্ট এডিট করুন" else "📝 পরীক্ষার রেজাল্ট এন্ট্রি",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MadrasaContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "পাশ মার্ক: ৩৩",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MadrasaPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    var selectedClassFilter by remember { mutableStateOf("সকল শ্রেণী") }
                    var expandedClassFilter by remember { mutableStateOf(false) }

                    val classesList = remember(students) {
                        listOf("সকল শ্রেণী") + students.map { it.cls }.distinct().filter { it.isNotBlank() }
                    }

                    val filteredStudentsForResults = remember(students, selectedClassFilter) {
                        val list = if (selectedClassFilter == "সকল শ্রেণী") students else students.filter { it.cls == selectedClassFilter }
                        list.sortedBy { s -> s.roll.toIntOrNull() ?: 9999 }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "শ্রেণী ফিল্টার:", fontSize = 12.sp, color = Color.Gray)
                    ExposedDropdownMenuBox(
                        expanded = expandedClassFilter,
                        onExpandedChange = { expandedClassFilter = !expandedClassFilter }
                    ) {
                        OutlinedTextField(
                            value = selectedClassFilter,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClassFilter) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(8.dp)
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
                                        filteredStudentsForResults.firstOrNull()?.let { selectedStudentId = it.id }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "ছাত্র নির্বাচন (রোল অনুযায়ী):", fontSize = 13.sp, color = Color.Gray)
                    ExposedDropdownMenuBox(
                        expanded = expandedStudent,
                        onExpandedChange = { expandedStudent = !expandedStudent }
                    ) {
                        OutlinedTextField(
                            value = "${selectedStudent.name} (${selectedStudent.cls}${if (selectedStudent.roll.isNotBlank()) ", রোল: " + selectedStudent.roll else ""})",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStudent) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedStudent,
                            onDismissRequest = { expandedStudent = false }
                        ) {
                            filteredStudentsForResults.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.name} - ${s.cls}${if (s.roll.isNotBlank()) " (রোল: ${s.roll})" else ""}") },
                                    onClick = {
                                        selectedStudentId = s.id
                                        expandedStudent = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "পরীক্ষার নাম:", fontSize = 13.sp, color = Color.Gray)
                    ExposedDropdownMenuBox(
                        expanded = expandedExam,
                        onExpandedChange = { expandedExam = !expandedExam }
                    ) {
                        OutlinedTextField(
                            value = examType,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedExam) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedExam,
                            onDismissRequest = { expandedExam = false }
                        ) {
                            examOptions.forEach { ex ->
                                DropdownMenuItem(
                                    text = { Text(ex) },
                                    onClick = {
                                        examType = ex
                                        expandedExam = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "বিষয় ও নম্বরসমূহ (এডিটেবল • পাশ মার্ক ৩৩):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MadrasaPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    subjectRows.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = row.name,
                                onValueChange = { newName ->
                                    subjectRows[index] = row.copy(name = newName)
                                },
                                label = { Text("বিষয় ${index + 1}") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("subject_name_$index"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = row.mark,
                                onValueChange = { newMark ->
                                    subjectRows[index] = row.copy(mark = newMark)
                                },
                                label = { Text("নম্বর") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(0.65f)
                                    .testTag("subject_mark_$index"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            if (subjectRows.size > 1) {
                                IconButton(
                                    onClick = { subjectRows.removeAt(index) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("remove_subject_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "বিষয় মুছুন",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            subjectRows.add(EditableSubjectRow("বিষয় ${subjectRows.size + 1}", ""))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_subject_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "নতুন বিষয় যোগ করুন",
                            tint = MadrasaPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "নতুন বিষয় যোগ করুন",
                            color = MadrasaPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val parsedSubjects = subjectRows.mapIndexedNotNull { idx, row ->
                                    val cleanName = row.name.trim().ifEmpty { "বিষয় ${idx + 1}" }
                                    val markVal = (row.mark.trim().toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
                                    SubjectMark(name = cleanName, mark = markVal)
                                }
                                if (parsedSubjects.isNotEmpty()) {
                                    onSaveResult(
                                        editingResultId ?: 0,
                                        selectedStudentId,
                                        examType,
                                        parsedSubjects,
                                        editingResultDate
                                    )
                                    if (editingResultId != null) {
                                        resetForm()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("save_result_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (editingResultId != null) "রেজাল্ট আপডেট করুন" else "রেজাল্ট সেভ করুন",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (editingResultId != null) {
                            OutlinedButton(
                                onClick = { resetForm() },
                                modifier = Modifier.testTag("cancel_edit_result_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("বাতিল")
                            }
                        }
                    }
                }
            }
        }

        // Active Result Sheet Card Preview
        if (activeResultSheet != null) {
            val r = activeResultSheet
            val rStudent = students.find { it.id == r.studentId }
            val subjects = r.getSubjectList()
            val isPass = r.isAllPassed(33.0)
            val overallGrade = if (isPass) gradeCalculator(r.avg) else "F"
            val maxTotal = (subjects.size * 100).coerceAtLeast(100)
            val meritRank = getMeritRank(r, rStudent)

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MadrasaPrimary)
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(id = R.drawable.madrasa_logo_round),
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = madrasaInfo.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "${madrasaInfo.address} • ${madrasaInfo.phone}",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MadrasaGold)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "মার্কশিট ও রেজাল্ট কার্ড • ${r.exam} (মেধাস্থান: $meritRank)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }

                        Column(modifier = Modifier.padding(14.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MadrasaContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (rStudent?.photoUri.orEmpty().isNotEmpty()) {
                                        AsyncImage(
                                            model = rStudent!!.photoUri,
                                            contentDescription = rStudent.name,
                                            modifier = Modifier.size(50.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "ছাত্রের নাম: ${rStudent?.name ?: "অজ্ঞাত"}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MadrasaPrimary)
                                        Text(text = "জামাত: ${rStudent?.cls ?: ""}${if (!rStudent?.roll.isNullOrBlank()) " • রোল: ${rStudent?.roll}" else ""}", fontSize = 12.sp, color = TextDark)
                                        Text(text = "মেধাস্থান: $meritRank • তারিখ: ${r.date}", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MadrasaPrimary, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .padding(vertical = 8.dp, horizontal = 6.dp)
                            ) {
                                Text(text = "বিষয়", color = Color.White, modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "পূর্ণমান", color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 13.sp)
                                Text(text = "নম্বর", color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 13.sp)
                                Text(text = "গ্রেড", color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 13.sp)
                            }

                            subjects.forEachIndexed { index, sub ->
                                val rowBg = if (index % 2 == 0) Color(0xFFF8FAFC) else Color.White
                                val subPass = sub.mark >= 33.0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .padding(vertical = 7.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = sub.name, modifier = Modifier.weight(2f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark)
                                    Text(text = "100", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, color = TextMuted)
                                    Text(
                                        text = "${sub.mark.toInt()}",
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (subPass) MadrasaPrimary else MadrasaRed
                                    )
                                    Text(
                                        text = gradeCalculator(sub.mark),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (subPass) MadrasaGreen else MadrasaRed
                                    )
                                }
                                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isPass) MadrasaGreenSoft else MadrasaRedSoft)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "মোট: ${r.total.toInt()} / $maxTotal  •  গড়: %.1f%%".format(r.avg),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextDark
                                        )
                                        Text(
                                            text = "ফলাফল: $overallGrade  •  মেধাস্থান: $meritRank",
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (isPass) MadrasaGreen else MadrasaRed),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(
                                            text = if (isPass) "উত্তীর্ণ (PASS)" else "অনুত্তীর্ণ (FAIL)",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("শিক্ষকের স্বাক্ষর: __________", fontSize = 10.sp, color = TextMuted)
                                Text("মুহতামিমের সীল ও স্বাক্ষর: __________", fontSize = 10.sp, color = TextMuted)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Dual Download Options: 1-Exam or 3-Exam Combined!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        PdfPrintUtil.generateAndShareResultPdf(
                                            context = context,
                                            student = rStudent,
                                            result = r,
                                            meritRank = meritRank,
                                            gradeCalculator = gradeCalculator
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("১ পরীক্ষার PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        val studentExams = results.filter { it.studentId == r.studentId }
                                        PdfPrintUtil.generateAndShareCombinedResultPdf(
                                            context = context,
                                            student = rStudent,
                                            results = studentExams,
                                            gradeCalculator = gradeCalculator
                                        )
                                    },
                                    modifier = Modifier.weight(1.3f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("৩ পরীক্ষার সমন্বিত PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF063D23))
                                }

                                OutlinedButton(
                                    onClick = { activeResultId = null },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("বন্ধ", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "সংরক্ষিত রেজাল্ট তালিকা (${results.size} টি)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (results.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.School,
                    title = "কোন রেজাল্ট নেই",
                    subtitle = "উপরের ফর্ম থেকে পরীক্ষার নম্বর এন্ট্রি করুন"
                )
            }
        } else {
            items(results.reversed()) { res ->
                val s = students.find { it.id == res.studentId }
                val resSubs = res.getSubjectList()
                val resPass = res.isAllPassed(33.0)
                val resGrade = if (resPass) gradeCalculator(res.avg) else "F"
                val resMax = (resSubs.size * 100).coerceAtLeast(100)
                val meritRank = getMeritRank(res, s)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = madrasaCardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${s?.name ?: "ছাত্র"} (${s?.cls.orEmpty()}) - ${res.exam}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "মোট: ${res.total.toInt()} / $resMax • গ্রেড: $resGrade • মেধাস্থান: $meritRank",
                                    fontSize = 13.sp,
                                    color = if (resPass) MaterialTheme.colorScheme.primary else MadrasaRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { activeResultId = res.id }) {
                                    Text("শিট")
                                }
                                IconButton(onClick = { loadResultForEdit(res) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    if (editingResultId == res.id) resetForm()
                                    if (activeResultId == res.id) activeResultId = null
                                    onDeleteResult(res.id)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MadrasaRed)
                                }
                            }
                        }

                        // Quick Download Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    PdfPrintUtil.generateAndShareResultPdf(
                                        context = context,
                                        student = s,
                                        result = res,
                                        meritRank = meritRank,
                                        gradeCalculator = gradeCalculator
                                    )
                                }
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("১ পরীক্ষার PDF", fontSize = 11.sp)
                            }

                            TextButton(
                                onClick = {
                                    val studentExams = results.filter { it.studentId == res.studentId }
                                    PdfPrintUtil.generateAndShareCombinedResultPdf(
                                        context = context,
                                        student = s,
                                        results = studentExams,
                                        gradeCalculator = gradeCalculator
                                    )
                                }
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFD4AF37))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("৩ পরীক্ষার সমন্বিত PDF", fontSize = 11.sp, color = Color(0xFFD4AF37))
                            }
                        }
                    }
                }
            }
        }
    }
}
