package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

data class SubjectMark(
    val name: String,
    val mark: Double
)

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val father: String,
    val phone: String,
    val cls: String,
    val fee: Double,
    val photoUri: String = "",
    val roll: String = "",
    val electricBill: Double = 0.0
)

@Entity(tableName = "fees")
data class FeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val amount: Double,
    val type: String,
    val month: String,
    val date: String
)

@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val studentId: Int,
    val status: String
)

@Entity(tableName = "results")
data class ResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val exam: String,
    val subjectsJson: String,
    val total: Double,
    val avg: Double,
    val date: String
) {
    @androidx.room.Ignore
    @Transient
    private var cachedSubjectList: List<SubjectMark>? = null

    fun getSubjectList(): List<SubjectMark> {
        cachedSubjectList?.let { return it }
        val list = mutableListOf<SubjectMark>()
        try {
            val arr = JSONArray(subjectsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SubjectMark(
                        name = obj.optString("name", "বিষয় ${i + 1}"),
                        mark = obj.optDouble("mark", 0.0)
                    )
                )
            }
        } catch (_: Exception) {
        }
        cachedSubjectList = list
        return list
    }

    fun isAllPassed(passMark: Double = 33.0): Boolean {
        val subs = getSubjectList()
        return subs.isNotEmpty() && subs.all { it.mark >= passMark } && avg >= passMark
    }

    companion object {
        fun serializeSubjects(subjects: List<SubjectMark>): String {
            val arr = JSONArray()
            subjects.forEach { s ->
                val obj = JSONObject().apply {
                    put("name", s.name)
                    put("mark", s.mark)
                }
                arr.put(obj)
            }
            return arr.toString()
        }
    }
}

@Entity(tableName = "teachers")
data class TeacherEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val subject: String,
    val salary: Double,
    val address: String = "",
    val phone: String = "",
    val photoUri: String = "",
    val joinDate: String = ""
)

@Entity(tableName = "teacher_payments")
data class TeacherPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val teacherId: Int,
    val amount: Double,
    val month: String,
    val year: String,
    val date: String,
    val note: String = ""
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,
    val desc: String,
    val amount: Double,
    val date: String = ""
)

@Entity(tableName = "notices")
data class NoticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val date: String
)

@Entity(tableName = "question_papers")
data class QuestionPaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val examName: String,
    val className: String,
    val subject: String,
    val fullMarks: String,
    val duration: String,
    val instructions: String,
    val questionsText: String,
    val date: String
)

@Entity(tableName = "class_routines")
data class ClassRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val className: String,
    val dayOfWeek: String,
    val timeSlot: String,
    val subject: String,
    val teacherName: String,
    val duration: String = "",
    val serialOrder: Int = 0
)
