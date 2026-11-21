package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.MadrasaInfo
import com.example.ui.components.IslamicPatternOverlay
import com.example.ui.components.MadrasaHeaderGradient
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaSky
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MadrasaSplashOverlay(
    madrasaInfo: MadrasaInfo,
    onFinished: () -> Unit
) {
    val emblemScale = remember { Animatable(0.55f) }
    val emblemAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    val pulseTransition = rememberInfiniteTransition(label = "splash_ring_pulse")
    val ringScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    LaunchedEffect(Unit) {
        launch {
            emblemAlpha.animateTo(1f, animationSpec = tween(durationMillis = 480))
        }
        launch {
            emblemScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        delay(280)
        textAlpha.animateTo(1f, animationSpec = tween(durationMillis = 480))
        delay(1300)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MadrasaHeaderGradient)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        IslamicPatternOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Soft outer halo ring + circular calligraphy emblem
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = MadrasaGold.copy(alpha = 0.16f),
                    border = BorderStroke(1.5.dp, MadrasaGold.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .size(196.dp)
                        .scale(emblemScale.value * ringScale)
                        .alpha(emblemAlpha.value)
                ) {}

                Surface(
                    shape = CircleShape,
                    color = Color.Black,
                    border = BorderStroke(3.dp, MadrasaGold),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .size(164.dp)
                        .scale(emblemScale.value)
                        .alpha(emblemAlpha.value)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.splash_ishaqia_emblem),
                        contentDescription = "ইসহাকিয়া মাদ্রাসা ওপেনিং লোগো",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Column(
                modifier = Modifier.alpha(textAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = madrasaInfo.name,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = madrasaInfo.address,
                    color = MadrasaSky,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MadrasaGold.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "মোবাইল: ${madrasaInfo.phone}",
                        color = MadrasaGold,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
