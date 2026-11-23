package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaPrimaryDark
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaSky
import com.example.ui.theme.MadrasaTeal
import androidx.compose.foundation.clickable
import com.example.ui.auth.UserProfileAvatar
import com.google.firebase.auth.FirebaseUser
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MadrasaRoundLogo(
    logoPath: String,
    size: Dp,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 1.5.dp,
    borderColor: Color = MadrasaGold.copy(alpha = 0.85f),
    showBorder: Boolean = true,
    backgroundColor: Color? = null,
    shadowElevation: Dp = 0.dp,
    @DrawableRes fallbackResId: Int = R.drawable.img_mpro_emblem,
    contentDescription: String = "মাদ্রাসা লোগো"
) {
    val context = LocalContext.current
    val hasValidCustomLogo = remember(logoPath) {
        if (logoPath.isBlank()) {
            false
        } else {
            try {
                val uri = Uri.parse(logoPath)
                if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
                    val f = File(uri.path ?: logoPath)
                    f.exists() && f.length() > 0L
                } else {
                    true
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    val resolvedBg = backgroundColor ?: if (hasValidCustomLogo) Color.White else Color(0xFF0C4437)

    Surface(
        shape = CircleShape,
        color = resolvedBg,
        border = if (showBorder && borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null,
        shadowElevation = shadowElevation,
        modifier = modifier.size(size)
    ) {
        val innerPadding = if (showBorder && borderWidth > 0.dp) 1.5.dp else 0.dp
        val fallbackPainter = painterResource(id = fallbackResId)
        if (hasValidCustomLogo) {
            AsyncImage(
                model = remember(logoPath) {
                    ImageRequest.Builder(context)
                        .data(Uri.parse(logoPath))
                        .crossfade(true)
                        .build()
                },
                placeholder = fallbackPainter,
                error = fallbackPainter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .clip(CircleShape)
            )
        } else {
            Image(
                painter = fallbackPainter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .clip(CircleShape)
            )
        }
    }
}

val MadrasaHeaderGradient: Brush
    get() = Brush.horizontalGradient(
        colors = listOf(
            MadrasaPrimaryDark,
            Color(0xFF0C5B6E),
            MadrasaTeal
        )
    )

@Composable
fun madrasaCardBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

@Composable
fun ThreeDotDeleteMenu(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    deleteLabel: String = "ডিলেট করুন",
    menuTestTag: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .let { base -> if (menuTestTag != null) base.testTag(menuTestTag) else base }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "আরো অপশন",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = deleteLabel,
                        color = MadrasaRed,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MadrasaRed
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

fun safeStartActivity(
    context: Context,
    intent: Intent,
    fallbackMessage: String = "এই কাজটি করার জন্য উপযুক্ত অ্যাপ পাওয়া যায়নি"
) {
    try {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun IslamicPatternOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val patternColor = Color.White.copy(alpha = 0.08f)
            val dotTint = MadrasaSky.copy(alpha = 0.12f)
            val goldTint = MadrasaGold.copy(alpha = 0.09f)
            val step = 54.dp.toPx()
            val cols = (size.width / step).toInt() + 2
            val rows = (size.height / step).toInt() + 2
            val combinedPath = Path()
            val evenDots = mutableListOf<Offset>()
            val oddDots = mutableListOf<Offset>()
            val radius = step * 0.34f
            val innerR = radius * 0.48f
            val dotRadius = 2.dp.toPx()

            for (r in 0..rows) {
                for (c in 0..cols) {
                    val cx = c * step + if (r % 2 == 0) 0f else step / 2f
                    val cy = r * step
                    for (i in 0 until 16) {
                        val angle = Math.toRadians(i * 22.5)
                        val rad = if (i % 2 == 0) radius else innerR
                        val px = cx + (rad * cos(angle)).toFloat()
                        val py = cy + (rad * sin(angle)).toFloat()
                        if (i == 0) combinedPath.moveTo(px, py) else combinedPath.lineTo(px, py)
                    }
                    combinedPath.close()
                    if ((r + c) % 2 == 0) evenDots.add(Offset(cx, cy)) else oddDots.add(Offset(cx, cy))
                }
            }
            val strokeStyle = Stroke(width = 1.15f)

            onDrawBehind {
                drawPath(combinedPath, color = patternColor, style = strokeStyle)
                evenDots.forEach { center ->
                    drawCircle(color = dotTint, radius = dotRadius, center = center)
                }
                oddDots.forEach { center ->
                    drawCircle(color = goldTint, radius = dotRadius, center = center)
                }
            }
        }
    )
}

@Composable
fun SubScreenTopBar(
    title: String,
    subtitle: String? = null,
    logoPath: String = "",
    currentUser: FirebaseUser? = null,
    onLoginClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        shadowElevation = 6.dp,
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
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("subscreen_back_button")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "পিছনে যান",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                color = MadrasaSky,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (currentUser == null && onLoginClick != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MadrasaGold,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onLoginClick() }
                                .testTag("subscreen_login_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = "লগইন",
                                    tint = Color(0xFF063D23),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Login",
                                    color = Color(0xFF063D23),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (currentUser != null && onProfileClick != null) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { onProfileClick() }
                                .testTag("subscreen_profile_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            UserProfileAvatar(
                                photoUrl = currentUser.photoUrl,
                                size = 34.dp,
                                borderWidth = 1.5.dp,
                                contentDescription = currentUser.displayName ?: "প্রোফাইল"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    MadrasaRoundLogo(
                        logoPath = logoPath,
                        size = 38.dp,
                        borderWidth = 1.5.dp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                // Subtle Warm Gold Hairline Trim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MadrasaGold.copy(alpha = 0.15f),
                                    MadrasaGold.copy(alpha = 0.75f),
                                    MadrasaGold.copy(alpha = 0.15f)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun MadrasaEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = madrasaCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
