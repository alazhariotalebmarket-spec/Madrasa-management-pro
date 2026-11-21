package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.QuestionPaperEntity
import com.example.ui.components.MadrasaEmptyState
import com.example.ui.components.madrasaCardBorder
import com.example.ui.theme.MadrasaPrimary
import com.example.ui.theme.MadrasaRed
import com.example.ui.theme.MadrasaSapphire
import com.example.util.PdfPrintUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionPaperScreen(
    questionPapers: List<QuestionPaperEntity>,
    onAddPaper: (String, String, String, String, String, String, String) -> Unit,
    onDeletePaper: (Int) -> Unit
) {
    val context = LocalContext.current
    var examName by remember { mutableStateOf("১ম সাময়িক পরীক্ষা - ২০২৬") }
    var className by remember { mutableStateOf("নূরানী - প্রথম") }
    var subject by remember { mutableStateOf("কুরআন ও তাজবীদ") }
    var fullMarks by remember { mutableStateOf("১০০") }
    var duration by remember { mutableStateOf("২ ঘণ্টা ৩০ মিনিট") }
    var instructions by remember { mutableStateOf("সকল প্রশ্নের উত্তর দিতে হবে। ডানপাশের সংখ্যা প্রশ্নের পূর্ণমান জ্ঞাপক।") }
    var questionsText by remember {
        mutableStateOf(
            "১। নিচের প্রশ্নগুলোর উত্তর দাও: (১০×৫ = ৫০)\n" +
                    "ক) মাখরাজ কাকে বলে? মোট মাখরাজ কয়টি?\n" +
                    "খ) ইজহারের হরফ কয়টি ও কী কী?\n\n" +
                    "২। শূন্যস্থান পূরণ করো ও অর্থ লিখো: (২৫)"
        )
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
                    Text(
                        text = "📝 প্রশ্নপত্র তৈরি ও PDF ডাউনলোড",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = examName,
                        onValueChange = { examName = it },
                        label = { Text("পরীক্ষার নাম *") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = className,
                            onValueChange = { className = it },
                            label = { Text("শ্রেণী / জামাত *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("বিষয় *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fullMarks,
                            onValueChange = { fullMarks = it },
                            label = { Text("পূর্ণমান") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            label = { Text("সময়") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("নির্দেশনা (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = questionsText,
                        onValueChange = { questionsText = it },
                        label = { Text("প্রশ্নসমূহ লিখুন *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (examName.isBlank() || subject.isBlank() || questionsText.isBlank()) return@Button
                            onAddPaper(
                                examName.trim(),
                                className.trim(),
                                subject.trim(),
                                fullMarks.trim(),
                                duration.trim(),
                                instructions.trim(),
                                questionsText.trim()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_question_paper_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MadrasaPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("প্রশ্নপত্র সেভ করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "সংরক্ষিত প্রশ্নপত্র (${questionPapers.size} টি)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (questionPapers.isEmpty()) {
            item {
                MadrasaEmptyState(
                    icon = Icons.Default.Description,
                    title = "কোন সংরক্ষিত প্রশ্নপত্র নেই",
                    subtitle = "উপরের ফর্মে প্রশ্ন লিখে সেভ করুন ও ২-কপি A4 ল্যান্ডস্কেপ PDF তৈরি করুন"
                )
            }
        } else {
            items(questionPapers, key = { it.id }) { paper ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = madrasaCardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${paper.subject} (${paper.className})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${paper.examName} • পূর্ণমান: ${paper.fullMarks} • সময়: ${paper.duration}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeletePaper(paper.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = MadrasaRed)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = paper.questionsText,
                            fontSize = 13.sp,
                            maxLines = 4,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { PdfPrintUtil.generateAndShareQuestionPaperPdf(context, paper) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MadrasaSapphire, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("A4 ল্যান্ডস্কেপ (২ কপি) PDF ডাউনলোড", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
