package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.FeeEntity
import com.example.data.entity.TeacherEntity
import com.example.data.entity.TeacherPaymentEntity
import com.example.ui.theme.MadrasaPrimary

@Composable
fun AccountsHubScreen(
    fees: List<FeeEntity>,
    teachers: List<TeacherEntity>,
    teacherPayments: List<TeacherPaymentEntity>,
    expenses: List<ExpenseEntity>,
    onAddExpense: (String, String, Double, String) -> Unit,
    onDeleteExpense: (Int) -> Unit
) {
    var selectedSection by remember { mutableStateOf(0) } // 0 = খরচ ও জমা, 1 = আর্থিক হিসাব বিবরণী

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = com.example.ui.components.madrasaCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { selectedSection = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("accounts_tab_expenses"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedSection == 0) MadrasaPrimary else Color.Transparent,
                            contentColor = if (selectedSection == 0) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text("খরচ ও জমা এন্ট্রি", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { selectedSection = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("accounts_tab_financial"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedSection == 1) MadrasaPrimary else Color.Transparent,
                            contentColor = if (selectedSection == 1) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text("আর্থিক বিবরণী", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (selectedSection == 0) {
            ExpensesScreen(
                expenses = expenses,
                fees = fees,
                onAddExpense = onAddExpense,
                onDeleteExpense = onDeleteExpense
            )
        } else {
            FinancialScreen(
                fees = fees,
                teachers = teachers,
                teacherPayments = teacherPayments,
                expenses = expenses
            )
        }
    }
}
