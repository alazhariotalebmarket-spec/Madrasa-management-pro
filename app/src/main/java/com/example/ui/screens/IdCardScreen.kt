package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.MadrasaInfo
import com.example.data.entity.StudentEntity
import com.example.data.entity.TeacherEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.MadrasaRoundLogo
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaContainer
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaSapphire
import com.example.util.PdfPrintUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardScreen(
    students: List<StudentEntity>,
    teachers: List<TeacherEntity>,
    madrasaInfo: MadrasaInfo = MadrasaInfo()
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf("student") } // "student" or "teacher"
    var searchQuery by remember { mutableStateOf("") }
    var selectedClassFilter by remember { mutableStateOf("সকল শ্রেণী") }
    var expandedClassFilter by remember { mutableStateOf(false) }

    var previewStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var previewTeacher by remember { mutableStateOf<TeacherEntity?>(null) }

    val classesList = remember(students) {
        listOf("সকল শ্রেণী") + students.map { it.cls }.distinct().filter { it.isNotBlank() }
    }

    val filteredStudents = remember(students, selectedClassFilter, searchQuery) {
        val list = if (selectedClassFilter == "সকল শ্রেণী") students else students.filter { it.cls == selectedClassFilter }
        val searched = if (searchQuery.isBlank()) list else list.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.roll.contains(searchQuery)
        }
        searched.sortedBy { s -> s.roll.toIntOrNull() ?: 9999 }
    }

    val filteredTeachers = remember(teachers, searchQuery) {
        if (searchQuery.isBlank()) teachers else teachers.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.subject.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Spec Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = madrasaCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🪪 আইডি কার্ড (শ্রেণী ও রোল অনুযায়ী)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CR80 সাইজ: 54mm × 85.6mm • 300 DPI (638 × 1012 px) • PDF ও High-Res PNG",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = madrasaCardBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { selectedType = "student" },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedType == "student") MadrasaPrimary else Color.Transparent,
                                    contentColor = if (selectedType == "student") Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ছাত্র-ছাত্রী ID", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { selectedType = "teacher" },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedType == "teacher") MadrasaSapphire else Color.Transparent,
                                    contentColor = if (selectedType == "teacher") Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("শিক্ষক ID", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedType == "student") {
                            ExposedDropdownMenuBox(
                                expanded = expandedClassFilter,
                                onExpandedChange = { expandedClassFilter = !expandedClassFilter },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedClassFilter,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("শ্রেণী ফিল্টার") },
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

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("নাম বা রোল দিয়ে খুঁজুন") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }

        if (selectedType == "student") {
            if (filteredStudents.isEmpty()) {
                item {
                    MadrasaEmptyState(
                        icon = Icons.Default.Badge,
                        title = "কোন ছাত্র পাওয়া যায়নি",
                        subtitle = "ছাত্র ভর্তি করা হলে এখানে আইডি কার্ড তৈরি করতে পারবেন"
                    )
                }
            } else {
                items(filteredStudents, key = { it.id }) { student ->
                    val rollStr = if (student.roll.isNotBlank()) "রোল: ${student.roll}" else "রোল: -"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = madrasaCardBorder(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(student.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "${student.cls} • $rollStr • পিতা: ${student.father.ifEmpty { "প্রযোজ্য নয়" }}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { previewStudent = student }) {
                                    Icon(Icons.Default.Visibility, contentDescription = "প্রিভিউ", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { PdfPrintUtil.generateAndShareStudentIdCardPdf(context, student) },
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CR80 PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { PdfPrintUtil.generateAndShareCr80IdCardPng(context, student = student) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("300 DPI PNG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (filteredTeachers.isEmpty()) {
                item {
                    MadrasaEmptyState(
                        icon = Icons.Default.SupervisorAccount,
                        title = "কোন শিক্ষক পাওয়া যায়নি",
                        subtitle = "শিক্ষক যুক্ত করা হলে এখানে আইডি কার্ড তৈরি করতে পারবেন"
                    )
                }
            } else {
                items(filteredTeachers, key = { it.id }) { teacher ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = madrasaCardBorder(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(teacher.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "পদবী: ${teacher.subject} • আইডি: #IKM-T${teacher.id}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { previewTeacher = teacher }) {
                                    Icon(Icons.Default.Visibility, contentDescription = "প্রিভিউ", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { PdfPrintUtil.generateAndShareTeacherIdCardPdf(context, teacher) },
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CR80 PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { PdfPrintUtil.generateAndShareCr80IdCardPng(context, teacher = teacher) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("300 DPI PNG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewStudent?.let { st ->
        StudentIdCardDialog(
            student = st,
            madrasaInfo = madrasaInfo,
            onDismiss = { previewStudent = null }
        )
    }

    previewTeacher?.let { t ->
        TeacherIdCardDialog(
            teacher = t,
            madrasaInfo = madrasaInfo,
            onDismiss = { previewTeacher = null }
        )
    }
}

@Composable
fun TeacherIdCardDialog(
    teacher: TeacherEntity,
    madrasaInfo: MadrasaInfo = MadrasaInfo(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val headerBlue = Color(0xFF0B4F6C)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { PdfPrintUtil.generateAndShareTeacherIdCardPdf(context, teacher) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = headerBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🖨️ CR80 PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { PdfPrintUtil.generateAndShareCr80IdCardPng(context, teacher = teacher) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🖼️ 300 DPI PNG", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("বন্ধ করুন")
                }
            }
        },
        dismissButton = {},
        text = {
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
                            .background(headerBlue)
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MadrasaRoundLogo(
                                logoPath = madrasaInfo.logoPath,
                                size = 46.dp,
                                borderWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = madrasaInfo.name,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = madrasaInfo.address,
                                fontSize = 9.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFD4AF37))
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "শিক্ষক পরিচিতি কার্ড • CR80 (54×85.6mm + 2mm ব্লিড)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF062C1B)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (teacher.photoUri.isNotBlank()) {
                            AsyncImage(
                                model = teacher.photoUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.SupervisorAccount, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = teacher.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text(text = "পদবী/বিষয়: ${teacher.subject}", fontSize = 13.sp, color = Color(0xFF475569))
                    Text(text = "মোবাইল: ${teacher.phone.ifEmpty { "নেই" }}", fontSize = 12.sp, color = Color(0xFF64748B))

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    )
}
