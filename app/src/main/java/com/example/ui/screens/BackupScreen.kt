package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
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
import androidx.core.content.FileProvider
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaAccent
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

    fun createBackupJson(): String {
        val root = JSONObject()
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        root.put("institution", "ইসহাকিয়া কেরাতুল কোরআন নূরানী ও হাফেজিয়া মাদ্রাসা")
        root.put("backupDate", timeStamp)

        val studentsArray = JSONArray()
        students.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("father", s.father)
                put("phone", s.phone)
                put("cls", s.cls)
                put("fee", s.fee)
                put("roll", s.roll)
                put("electricBill", s.electricBill)
                put("photoUri", s.photoUri)
            }
            studentsArray.put(obj)
        }
        root.put("students", studentsArray)

        val feesArray = JSONArray()
        fees.forEach { f ->
            val obj = JSONObject().apply {
                put("studentId", f.studentId)
                put("amount", f.amount)
                put("type", f.type)
                put("month", f.month)
                put("date", f.date)
            }
            feesArray.put(obj)
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
            val obj = JSONObject().apply {
                put("studentId", r.studentId)
                put("exam", r.exam)
                put("subjects", subjectsArr)
                put("total", r.total)
                put("avg", r.avg)
                put("date", r.date)
            }
            resultsArray.put(obj)
        }
        root.put("results", resultsArray)

        val teachersArray = JSONArray()
        teachers.forEach { t ->
            val obj = JSONObject().apply {
                put("name", t.name)
                put("subject", t.subject)
                put("salary", t.salary)
                put("address", t.address)
                put("phone", t.phone)
                put("photoUri", t.photoUri)
                put("joinDate", t.joinDate)
            }
            teachersArray.put(obj)
        }
        root.put("teachers", teachersArray)

        val teacherPaymentsArray = JSONArray()
        teacherPayments.forEach { tp ->
            val obj = JSONObject().apply {
                put("teacherId", tp.teacherId)
                put("amount", tp.amount)
                put("month", tp.month)
                put("year", tp.year)
                put("date", tp.date)
                put("note", tp.note)
            }
            teacherPaymentsArray.put(obj)
        }
        root.put("teacherPayments", teacherPaymentsArray)

        val expensesArray = JSONArray()
        expenses.forEach { e ->
            val obj = JSONObject().apply {
                put("type", e.type)
                put("desc", e.desc)
                put("amount", e.amount)
                put("date", e.date)
            }
            expensesArray.put(obj)
        }
        root.put("expenses", expensesArray)

        val noticesArray = JSONArray()
        notices.forEach { n ->
            val obj = JSONObject().apply {
                put("title", n.title)
                put("description", n.description)
                put("date", n.date)
            }
            noticesArray.put(obj)
        }
        root.put("notices", noticesArray)

        return root.toString(2)
    }

    fun saveBackupToFile(jsonContent: String): File {
        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(context.cacheDir, "Madrasa_Backup_$dateSuffix.json")
        FileOutputStream(backupFile).use { out ->
            out.write(jsonContent.toByteArray(Charsets.UTF_8))
        }
        return backupFile
    }

    fun prepareApkCacheFile(): File {
        val sourceApk = File(context.applicationInfo.sourceDir)
        val cachedApk = File(context.cacheDir, "Madrasa_Management_Pro.apk")
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
                Toast.makeText(context, "ব্যাকআপ ফাইল সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "সংরক্ষণে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    text = "📲 অ্যাপ্লিকেশন (APK) ডাউনলোড ও শেয়ার",
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
                                displayName = "Madrasa_Management_Pro.apk",
                                mimeType = "application/vnd.android.package-archive"
                            )
                            if (savedUri != null) {
                                Toast.makeText(
                                    context,
                                    "APK ফাইল Downloads/MadrasaPro ফোল্ডারে ডাউনলোড হয়েছে!",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                saveApkDocumentLauncher.launch("Madrasa_Management_Pro.apk")
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
                            saveApkDocumentLauncher.launch("Madrasa_Management_Pro.apk")
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
                                    putExtra(Intent.EXTRA_SUBJECT, "মাদ্রাসা ম্যানেজমেন্ট প্রো (APK)")
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

        // Data Backup & Google Drive Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = madrasaCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "💾 ডেটা ব্যাকআপ ও গুগল ড্রাইভ সিঙ্ক",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "আপনার মাদ্রাসার সকল ছাত্র, ফি, রেজাল্ট, শিক্ষক, খরচ ও নোটিশ ডেটা গুগল ড্রাইভে সংরক্ষণ অথবা ফাইল হিসেবে ডাউনলোড করুন।",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        try {
                            val jsonString = createBackupJson()
                            val backupFile = saveBackupToFile(jsonString)
                            val fileUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                backupFile
                            )

                            val driveIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                putExtra(Intent.EXTRA_SUBJECT, "মাদ্রাসা ব্যাকআপ ফাইল")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                setPackage("com.google.android.apps.docs")
                            }

                            if (driveIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(driveIntent)
                            } else {
                                saveDriveJsonLauncher.launch(backupFile.name)
                            }
                        } catch (_: Exception) {
                            val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            saveDriveJsonLauncher.launch("Madrasa_Backup_$dateSuffix.json")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("google_drive_backup_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("গুগল ড্রাইভে ব্যাকআপ সেভ করুন", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        try {
                            val jsonString = createBackupJson()
                            val backupFile = saveBackupToFile(jsonString)
                            val savedUri = PdfPrintUtil.saveFileToDownloads(
                                context = context,
                                sourceFile = backupFile,
                                displayName = backupFile.name,
                                mimeType = "application/json"
                            )
                            if (savedUri != null) {
                                Toast.makeText(
                                    context,
                                    "ব্যাকআপ Downloads/MadrasaPro ফোল্ডারে সেভ হয়েছে!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            val fileUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                backupFile
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                putExtra(Intent.EXTRA_SUBJECT, "ইসহাকিয়া মাদ্রাসা - ব্যাকআপ ফাইল")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "ব্যাকআপ ফাইল সংরক্ষণ বা শেয়ার করুন"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "ব্যাকআপ তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("download_backup_json_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaAccent, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📥 ডেটা ব্যাকআপ ডাউনলোড ও শেয়ার", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
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
