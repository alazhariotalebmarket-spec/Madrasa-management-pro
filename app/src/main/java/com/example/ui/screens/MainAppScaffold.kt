package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.IslamicPatternOverlay
import com.example.ui.components.MadrasaHeaderGradient
import com.example.ui.components.MadrasaRoundLogo
import com.example.ui.components.SubScreenTopBar
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaSky
import com.example.ui.viewmodel.MadrasaViewModel
import kotlinx.coroutines.launch

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private data class DrawerNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private fun subScreenMeta(route: String): Pair<String, String> = when (route) {
    "students" -> "ছাত্র ভর্তি ও তালিকা" to "নতুন ভর্তি, তথ্য সম্পাদনা ও তালিকা"
    "attendance" -> "দৈনিক হাজিরা" to "ছাত্র-ছাত্রীদের উপস্থিতি ও ছুটি"
    "report" -> "কল ও এসএমএস" to "বকেয়া বেতন রিমাইন্ডার ও যোগাযোগ"
    "fees" -> "বেতন ও বিদ্যুৎ বিল" to "ফি আদায়, রসিদ ও মাসিক/বাৎসরিক রিপোর্ট"
    "question_paper" -> "প্রশ্নপত্র" to "প্রশ্ন তৈরি ও A4 প্রিন্ট PDF"
    "results" -> "পরীক্ষার রেজাল্ট" to "নম্বর এন্ট্রি, মার্কশিট ও PDF"
    "id_card" -> "আইডি কার্ড" to "ছাত্র ও শিক্ষকের CR80 প্রিন্ট কার্ড"
    "admit_card" -> "প্রবেশ পত্র" to "পরীক্ষার এডমিট কার্ড ও PDF"
    "routine" -> "বাৎসরিক ক্লাস রুটিন" to "বার্ষিক পাঠদান সময়সূচী ও পিরিয়ড তালিকা"
    "teachers" -> "শিক্ষকের বেতন ও তথ্য" to "শিক্ষক নিবন্ধন, বেতন প্রদান ও রিপোর্ট"
    "accounts" -> "আয় ব্যয় হিসাব" to "সাধারণ খরচ, মুহতামিম জমা ও আর্থিক বিবরণী"
    "expenses" -> "খরচ ও মুহতামিম জমা" to "ব্যয় এন্ট্রি ও PDF রিপোর্ট"
    "financial" -> "আর্থিক বিবরণী" to "মাদ্রাসার আয়-ব্যয়ের পূর্ণাঙ্গ হিসাব"
    "merit_list" -> "মেধাতালিকা" to "পরীক্ষার মেধাক্রম ও PDF রিপোর্ট"
    "notice" -> "নোটিশ বোর্ড" to "ঘোষণা প্রকাশ ও হাই-রেজুলেশন ফটো"
    "backup" -> "অফলাইন ব্যাকআপ ও রিস্টোর" to "ফোনে JSON ব্যাকআপ সেভ, শেয়ার ও রিস্টোর"
    "developer" -> "ডেভেলপার" to "প্রস্তুতকারকের তথ্য ও APK ডাউনলোড"
    else -> "M PRO" to ""
}

private fun subScreenIcon(route: String): ImageVector = when (route) {
    "students" -> Icons.Default.PersonAdd
    "attendance" -> Icons.AutoMirrored.Filled.FactCheck
    "report" -> Icons.Default.Call
    "fees" -> Icons.Default.Payments
    "question_paper" -> Icons.Default.Description
    "results" -> Icons.Default.School
    "id_card" -> Icons.Default.Badge
    "admit_card" -> Icons.Default.AssignmentInd
    "routine" -> Icons.Default.CalendarMonth
    "teachers" -> Icons.Default.SupervisorAccount
    "accounts", "expenses", "financial" -> Icons.Default.AccountBalanceWallet
    "merit_list" -> Icons.Default.EmojiEvents
    "notice" -> Icons.Default.NotificationsActive
    "backup" -> Icons.Default.Backup
    "developer" -> Icons.Default.Code
    else -> Icons.Default.Home
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: MadrasaViewModel
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentTab by rememberSaveable { mutableStateOf("dashboard") }

    // Intercept hardware/system Back button: close drawer first, otherwise return to Dashboard
    BackHandler(enabled = drawerState.isOpen || currentTab != "dashboard") {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            currentTab = "dashboard"
        }
    }

    val students by viewModel.students.collectAsStateWithLifecycle()
    val fees by viewModel.fees.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val teachers by viewModel.teachers.collectAsStateWithLifecycle()
    val teacherPayments by viewModel.teacherPayments.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val questionPapers by viewModel.questionPapers.collectAsStateWithLifecycle()
    val classRoutines by viewModel.classRoutines.collectAsStateWithLifecycle()
    val madrasaInfo by viewModel.madrasaInfo.collectAsStateWithLifecycle()
    val logoState by viewModel.logoState.collectAsStateWithLifecycle()

    val bottomTabs = remember {
        listOf(
            BottomNavTab("dashboard", "হোম", Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavTab("students", "ছাত্র", Icons.Filled.Person, Icons.Outlined.Person),
            BottomNavTab("attendance", "হাজিরা", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
            BottomNavTab("fees", "বেতন", Icons.Filled.Payments, Icons.Outlined.Payments),
            BottomNavTab("results", "রেজাল্ট", Icons.Filled.School, Icons.Outlined.School),
            BottomNavTab("notice", "নোটিশ", Icons.Filled.Notifications, Icons.Outlined.Notifications)
        )
    }

    val drawerItems = remember {
        listOf(
            DrawerNavItem("dashboard", "ড্যাশবোর্ড / হোম", Icons.Default.Home),
            DrawerNavItem("students", "ছাত্র ভর্তি ও তালিকা", Icons.Default.Person),
            DrawerNavItem("teachers", "শিক্ষক ও বেতন", Icons.Default.SupervisorAccount),
            DrawerNavItem("routine", "বাৎসরিক ক্লাস রুটিন", Icons.Default.CalendarMonth),
            DrawerNavItem("attendance", "দৈনিক হাজিরা", Icons.Default.CheckCircle),
            DrawerNavItem("fees", "বেতন ও বিদ্যুৎ বিল", Icons.Default.Payments),
            DrawerNavItem("results", "পরীক্ষার রেজাল্ট", Icons.Default.School),
            DrawerNavItem("accounts", "আয়-ব্যয় ও হিসাব", Icons.Default.AccountBalanceWallet),
            DrawerNavItem("backup", "অফলাইন ব্যাকআপ ও রিস্টোর", Icons.Default.Backup),
            DrawerNavItem("developer", "ডেভেলপার ও APK", Icons.Default.Code)
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 310.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Drawer Institution Header (100% Offline)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MadrasaHeaderGradient)
                ) {
                    IslamicPatternOverlay(modifier = Modifier.matchParentSize())

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 20.dp)
                    ) {
                        MadrasaRoundLogo(
                            logoPath = logoState.logoPath,
                            logoVersion = logoState.version,
                            size = 64.dp,
                            borderWidth = 2.dp,
                            borderColor = MadrasaGold,
                            contentDescription = madrasaInfo.name
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = madrasaInfo.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = madrasaInfo.address,
                            color = MadrasaSky,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, MadrasaGold.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "M PRO • অফলাইন ম্যানেজমেন্ট",
                                color = MadrasaGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp)
                ) {
                    drawerItems.forEach { item ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (currentTab == item.route) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            selected = currentTab == item.route,
                            onClick = {
                                currentTab = item.route
                                scope.launch { drawerState.close() }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null
                                )
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (currentTab != "dashboard") {
                    val (title, subtitle) = subScreenMeta(currentTab)
                    SubScreenTopBar(
                        title = title,
                        subtitle = subtitle,
                        logoPath = logoState.logoPath,
                        logoVersion = logoState.version,
                        screenIcon = subScreenIcon(currentTab),
                        onBack = { currentTab = "dashboard" }
                    )
                }
            },
                bottomBar = {
                    Column {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            bottomTabs.forEach { tab ->
                                val isSelected = currentTab == tab.route
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = tab.route },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) +
                                slideInVertically(animationSpec = tween(220)) { it / 28 }) togetherWith
                                fadeOut(animationSpec = tween(150))
                        },
                        label = "screen_transition"
                    ) { targetTab ->
                        when (targetTab) {
                            "dashboard" -> DashboardScreen(
                                students = students,
                                fees = fees,
                                madrasaInfo = madrasaInfo,
                                logoVersion = logoState.version,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onUpdateMadrasaInfo = { name, address, phone, logoPath ->
                                    viewModel.updateMadrasaInfo(name, address, phone, logoPath)
                                },
                                onUpdateMadrasaLogo = { logoPath ->
                                    viewModel.updateMadrasaLogo(logoPath)
                                },
                                onNavigate = { screen -> currentTab = screen }
                            )
                            "students" -> StudentsScreen(
                                students = students,
                                madrasaInfo = madrasaInfo,
                                logoVersion = logoState.version,
                                onAddStudent = { name, father, phone, cls, fee, photo, roll, electricBill ->
                                    viewModel.addStudent(name, father, phone, cls, fee, photo, roll, electricBill)
                                },
                                onUpdateStudent = { student ->
                                    viewModel.updateStudent(student)
                                },
                                onDeleteStudent = { id ->
                                    viewModel.deleteStudent(id)
                                }
                            )
                            "attendance" -> AttendanceScreen(
                                students = students,
                                getAttendanceForDate = { date -> viewModel.getAttendanceForDate(date) },
                                onSaveAttendance = { date, map ->
                                    viewModel.saveAttendance(date, map)
                                }
                            )
                            "fees" -> FeesScreen(
                                students = students,
                                fees = fees,
                                madrasaInfo = madrasaInfo,
                                logoVersion = logoState.version,
                                onAddFee = { studentId, amount, type, month ->
                                    viewModel.addFee(studentId, amount, type, month)
                                },
                                onDeleteFee = { id ->
                                    viewModel.deleteFee(id)
                                }
                            )
                            "report" -> ReportScreen(
                                students = students,
                                fees = fees,
                                madrasaInfo = madrasaInfo
                            )
                            "results" -> ResultsScreen(
                                students = students,
                                results = results,
                                madrasaInfo = madrasaInfo,
                                logoVersion = logoState.version,
                                onSaveResult = { id, sId, exam, subjects, existingDate ->
                                    viewModel.saveResult(id, sId, exam, subjects, existingDate)
                                },
                                onDeleteResult = { id ->
                                    viewModel.deleteResult(id)
                                },
                                gradeCalculator = { avg -> viewModel.calculateGrade(avg) }
                            )
                            "teachers" -> TeachersScreen(
                                teachers = teachers,
                                teacherPayments = teacherPayments,
                                onAddTeacher = { name, sub, sal, addr, ph, photo, joinDate ->
                                    viewModel.addTeacher(name, sub, sal, addr, ph, photo, joinDate)
                                },
                                onUpdateTeacher = { teacher ->
                                    viewModel.updateTeacher(teacher)
                                },
                                onDeleteTeacher = { id ->
                                    viewModel.deleteTeacher(id)
                                },
                                onAddPayment = { teacherId, amount, month, year, note ->
                                    viewModel.addTeacherPayment(teacherId, amount, month, year, note)
                                },
                                onDeletePayment = { id ->
                                    viewModel.deleteTeacherPayment(id)
                                }
                            )
                            "accounts" -> AccountsHubScreen(
                                fees = fees,
                                teachers = teachers,
                                teacherPayments = teacherPayments,
                                expenses = expenses,
                                onAddExpense = { type, desc, amt, date ->
                                    viewModel.addExpense(type, desc, amt, date)
                                },
                                onDeleteExpense = { id ->
                                    viewModel.deleteExpense(id)
                                }
                            )
                            "expenses" -> ExpensesScreen(
                                expenses = expenses,
                                fees = fees,
                                onAddExpense = { type, desc, amt, date ->
                                    viewModel.addExpense(type, desc, amt, date)
                                },
                                onDeleteExpense = { id ->
                                    viewModel.deleteExpense(id)
                                }
                            )
                            "financial" -> FinancialScreen(
                                fees = fees,
                                teachers = teachers,
                                teacherPayments = teacherPayments,
                                expenses = expenses
                            )
                            "id_card" -> IdCardScreen(
                                students = students,
                                teachers = teachers,
                                madrasaInfo = madrasaInfo,
                                logoVersion = logoState.version
                            )
                            "admit_card" -> AdmitCardScreen(
                                students = students
                            )
                            "question_paper" -> QuestionPaperScreen(
                                questionPapers = questionPapers,
                                onAddPaper = { exam, cls, sub, marks, dur, inst, qs ->
                                    viewModel.addQuestionPaper(exam, cls, sub, marks, dur, inst, qs)
                                },
                                onDeletePaper = { id ->
                                    viewModel.deleteQuestionPaper(id)
                                }
                            )
                            "routine" -> ClassRoutineScreen(
                                routines = classRoutines,
                                onAddRoutine = { cls, day, slot, sub, teacher, dur, serial ->
                                    viewModel.addClassRoutine(cls, day, slot, sub, teacher, dur, serial)
                                },
                                onAddRoutinesForClasses = { classes, day, slot, sub, teacher, dur, serial ->
                                    viewModel.addClassRoutinesForClasses(classes, day, slot, sub, teacher, dur, serial)
                                },
                                onUpdateRoutine = { updated ->
                                    viewModel.updateClassRoutine(updated)
                                },
                                onSwapRoutineSerial = { classList, fromIdx, toIdx ->
                                    viewModel.swapRoutineSerial(classList, fromIdx, toIdx)
                                },
                                onDeleteRoutine = { id ->
                                    viewModel.deleteClassRoutine(id)
                                }
                            )
                            "merit_list" -> MeritListScreen(
                                students = students,
                                results = results,
                                gradeCalculator = { avg -> viewModel.calculateGrade(avg) }
                            )
                            "notice" -> NoticeScreen(
                                notices = notices,
                                madrasaInfo = madrasaInfo,
                                logoVersion = logoState.version,
                                onAddNotice = { title, desc ->
                                    viewModel.addNotice(title, desc)
                                },
                                onDeleteNotice = { id ->
                                    viewModel.deleteNotice(id)
                                }
                            )
                            "backup" -> BackupScreen(
                                viewModel = viewModel
                            )
                            "developer" -> DeveloperScreen(
                                onBack = { currentTab = "dashboard" }
                            )
                        }
                    }
                }
            }
        }
}
