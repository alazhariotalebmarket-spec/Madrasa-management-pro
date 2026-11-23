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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.MadrasaDatabase
import com.example.data.MadrasaInfoStore
import com.example.data.MadrasaRepository
import com.example.data.cloud.CloudSyncRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.screens.MadrasaSplashOverlay
import com.example.ui.screens.MainAppScaffold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MadrasaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(
          initialValue = Firebase.auth.currentUser
        )
        var showSplash by rememberSaveable { mutableStateOf(true) }
        var splashInfo by remember { mutableStateOf(MadrasaInfoStore.getInfo(applicationContext)) }

        Box(modifier = Modifier.fillMaxSize()) {
          val user = currentUser
          if (user == null) {
            AuthScreen(
              onAuthSuccess = {
                // authStateFlow will emit the signed-in FirebaseUser and transition to MainAppScaffold
              }
            )
          } else {
            val uid = user.uid
            key(uid) {
              val viewModel: MadrasaViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                  initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val dbId = app.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(dbId)
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
                    val cloudRepo = CloudSyncRepository(
                      db = firestore,
                      localRepo = localRepo,
                      context = app
                    )
                    MadrasaViewModel(
                      appContext = app,
                      repository = localRepo,
                      cloudSyncRepository = cloudRepo,
                      currentUserId = uid
                    )
                  }
                }
              )
              val madrasaInfo by viewModel.madrasaInfo.collectAsStateWithLifecycle()
              splashInfo = madrasaInfo

              MainAppScaffold(viewModel = viewModel)
            }
          }

          AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(420)) +
              scaleOut(targetScale = 1.06f, animationSpec = tween(420))
          ) {
            MadrasaSplashOverlay(
              madrasaInfo = splashInfo,
              onFinished = { showSplash = false }
            )
          }
        }
      }
    }
  }
}
