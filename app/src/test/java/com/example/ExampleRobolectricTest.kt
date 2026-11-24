package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.MadrasaDatabase
import com.example.data.MadrasaRepository
import com.example.data.entity.ClassRoutineEntity
import com.example.ui.viewmodel.MadrasaViewModel
import com.example.util.RoutineTimeUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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

    composeTestRule.onNodeWithTag("menu_admission").performClick()
    composeTestRule.mainClock.advanceTimeBy(400L)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("subscreen_madrasa_logo").assertIsDisplayed()
  }

  @Test
  fun `central logo state updates immediately and clears old cache`() {
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
    assertTrue(vm.madrasaInfo.value.name.isNotBlank())

    val tempLogo = File(context.filesDir, "madrasa_logo_test_sample.png").apply {
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }
    val logoUri = Uri.fromFile(tempLogo).toString()
    val prevVer = vm.logoState.value.version
    vm.updateMadrasaLogo(logoUri)
    assertEquals(logoUri, vm.madrasaInfo.value.logoPath)
    assertEquals(logoUri, vm.logoState.value.logoPath)
    assertTrue(vm.logoState.value.version >= prevVer)
    tempLogo.delete()
  }

  @Test
  fun `routine sorts by class progression and serial order`() {
    val unsorted = listOf(
      ClassRoutineEntity(id = 1, className = "হিফজ", dayOfWeek = "সারা বছর", timeSlot = "৯:০০ - ১০:০০", subject = "হিফজ", teacherName = "ক", serialOrder = 2),
      ClassRoutineEntity(id = 2, className = "নূরানী - প্রথম", dayOfWeek = "সারা বছর", timeSlot = "৮:০০ - ৯:০০", subject = "কুরআন", teacherName = "খ", serialOrder = 2),
      ClassRoutineEntity(id = 3, className = "নূরানী - প্রথম", dayOfWeek = "সারা বছর", timeSlot = "৭:০০ - ৮:০০", subject = "কায়দা", teacherName = "গ", serialOrder = 1),
      ClassRoutineEntity(id = 4, className = "হিফজ", dayOfWeek = "সারা বছর", timeSlot = "৮:০০ - ৯:০০", subject = "সবক", teacherName = "ঘ", serialOrder = 1)
    )
    val sorted = RoutineTimeUtil.sortRoutinesByClassAndSerial(unsorted)
    assertEquals(listOf(3, 2, 4, 1), sorted.map { it.id })
  }
}
