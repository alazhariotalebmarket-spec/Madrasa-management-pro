package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Share
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
import androidx.core.content.FileProvider
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaAccent
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaSapphire
import com.example.ui.viewmodel.MadrasaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupScreen(
    viewModel: MadrasaViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val students by viewModel.students.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val results by viewModel.results.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val teacherPayments by viewModel.teacherPayments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val notices by viewModel.notices.collectAsState()
    val questionPapers by viewModel.questionPapers.collectAsState()
    val classRoutines by viewModel.classRoutines.collectAsState()
    val madrasaInfo by viewModel.madrasaInfo.collectAsState()

    val prefs = remember {
        context.getSharedPreferences("local_offline_backup_prefs", Context.MODE_PRIVATE)
    }

    var isRestoringFile by remember { mutableStateOf(false) }
    var lastLocalRestoreStatus by remember { mutableStateOf<String?>(null) }
    var lastLocalRestoreIsWarning by remember { mutableStateOf(false) }

    var lastBackupTime by remember {
        mutableStateOf(prefs.getString("last_backup_time", "কখনো ব্যাকআপ করা হয়নি") ?: "কখনো ব্যাকআপ করা হয়নি")
    }

    fun createBackupJson(): String {
        val root = JSONObject()
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        root.put("institution", madrasaInfo.name)
        root.put("institutionAddress", madrasaInfo.address)
        root.put("institutionPhone", madrasaInfo.phone)
        val logoBase64 = com.example.util.MadrasaLogoUtil.readLogoAsBase64(madrasaInfo.logoPath)
        if (logoBase64.isNotBlank()) {
            root.put("institutionLogoBase64", logoBase64)
        }
        root.put("backupDate", timeStamp)

        val studentsArray = JSONArray()
        students.forEach { s ->
            studentsArray.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("father", s.father)
                put("phone", s.phone)
                put("cls", s.cls)
                put("fee", s.fee)
                put("roll", s.roll)
                put("electricBill", s.electricBill)
                put("photoUri", s.photoUri)
            })
        }
        root.put("students", studentsArray)

        val feesArray = JSONArray()
        fees.forEach { f ->
            feesArray.put(JSONObject().apply {
                put("id", f.id)
                put("studentId", f.studentId)
                put("amount", f.amount)
                put("type", f.type)
                put("month", f.month)
                put("date", f.date)
            })
        }
        root.put("fees", feesArray)

        val resultsArray = JSONArray()
        results.forEach { r ->
            val subjectsArr = JSONArray()
            r.getSubjectList().forEach { sub ->
                subjectsArr.put(JSONObject().apply {
                    put("name", sub.name)
                    put("mark", sub.mark)
                })
            }
            resultsArray.put(JSONObject().apply {
                put("id", r.id)
                put("studentId", r.studentId)
                put("exam", r.exam)
                put("subjects", subjectsArr)
                put("total", r.total)
                put("avg", r.avg)
                put("date", r.date)
            })
        }
        root.put("results", resultsArray)

        val teachersArray = JSONArray()
        teachers.forEach { t ->
            teachersArray.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("subject", t.subject)
                put("salary", t.salary)
                put("address", t.address)
                put("phone", t.phone)
                put("photoUri", t.photoUri)
                put("joinDate", t.joinDate)
            })
        }
        root.put("teachers", teachersArray)

        val teacherPaymentsArray = JSONArray()
        teacherPayments.forEach { tp ->
            teacherPaymentsArray.put(JSONObject().apply {
                put("id", tp.id)
                put("teacherId", tp.teacherId)
                put("amount", tp.amount)
                put("month", tp.month)
                put("year", tp.year)
                put("date", tp.date)
                put("note", tp.note)
            })
        }
        root.put("teacherPayments", teacherPaymentsArray)

        val expensesArray = JSONArray()
        expenses.forEach { e ->
            expensesArray.put(JSONObject().apply {
                put("id", e.id)
                put("type", e.type)
                put("desc", e.desc)
                put("amount", e.amount)
                put("date", e.date)
            })
        }
        root.put("expenses", expensesArray)

        val noticesArray = JSONArray()
        notices.forEach { n ->
            noticesArray.put(JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("description", n.description)
                put("date", n.date)
            })
        }
        root.put("notices", noticesArray)

        val papersArray = JSONArray()
        questionPapers.forEach { q ->
            papersArray.put(JSONObject().apply {
                put("id", q.id)
                put("examName", q.examName)
                put("className", q.className)
                put("subject", q.subject)
                put("fullMarks", q.fullMarks)
                put("duration", q.duration)
                put("instructions", q.instructions)
                put("questionsText", q.questionsText)
                put("date", q.date)
            })
        }
        root.put("questionPapers", papersArray)

        val routinesArray = JSONArray()
        classRoutines.forEach { r ->
            routinesArray.put(JSONObject().apply {
                put("id", r.id)
                put("className", r.className)
                put("dayOfWeek", r.dayOfWeek)
                put("timeSlot", r.timeSlot)
                put("subject", r.subject)
                put("teacherName", r.teacherName)
                put("duration", r.duration)
                put("serialOrder", r.serialOrder)
            })
        }
        root.put("classRoutines", routinesArray)

        return root.toString(2)
    }

    val restoreJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !isRestoringFile) {
            isRestoringFile = true
            scope.launch {
                try {
                    val (fileName, jsonStr) = withContext(Dispatchers.IO) {
                        val displayName = try {
                            context.contentResolver.query(
                                uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME),
                                null,
                                null,
                                null
                            )?.use { cursor ->
                                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                            }
                        } catch (_: Exception) {
                            null
                        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Backup.json"

                        val rawText = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            ?.trimStart('\uFEFF')
                        displayName to rawText
                    }

                    if (jsonStr.isNullOrBlank()) {
                        lastLocalRestoreIsWarning = true
                        lastLocalRestoreStatus = "নির্বাচিত ফাইলটি ($fileName) খালি বা পড়া যাচ্ছে না।"
                        Toast.makeText(context, lastLocalRestoreStatus, Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val res = viewModel.restoreFromBackupJson(jsonStr)
                    res.onSuccess { summary ->
                        val statusMsg = when {
                            summary.isDuplicateFile ->
                                "এই ফাইলটি ($fileName) আগেই রিস্টোর করা আছে! ${summary.skippedDuplicateCount} টি তথ্য ডাবল হওয়া থেকে সুরক্ষিত রাখা হয়েছে।"
                            summary.addedOrUpdatedCount == 0 && summary.totalInFile == 0 ->
                                "$fileName থেকে প্রতিষ্ঠানের তথ্য রিস্টোর হয়েছে (ফাইলে কোনো অতিরিক্ত রেকর্ড নেই)।"
                            summary.skippedDuplicateCount > 0 ->
                                "$fileName থেকে ${summary.addedOrUpdatedCount} টি নতুন/আপডেটেড তথ্য রিস্টোর হয়েছে (${summary.skippedDuplicateCount} টি ডুপ্লিকেট এড়ানো হয়েছে)।"
                            else ->
                                "$fileName থেকে ${summary.addedOrUpdatedCount} টি তথ্য সফলভাবে রিস্টোর সম্পন্ন হয়েছে!"
                        }
                        lastLocalRestoreIsWarning = false
                        lastLocalRestoreStatus = statusMsg
                        Toast.makeText(context, statusMsg, Toast.LENGTH_LONG).show()
                    }.onFailure { err ->
                        val errMsg = "রিস্টোর ত্রুটি: ${err.message ?: "ফাইল ফরম্যাট সঠিক নয়"}"
                        lastLocalRestoreIsWarning = true
                        lastLocalRestoreStatus = errMsg
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    val errMsg = "রিস্টোরে ত্রুটি: ${e.message ?: "অজানা সমস্যা"}"
                    lastLocalRestoreIsWarning = true
                    lastLocalRestoreStatus = errMsg
                    Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                } finally {
                    isRestoringFile = false
                }
            }
        }
    }

    val saveLocalJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { targetUri ->
        if (targetUri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val jsonString = createBackupJson()
                        context.contentResolver.openOutputStream(targetUri)?.use { output ->
                            output.write(jsonString.toByteArray(Charsets.UTF_8))
                        }
                    }
                    val timeNow = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                    lastBackupTime = timeNow
                    prefs.edit().putString("last_backup_time", timeNow).apply()
                    Toast.makeText(context, "অফলাইন ব্যাকআপ সফলভাবে সেভ হয়েছে!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "সংরক্ষণে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Offline Mode Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = madrasaCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MadrasaGreen.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OfflinePin,
                            contentDescription = null,
                            tint = MadrasaGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "১০০% অফলাইন ও নিরাপদ স্টোরেজ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "কোনো লগইন ছাড়াই সব তথ্য ফোনে সেভ থাকে",
                            fontSize = 12.sp,
                            color = MadrasaGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(11.dp)) {
                        Text(
                            text = "সর্বশেষ লোকাল ব্যাকআপ: $lastBackupTime",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "অন্য ফোনে তথ্য নিতে বা নিরাপদ রাখতে JSON ফাইল ফোনে সেভ/শেয়ার করুন এবং প্রয়োজনে ফাইল ম্যানেজার থেকে রিস্টোর করুন।",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Local Export / Share / Restore Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = madrasaCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📥 স্থানীয় ব্যাকআপ ও রিস্টোর (Device Storage)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "আপনার ফোনের মেমোরি বা ফাইল ম্যানেজারে JSON ফাইল হিসেবে ব্যাকআপ সেভ করুন, শেয়ার করুন অথবা ফাইল ম্যানেজার থেকে ডাবল হওয়া ছাড়াই রিস্টোর করুন।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            saveLocalJsonLauncher.launch("MPro_Backup_$dateSuffix.json")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("local_export_json_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MadrasaPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val shareUri = withContext(Dispatchers.IO) {
                                        val jsonString = createBackupJson()
                                        val backupFile = File(context.cacheDir, "MPro_Share_Backup.json")
                                        FileOutputStream(backupFile).use { out ->
                                            out.write(jsonString.toByteArray(Charsets.UTF_8))
                                        }
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            backupFile
                                        )
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "M PRO Backup")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "শেয়ার করুন"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "শেয়ার করতে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("local_share_backup_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MadrasaAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Backup", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (!isRestoringFile) {
                            try {
                                restoreJsonLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                                )
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "ফাইল ম্যানেজার খুলতে সমস্যা: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = !isRestoringFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("local_restore_json_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MadrasaSapphire,
                        contentColor = Color.White,
                        disabledContainerColor = MadrasaSapphire.copy(alpha = 0.55f),
                        disabledContentColor = Color.White.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isRestoringFile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRestoringFile) "রিস্টোর হচ্ছে..." else "ফাইল ম্যানেজার থেকে রিস্টোর (Restore JSON)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val restoreStatus = lastLocalRestoreStatus
                if (!restoreStatus.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val tintColor = if (lastLocalRestoreIsWarning) MadrasaRed else MadrasaGreen
                    Surface(
                        color = tintColor.copy(alpha = 0.11f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = restoreStatus,
                            color = tintColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = madrasaCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📊 ব্যাকআপে থাকা বর্তমান তথ্যের সারসংক্ষেপ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(10.dp))
                BackupStatRow(label = "মোট ছাত্র-ছাত্রী:", count = "${students.size} জন")
                BackupStatRow(label = "মোট ফি আদায়ের রশিদ:", count = "${fees.size} টি")
                BackupStatRow(label = "সংরক্ষিত পরীক্ষার রেজাল্ট:", count = "${results.size} টি")
                BackupStatRow(label = "শিক্ষক সংখ্যা:", count = "${teachers.size} জন")
                BackupStatRow(label = "শিক্ষক বেতন রশিদ:", count = "${teacherPayments.size} টি")
                BackupStatRow(label = "খরচ ও দানের এন্ট্রি:", count = "${expenses.size} টি")
                BackupStatRow(label = "নোটিশ সংখ্যা:", count = "${notices.size} টি")
                BackupStatRow(label = "প্রশ্নপত্র সংখ্যা:", count = "${questionPapers.size} টি")
                BackupStatRow(label = "ক্লাস রুটিন:", count = "${classRoutines.size} টি")
            }
        }
    }
}

@Composable
fun BackupStatRow(label: String, count: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
        )
        Text(
            text = count,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
