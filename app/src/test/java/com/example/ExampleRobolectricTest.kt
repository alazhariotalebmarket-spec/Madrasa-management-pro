package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.MadrasaDatabase
import com.example.data.MadrasaRepository
import com.example.ui.viewmodel.MadrasaViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("M PRO", appName)
  }

  @Test
  fun `app starts directly on dashboard in offline mode without crash`() {
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.mainClock.advanceTimeBy(2500L)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("topbar_backup_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dashboard_offline_status_pill").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_admission").assertIsDisplayed()
  }

  @Test
  fun `viewmodel initializes safely offline with room repository`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val roomDb = MadrasaDatabase.getDatabase(context)
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
    val vm = MadrasaViewModel(
      appContext = context,
      repository = localRepo
    )
    assertNotNull(vm)
    assertEquals("A+", vm.calculateGrade(85.0))
    assert(vm.madrasaInfo.value.name.isNotBlank())
  }
}
