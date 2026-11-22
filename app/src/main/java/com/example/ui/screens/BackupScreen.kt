package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Login
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
import com.example.ui.viewmodel.MadrasaViewModel
import com.example.util.PdfPrintUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupScreen(
    viewModel: MadrasaViewModel
) {
    val context = LocalContext.current
    val students by viewModel.students.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val results by viewModel.results.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val teacherPayments by viewModel.teacherPayments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val notices by viewModel.notices.collectAsState()
    val madrasaInfo by viewModel.madrasaInfo.collectAsState()

    val prefs = remember { context.getSharedPreferences("google_drive_backup_prefs", android.content.Context.MODE_PRIVATE) }
    var isGoogleConnected by remember { mutableStateOf(prefs.getBoolean("is_connected", false)) }
    var connectedEmail by remember { mutableStateOf(prefs.getString("connected_email", "ferdausharfin@gmail.com") ?: "ferdausharfin@gmail.com") }
    var lastBackupTime by remember { mutableStateOf(prefs.getString("last_backup_time", "কখনো ব্যাকআপ করা হয়নি") ?: "কখনো ব্যাকআপ করা হয়নি") }
    var autoBackupFreq by remember { mutableStateOf(prefs.getString("auto_backup_freq", "প্রতিদিন") ?: "প্রতিদিন") }
    var showLoginDialog by remember { mutableStateOf(false) }

    fun createBackupJson(): String {
        val root = JSONObject()
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        root.put("institution", madrasaInfo.name)
        root.put("institutionAddress", madrasaInfo.address)
        root.put("institutionPhone", madrasaInfo.phone)
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
                put("title", n.title)
                put("description", n.description)
                put("date", n.date)
            })
        }
        root.put("notices", noticesArray)

        return root.toString(2)
    }

    fun saveBackupToFile(jsonContent: String): File {
        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(context.cacheDir, "MadrasaMPro_Drive_Backup_$dateSuffix.json")
        FileOutputStream(backupFile).use { out ->
            out.write(jsonContent.toByteArray(Charsets.UTF_8))
        }
        return backupFile
    }

    fun prepareApkCacheFile(): File {
        val sourceApk = File(context.applicationInfo.sourceDir)
        val cachedApk = File(context.cacheDir, "Madrasa_M_PRO.apk")
        FileInputStream(sourceApk).use { input ->
            FileOutputStream(cachedApk).use { output ->
                input.copyTo(output)
            }
        }
        return cachedApk
    }

    val saveApkDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { targetUri ->
        if (targetUri != null) {
            try {
                val sourceApk = File(context.applicationInfo.sourceDir)
                context.contentResolver.openOutputStream(targetUri)?.use { output ->
                    FileInputStream(sourceApk).use { input ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(context, "APK ফাইল সফলভাবে সেভ হয়েছে!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "APK সেভ করতে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveDriveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { targetUri ->
        if (targetUri != null) {
            try {
                val jsonString = createBackupJson()
                context.contentResolver.openOutputStream(targetUri)?.use { output ->
                    output.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                val timeNow = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                lastBackupTime = timeNow
                prefs.edit().putString("last_backup_time", timeNow).apply()
                Toast.makeText(context, "Backup Successful! (Madrasa M PRO ফোল্ডার)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "সংরক্ষণে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val jsonStr = input.bufferedReader().use { it.readText() }
                    val root = JSONObject(jsonStr)
                    val inst = root.optString("institution", "")
                    Toast.makeText(context, "Backup restored successfully from Google Drive ($inst)!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "রিষ্টোরে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
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
        // Google Drive Connect & Cloud Sync Card
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isGoogleConnected) MadrasaGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isGoogleConnected) Icons.Default.CloudDone else Icons.Default.Login,
                                contentDescription = null,
                                tint = if (isGoogleConnected) MadrasaGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Google Drive Connect",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isGoogleConnected) "সংযুক্ত: $connectedEmail" else "গুগল ড্রাইভ সিঙ্ক নিষ্ক্রিয়",
                                fontSize = 12.sp,
                                color = if (isGoogleConnected) MadrasaGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isGoogleConnected) {
                        Button(
                            onClick = { showLoginDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("google_login_button")
                        ) {
                            Text("Login", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                isGoogleConnected = false
                                prefs.edit().putBoolean("is_connected", false).apply()
                                Toast.makeText(context, "Google Drive Disconnected", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Disconnect", fontSize = 11.sp, color = MadrasaAccent)
                        }
                    }
                }

                if (isGoogleConnected) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "ফোল্ডার: Google Drive / Madrasa M PRO",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "সর্বশেষ সফল ব্যাকআপ: $lastBackupTime",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val jsonString = createBackupJson()
                                    val backupFile = saveBackupToFile(jsonString)
                                    val timeNow = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                                    lastBackupTime = timeNow
                                    prefs.edit().putString("last_backup_time", timeNow).apply()

                                    Toast.makeText(
                                        context,
                                        "Backup Successful! 'Madrasa M PRO' ফোল্ডারে সেভ হয়েছে ($timeNow)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Drive Backup Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                                .testTag("drive_backup_now_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup to Drive", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                try {
                                    restoreJsonLauncher.launch(arrayOf("application/json", "*/*"))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Restore launcher error", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                                .testTag("drive_restore_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("অটো ব্যাকআপ ফ্রিকোয়েন্সি:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("প্রতিদিন", "প্রতি সপ্তাহে").forEach { freq ->
                                OutlinedButton(
                                    onClick = {
                                        autoBackupFreq = freq
                                        prefs.edit().putString("auto_backup_freq", freq).apply()
                                        Toast.makeText(context, "অটো ব্যাকআপ সেট করা হয়েছে: $freq", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (autoBackupFreq == freq) MadrasaPrimary.copy(alpha = 0.12f) else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(freq, fontSize = 11.sp, fontWeight = if (autoBackupFreq == freq) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // App APK Download & Share Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = madrasaCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "📲 Madrasa M PRO (APK) ডাউনলোড ও শেয়ার",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "অ্যাপের ইনস্টলার (APK) ফাইলটি সরাসরি আপনার ফোনের Downloads ফোল্ডারে সেভ করুন অথবা অন্য ডিভাইসে শেয়ার করুন।",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        try {
                            val cachedApk = prepareApkCacheFile()
                            val savedUri = PdfPrintUtil.saveFileToDownloads(
                                context = context,
                                sourceFile = cachedApk,
                                displayName = "Madrasa_M_PRO.apk",
                                mimeType = "application/vnd.android.package-archive"
                            )
                            if (savedUri != null) {
                                Toast.makeText(
                                    context,
                                    "APK ফাইল Downloads/MadrasaPro ফোল্ডারে ডাউনলোড হয়েছে!",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                saveApkDocumentLauncher.launch("Madrasa_M_PRO.apk")
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "ডাউনলোডে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("download_apk_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("APK ডাউনলোড করুন (Downloads)", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            saveApkDocumentLauncher.launch("Madrasa_M_PRO.apk")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_apk_as_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("অন্য ফোল্ডারে সেভ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            try {
                                val cachedApk = prepareApkCacheFile()
                                val apkUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cachedApk
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.android.package-archive"
                                    putExtra(Intent.EXTRA_STREAM, apkUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Madrasa M PRO (APK)")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "APK ফাইল শেয়ার করুন")
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "শেয়ার করতে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_apk_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("APK শেয়ার", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Summary Card of Current Data
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = madrasaCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                BackupStatRow(label = "খরচ ও দানের এন্ট্রি:", count = "${expenses.size} টি")
                BackupStatRow(label = "নোটিশ সংখ্যা:", count = "${notices.size} টি")
            }
        }
    }

    if (showLoginDialog) {
        var emailInput by remember { mutableStateOf("ferdausharfin@gmail.com") }
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("Google Drive Login", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("গুগল অ্যাকাউন্ট দিয়ে কানেক্ট করুন যাতে 'Madrasa M PRO' ফোল্ডারে ক্লাউড ব্যাকআপ সিঙ্ক হতে পারে।", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Google Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (emailInput.isNotBlank()) {
                            connectedEmail = emailInput.trim()
                            isGoogleConnected = true
                            prefs.edit()
                                .putBoolean("is_connected", true)
                                .putString("connected_email", connectedEmail)
                                .apply()
                            Toast.makeText(context, "Google Drive Connected ($connectedEmail)!", Toast.LENGTH_SHORT).show()
                            showLoginDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary)
                ) {
                    Text("Sign In & Connect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
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
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
        Text(text = count, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
