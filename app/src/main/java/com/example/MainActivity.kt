package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MadrasaSplashOverlay
import com.example.ui.screens.MainAppScaffold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MadrasaViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: MadrasaViewModel = viewModel()
        val madrasaInfo by viewModel.madrasaInfo.collectAsState()
        var showSplash by rememberSaveable { mutableStateOf(true) }

        Box(modifier = Modifier.fillMaxSize()) {
          MainAppScaffold(viewModel = viewModel)

          AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(420)) +
              scaleOut(targetScale = 1.06f, animationSpec = tween(420))
          ) {
            MadrasaSplashOverlay(
              madrasaInfo = madrasaInfo,
              onFinished = { showSplash = false }
            )
          }
        }
      }
    }
  }
}
