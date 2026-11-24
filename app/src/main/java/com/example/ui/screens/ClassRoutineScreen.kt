package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ClassRoutineEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.ThreeDotDeleteMenu
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaGold
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaSapphire
import com.example.util.PdfPrintUtil
import com.example.util.RoutineTimeUtil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClassRoutineScreen(
    routines: List<ClassRoutineEntity>,
    onAddRoutine: (String, String, String, String, String, String, Int) -> Unit,
    onAddRoutinesForClasses: (List<String>, String, String, String, String, String, Int) -> Unit = { classes, day, slot, sub, teacher, dur, serial ->
        classes.forEach { cls -> onAddRoutine(cls, day, slot, sub, teacher, dur, serial) }
    },
    onUpdateRoutine: (ClassRoutineEntity) -> Unit = {},
    onSwapRoutineSerial: (List<ClassRoutineEntity>, Int, Int) -> Unit = { _, _, _ -> },
    onDeleteRoutine: (Int) -> Unit
) {
    val context = LocalContext.current
    val sessionPresets = remember {
        listOf(
            "সারা বছর (শনি–বৃহঃ)",
            "২০২৬ শিক্ষাবর্ষ",
            "২০২৭ শিক্ষাবর্ষ",
            "শনিবার",
            "রবিবার",
            "সোমবার",
            "মঙ্গলবার",
            "বুধবার",
            "বৃহস্পতিবার"
        )
    }
    val classPresets = remember {
        listOf(
            "নূরানী - শিশু",
            "নূরানী - প্রথম",
            "নূরানী - দ্বিতীয়",
            "নূরানী - তৃতীয়",
            "নাজেরা",
            "হিফজ",
            "কিতাব বিভাগ",
            "সকল শ্রেণী (সম্মিলিত)"
        )
    }
    val batchSelectableClasses = remember {
        listOf(
            "নূরানী - শিশু",
            "নূরানী - প্রথম",
            "নূরানী - দ্বিতীয়",
            "নূরানী - তৃতীয়",
            "নাজেরা",
            "হিফজ",
            "কিতাব বিভাগ"
        )
    }

    var editingRoutine by remember { mutableStateOf<ClassRoutineEntity?>(null) }
    var multiClassMode by remember { mutableStateOf(false) }
    var selectedBatchClasses by remember { mutableStateOf(batchSelectableClasses.toSet()) }

    var annualSession by remember { mutableStateOf("সারা বছর (শনি–বৃহঃ)") }
    var expandedSession by remember { mutableStateOf(false) }

    var className by remember { mutableStateOf("নূরানী - প্রথম") }
    var expandedClass by remember { mutableStateOf(false) }

    var serialInput by remember { mutableStateOf("1") }
    var serialManualOverride by remember { mutableStateOf(false) }

    var timeSlot by remember { mutableStateOf("সকাল ৮:০০ - ৯:০০") }
    var durationManualOverride by remember { mutableStateOf(false) }
    var totalDuration by remember {
        mutableStateOf(RoutineTimeUtil.suggestDurationLabel("সকাল ৮:০০ - ৯:০০").ifBlank { "১ ঘন্টা" })
    }
    var subject by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf("") }

    var filterClass by remember { mutableStateOf("সকল শ্রেণী (একসাথে)") }
    var expandedFilter by remember { mutableStateOf(false) }
    var combinedMasterView by remember { mutableStateOf(false) }

    // Auto-suggest next serial number for the currently selected class when adding
    LaunchedEffect(className, routines.size, editingRoutine) {
        if (editingRoutine == null && !serialManualOverride) {
            val classRoutines = routines.filter { it.className.equals(className.trim(), ignoreCase = true) }
            val nextSerial = maxOf(
                classRoutines.size + 1,
                (classRoutines.maxOfOrNull { it.serialOrder } ?: 0) + 1
            )
            serialInput = nextSerial.toString()
        }
    }

    val availableFilterClasses = remember(routines) {
        listOf("সকল শ্রেণী (একসাথে)") + (classPresets + routines.map { it.className }).filter { it.isNotBlank() }.distinct()
    }

    val sortedAllRoutines = remember(routines) {
        RoutineTimeUtil.sortRoutinesByClassAndSerial(routines)
    }

    val filteredRoutines = remember(sortedAllRoutines, filterClass) {
        if (filterClass == "সকল শ্রেণী (একসাথে)") {
            sortedAllRoutines
        } else {
            RoutineTimeUtil.sortSingleClassBySerial(
                sortedAllRoutines.filter { it.className == filterClass }
            )
        }
    }

    val groupedByClass = remember(filteredRoutines) {
        filteredRoutines.groupBy { it.className.ifBlank { "সাধারণ শ্রেণী" } }
    }

    val totalFilteredMinutes = remember(filteredRoutines) {
        filteredRoutines.sumOf { r ->
            RoutineTimeUtil.parseDurationLabelMinutes(r.duration, r.timeSlot)
        }
    }
    val totalFilteredTimeLabel = remember(totalFilteredMinutes) {
        if (totalFilteredMinutes > 0) RoutineTimeUtil.formatMinutesBengali(totalFilteredMinutes) else "-"
    }

    fun resetForm() {
        editingRoutine = null
        subject = ""
        teacherName = ""
        serialManualOverride = false
        durationManualOverride = false
        totalDuration = RoutineTimeUtil.suggestDurationLabel(timeSlot).ifBlank { totalDuration }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = madrasaCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (editingRoutine != null) "✏️ রুটিন সম্পাদনা করুন" else "📅 বাৎসরিক ক্লাস রুটিন যুক্ত করুন",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MadrasaGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "শ্রেণী ও সিরিয়াল",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (editingRoutine == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !multiClassMode,
                                onClick = { multiClassMode = false },
                                label = { Text("একটি শ্রেণী", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = multiClassMode,
                                onClick = { multiClassMode = true },
                                label = { Text("সব শ্রেণী একসাথে", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (multiClassMode && editingRoutine == null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "যেসব শ্রেণীতে যুক্ত হবে (${RoutineTimeUtil.toBengaliDigits(selectedBatchClasses.size)} টি):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = {
                                            selectedBatchClasses = if (selectedBatchClasses.size == batchSelectableClasses.size) {
                                                emptySet()
                                            } else {
                                                batchSelectableClasses.toSet()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = if (selectedBatchClasses.size == batchSelectableClasses.size) "সব বাতিল" else "সব সিলেক্ট",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    batchSelectableClasses.forEach { cls ->
                                        val isChecked = selectedBatchClasses.contains(cls)
                                        FilterChip(
                                            selected = isChecked,
                                            onClick = {
                                                selectedBatchClasses = if (isChecked) {
                                                    selectedBatchClasses - cls
                                                } else {
                                                    selectedBatchClasses + cls
                                                }
                                            },
                                            label = { Text(cls, fontSize = 11.5.sp) }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedClass,
                                onExpandedChange = { expandedClass = !expandedClass },
                                modifier = Modifier.weight(1.45f)
                            ) {
                                OutlinedTextField(
                                    value = className,
                                    onValueChange = { className = it },
                                    label = { Text("শ্রেণী / জামাত") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClass) },
                                    modifier = Modifier.menuAnchor(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedClass,
                                    onDismissRequest = { expandedClass = false }
                                ) {
                                    classPresets.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text(c) },
                                            onClick = {
                                                className = c
                                                expandedClass = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = serialInput,
                                onValueChange = { txt ->
                                    val digitsOnly = RoutineTimeUtil.toAsciiDigits(txt).filter { it.isDigit() }
                                    serialManualOverride = digitsOnly.isNotBlank()
                                    serialInput = digitsOnly
                                },
                                label = { Text("সিরিয়াল নং") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(0.75f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = expandedSession,
                            onExpandedChange = { expandedSession = !expandedSession },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = annualSession,
                                onValueChange = { annualSession = it },
                                label = { Text("শিক্ষাবর্ষ / সময়কাল") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSession) },
                                modifier = Modifier.menuAnchor(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedSession,
                                onDismissRequest = { expandedSession = false }
                            ) {
                                sessionPresets.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            annualSession = s
                                            expandedSession = false
                                        }
                                    )
                                }
                            }
                        }

                        if (multiClassMode && editingRoutine == null) {
                            OutlinedTextField(
                                value = serialInput,
                                onValueChange = { txt ->
                                    val digitsOnly = RoutineTimeUtil.toAsciiDigits(txt).filter { it.isDigit() }
                                    serialManualOverride = digitsOnly.isNotBlank()
                                    serialInput = digitsOnly
                                },
                                label = { Text("সিরিয়াল নং (ঐচ্ছিক)") },
                                placeholder = { Text("অটো") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timeSlot,
                            onValueChange = { newVal ->
                                timeSlot = newVal
                                if (!durationManualOverride) {
                                    val suggested = RoutineTimeUtil.suggestDurationLabel(newVal)
                                    if (suggested.isNotBlank()) {
                                        totalDuration = suggested
                                    }
                                }
                            },
                            label = { Text("সময় (যেমন: ৮:০০ - ৯:০০)") },
                            modifier = Modifier.weight(1.3f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = totalDuration,
                            onValueChange = { newDur ->
                                durationManualOverride = newDur.isNotBlank()
                                totalDuration = newDur
                            },
                            label = { Text("মোট সময়") },
                            placeholder = { Text("যেমন: ১ ঘন্টা") },
                            modifier = Modifier.weight(0.9f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("বিষয় / কিতাব *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = teacherName,
                            onValueChange = { teacherName = it },
                            label = { Text("শিক্ষকের নাম") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (editingRoutine != null) {
                            OutlinedButton(
                                onClick = { resetForm() },
                                modifier = Modifier
                                    .weight(0.8f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("বাতিল", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = {
                                if (subject.isBlank()) return@Button
                                val effectiveDuration = RoutineTimeUtil.resolveDurationDisplay(totalDuration, timeSlot)
                                val cleanDur = if (effectiveDuration == "-") "" else effectiveDuration
                                val cleanSession = annualSession.trim().ifBlank { "সারা বছর (শনি–বৃহঃ)" }
                                val parsedSerial = RoutineTimeUtil.toAsciiDigits(serialInput).toIntOrNull() ?: 0

                                val currentEditing = editingRoutine
                                if (currentEditing != null) {
                                    onUpdateRoutine(
                                        currentEditing.copy(
                                            className = className.trim().ifBlank { currentEditing.className },
                                            dayOfWeek = cleanSession,
                                            timeSlot = timeSlot.trim(),
                                            subject = subject.trim(),
                                            teacherName = teacherName.trim(),
                                            duration = cleanDur,
                                            serialOrder = if (parsedSerial > 0) parsedSerial else currentEditing.serialOrder
                                        )
                                    )
                                    resetForm()
                                } else if (multiClassMode) {
                                    val targets = selectedBatchClasses.toList()
                                    if (targets.isEmpty()) return@Button
                                    onAddRoutinesForClasses(
                                        targets,
                                        cleanSession,
                                        timeSlot.trim(),
                                        subject.trim(),
                                        teacherName.trim(),
                                        cleanDur,
                                        if (serialManualOverride) parsedSerial else 0
                                    )
                                    resetForm()
                                } else {
                                    onAddRoutine(
                                        className.trim().ifBlank { "নূরানী - প্রথম" },
                                        cleanSession,
                                        timeSlot.trim(),
                                        subject.trim(),
                                        teacherName.trim(),
                                        cleanDur,
                                        parsedSerial
                                    )
                                    resetForm()
                                }
                            },
                            modifier = Modifier
                                .weight(1.4f)
                                .heightIn(min = 48.dp)
                                .testTag("save_routine_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = when {
                                    editingRoutine != null -> "পরিবর্তন সেভ করুন"
                                    multiClassMode -> "নির্বাচিত সব শ্রেণীতে যুক্ত করুন"
                                    else -> "রুটিনে যুক্ত করুন"
                                },
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Filter + View Toggle + A4 PDF Export Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = madrasaCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedFilter,
                        onExpandedChange = { expandedFilter = !expandedFilter }
                    ) {
                        OutlinedTextField(
                            value = filterClass,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("শ্রেণী অনুযায়ী ফিল্টার") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFilter) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFilter,
                            onDismissRequest = { expandedFilter = false }
                        ) {
                            availableFilterClasses.forEach { fc ->
                                DropdownMenuItem(
                                    text = { Text(fc) },
                                    onClick = {
                                        filterClass = fc
                                        expandedFilter = false
                                    }
                                )
                            }
                        }
                    }

                    if (filterClass == "সকল শ্রেণী (একসাথে)" && filteredRoutines.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !combinedMasterView,
                                onClick = { combinedMasterView = false },
                                label = { Text("শ্রেণীভিত্তিক ও সিরিয়াল", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = combinedMasterView,
                                onClick = { combinedMasterView = true },
                                label = { Text("সব শ্রেণী এক টেবিলে", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (filteredRoutines.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "মোট ক্লাস: ${RoutineTimeUtil.toBengaliDigits(filteredRoutines.size)} টি",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "মোট সময়: $totalFilteredTimeLabel",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    if (filterClass == "সকল শ্রেণী (একসাথে)") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    PdfPrintUtil.generateAndShareClassRoutinePdf(
                                        context = context,
                                        classFilterTitle = "সকল শ্রেণী (শ্রেণীভিত্তিক)",
                                        routines = filteredRoutines,
                                        combinedMasterMode = false
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("download_routine_pdf_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MadrasaSapphire, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("শ্রেণীভিত্তিক A4 PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            Button(
                                onClick = {
                                    PdfPrintUtil.generateAndShareClassRoutinePdf(
                                        context = context,
                                        classFilterTitle = "সকল শ্রেণী (একসাথে)",
                                        routines = filteredRoutines,
                                        combinedMasterMode = true
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("download_master_routine_pdf_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("সব একসাথে A4 PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                PdfPrintUtil.generateAndShareClassRoutinePdf(
                                    context = context,
                                    classFilterTitle = filterClass,
                                    routines = filteredRoutines,
                                    combinedMasterMode = false
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("download_routine_pdf_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaSapphire, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$filterClass — রুটিন A4 PDF (${RoutineTimeUtil.toBengaliDigits(filteredRoutines.size)} টি)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (filteredRoutines.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.CalendarMonth,
                    title = "কোন বাৎসরিক রুটিন যুক্ত করা হয়নি",
                    subtitle = "উপরের ফর্ম থেকে একটি বা সব শ্রেণীর জন্য সিরিয়াল অনুযায়ী রুটিন যুক্ত করুন"
                )
            }
        } else if (filterClass == "সকল শ্রেণী (একসাথে)" && !combinedMasterView) {
            groupedByClass.forEach { (clsName, rawClassList) ->
                val classList = RoutineTimeUtil.sortSingleClassBySerial(rawClassList)
                val classMinutes = classList.sumOf { r ->
                    RoutineTimeUtil.parseDurationLabelMinutes(r.duration, r.timeSlot)
                }
                val classDurLabel = if (classMinutes > 0) RoutineTimeUtil.formatMinutesBengali(classMinutes) else "-"

                item(key = "header_$clsName") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "শ্রেণী: $clsName",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${RoutineTimeUtil.toBengaliDigits(classList.size)} টি • $classDurLabel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                itemsIndexed(classList, key = { _, item -> item.id }) { index, item ->
                    RoutineItemCard(
                        item = item,
                        displaySerial = if (item.serialOrder > 0) item.serialOrder else (index + 1),
                        canMoveUp = index > 0,
                        canMoveDown = index < classList.lastIndex,
                        onMoveUp = { onSwapRoutineSerial(classList, index, index - 1) },
                        onMoveDown = { onSwapRoutineSerial(classList, index, index + 1) },
                        onEdit = {
                            editingRoutine = item
                            multiClassMode = false
                            className = item.className
                            annualSession = item.dayOfWeek
                            timeSlot = item.timeSlot
                            totalDuration = item.duration.ifBlank {
                                RoutineTimeUtil.resolveDurationDisplay(item.duration, item.timeSlot)
                            }
                            durationManualOverride = item.duration.isNotBlank()
                            subject = item.subject
                            teacherName = item.teacherName
                            serialInput = (if (item.serialOrder > 0) item.serialOrder else (index + 1)).toString()
                            serialManualOverride = true
                        },
                        onDelete = { onDeleteRoutine(item.id) }
                    )
                }
            }
        } else {
            itemsIndexed(filteredRoutines, key = { _, item -> item.id }) { index, item ->
                val sameClassList = groupedByClass[item.className.ifBlank { "সাধারণ শ্রেণী" }]
                    ?.let { RoutineTimeUtil.sortSingleClassBySerial(it) }
                    ?: listOf(item)
                val idxInClass = sameClassList.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                RoutineItemCard(
                    item = item,
                    displaySerial = if (item.serialOrder > 0) item.serialOrder else (idxInClass + 1),
                    canMoveUp = idxInClass > 0,
                    canMoveDown = idxInClass < sameClassList.lastIndex,
                    onMoveUp = { onSwapRoutineSerial(sameClassList, idxInClass, idxInClass - 1) },
                    onMoveDown = { onSwapRoutineSerial(sameClassList, idxInClass, idxInClass + 1) },
                    onEdit = {
                        editingRoutine = item
                        multiClassMode = false
                        className = item.className
                        annualSession = item.dayOfWeek
                        timeSlot = item.timeSlot
                        totalDuration = item.duration.ifBlank {
                            RoutineTimeUtil.resolveDurationDisplay(item.duration, item.timeSlot)
                        }
                        durationManualOverride = item.duration.isNotBlank()
                        subject = item.subject
                        teacherName = item.teacherName
                        serialInput = (if (item.serialOrder > 0) item.serialOrder else (idxInClass + 1)).toString()
                        serialManualOverride = true
                    },
                    onDelete = { onDeleteRoutine(item.id) }
                )
            }
        }
    }
}

@Composable
private fun RoutineItemCard(
    item: ClassRoutineEntity,
    displaySerial: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val resolvedDuration = RoutineTimeUtil.resolveDurationDisplay(item.duration, item.timeSlot)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = madrasaCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (canMoveUp) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "উপরে নিন",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = RoutineTimeUtil.toBengaliDigits(displaySerial),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (canMoveDown) {
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "নিচে নিন",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.timeSlot,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (resolvedDuration != "-") {
                        Surface(
                            color = MadrasaGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = resolvedDuration,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "বিষয়: ${item.subject}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "শিক্ষক: ${item.teacherName.ifBlank { "নির্ধারিত নয়" }}",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${item.className} • ${item.dayOfWeek}",
                    fontSize = 11.5.sp,
                    color = MadrasaSapphire,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "সম্পাদনা",
                    tint = MadrasaSapphire,
                    modifier = Modifier.size(18.dp)
                )
            }

            ThreeDotDeleteMenu(
                onDelete = onDelete
            )
        }
    }
}
