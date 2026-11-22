package com.example.util

import com.example.data.entity.ResultEntity

const val EXAM_FIRST_TERM = "১ম সাময়িক"
const val EXAM_SECOND_TERM = "২য় সাময়িক"
const val EXAM_ANNUAL = "বার্ষিক পরীক্ষা"
const val EXAM_COMBINED_THREE = "তিন পরীক্ষার সমন্বিত রেজাল্ট"
const val EXAM_MONTHLY = "মাসিক পরীক্ষা"

data class CombinedSubjectRow(
    val name: String,
    val term1Mark: Double?,
    val term2Mark: Double?,
    val annualMark: Double?,
    val total: Double,
    val average: Double,
    val passed: Boolean
)

data class CombinedStudentResult(
    val studentId: Int,
    val exam1: ResultEntity?,
    val exam2: ResultEntity?,
    val exam3: ResultEntity?,
    val examCount: Int,
    val subjectRows: List<CombinedSubjectRow>,
    val grandTotal: Double,
    val maxPossible: Int,
    val overallAvg: Double,
    val isOverallPass: Boolean
)

object CombinedResultHelper {
    fun isFirstTerm(exam: String): Boolean = exam.contains("১ম")
    fun isSecondTerm(exam: String): Boolean = exam.contains("২য়") || exam.contains("দ্বিতীয়")
    fun isAnnual(exam: String): Boolean = exam.contains("বার্ষিক")

    fun computeForStudent(
        studentId: Int,
        allResults: List<ResultEntity>
    ): CombinedStudentResult? {
        val studentResults = allResults.filter { it.studentId == studentId }
        val exam1 = studentResults.lastOrNull { isFirstTerm(it.exam) }
        val exam2 = studentResults.lastOrNull { isSecondTerm(it.exam) }
        val exam3 = studentResults.lastOrNull { isAnnual(it.exam) }

        val presentExams = listOfNotNull(exam1, exam2, exam3)
        if (presentExams.isEmpty()) return null

        val subjectNames = linkedSetOf<String>()
        presentExams.forEach { res ->
            res.getSubjectList().forEach { subjectNames.add(it.name) }
        }
        if (subjectNames.isEmpty()) return null

        var grandTotal = 0.0
        var allSubjectsPassed = true

        val rows = subjectNames.map { subName ->
            val m1 = exam1?.getSubjectList()?.find { it.name == subName }?.mark
            val m2 = exam2?.getSubjectList()?.find { it.name == subName }?.mark
            val m3 = exam3?.getSubjectList()?.find { it.name == subName }?.mark

            val subSum = (m1 ?: 0.0) + (m2 ?: 0.0) + (m3 ?: 0.0)
            val presentForSub = listOfNotNull(m1, m2, m3).size.coerceAtLeast(1)
            val subAvg = subSum / presentForSub
            grandTotal += subSum
            val subPassed = subAvg >= 33.0
            if (!subPassed) allSubjectsPassed = false

            CombinedSubjectRow(
                name = subName,
                term1Mark = m1,
                term2Mark = m2,
                annualMark = m3,
                total = subSum,
                average = subAvg,
                passed = subPassed
            )
        }

        val maxPossible = rows.sumOf { row ->
            listOfNotNull(row.term1Mark, row.term2Mark, row.annualMark).size * 100
        }.coerceAtLeast(100)
        val overallAvg = (grandTotal / maxPossible) * 100.0
        val isPass = allSubjectsPassed && overallAvg >= 33.0

        return CombinedStudentResult(
            studentId = studentId,
            exam1 = exam1,
            exam2 = exam2,
            exam3 = exam3,
            examCount = presentExams.size,
            subjectRows = rows,
            grandTotal = grandTotal,
            maxPossible = maxPossible,
            overallAvg = overallAvg,
            isOverallPass = isPass
        )
    }
}
