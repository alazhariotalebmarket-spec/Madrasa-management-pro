package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.TeacherEntity
import com.example.data.entity.TeacherPaymentEntity
import com.example.ui.components.ImageCropperDialog
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaAccent
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.util.PdfPrintUtil
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachersScreen(
    teachers: List<TeacherEntity>,
    teacherPayments: List<TeacherPaymentEntity>,
    onAddTeacher: (String, String, Double, String, String, String, String) -> Unit,
    onUpdateTeacher: (TeacherEntity) -> Unit,
    onDeleteTeacher: (Int) -> Unit,
    onAddPayment: (Int, Double, String, String, String) -> Unit,
    onDeletePayment: (Int) -> Unit
) {
    val context = LocalContext.current
    var editingTeacherId by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var salaryStr by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var joinDate by remember {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
    }
    var photoUri by remember { mutableStateOf("") }
    var rawPhotoUri by remember { mutableStateOf<Uri?>(null) }

    var payingTeacher by remember { mutableStateOf<TeacherEntity?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            rawPhotoUri = uri
        }
    }

    if (rawPhotoUri != null) {
        ImageCropperDialog(
            imageUri = rawPhotoUri!!,
            onCropCompleted = { croppedPath ->
                photoUri = croppedPath
                rawPhotoUri = null
            },
            onDismiss = { rawPhotoUri = null }
        )
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
                        text = if (editingTeacherId != null) "✏️ শিক্ষকের তথ্য এডিট করুন" else "👳 শিক্ষক যুক্ত করুন",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("শিক্ষকের নাম *") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("বিষয় / পদবী *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = salaryStr,
                            onValueChange = { salaryStr = it },
                            label = { Text("মাসিক বেতন (৳) *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("মোবাইল নাম্বার") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = joinDate,
                            onValueChange = { joinDate = it },
                            label = { Text("যোগদানের তারিখ") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("ঠিকানা (গ্রাম, ডাকঘর, উপজেলা)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (photoUri.isEmpty()) "ছবি যুক্ত ও ক্রপ করুন" else "ছবি পরিবর্তন ও ক্রপ")
                        }

                        if (photoUri.isNotEmpty()) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "Teacher Photo",
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text("ছবি নেই", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (name.isBlank()) return@Button
                                val sal = salaryStr.toDoubleOrNull() ?: 0.0
                                val currentEditId = editingTeacherId
                                if (currentEditId != null) {
                                    onUpdateTeacher(
                                        TeacherEntity(
                                            id = currentEditId,
                                            name = name.trim(),
                                            subject = subject.trim(),
                                            salary = sal,
                                            address = address.trim(),
                                            phone = phone.trim(),
                                            photoUri = photoUri,
                                            joinDate = joinDate.trim()
                                        )
                                    )
                                    editingTeacherId = null
                                } else {
                                    onAddTeacher(
                                        name.trim(),
                                        subject.trim(),
                                        sal,
                                        address.trim(),
                                        phone.trim(),
                                        photoUri,
                                        joinDate.trim()
                                    )
                                }
                                name = ""
                                subject = ""
                                salaryStr = ""
                                phone = ""
                                address = ""
                                photoUri = ""
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                if (editingTeacherId != null) "আপডেট করুন" else "শিক্ষক যুক্ত করুন",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (editingTeacherId != null) {
                            OutlinedButton(
                                onClick = {
                                    editingTeacherId = null
                                    name = ""
                                    subject = ""
                                    salaryStr = ""
                                    phone = ""
                                    address = ""
                                    photoUri = ""
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("বাতিল")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "শিক্ষক তালিকা (${teachers.size} জন)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (teachers.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.SupervisorAccount,
                    title = "কোন শিক্ষক নেই",
                    subtitle = "উপরের ফর্ম থেকে শিক্ষক যুক্ত করুন"
                )
            }
        } else {
            items(teachers) { teacher ->
                val payments = teacherPayments.filter { it.teacherId == teacher.id }
                val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
                val currentMonth = "জানুয়ারি"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = madrasaCardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (teacher.photoUri.isNotEmpty()) {
                                AsyncImage(
                                    model = teacher.photoUri,
                                    contentDescription = teacher.name,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = teacher.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                Text(text = "বিষয়: ${teacher.subject} • বেতন: ${teacher.salary.toInt()} ৳", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                if (teacher.phone.isNotBlank()) {
                                    Text(text = "মোবাইল: ${teacher.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (teacher.address.isNotBlank()) {
                                    Text(text = "ঠিকানা: ${teacher.address}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (teacher.joinDate.isNotBlank()) {
                                    Text(text = "যোগদান: ${teacher.joinDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                if (teacher.phone.isNotBlank()) {
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${teacher.phone}"))
                                        context.startActivity(intent)
                                    }) {
                                        Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = {
                                    editingTeacherId = teacher.id
                                    name = teacher.name
                                    subject = teacher.subject
                                    salaryStr = teacher.salary.toInt().toString()
                                    phone = teacher.phone
                                    address = teacher.address
                                    photoUri = teacher.photoUri
                                    joinDate = teacher.joinDate
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onDeleteTeacher(teacher.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MadrasaRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons: Pay Salary & Monthly / Yearly PDF
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { payingTeacher = teacher },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("বেতন পরিশোধ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    PdfPrintUtil.generateAndShareTeacherSalaryStatementPdf(
                                        context = context,
                                        teacher = teacher,
                                        payments = payments,
                                        period = currentMonth,
                                        isYearly = false
                                    )
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("📄 মাসিক PDF", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    PdfPrintUtil.generateAndShareTeacherSalaryStatementPdf(
                                        context = context,
                                        teacher = teacher,
                                        payments = payments,
                                        period = currentYear,
                                        isYearly = true
                                    )
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("📊 বাৎসরিক PDF", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Salary Payment Dialog
    if (payingTeacher != null) {
        val t = payingTeacher!!
        var payAmountStr by remember { mutableStateOf(t.salary.toInt().toString()) }
        var payMonth by remember { mutableStateOf("জানুয়ারি") }
        var payYear by remember { mutableStateOf(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())) }
        var payNote by remember { mutableStateOf("") }
        val months = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
        var expandedMonth by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { payingTeacher = null },
            title = { Text(text = "💰 বেতন প্রদান: ${t.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("মাস নির্বাচন করুন:", fontSize = 12.sp, color = Color.Gray)
                    ExposedDropdownMenuBox(
                        expanded = expandedMonth,
                        onExpandedChange = { expandedMonth = !expandedMonth }
                    ) {
                        OutlinedTextField(
                            value = payMonth,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonth) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMonth,
                            onDismissRequest = { expandedMonth = false }
                        ) {
                            months.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        payMonth = m
                                        expandedMonth = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = payYear,
                        onValueChange = { payYear = it },
                        label = { Text("বছর (যেমন: ২০২৬)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = payAmountStr,
                        onValueChange = { payAmountStr = it },
                        label = { Text("প্রদত্ত টাকার পরিমাণ") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = payNote,
                        onValueChange = { payNote = it },
                        label = { Text("মন্তব্য / রশিদ নং") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = payAmountStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            onAddPayment(t.id, amt, payMonth, payYear.trim(), payNote.trim())
                            payingTeacher = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary)
                ) {
                    Text("বেতন জমা নিশ্চিত করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { payingTeacher = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
