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
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaContainer
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaSapphire
import com.example.util.PdfPrintUtil

@Composable
fun IdCardScreen(
    students: List<StudentEntity>,
    teachers: List<TeacherEntity>,
    madrasaInfo: MadrasaInfo = MadrasaInfo()
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf("student") } // "student" or "teacher"
    var searchQuery by remember { mutableStateOf("") }
    var previewStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var previewTeacher by remember { mutableStateOf<TeacherEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    text = "🪪 আইডি কার্ড (CR80 প্রিন্ট সাইজ)",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "সাইজ: 54mm × 85.6mm (2.125\" × 3.375\") • 300 DPI (638 × 1012 px) • 2mm ব্লিড ও ক্রপ মার্কসহ",
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
                                .heightIn(min = 44.dp)
                                .testTag("id_tab_student"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedType == "student") MadrasaPrimary else Color.Transparent,
                                contentColor = if (selectedType == "student") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text("ছাত্র আইডি কার্ড (${students.size})", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                        Button(
                            onClick = { selectedType = "teacher" },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("id_tab_teacher"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedType == "teacher") MadrasaPrimary else Color.Transparent,
                                contentColor = if (selectedType == "teacher") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text("শিক্ষক আইডি কার্ড (${teachers.size})", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("নাম, শ্রেণী বা রোল দিয়ে খুঁজুন") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        if (selectedType == "student") {
            val filtered = students.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.cls.contains(searchQuery, ignoreCase = true) ||
                        it.roll.contains(searchQuery, ignoreCase = true)
            }
            if (filtered.isEmpty()) {
                MadrasaEmptyState(
                    icon = Icons.Default.Badge,
                    title = "কোন ছাত্র পাওয়া যায়নি",
                    subtitle = "ভর্তি অপশন থেকে ছাত্র যুক্ত করুন অথবা অনুসন্ধান পরিবর্তন করুন"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { student ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = madrasaCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (student.photoUri.isNotEmpty()) {
                                        AsyncImage(
                                            model = student.photoUri,
                                            contentDescription = student.name,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape),
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
                                        Text(
                                            "জামাত: ${student.cls}$rollTxt • আইডি: #IKM-${student.id}",
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
                                        onClick = { PdfPrintUtil.generateAndShareIdCardPdf(context, student) },
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
            }
        } else {
            val filtered = teachers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.subject.contains(searchQuery, ignoreCase = true)
            }
            if (filtered.isEmpty()) {
                MadrasaEmptyState(
                    icon = Icons.Default.SupervisorAccount,
                    title = "কোন শিক্ষক পাওয়া যায়নি",
                    subtitle = "শিক্ষকের বেতন অপশন থেকে শিক্ষক যুক্ত করুন"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { teacher ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = madrasaCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (teacher.photoUri.isNotEmpty()) {
                                        AsyncImage(
                                            model = teacher.photoUri,
                                            contentDescription = teacher.name,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape),
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
    }

    if (previewStudent != null) {
        StudentIdCardDialog(
            student = previewStudent!!,
            madrasaInfo = madrasaInfo,
            onDismiss = { previewStudent = null }
        )
    }

    if (previewTeacher != null) {
        TeacherIdCardDialog(
            teacher = previewTeacher!!,
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
                shape = RoundedCornerShape(14.dp),
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
                            Image(
                                painter = painterResource(id = R.drawable.madrasa_logo_round),
                                contentDescription = "মাদ্রাসা লোগো",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF062C1B)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (teacher.photoUri.isNotEmpty()) {
                            AsyncImage(
                                model = teacher.photoUri,
                                contentDescription = teacher.name,
                                modifier = Modifier
                                    .size(74.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(74.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE6F4FA)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = headerBlue, modifier = Modifier.size(42.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(teacher.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = headerBlue)
                        Surface(
                            color = Color(0xFFE6F4FA),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "পদবী: ${teacher.subject}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = headerBlue,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(8.dp))

                        IdCardInfoRow(label = "আইডি নং:", value = "#IKM-T${teacher.id}")
                        IdCardInfoRow(label = "মোবাইল:", value = teacher.phone.ifEmpty { "নেই" })
                        IdCardInfoRow(label = "যোগদান:", value = teacher.joinDate.ifEmpty { "প্রযোজ্য নয়" })
                        IdCardInfoRow(label = "ঠিকানা:", value = teacher.address.ifEmpty { "মাদ্রাসা ক্যাম্পাস" })
                    }
                }
            }
        }
    )
}
