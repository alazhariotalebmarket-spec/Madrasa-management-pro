package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassRoutineScreen(
    routines: List<ClassRoutineEntity>,
    onAddRoutine: (String, String, String, String, String, String) -> Unit,
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
            "কিতাব বিভাগ"
        )
    }

    var annualSession by remember { mutableStateOf("সারা বছর (শনি–বৃহঃ)") }
    var expandedSession by remember { mutableStateOf(false) }

    var className by remember { mutableStateOf("নূরানী - প্রথম") }
    var expandedClass by remember { mutableStateOf(false) }

    var timeSlot by remember { mutableStateOf("সকাল ৮:০০ - ৯:০০") }
    var durationManualOverride by remember { mutableStateOf(false) }
    var totalDuration by remember {
        mutableStateOf(RoutineTimeUtil.suggestDurationLabel("সকাল ৮:০০ - ৯:০০").ifBlank { "১ ঘন্টা" })
    }
    var subject by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf("") }

    var filterClass by remember { mutableStateOf("সকল শ্রেণী") }
    var expandedFilter by remember { mutableStateOf(false) }

    val availableFilterClasses = remember(routines) {
        listOf("সকল শ্রেণী") + (classPresets + routines.map { it.className }).distinct()
    }

    val filteredRoutines = remember(routines, filterClass) {
        if (filterClass == "সকল শ্রেণী") routines else routines.filter { it.className == filterClass }
    }

    val totalFilteredMinutes = remember(filteredRoutines) {
        filteredRoutines.sumOf { r ->
            RoutineTimeUtil.parseDurationLabelMinutes(r.duration, r.timeSlot)
        }
    }
    val totalFilteredTimeLabel = remember(totalFilteredMinutes) {
        if (totalFilteredMinutes > 0) RoutineTimeUtil.formatMinutesBengali(totalFilteredMinutes) else "-"
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
                            text = "📅 বাৎসরিক ক্লাস রুটিন যুক্ত করুন",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MadrasaGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "বাৎসরিক",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = expandedClass,
                            onExpandedChange = { expandedClass = !expandedClass },
                            modifier = Modifier.weight(1f)
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
                            label = { Text("সময় (যেমন: ৮:০০ - ৯:০০)") },
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
                            label = { Text("মোট সময়") },
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
                            label = { Text("বিষয় / কিতাব *") },
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
                    Button(
                        onClick = {
                            if (subject.isBlank()) return@Button
                            val effectiveDuration = RoutineTimeUtil.resolveDurationDisplay(totalDuration, timeSlot)
                            onAddRoutine(
                                className.trim(),
                                annualSession.trim().ifBlank { "সারা বছর (শনি–বৃহঃ)" },
                                timeSlot.trim(),
                                subject.trim(),
                                teacherName.trim(),
                                if (effectiveDuration == "-") "" else effectiveDuration
                            )
                            subject = ""
                            teacherName = ""
                            durationManualOverride = false
                            totalDuration = RoutineTimeUtil.suggestDurationLabel(timeSlot).ifBlank { totalDuration }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_routine_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("বাৎসরিক রুটিনে যুক্ত করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

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
                                    text = "মোট সময়: $totalFilteredTimeLabel",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            PdfPrintUtil.generateAndShareClassRoutinePdf(
                                context = context,
                                classFilterTitle = filterClass,
                                routines = filteredRoutines
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
                            text = "বাৎসরিক ক্লাস রুটিন PDF (${RoutineTimeUtil.toBengaliDigits(filteredRoutines.size)} টি)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (filteredRoutines.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.CalendarMonth,
                    title = "কোন বাৎসরিক রুটিন যুক্ত করা হয়নি",
                    subtitle = "উপরের ফর্ম থেকে বিষয়, সময় ও শিক্ষকের নাম দিয়ে বাৎসরিক ক্লাস রুটিন যুক্ত করুন"
                )
            }
        } else {
            itemsIndexed(filteredRoutines, key = { _, item -> item.id }) { index, item ->
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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = RoutineTimeUtil.toBengaliDigits(index + 1),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
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

                        ThreeDotDeleteMenu(
                            onDelete = { onDeleteRoutine(item.id) }
                        )
                    }
                }
            }
        }
    }
}
