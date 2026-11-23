package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.cloud.SyncConnectionState
import com.example.ui.auth.signOutFromCloudSync
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaAccent
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaSapphire
import com.example.ui.viewmodel.BackupRestoreSummary
import com.example.ui.viewmodel.MadrasaViewModel
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private val driveHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
}

private data class DriveBackupEntry(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val sizeBytes: Long
)

private data class VerifiedDriveSession(
    val isValid: Boolean,
    val email: String = "",
    val accessToken: String = "",
    val errorMessage: String = ""
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun getAppSigningSha1(context: Context): String {
    return try {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = info.signingInfo
            if (signingInfo == null) emptyArray()
            else if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }
        val certBytes = signatures.firstOrNull()?.toByteArray() ?: return ""
        val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
        digest.joinToString(":") { "%02X".format(it) }
    } catch (_: Exception) {
        ""
    }
}

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private val DRIVE_SCOPES = listOf(Scope(DRIVE_APPDATA_SCOPE))

private suspend fun verifyGoogleDriveToken(token: String): VerifiedDriveSession {
    return withContext(Dispatchers.IO) {
        try {
            val aboutReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,displayName)")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            driveHttpClient.newCall(aboutReq).execute().use { aboutRes ->
                val aboutBody = aboutRes.body?.string().orEmpty()

                if (aboutRes.isSuccessful) {
                    val json = JSONObject(aboutBody)
                    val userObj = json.optJSONObject("user")
                    var email = userObj?.optString("emailAddress", "").orEmpty()
                    val displayName = userObj?.optString("displayName", "").orEmpty()

                    if (email.isBlank()) {
                        try {
                            val infoReq = Request.Builder()
                                .url("https://oauth2.googleapis.com/tokeninfo?access_token=$token")
                                .get()
                                .build()
                            driveHttpClient.newCall(infoReq).execute().use { infoRes ->
                                if (infoRes.isSuccessful) {
                                    val infoJson = JSONObject(infoRes.body?.string().orEmpty())
                                    email = infoJson.optString("email", "")
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }

                    VerifiedDriveSession(
                        isValid = true,
                        email = email.ifBlank { displayName.ifBlank { "Google Account" } },
                        accessToken = token
                    )
                } else {
                    val errMsg = try {
                        JSONObject(aboutBody).optJSONObject("error")?.optString("message") ?: "HTTP ${aboutRes.code}"
                    } catch (_: Exception) {
                        "HTTP ${aboutRes.code}"
                    }
                    VerifiedDriveSession(
                        isValid = false,
                        errorMessage = "Drive API verification failed: $errMsg"
                    )
                }
            }
        } catch (e: Exception) {
            VerifiedDriveSession(
                isValid = false,
                errorMessage = "Network error: ${e.localizedMessage ?: "Unknown"}"
            )
        }
    }
}

private suspend fun authorizeDriveResult(
    context: Context,
    scopes: List<Scope> = DRIVE_SCOPES
) = suspendCancellableCoroutine<Result<com.google.android.gms.auth.api.identity.AuthorizationResult>> { cont ->
    val activity = context.findActivity()
    val client = if (activity != null) {
        Identity.getAuthorizationClient(activity)
    } else {
        Identity.getAuthorizationClient(context)
    }
    val req = AuthorizationRequest.builder()
        .setRequestedScopes(scopes)
        .build()
    client.authorize(req)
        .addOnSuccessListener { res ->
            if (cont.isActive) cont.resume(Result.success(res))
        }
        .addOnFailureListener { e ->
            if (cont.isActive) cont.resume(Result.failure(e))
        }
}

@Composable
fun BackupScreen(
    viewModel: MadrasaViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val appSha1 = remember { getAppSigningSha1(context) }
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
        context.getSharedPreferences("google_drive_backup_prefs", Context.MODE_PRIVATE)
    }

    // Never trust local cache on startup — always default to false until Google verifies token
    var isGoogleConnected by remember { mutableStateOf(false) }
    var connectedEmail by remember { mutableStateOf("") }
    var activeAccessToken by remember { mutableStateOf<String?>(null) }
    var isCheckingConnection by remember { mutableStateOf(true) }
    var isConnecting by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isLoadingDriveFiles by remember { mutableStateOf(false) }
    var isRestoringFile by remember { mutableStateOf(false) }
    var lastLocalRestoreStatus by remember { mutableStateOf<String?>(null) }
    var lastLocalRestoreIsWarning by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var showSha1Diagnostic by remember { mutableStateOf(false) }

    var showDriveRestoreDialog by remember { mutableStateOf(false) }
    var driveBackups by remember { mutableStateOf<List<DriveBackupEntry>>(emptyList()) }

    var lastBackupTime by remember {
        mutableStateOf(prefs.getString("last_backup_time", "কখনো ব্যাকআপ করা হয়নি") ?: "কখনো ব্যাকআপ করা হয়নি")
    }
    var autoBackupFreq by remember {
        mutableStateOf(prefs.getString("auto_backup_freq", "প্রতিদিন") ?: "প্রতিদিন")
    }

    fun clearLocalConnectionState() {
        isGoogleConnected = false
        connectedEmail = ""
        activeAccessToken = null
        prefs.edit()
            .putBoolean("is_connected", false)
            .remove("connected_email")
            .apply()
    }

    suspend fun handleVerifiedToken(token: String, showSuccessToast: Boolean) {
        val verified = verifyGoogleDriveToken(token)
        if (verified.isValid) {
            isGoogleConnected = true
            connectedEmail = verified.email
            activeAccessToken = verified.accessToken
            connectionError = null
            showSha1Diagnostic = false
            prefs.edit()
                .putBoolean("is_connected", true)
                .putString("connected_email", verified.email)
                .apply()
            if (showSuccessToast) {
                Toast.makeText(
                    context,
                    "Google Drive Connected (${verified.email})!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            clearLocalConnectionState()
            connectionError = verified.errorMessage
            if (showSuccessToast) {
                Toast.makeText(context, verified.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Test Connection silently on Backup page open (never shows surprise popup if unauthenticated)
    suspend fun runSilentConnectionTest(showStatusToast: Boolean = false) {
        isCheckingConnection = true
        try {
            val res = authorizeDriveResult(context, DRIVE_SCOPES)
            val authResult = res.getOrNull()
            val token = authResult?.accessToken
            if (authResult != null && !authResult.hasResolution() && !token.isNullOrBlank()) {
                handleVerifiedToken(token, showSuccessToast = showStatusToast)
                if (showStatusToast && isGoogleConnected) {
                    Toast.makeText(context, "Connection Verified ($connectedEmail)", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Also check if a prior GoogleSignIn account has a silent token
                val signedInAcc = GoogleSignIn.getLastSignedInAccount(context)?.account
                val gsiToken = if (signedInAcc != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            GoogleAuthUtil.getToken(context, signedInAcc, "oauth2:$DRIVE_APPDATA_SCOPE")
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else null

                if (!gsiToken.isNullOrBlank()) {
                    handleVerifiedToken(gsiToken, showSuccessToast = showStatusToast)
                } else {
                    clearLocalConnectionState()
                    if (showStatusToast) {
                        Toast.makeText(context, "Google Drive সংযুক্ত নয় (Not Connected)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            clearLocalConnectionState()
            if (showStatusToast) {
                Toast.makeText(context, "Connection check failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            isCheckingConnection = false
        }
    }

    LaunchedEffect(Unit) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val pendingTask = try {
            if (FirebaseApp.getApps(context).isNotEmpty()) Firebase.auth.pendingAuthResult else null
        } catch (_: Exception) {
            null
        }
        if (pendingTask != null) {
            pendingTask
                .addOnSuccessListener { authRes ->
                    val token = (authRes.credential as? OAuthCredential)?.accessToken
                    if (!token.isNullOrBlank()) {
                        scope.launch {
                            handleVerifiedToken(token, showSuccessToast = true)
                            isCheckingConnection = false
                        }
                    } else {
                        scope.launch { runSilentConnectionTest(showStatusToast = false) }
                    }
                }
                .addOnFailureListener {
                    scope.launch { runSilentConnectionTest(showStatusToast = false) }
                }
        } else {
            runSilentConnectionTest(showStatusToast = false)
        }
    }

    fun launchFirebaseWebOAuthFallback(priorReason: String = "") {
        val activity = context.findActivity()
        if (activity == null) {
            isConnecting = false
            clearLocalConnectionState()
            showSha1Diagnostic = true
            connectionError = priorReason.ifBlank { "Activity পাওয়া যায়নি" }
            return
        }
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            if (FirebaseApp.getApps(context).isEmpty()) {
                isConnecting = false
                clearLocalConnectionState()
                showSha1Diagnostic = true
                connectionError = "${priorReason.ifBlank { "Google Sign-In" }}\nFirebaseApp কনফিগারেশন পাওয়া যায়নি (google-services.json পরীক্ষা করুন)"
                Toast.makeText(context, connectionError, Toast.LENGTH_LONG).show()
                return
            }
            val provider = OAuthProvider.newBuilder("google.com")
                .setScopes(listOf(DRIVE_APPDATA_SCOPE))
                .addCustomParameter("prompt", "select_account consent")
                .build()
            Firebase.auth.startActivityForSignInWithProvider(activity, provider)
                .addOnSuccessListener { authResult ->
                    val oauthCred = authResult.credential as? OAuthCredential
                    val token = oauthCred?.accessToken
                    scope.launch {
                        if (!token.isNullOrBlank()) {
                            handleVerifiedToken(token, showSuccessToast = true)
                        } else {
                            clearLocalConnectionState()
                            showSha1Diagnostic = true
                            connectionError = "Google Drive Access Token পাওয়া যায়নি।"
                            Toast.makeText(context, connectionError, Toast.LENGTH_LONG).show()
                        }
                        isConnecting = false
                    }
                }
                .addOnFailureListener { err ->
                    isConnecting = false
                    clearLocalConnectionState()
                    val detail = err.localizedMessage ?: "OAuth failed"
                    val isUserCanceled = detail.contains("canceled", ignoreCase = true) ||
                        detail.contains("cancelled", ignoreCase = true)
                    showSha1Diagnostic = !isUserCanceled
                    connectionError = if (isUserCanceled) {
                        "গুগল ড্রাইভ অনুমতি বাতিল করা হয়েছে (Not Connected)"
                    } else if (priorReason.isNotBlank()) {
                        "$priorReason\nWeb OAuth: $detail"
                    } else {
                        "Google Drive OAuth failed: $detail"
                    }
                    Toast.makeText(context, connectionError, Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            isConnecting = false
            clearLocalConnectionState()
            showSha1Diagnostic = true
            connectionError = if (priorReason.isNotBlank()) {
                "$priorReason\nWeb OAuth error: ${e.localizedMessage}"
            } else {
                "Google Drive OAuth error: ${e.localizedMessage}"
            }
            Toast.makeText(context, connectionError, Toast.LENGTH_LONG).show()
        }
    }

    val consentRecoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { actResult ->
        if (actResult.resultCode == Activity.RESULT_OK) {
            scope.launch {
                isConnecting = true
                try {
                    val acc = GoogleSignIn.getLastSignedInAccount(context)?.account
                    if (acc != null) {
                        val token = withContext(Dispatchers.IO) {
                            GoogleAuthUtil.getToken(context, acc, "oauth2:$DRIVE_APPDATA_SCOPE")
                        }
                        handleVerifiedToken(token, showSuccessToast = true)
                    } else {
                        clearLocalConnectionState()
                        connectionError = "Google Account পাওয়া যায়নি (Not Connected)"
                    }
                } catch (e: Exception) {
                    clearLocalConnectionState()
                    connectionError = "Drive OAuth failed: ${e.localizedMessage ?: "Unknown"}"
                    Toast.makeText(context, connectionError, Toast.LENGTH_LONG).show()
                } finally {
                    isConnecting = false
                }
            }
        } else {
            isConnecting = false
            clearLocalConnectionState()
            connectionError = "গুগল ড্রাইভ অনুমতি বাতিল করা হয়েছে (Not Connected)"
            Toast.makeText(context, connectionError, Toast.LENGTH_SHORT).show()
        }
    }

    val googleSignInFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { actResult ->
        scope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(actResult.data)
                val account = task.getResult(ApiException::class.java)
                val androidAccount = account.account
                if (androidAccount != null) {
                    val token = withContext(Dispatchers.IO) {
                        GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_APPDATA_SCOPE")
                    }
                    handleVerifiedToken(token, showSuccessToast = true)
                    isConnecting = false
                } else {
                    launchFirebaseWebOAuthFallback("Google account তথ্য পাওয়া যায়নি")
                }
            } catch (e: UserRecoverableAuthException) {
                e.intent?.let { intent ->
                    consentRecoverLauncher.launch(intent)
                    return@launch
                }
                launchFirebaseWebOAuthFallback("Recoverable auth error: ${e.localizedMessage}")
            } catch (e: ApiException) {
                if (e.statusCode == CommonStatusCodes.CANCELED || e.statusCode == 12501) {
                    isConnecting = false
                    clearLocalConnectionState()
                    connectionError = "গুগল ড্রাইভ অনুমতি বাতিল করা হয়েছে (Not Connected)"
                    Toast.makeText(context, connectionError, Toast.LENGTH_SHORT).show()
                } else {
                    // Status 10 (DEVELOPER_ERROR) typically means APK signing cert != debug.keystore SHA-1
                    // Fall back seamlessly to Firebase Web Custom Tab OAuth flow
                    launchFirebaseWebOAuthFallback("Play Services Sign-In (${e.statusCode})")
                }
            } catch (e: Exception) {
                launchFirebaseWebOAuthFallback("Google Sign-In error: ${e.localizedMessage ?: "Unknown"}")
            }
        }
    }

    fun launchGoogleSignInFallback() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
                .build()
            val gsiClient = GoogleSignIn.getClient(context, gso)
            googleSignInFallbackLauncher.launch(gsiClient.signInIntent)
        } catch (e: Exception) {
            launchFirebaseWebOAuthFallback("Google Sign-In init: ${e.localizedMessage}")
        }
    }

    // Launcher for official Google OAuth Consent Screen / Account Chooser
    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        scope.launch {
            try {
                val activity = context.findActivity()
                val client = if (activity != null) {
                    Identity.getAuthorizationClient(activity)
                } else {
                    Identity.getAuthorizationClient(context)
                }
                val data = activityResult.data
                if (activityResult.resultCode == Activity.RESULT_OK && data != null) {
                    val authResult = client.getAuthorizationResultFromIntent(data)
                    val token = authResult.accessToken
                    if (!token.isNullOrBlank()) {
                        handleVerifiedToken(token, showSuccessToast = true)
                        isConnecting = false
                    } else {
                        launchFirebaseWebOAuthFallback("Google কোন Access Token দেয়নি")
                    }
                } else {
                    var apiEx: ApiException? = null
                    if (data != null) {
                        try {
                            client.getAuthorizationResultFromIntent(data)
                        } catch (e: ApiException) {
                            apiEx = e
                        } catch (_: Exception) {
                        }
                    }
                    if (apiEx != null && apiEx.statusCode != CommonStatusCodes.CANCELED) {
                        launchGoogleSignInFallback()
                        return@launch
                    }
                    isConnecting = false
                    clearLocalConnectionState()
                    connectionError = "গুগল ড্রাইভ অনুমতি বাতিল করা হয়েছে (Not Connected)"
                    Toast.makeText(context, connectionError, Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                if (e.statusCode != CommonStatusCodes.CANCELED) {
                    launchGoogleSignInFallback()
                    return@launch
                }
                isConnecting = false
                clearLocalConnectionState()
                connectionError = "গুগল ড্রাইভ অনুমতি বাতিল করা হয়েছে (Not Connected)"
                Toast.makeText(context, connectionError, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                launchFirebaseWebOAuthFallback("Auth error: ${e.localizedMessage ?: "Unknown"}")
            }
        }
    }

    fun startInteractiveDriveConnect() {
        if (isConnecting) return
        isConnecting = true
        connectionError = null
        showSha1Diagnostic = false
        scope.launch {
            try {
                val result = authorizeDriveResult(context, DRIVE_SCOPES)
                result.onSuccess { authResult ->
                    val pendingIntent = authResult.pendingIntent
                    val accessToken = authResult.accessToken
                    if (authResult.hasResolution() && pendingIntent != null) {
                        authResolutionLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else if (!accessToken.isNullOrBlank()) {
                        handleVerifiedToken(accessToken, showSuccessToast = true)
                        isConnecting = false
                    } else {
                        launchGoogleSignInFallback()
                    }
                }.onFailure {
                    launchGoogleSignInFallback()
                }
            } catch (_: Exception) {
                launchGoogleSignInFallback()
            }
        }
    }

    fun disconnectGoogleDrive() {
        val tokenToRevoke = activeAccessToken
        scope.launch(Dispatchers.IO) {
            try {
                if (!tokenToRevoke.isNullOrBlank()) {
                    val req = Request.Builder()
                        .url("https://oauth2.googleapis.com/revoke?token=$tokenToRevoke")
                        .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                        .build()
                    driveHttpClient.newCall(req).execute().close()
                    try {
                        GoogleAuthUtil.clearToken(context, tokenToRevoke)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) {
                try {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
                        .build()
                    val gsiClient = GoogleSignIn.getClient(context, gso)
                    gsiClient.revokeAccess()
                    gsiClient.signOut()
                } catch (_: Exception) {
                }
                clearLocalConnectionState()
                connectionError = null
                showSha1Diagnostic = false
                Toast.makeText(context, "Google Drive Disconnected & Revoked", Toast.LENGTH_SHORT).show()
            }
        }
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
            })
        }
        root.put("classRoutines", routinesArray)

        return root.toString(2)
    }

    suspend fun getFreshAccessToken(): String? {
        val res = authorizeDriveResult(context, DRIVE_SCOPES)
        val authRes = res.getOrNull()
        var token = if (authRes != null && !authRes.hasResolution()) authRes.accessToken else null
        if (token.isNullOrBlank()) {
            val signedInAcc = GoogleSignIn.getLastSignedInAccount(context)?.account
            if (signedInAcc != null) {
                token = withContext(Dispatchers.IO) {
                    try {
                        GoogleAuthUtil.getToken(context, signedInAcc, "oauth2:$DRIVE_APPDATA_SCOPE")
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
        if (token.isNullOrBlank()) {
            token = activeAccessToken
        } else {
            activeAccessToken = token
        }
        return token
    }

    suspend fun uploadToGoogleDrive(jsonContent: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val token = getFreshAccessToken()
                    ?: return@withContext Result.failure(Exception("Valid Google Drive token not available"))

                val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "MadrasaMPro_Backup_$dateSuffix.json"

                val metadata = JSONObject().apply {
                    put("name", fileName)
                    put("parents", JSONArray().put("appDataFolder"))
                }
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "metadata",
                        null,
                        metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                    )
                    .addFormDataPart(
                        "file",
                        fileName,
                        jsonContent.toRequestBody("application/json; charset=UTF-8".toMediaType())
                    )
                    .build()

                val uploadReq = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                driveHttpClient.newCall(uploadReq).execute().use { uploadRes ->
                    val uploadBody = uploadRes.body?.string().orEmpty()
                    if (uploadRes.isSuccessful) {
                        Result.success("Google Drive (M PRO)")
                    } else {
                        val apiMsg = try {
                            JSONObject(uploadBody).optJSONObject("error")?.optString("message") ?: "HTTP ${uploadRes.code}"
                        } catch (_: Exception) {
                            "HTTP ${uploadRes.code}"
                        }
                        Result.failure(Exception(apiMsg))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun listDriveBackups(): Result<List<DriveBackupEntry>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = getFreshAccessToken()
                    ?: return@withContext Result.failure(Exception("Token expired"))
                val collected = mutableListOf<DriveBackupEntry>()

                val req = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=trashed=false&fields=files(id,name,modifiedTime,size)&orderBy=modifiedTime desc")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                driveHttpClient.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        val errMsg = try {
                            JSONObject(body).optJSONObject("error")?.optString("message") ?: "HTTP ${res.code}"
                        } catch (_: Exception) {
                            "HTTP ${res.code}"
                        }
                        return@withContext Result.failure(Exception(errMsg))
                    }
                    val arr = JSONObject(body).optJSONArray("files")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val f = arr.getJSONObject(i)
                            collected.add(
                                DriveBackupEntry(
                                    id = f.optString("id"),
                                    name = f.optString("name", "Backup.json"),
                                    modifiedTime = f.optString("modifiedTime", ""),
                                    sizeBytes = f.optString("size", "0").toLongOrNull() ?: 0L
                                )
                            )
                        }
                    }
                }
                Result.success(collected)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun downloadAndRestoreDriveFile(fileId: String): Result<BackupRestoreSummary> {
        return withContext(Dispatchers.IO) {
            try {
                val token = getFreshAccessToken()
                    ?: return@withContext Result.failure(Exception("Token expired"))
                val req = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                driveHttpClient.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        return@withContext Result.failure(Exception("Download failed: HTTP ${res.code}"))
                    }
                    viewModel.restoreFromBackupJson(body)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    LaunchedEffect(isGoogleConnected, autoBackupFreq) {
        if (!isGoogleConnected || autoBackupFreq == "বন্ধ" || isUploading) return@LaunchedEffect
        val hasAnyData = students.isNotEmpty() || fees.isNotEmpty() || results.isNotEmpty() ||
            teachers.isNotEmpty() || teacherPayments.isNotEmpty() || expenses.isNotEmpty() ||
            notices.isNotEmpty() || questionPapers.isNotEmpty() || classRoutines.isNotEmpty()
        if (!hasAnyData) return@LaunchedEffect

        val intervalMs = when (autoBackupFreq) {
            "প্রতিদিন" -> 24L * 60L * 60L * 1000L
            "প্রতি সপ্তাহে" -> 7L * 24L * 60L * 60L * 1000L
            else -> return@LaunchedEffect
        }
        val lastEpoch = prefs.getLong("last_backup_epoch_ms", 0L)
        val now = System.currentTimeMillis()
        if (lastEpoch > 0L && (now - lastEpoch) >= intervalMs) {
            isUploading = true
            try {
                val jsonString = withContext(Dispatchers.Default) { createBackupJson() }
                val res = uploadToGoogleDrive(jsonString)
                res.onSuccess {
                    val timeNow = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                    lastBackupTime = timeNow
                    prefs.edit()
                        .putString("last_backup_time", timeNow)
                        .putLong("last_backup_epoch_ms", System.currentTimeMillis())
                        .apply()
                }
            } catch (_: Exception) {
            } finally {
                isUploading = false
            }
        }
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

    val saveDriveJsonLauncher = rememberLauncherForActivityResult(
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
                    Toast.makeText(context, "Local Backup Successful!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "সংরক্ষণে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    var isManualCloudSyncing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Multi-Device Live Cloud Sync Card (Firestore + Google Auth)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = madrasaCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                val (badgeColor, badgeLabel) = when (syncStatus.state) {
                    SyncConnectionState.LIVE -> MadrasaGreen to "লাইভ সিঙ্ক চালু"
                    SyncConnectionState.SYNCING -> MadrasaSapphire to "সিঙ্ক হচ্ছে..."
                    SyncConnectionState.OFFLINE_PENDING -> Color(0xFFD97706) to "অফলাইন / অপেক্ষমাণ"
                    SyncConnectionState.ERROR -> MadrasaRed to "সিঙ্ক ত্রুটি"
                    SyncConnectionState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant to "প্রস্তুত"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(badgeColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "মাল্টি-ডিভাইস লাইভ ক্লাউড সিঙ্ক",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (syncStatus.userEmail.isNotBlank()) {
                                    "অ্যাকাউন্ট: ${syncStatus.userEmail}"
                                } else {
                                    "M PRO Cloud Sync"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = badgeColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = badgeLabel,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(11.dp)) {
                        Text(
                            text = syncStatus.message,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (syncStatus.lastSyncedTime.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "সর্বশেষ সিঙ্ক: ${syncStatus.lastSyncedTime}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "অন্য মোবাইলে এই অ্যাপ ইনস্টল করে একই Google অ্যাকাউন্ট (${syncStatus.userEmail.ifBlank { "আপনার ইমেইল" }}) দিয়ে লগইন করলে সাথে সাথে বা কয়েকদিন পরেও সব ডেটা স্বয়ংক্রিয়ভাবে দেখা ও এডিট করা যাবে।",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (!syncStatus.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MadrasaRed.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MadrasaRed.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = syncStatus.errorMessage ?: "",
                            fontSize = 11.5.sp,
                            color = MadrasaRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isManualCloudSyncing) return@Button
                            isManualCloudSyncing = true
                            scope.launch {
                                val res = viewModel.syncNow()
                                isManualCloudSyncing = false
                                res.onSuccess { count ->
                                    Toast.makeText(
                                        context,
                                        "$count টি রেকর্ড ক্লাউডে সিঙ্ক সম্পন্ন হয়েছে!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }.onFailure { err ->
                                    Toast.makeText(
                                        context,
                                        "সিঙ্ক ত্রুটি: ${err.message ?: "ইন্টারনেট সংযোগ পরীক্ষা করুন"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = !isManualCloudSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cloud_sync_now_button")
                    ) {
                        if (isManualCloudSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সিঙ্ক হচ্ছে...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("এখনই সিঙ্ক করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            signOutFromCloudSync(
                                context = context,
                                scope = scope,
                                beforeSignOut = { viewModel.stopCloudSync() }
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MadrasaRed),
                        border = BorderStroke(1.dp, MadrasaRed.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("cloud_sync_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "লগআউট",
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("লগআউট", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isGoogleConnected -> MadrasaGreen.copy(alpha = 0.16f)
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isGoogleConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isGoogleConnected) MadrasaGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Google Drive Connect",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = when {
                                    isCheckingConnection -> "সংযোগ যাচাই করা হচ্ছে..."
                                    isGoogleConnected -> "সংযুক্ত: $connectedEmail"
                                    else -> "সংযুক্ত নয় (Not Connected)"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isGoogleConnected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isGoogleConnected) MadrasaGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                scope.launch { runSilentConnectionTest(showStatusToast = true) }
                            },
                            enabled = !isCheckingConnection
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Test Connection",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isGoogleConnected) {
                            OutlinedButton(
                                onClick = { disconnectGoogleDrive() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MadrasaRed.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Disconnect", fontSize = 11.5.sp, color = MadrasaRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                val currentConnErr = connectionError
                if (!currentConnErr.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MadrasaRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MadrasaRed.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = currentConnErr,
                                color = MadrasaRed,
                                fontSize = 12.sp
                            )
                            if (showSha1Diagnostic && appSha1.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "App ID: ${context.packageName}\nAPK SHA-1: $appSha1",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(appSha1))
                                        Toast.makeText(context, "SHA-1 কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy SHA-1",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SHA-1 কপি করুন", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(14.dp))

                if (isGoogleConnected) {
                    Text(
                        text = "ক্লাউড স্টোরেজ: Google Drive (M PRO)",
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

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "অটো ব্যাকআপ সময়সূচী:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("প্রতিদিন", "প্রতি সপ্তাহে", "বন্ধ").forEach { freq ->
                            FilterChip(
                                selected = autoBackupFreq == freq,
                                onClick = {
                                    autoBackupFreq = freq
                                    prefs.edit().putString("auto_backup_freq", freq).apply()
                                    Toast.makeText(context, "অটো ব্যাকআপ: $freq সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(freq, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Text(
                        text = "গুগল ড্রাইভে নিরাপদ ক্লাউড ব্যাকআপ রাখতে Google Login করুন অথবা আগের ব্যাকআপ ফাইল থেকে রিস্টোর করুন।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Side-by-side Primary Action Row (Clean spacing & crisp high-contrast colors)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isGoogleConnected) {
                        Button(
                            onClick = { startInteractiveDriveConnect() },
                            enabled = !isConnecting && !isCheckingConnection,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("google_login_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MadrasaPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = MadrasaPrimary.copy(alpha = 0.55f),
                                disabledContentColor = Color.White.copy(alpha = 0.85f)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnecting) "Connecting..." else "Google Login",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isUploading) return@Button
                                isUploading = true
                                connectionError = null
                                scope.launch {
                                    try {
                                        val jsonString = withContext(Dispatchers.Default) { createBackupJson() }
                                        val uploadResult = uploadToGoogleDrive(jsonString)
                                        uploadResult.onSuccess { folderLabel ->
                                            val timeNow = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                                            lastBackupTime = timeNow
                                            prefs.edit()
                                                .putString("last_backup_time", timeNow)
                                                .putLong("last_backup_epoch_ms", System.currentTimeMillis())
                                                .apply()
                                            Toast.makeText(
                                                context,
                                                "Backup Successful! $folderLabel-এ আপলোড হয়েছে ($timeNow)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }.onFailure { err ->
                                            val msg = "Drive Backup Failed: ${err.message}"
                                            connectionError = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        val msg = "Drive Backup Error: ${e.message}"
                                        connectionError = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    } finally {
                                        isUploading = false
                                    }
                                }
                            },
                            enabled = !isUploading,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("drive_backup_now_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MadrasaPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = MadrasaPrimary.copy(alpha = 0.55f),
                                disabledContentColor = Color.White.copy(alpha = 0.85f)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUploading) "Uploading..." else "Backup to Drive",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isGoogleConnected) {
                                isLoadingDriveFiles = true
                                showDriveRestoreDialog = true
                                scope.launch {
                                    val res = listDriveBackups()
                                    res.onSuccess { list ->
                                        driveBackups = list
                                    }.onFailure { err ->
                                        driveBackups = emptyList()
                                        Toast.makeText(context, "Drive list error: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    isLoadingDriveFiles = false
                                }
                            } else {
                                try {
                                    restoreJsonLauncher.launch(
                                        arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Restore launcher error", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("drive_restore_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MadrasaAccent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "রিস্টোর",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
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
                            saveDriveJsonLauncher.launch("MPro_Backup_$dateSuffix.json")
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

    if (showDriveRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showDriveRestoreDialog = false },
            title = { Text("গুগল ড্রাইভ থেকে রিস্টোর", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isLoadingDriveFiles) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("ড্রাইভ ব্যাকআপ তালিকা খোঁজা হচ্ছে...", fontSize = 13.sp)
                        }
                    } else if (driveBackups.isEmpty()) {
                        Text(
                            "Google Drive-এ এখনো কোন ব্যাকআপ পাওয়া যায়নি। আপনি ফোনের স্টোরেজ থেকেও JSON ফাইল রিস্টোর করতে পারেন।",
                            fontSize = 13.sp
                        )
                    } else {
                        Text("রিস্টোর করতে একটি ব্যাকআপ সিলেক্ট করুন:", fontSize = 13.sp)
                        driveBackups.take(5).forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDriveRestoreDialog = false
                                        scope.launch {
                                            val res = downloadAndRestoreDriveFile(entry.id)
                                            res.onSuccess { summary ->
                                                val msg = when {
                                                    summary.isDuplicateFile ->
                                                        "Drive ফাইলটি (${entry.name}) আগেই রিস্টোর করা আছে! ${summary.skippedDuplicateCount} টি তথ্য ডাবল হওয়া থেকে সুরক্ষিত রাখা হয়েছে।"
                                                    summary.skippedDuplicateCount > 0 ->
                                                        "Drive থেকে ${summary.addedOrUpdatedCount} টি তথ্য রিস্টোর হয়েছে (${summary.skippedDuplicateCount} টি ডুপ্লিকেট এড়ানো হয়েছে)।"
                                                    else ->
                                                        "Drive থেকে ${summary.addedOrUpdatedCount} টি তথ্য সফলভাবে রিস্টোর হয়েছে!"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }.onFailure { err ->
                                                Toast.makeText(
                                                    context,
                                                    "রিস্টোর ব্যর্থ: ${err.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(entry.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${entry.modifiedTime.take(19).replace('T', ' ')} • ${entry.sizeBytes / 1024} KB",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDriveRestoreDialog = false
                        restoreJsonLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MadrasaAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text("ফোন থেকে ফাইল নিন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDriveRestoreDialog = false }) {
                    Text("বন্ধ করুন")
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
