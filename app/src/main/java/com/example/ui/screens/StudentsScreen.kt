package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.MadrasaInfo
import com.example.data.entity.StudentEntity
import com.example.ui.components.ImageCropperDialog
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.MadrasaRoundLogo
import com.example.ui.components.ThreeDotDeleteMenu
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaContainer
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaSapphire
import com.example.ui.theme.TextDark

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentsScreen(
    students: List<StudentEntity>,
    madrasaInfo: MadrasaInfo = MadrasaInfo(),
    logoVersion: Long = 0L,
    onAddStudent: (String, String, String, String, Double, String, String, Double) -> Unit,
    onUpdateStudent: (StudentEntity) -> Unit,
    onDeleteStudent: (Int) -> Unit
) {
    var editingStudentId by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    var roll by remember { mutableStateOf("") }
    var father by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf("নূরানী - শিশু") }
    var feeStr by remember { mutableStateOf("500") }
    var electricBillStr by remember { mutableStateOf("50") }
    var photoUri by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedClass by remember { mutableStateOf(false) }
    var expandedNooraniSub by remember { mutableStateOf(true) }
    var selectedStudentForIdCard by remember { mutableStateOf<StudentEntity?>(null) }

    var rawSelectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val nooraniSubOptions = listOf("শিশু", "প্রথম", "দ্বিতীয়", "তৃতীয়")
    val otherClassOptions = listOf(
        "নাজেরা",
        "হিফজ",
        "জামাতে উলা",
        "জামাতে সানিয়া",
        "জামাতে সালেসা",
        "জামাতে রাবেয়া",
        "কিতাব বিভাগ"
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            rawSelectedPhotoUri = uri
        }
    }

    rawSelectedPhotoUri?.let { selectedUri ->
        ImageCropperDialog(
            imageUri = selectedUri,
            onCropCompleted = { croppedPath ->
                photoUri = croppedPath
                rawSelectedPhotoUri = null
            },
            onDismiss = {
                rawSelectedPhotoUri = null
            }
        )
    }

    val filteredStudents = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else students.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.cls.contains(searchQuery, ignoreCase = true) ||
                it.roll.contains(searchQuery, ignoreCase = true)
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
                    Text(
                        text = if (editingStudentId != null) "✏️ ছাত্রের তথ্য এডিট করুন" else "নতুন ছাত্র ভর্তি + ছবি (ক্রপ সুবিধা)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("ছাত্রের নাম *") },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = roll,
                            onValueChange = { roll = it },
                            label = { Text("রোল নং") },
                            modifier = Modifier.weight(0.7f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = father,
                        onValueChange = { father = it },
                        label = { Text("পিতার নাম") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("মোবাইল নম্বর") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "জামাত নির্বাচন করুন:", fontSize = 13.sp, color = Color.Gray)
                    ExposedDropdownMenuBox(
                        expanded = expandedClass,
                        onExpandedChange = { expandedClass = !expandedClass }
                    ) {
                        OutlinedTextField(
                            value = cls,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClass) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedClass,
                            onDismissRequest = { expandedClass = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (expandedNooraniSub) "নূরানী ▾" else "নূরানী ▸",
                                        fontWeight = FontWeight.Bold,
                                        color = MadrasaPrimary
                                    )
                                },
                                onClick = { expandedNooraniSub = !expandedNooraniSub }
                            )
                            if (expandedNooraniSub) {
                                nooraniSubOptions.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text("   • নূরানী - $sub") },
                                        onClick = {
                                            cls = "নূরানী - $sub"
                                            expandedClass = false
                                        }
                                    )
                                }
                            }
                            otherClassOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        cls = option
                                        expandedClass = false
                                    }
                                )
                            }
                        }
                    }

                    if (cls.startsWith("নূরানী")) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "নূরানী শ্রেণি:", fontSize = 12.sp, color = MadrasaPrimary, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            nooraniSubOptions.forEach { sub ->
                                val fullValue = "নূরানী - $sub"
                                val selected = cls == fullValue
                                FilterChip(
                                    selected = selected,
                                    onClick = { cls = fullValue },
                                    label = { Text(sub, fontSize = 13.sp) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = feeStr,
                            onValueChange = { feeStr = it },
                            label = { Text("মাসিক বেতন (৳)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = electricBillStr,
                            onValueChange = { electricBillStr = it },
                            label = { Text("বিদ্যুৎ বিল (৳)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
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
                            Text(if (photoUri.isEmpty()) "ছবি নির্বাচন ও ক্রপ" else "ছবি পরিবর্তন ও ক্রপ")
                        }

                        if (photoUri.isNotEmpty()) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "Student Photo",
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
                                val feeVal = feeStr.toDoubleOrNull() ?: 500.0
                                val electricVal = electricBillStr.toDoubleOrNull() ?: 0.0
                                val currentEditId = editingStudentId
                                if (currentEditId != null) {
                                    onUpdateStudent(
                                        StudentEntity(
                                            id = currentEditId,
                                            name = name.trim(),
                                            father = father.trim(),
                                            phone = phone.trim(),
                                            cls = cls,
                                            fee = feeVal,
                                            photoUri = photoUri,
                                            roll = roll.trim(),
                                            electricBill = electricVal
                                        )
                                    )
                                    editingStudentId = null
                                } else {
                                    onAddStudent(
                                        name.trim(),
                                        father.trim(),
                                        phone.trim(),
                                        cls,
                                        feeVal,
                                        photoUri,
                                        roll.trim(),
                                        electricVal
                                    )
                                }
                                name = ""
                                roll = ""
                                father = ""
                                phone = ""
                                photoUri = ""
                                feeStr = "500"
                                electricBillStr = "50"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                if (editingStudentId != null) "আপডেট করুন" else "ভর্তি সম্পন্ন করুন",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (editingStudentId != null) {
                            OutlinedButton(
                                onClick = {
                                    editingStudentId = null
                                    name = ""
                                    roll = ""
                                    father = ""
                                    phone = ""
                                    photoUri = ""
                                    feeStr = "500"
                                    electricBillStr = "50"
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ছাত্র তালিকা (${students.size} জন)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("🔍 নাম, জামাত বা রোল দিয়ে খুঁজুন...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
        }

        if (filteredStudents.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.Person,
                    title = "কোন ছাত্র পাওয়া যায়নি",
                    subtitle = "উপরের ভর্তি ফর্ম থেকে নতুন ছাত্র যুক্ত করুন"
                )
            }
        } else {
            items(filteredStudents, key = { it.id }) { student ->
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (student.photoUri.isNotEmpty()) {
                                AsyncImage(
                                    model = student.photoUri,
                                    contentDescription = student.name,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column {
                                val rollText = if (student.roll.isNotBlank()) " (রোল: ${student.roll})" else ""
                                Text(text = "${student.name}$rollText", fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                                Text(
                                    text = "${student.cls} • বেতন: ${student.fee.toInt()}৳ • কারেন্ট: ${student.electricBill.toInt()}৳",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "পিতা: ${student.father.ifEmpty { "প্রযোজ্য নয়" }} • ফোন: ${student.phone.ifEmpty { "নেই" }}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { selectedStudentForIdCard = student }) {
                                Text("আইডি", fontSize = 12.sp)
                            }
                            IconButton(onClick = {
                                editingStudentId = student.id
                                name = student.name
                                roll = student.roll
                                father = student.father
                                phone = student.phone
                                cls = student.cls
                                feeStr = student.fee.toInt().toString()
                                electricBillStr = student.electricBill.toInt().toString()
                                photoUri = student.photoUri
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                            }
                            ThreeDotDeleteMenu(
                                onDelete = { onDeleteStudent(student.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedStudentForIdCard?.let { idStudent ->
        StudentIdCardDialog(
            student = idStudent,
            madrasaInfo = madrasaInfo,
            logoVersion = logoVersion,
            onDismiss = { selectedStudentForIdCard = null }
        )
    }
}

@Composable
fun StudentIdCardDialog(
    student: StudentEntity,
    madrasaInfo: MadrasaInfo = MadrasaInfo(),
    logoVersion: Long = 0L,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            com.example.util.PdfPrintUtil.generateAndShareIdCardPdf(context, student)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🖨️ CR80 PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            com.example.util.PdfPrintUtil.generateAndShareCr80IdCardPng(context, student = student)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
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
                            .background(MadrasaPrimary)
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MadrasaRoundLogo(
                                logoPath = madrasaInfo.logoPath,
                                logoVersion = logoVersion,
                                size = 50.dp,
                                borderWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = madrasaInfo.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = madrasaInfo.address,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "মোবাইল: ${madrasaInfo.phone}",
                                fontSize = 9.sp,
                                color = MadrasaGold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MadrasaGold)
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "স্টুডেন্ট আইডি কার্ড • CR80 (54×85.6mm + 2mm ব্লিড)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (student.photoUri.isNotEmpty()) {
                            AsyncImage(
                                model = student.photoUri,
                                contentDescription = student.name,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(MadrasaContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MadrasaPrimary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = student.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MadrasaPrimary
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MadrasaContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val rollInfo = if (student.roll.isNotBlank()) " • রোল: ${student.roll}" else ""
                            Text(
                                text = "জামাত: ${student.cls}$rollInfo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MadrasaPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        Spacer(modifier = Modifier.height(8.dp))
                        IdCardInfoRow(label = "আইডি নং:", value = "#IKM-${student.id}")
                        if (student.roll.isNotBlank()) {
                            IdCardInfoRow(label = "রোল নম্বর:", value = student.roll)
                        }
                        IdCardInfoRow(label = "পিতার নাম:", value = student.father.ifEmpty { "প্রযোজ্য নয়" })
                        IdCardInfoRow(label = "মোবাইল:", value = student.phone.ifEmpty { "নেই" })
                        IdCardInfoRow(label = "মাসিক বেতন:", value = "${student.fee.toInt()} ৳")
                        IdCardInfoRow(label = "মাসিক বিদ্যুৎ বিল:", value = "${student.electricBill.toInt()} ৳")

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("শ্রেণী শিক্ষকের স্বাক্ষর: ______", fontSize = 10.sp, color = Color.Gray)
                            Text("মুহতামিমের স্বাক্ষর: ______", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun IdCardInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF555555), fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 12.sp, color = Color(0xFF111111), fontWeight = FontWeight.Bold)
    }
}
