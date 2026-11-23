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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.MadrasaDatabase
import com.example.data.MadrasaRepository
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
        var showSplash by rememberSaveable { mutableStateOf(true) }

        val viewModel: MadrasaViewModel = viewModel(
          factory = viewModelFactory {
            initializer {
              val app = checkNotNull(this[APPLICATION_KEY])
              val roomDb = MadrasaDatabase.getDatabase(app)
              val localRepo = MadrasaRepository(
                db = roomDb,
                studentDao = roomDb.studentDao(),
                feeDao = roomDb.feeDao(),
                attendanceDao = roomDb.attendanceDao(),
                resultDao = roomDb.resultDao(),
                teacherDao = roomDb.teacherDao(),
                teacherPaymentDao = roomDb.teacherPaymentDao(),
                expenseDao = roomDb.expenseDao(),
                noticeDao = roomDb.noticeDao(),
                questionPaperDao = roomDb.questionPaperDao(),
                classRoutineDao = roomDb.classRoutineDao()
              )
              MadrasaViewModel(
                appContext = app,
                repository = localRepo
              )
            }
          }
        )

        val madrasaInfo by viewModel.madrasaInfo.collectAsStateWithLifecycle()

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
