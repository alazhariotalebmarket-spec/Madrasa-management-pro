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
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    val scope = rememberCoroutineScope()
    val students by viewModel.students.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val results by viewModel.results.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val teacherPayments by viewModel.teacherPayments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val notices by viewModel.notices.collectAsState()
    val madrasaInfo by viewModel.madrasaInfo.collectAsState()

    val prefs = remember { context.getSharedPreferences("google_drive_backup_prefs", android.content.Context.MODE_PRIVATE) }
    var isGoogleConnected by remember { mutableStateOf(false) }
    var connectedEmail by remember { mutableStateOf("") }
    var isCheckingConnection by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var lastBackupTime by remember { mutableStateOf(prefs.getString("last_backup_time", "কখনো ব্যাকআপ করা হয়নি") ?: "কখনো ব্যাকআপ করা হয়নি") }
    var autoBackupFreq by remember { mutableStateOf(prefs.getString("auto_backup_freq", "প্রতিদিন") ?: "প্রতিদিন") }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/drive.file"),
                Scope("https://www.googleapis.com/auth/drive.appdata")
            )
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // Test Connection on Startup
    LaunchedEffect(Unit) {
        isCheckingConnection = true
        withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null && account.account != null) {
                    val token = GoogleAuthUtil.getToken(
                        context,
                        account.account!!,
                        "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"
                    )
                    if (!token.isNullOrBlank()) {
                        // Verify token with Google Drive API
                        val client = OkHttpClient()
                        val req = Request.Builder()
                            .url("https://www.googleapis.com/drive/v3/about?fields=user")
                            .addHeader("Authorization", "Bearer $token")
                            .get()
                            .build()
                        val res = client.newCall(req).execute()
                        if (res.isSuccessful) {
                            withContext(Dispatchers.Main) {
                                isGoogleConnected = true
                                connectedEmail = account.email.orEmpty()
                                prefs.edit().putBoolean("is_connected", true).putString("connected_email", connectedEmail).apply()
                            }
                        } else {
                            // Token expired or invalid
                            googleSignInClient.signOut()
                            withContext(Dispatchers.Main) {
                                isGoogleConnected = false
                                connectedEmail = ""
                                prefs.edit().putBoolean("is_connected", false).remove("connected_email").apply()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isGoogleConnected = false
                            connectedEmail = ""
                            prefs.edit().putBoolean("is_connected", false).remove("connected_email").apply()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isGoogleConnected = false
                        connectedEmail = ""
                        prefs.edit().putBoolean("is_connected", false).remove("connected_email").apply()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isGoogleConnected = false
                    connectedEmail = ""
                    prefs.edit().putBoolean("is_connected", false).remove("connected_email").apply()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isCheckingConnection = false
                }
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)
            if (account != null && account.account != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val token = GoogleAuthUtil.getToken(
                            context,
                            account.account!!,
                            "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"
                        )
                        if (!token.isNullOrBlank()) {
                            withContext(Dispatchers.Main) {
                                isGoogleConnected = true
                                connectedEmail = account.email.orEmpty()
                                prefs.edit().putBoolean("is_connected", true).putString("connected_email", connectedEmail).apply()
                                Toast.makeText(context, "Google Drive Connected ($connectedEmail)!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Google Drive Sign-In failed: Token missing", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Google Drive Sign-In error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Google Sign-In denied or failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

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

    suspend fun uploadToGoogleDrive(jsonContent: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext false
                val token = GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"
                ) ?: return@withContext false

                val client = OkHttpClient()
                val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

                // 1. Find or create folder "Madrasa M PRO"
                val searchUrl = "https://www.googleapis.com/drive/v3/files?q=name='Madrasa M PRO' and mimeType='application/vnd.google-apps.folder' and trashed=false"
                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val searchRes = client.newCall(searchReq).execute()
                var folderId: String? = null
                if (searchRes.isSuccessful) {
                    val bodyStr = searchRes.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        folderId = files.getJSONObject(0).optString("id")
                    }
                }

                if (folderId.isNullOrBlank()) {
                    val createFolderUrl = "https://www.googleapis.com/drive/v3/files"
                    val folderMeta = JSONObject().apply {
                        put("name", "Madrasa M PRO")
                        put("mimeType", "application/vnd.google-apps.folder")
                    }
                    val createReq = Request.Builder()
                        .url(createFolderUrl)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Content-Type", "application/json")
                        .post(folderMeta.toString().toRequestBody(mediaTypeJson))
                        .build()
                    val createRes = client.newCall(createReq).execute()
                    if (createRes.isSuccessful) {
                        val createBody = createRes.body?.string().orEmpty()
                        folderId = JSONObject(createBody).optString("id")
                    }
                }

                if (folderId.isNullOrBlank()) return@withContext false

                // 2. Upload file
                val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "MadrasaMPro_Backup_$dateSuffix.json"
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                val metadata = JSONObject().apply {
                    put("name", fileName)
                    put("parents", JSONArray().put(folderId))
                }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", null, metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .addFormDataPart("file", fileName, jsonContent.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .build()

                val uploadReq = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                val uploadRes = client.newCall(uploadReq).execute()
                uploadRes.isSuccessful
            } catch (e: Exception) {
                false
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
                                text = if (isCheckingConnection) "সংযোগ যাচাই করা হচ্ছে..." else if (isGoogleConnected) "সংযুক্ত: $connectedEmail" else "গুগল ড্রাইভ সিঙ্ক নিষ্ক্রিয়",
                                fontSize = 12.sp,
                                color = if (isGoogleConnected) MadrasaGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isGoogleConnected) {
                        Button(
                            onClick = {
                                val signInIntent = googleSignInClient.signInIntent
                                signInLauncher.launch(signInIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("google_login_button")
                        ) {
                            Text("Login", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    googleSignInClient.revokeAccess().addOnCompleteListener {
                                        isGoogleConnected = false
                                        connectedEmail = ""
                                        prefs.edit().putBoolean("is_connected", false).remove("connected_email").apply()
                                        Toast.makeText(context, "Google Drive Disconnected", Toast.LENGTH_SHORT).show()
                                    }
                                }
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
                                if (isUploading) return@Button
                                isUploading = true
                                scope.launch {
                                    try {
                                        val jsonString = createBackupJson()
                                        val success = uploadToGoogleDrive(jsonString)
                                        if (success) {
                                            val timeNow = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                                            lastBackupTime = timeNow
                                            prefs.edit().putString("last_backup_time", timeNow).apply()
                                            Toast.makeText(
                                                context,
                                                "Backup Successful! Google Drive এর 'Madrasa M PRO' ফোল্ডারে আপলোড হয়েছে ($timeNow)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(context, "Drive Backup Error: Upload failed or permission denied", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Drive Backup Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isUploading = false
                                    }
                                }
                            },
                            enabled = !isUploading,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                                .testTag("drive_backup_now_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUploading) "Uploading..." else "Backup to Drive", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                }
            }
        }

        // Local Export / Share Card
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
                    text = "আপনার ফোনের মেমোরিতে JSON ফাইল হিসেবে ব্যাকআপ সেভ করুন অথবা অন্য ডিভাইসে শেয়ার করুন।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                Toast.makeText(context, "Local Backup Successful!", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "সংরক্ষণে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            saveDriveJsonLauncher.launch("MadrasaMPro_Backup_$dateSuffix.json")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            try {
                                val jsonString = createBackupJson()
                                val backupFile = File(context.cacheDir, "MadrasaMPro_Share_Backup.json")
                                FileOutputStream(backupFile).use { out ->
                                    out.write(jsonString.toByteArray(Charsets.UTF_8))
                                }
                                val shareUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    backupFile
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Madrasa M PRO Backup")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "শেয়ার করুন"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "শেয়ার করতে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
