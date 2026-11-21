package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.R
import com.example.data.MadrasaInfo
import com.example.data.entity.FeeEntity
import com.example.data.entity.StudentEntity
import com.example.ui.components.IslamicPatternOverlay
import com.example.ui.components.MadrasaHeaderGradient
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaPrimary
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
    onUpdateMadrasaInfo: (String, String, String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var showEditInfoDialog by remember { mutableStateOf(false) }

    val menuItems = remember {
        listOf(
            MenuOption("ভর্তি", Icons.Default.PersonAdd, "students", "menu_admission"),
            MenuOption("হাজিরা", Icons.Default.FactCheck, "attendance", "menu_attendance"),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Circular Madrasa Logo framed in Warm Gold
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(2.dp, MadrasaGold.copy(alpha = 0.88f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(66.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.madrasa_logo_round),
                                contentDescription = "ইসহাকিয়া মাদ্রাসা লোগো",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Right: Editable Madrasa Title, Address, Mobile + Edit & Backup pills
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showEditInfoDialog = true }
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
                                            .clickable { showEditInfoDialog = true }
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
                                                imageVector = Icons.Default.CloudUpload,
                                                contentDescription = "ব্যাকআপ",
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
                    text = "মাদ্রাসার তথ্য সম্পাদনা",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        onUpdateMadrasaInfo(nameInput, addressInput, phoneInput)
                        Toast.makeText(context, "মাদ্রাসার তথ্য সংরক্ষণ করা হয়েছে!", Toast.LENGTH_SHORT).show()
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
}

fun downloadApkDirectly(context: Context) {
    try {
        val sourceApk = File(context.applicationInfo.sourceDir)
        val cachedApk = File(context.cacheDir, "Madrasa_Management_Pro.apk")
        FileInputStream(sourceApk).use { input ->
            FileOutputStream(cachedApk).use { output ->
                input.copyTo(output)
            }
        }

        val savedUri = PdfPrintUtil.saveFileToDownloads(
            context = context,
            sourceFile = cachedApk,
            displayName = "Madrasa_Management_Pro.apk",
            mimeType = "application/vnd.android.package-archive"
        )

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cachedApk
        )

        val toastText = if (savedUri != null) {
            "APK ফাইল সফলভাবে Downloads/MadrasaPro ফোল্ডারে সেভ হয়েছে!"
        } else {
            "APK ফাইল প্রস্তুত হয়েছে!"
        }
        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, "মাদ্রাসা ম্যানেজমেন্ট প্রো (APK)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "APK ফাইল ওপেন বা ইনস্টল করুন"))
    } catch (e: Exception) {
        Toast.makeText(context, "APK ডাউনলোডে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
