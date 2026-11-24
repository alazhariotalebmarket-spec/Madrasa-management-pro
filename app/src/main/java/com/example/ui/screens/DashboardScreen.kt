package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.MadrasaInfo
import com.example.data.entity.FeeEntity
import com.example.data.entity.StudentEntity
import com.example.ui.components.ImageCropperDialog
import com.example.ui.components.IslamicPatternOverlay
import com.example.ui.components.MadrasaHeaderGradient
import com.example.ui.components.MadrasaRoundLogo
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaGreen
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaSapphire
import com.example.ui.theme.MadrasaSky
import com.example.util.PdfPrintUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class MenuOption(
    val title: String,
    val icon: ImageVector,
    val screen: String,
    val testTag: String
)

@Composable
fun DashboardScreen(
    students: List<StudentEntity>,
    fees: List<FeeEntity>,
    madrasaInfo: MadrasaInfo,
    logoVersion: Long = 0L,
    onOpenDrawer: () -> Unit = {},
    onUpdateMadrasaInfo: (String, String, String, String?) -> Unit,
    onUpdateMadrasaLogo: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var cropFromEditDialog by remember { mutableStateOf(false) }
    var dialogLogoPath by remember(madrasaInfo.logoPath, showEditInfoDialog) {
        mutableStateOf(madrasaInfo.logoPath)
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingCropUri = uri
        }
    }

    val menuItems = remember {
        listOf(
            MenuOption("ভর্তি", Icons.Default.PersonAdd, "students", "menu_admission"),
            MenuOption("হাজিরা", Icons.AutoMirrored.Filled.FactCheck, "attendance", "menu_attendance"),
            MenuOption("কল ও এসএমএস", Icons.Default.Call, "report", "menu_call_sms"),
            MenuOption("বেতন ও বিদ্যুৎ বিল", Icons.Default.Payments, "fees", "menu_fees"),
            MenuOption("প্রশ্নপত্র", Icons.Default.Description, "question_paper", "menu_question_paper"),
            MenuOption("রেজাল্ট", Icons.Default.School, "results", "menu_results"),
            MenuOption("আইডি কার্ড", Icons.Default.Badge, "id_card", "menu_id_card"),
            MenuOption("প্রবেশ পত্র", Icons.Default.AssignmentInd, "admit_card", "menu_admit_card"),
            MenuOption("ক্লাস রুটিন", Icons.Default.CalendarMonth, "routine", "menu_routine"),
            MenuOption("শিক্ষকের বেতন", Icons.Default.SupervisorAccount, "teachers", "menu_teachers"),
            MenuOption("আয় ব্যয়", Icons.Default.AccountBalanceWallet, "accounts", "menu_accounts"),
            MenuOption("মেধাতালিকা", Icons.Default.EmojiEvents, "merit_list", "menu_merit_list"),
            MenuOption("নোটিশ", Icons.Default.NotificationsActive, "notice", "menu_notice"),
            MenuOption("ডেভেলপার", Icons.Default.Code, "developer", "menu_developer")
        )
    }

    var titleFontSize by remember(madrasaInfo.name) { mutableStateOf(15.sp) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Signature Deep Blue-Teal Curved Header with Islamic Pattern & Warm Gold Trim
        Surface(
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            shadowElevation = 8.dp,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MadrasaHeaderGradient)
            ) {
                IslamicPatternOverlay(modifier = Modifier.matchParentSize())

                Column {
                    // Top App Bar utility strip: Drawer Menu + App Identity + Login / Profile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onOpenDrawer,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("dashboard_drawer_button")
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.14f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "মেনু ও প্রোফাইল",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "M PRO",
                                color = MadrasaGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MadrasaGold,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onNavigate("backup") }
                                .testTag("topbar_backup_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backup,
                                    contentDescription = "অফলাইন ব্যাকআপ ও রিস্টোর",
                                    tint = Color(0xFF063D23),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "ব্যাকআপ",
                                    color = Color(0xFF063D23),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Editable Circular Madrasa Logo framed in Warm Gold with edit badge
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .clickable {
                                    cropFromEditDialog = false
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("dashboard_logo_edit_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            MadrasaRoundLogo(
                                logoPath = madrasaInfo.logoPath,
                                logoVersion = logoVersion,
                                size = 64.dp,
                                borderWidth = 2.dp,
                                borderColor = MadrasaGold.copy(alpha = 0.9f),
                                shadowElevation = 6.dp,
                                contentDescription = "মাদ্রাসা লোগো পরিবর্তন করুন"
                            )
                            Surface(
                                shape = CircleShape,
                                color = MadrasaGold,
                                border = BorderStroke(1.5.dp, Color.White),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "লোগো পরিবর্তন",
                                        tint = Color(0xFF063D23),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Right: Editable Madrasa Title, Address, Mobile + Edit & Account/Backup pills
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        showEditInfoDialog = true
                                    }
                                    .testTag("edit_madrasa_info_area")
                            ) {
                                Text(
                                    text = madrasaInfo.name,
                                    color = Color.White,
                                    fontSize = titleFontSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { result ->
                                        if (result.hasVisualOverflow && titleFontSize.value > 10.5f) {
                                            titleFontSize = (titleFontSize.value - 0.5f).sp
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = madrasaInfo.address,
                                    color = MadrasaSky,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "মোবাইল: ${madrasaInfo.phone}",
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MadrasaGold.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, MadrasaGold.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                showEditInfoDialog = true
                                            }
                                            .testTag("header_edit_info_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "তথ্য সম্পাদনা",
                                                tint = MadrasaGold,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "এডিট",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onNavigate("backup") }
                                            .testTag("header_backup_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Storage,
                                                contentDescription = "ব্যাকআপ ও রিস্টোর",
                                                tint = MadrasaSky,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "ব্যাকআপ",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.22f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onNavigate("backup") }
                                    .testTag("dashboard_offline_status_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4ADE80))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "অফলাইন মোড চালু • ব্যাকআপ ও রিস্টোর",
                                        color = Color.White.copy(alpha = 0.92f),
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Gold hairline architectural accent at header bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MadrasaGold.copy(alpha = 0.15f),
                                        MadrasaGold.copy(alpha = 0.85f),
                                        MadrasaGold.copy(alpha = 0.15f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // Dashboard Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(MadrasaSapphire, MadrasaPrimary)
                            )
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ড্যাশবোর্ড",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2 Columns x 14 Cards with Halo-Framed Sapphire-Teal Circular Icon + Clean Label
            val iconCircleBrush = remember {
                Brush.linearGradient(
                    colors = listOf(MadrasaSapphire, MadrasaPrimary)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(menuItems, key = { it.screen }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .testTag(item.testTag)
                            .clickable { onNavigate(item.screen) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = madrasaCardBorder(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(iconCircleBrush),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditInfoDialog) {
        var nameInput by remember(madrasaInfo.name) { mutableStateOf(madrasaInfo.name) }
        var addressInput by remember(madrasaInfo.address) { mutableStateOf(madrasaInfo.address) }
        var phoneInput by remember(madrasaInfo.phone) { mutableStateOf(madrasaInfo.phone) }

        AlertDialog(
            onDismissRequest = { showEditInfoDialog = false },
            title = {
                Text(
                    text = "মাদ্রাসার তথ্য ও লোগো সম্পাদনা",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Editable Round Logo Row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MadrasaGold.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MadrasaRoundLogo(
                                logoPath = dialogLogoPath,
                                logoVersion = logoVersion,
                                size = 64.dp,
                                borderWidth = 2.dp,
                                borderColor = MadrasaGold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "মাদ্রাসার লোগো (রাউন্ড)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "গোল ক্রপ ও হাই-রেজুলেশন (১০২৪px)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            cropFromEditDialog = true
                                            logoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .heightIn(min = 36.dp)
                                            .testTag("dialog_change_logo_button")
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("পাল্টান", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (dialogLogoPath.isNotBlank()) {
                                        TextButton(
                                            onClick = { dialogLogoPath = "" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.heightIn(min = 36.dp)
                                        ) {
                                            Text("ডিফল্ট", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("মাদ্রাসার নাম *") },
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_madrasa_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = { Text("মাদ্রাসার ঠিকানা *") },
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_madrasa_address_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("মোবাইল নাম্বার *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_madrasa_phone_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isBlank() || addressInput.isBlank() || phoneInput.isBlank()) {
                            Toast.makeText(context, "নাম, ঠিকানা ও মোবাইল নাম্বার পূরণ করুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onUpdateMadrasaInfo(nameInput, addressInput, phoneInput, dialogLogoPath)
                        Toast.makeText(context, "মাদ্রাসার তথ্য ও লোগো সংরক্ষণ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                        showEditInfoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                    modifier = Modifier.testTag("save_madrasa_info_button")
                ) {
                    Text("সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditInfoDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    pendingCropUri?.let { uri ->
        ImageCropperDialog(
            imageUri = uri,
            title = "🕌 মাদ্রাসার লোগো ক্রপ (রাউন্ড)",
            subtitle = "গোল বৃত্তের ভেতরে লোগোটি সুন্দরভাবে সেট করুন (১০২৪px হাই-রেস)",
            initialCircular = true,
            lockCircularShape = true,
            outputSizePx = 1024,
            filePrefix = "madrasa_logo",
            onCropCompleted = { savedPath ->
                pendingCropUri = null
                if (cropFromEditDialog && showEditInfoDialog) {
                    dialogLogoPath = savedPath
                } else {
                    onUpdateMadrasaLogo(savedPath)
                }
            },
            onDismiss = {
                pendingCropUri = null
            }
        )
    }
}

private val apkExportExecutor = Executors.newSingleThreadExecutor()
private val apkMainHandler = Handler(Looper.getMainLooper())

fun downloadApkDirectly(context: Context) {
    val appContext = context.applicationContext
    Toast.makeText(appContext, "APK ফাইল প্রস্তুত হচ্ছে...", Toast.LENGTH_SHORT).show()
    apkExportExecutor.execute {
        try {
            val sourceApk = File(appContext.applicationInfo.sourceDir)
            val cachedApk = File(appContext.cacheDir, "Madrasa_Management_Pro.apk")
            FileInputStream(sourceApk).use { input ->
                FileOutputStream(cachedApk).use { output ->
                    input.copyTo(output)
                }
            }

            val savedUri = PdfPrintUtil.saveFileToDownloads(
                context = appContext,
                sourceFile = cachedApk,
                displayName = "Madrasa_Management_Pro.apk",
                mimeType = "application/vnd.android.package-archive"
            )

            val shareUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                cachedApk
            )

            val toastText = if (savedUri != null) {
                "APK ফাইল সফলভাবে Downloads/MadrasaPro ফোল্ডারে সেভ হয়েছে!"
            } else {
                "APK ফাইল প্রস্তুত হয়েছে!"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, "M PRO (APK)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            apkMainHandler.post {
                Toast.makeText(appContext, toastText, Toast.LENGTH_LONG).show()
                try {
                    val chooser = Intent.createChooser(shareIntent, "APK ফাইল ওপেন বা ইনস্টল করুন").apply {
                        if (context !is android.app.Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(chooser)
                } catch (_: Exception) {
                    try {
                        val chooser = Intent.createChooser(shareIntent, "APK ফাইল ওপেন বা ইনস্টল করুন")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        appContext.startActivity(chooser)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            apkMainHandler.post {
                Toast.makeText(appContext, "APK ডাউনলোডে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
