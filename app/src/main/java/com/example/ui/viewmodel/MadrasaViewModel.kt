package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MadrasaDatabase
import com.example.data.MadrasaRepository
import com.example.data.entity.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MadrasaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MadrasaRepository

    init {
        val db = MadrasaDatabase.getDatabase(application)
        repository = MadrasaRepository(
            db.studentDao(),
            db.feeDao(),
            db.attendanceDao(),
            db.resultDao(),
            db.teacherDao(),
            db.teacherPaymentDao(),
            db.expenseDao(),
            db.noticeDao(),
            db.questionPaperDao(),
            db.classRoutineDao()
        )
    }

    val students: StateFlow<List<StudentEntity>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fees: StateFlow<List<FeeEntity>> = repository.allFees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val results: StateFlow<List<ResultEntity>> = repository.allResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val teachers: StateFlow<List<TeacherEntity>> = repository.allTeachers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val teacherPayments: StateFlow<List<TeacherPaymentEntity>> = repository.allTeacherPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notices: StateFlow<List<NoticeEntity>> = repository.allNotices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questionPapers: StateFlow<List<QuestionPaperEntity>> = repository.allQuestionPapers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val classRoutines: StateFlow<List<ClassRoutineEntity>> = repository.allClassRoutines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addStudent(
        name: String,
        father: String,
        phone: String,
        cls: String,
        fee: Double,
        photoUri: String,
        roll: String = "",
        electricBill: Double = 0.0
    ) {
        viewModelScope.launch {
            repository.insertStudent(
                StudentEntity(
                    name = name,
                    father = father,
                    phone = phone,
                    cls = cls,
                    fee = fee,
                    photoUri = photoUri,
                    roll = roll,
                    electricBill = electricBill
                )
            )
        }
    }

    fun updateStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.insertStudent(student)
        }
    }

    fun deleteStudent(id: Int) {
        viewModelScope.launch {
            repository.deleteStudent(id)
        }
    }

    fun addFee(studentId: Int, amount: Double, type: String, month: String, customDate: String? = null) {
        viewModelScope.launch {
            val dateStr = customDate ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            repository.insertFee(FeeEntity(studentId = studentId, amount = amount, type = type, month = month, date = dateStr))
        }
    }

    fun deleteFee(id: Int) {
        viewModelScope.launch {
            repository.deleteFee(id)
        }
    }

    fun saveAttendance(date: String, attendanceMap: Map<Int, String>) {
        viewModelScope.launch {
            attendanceMap.forEach { (studentId, status) ->
                repository.insertAttendance(AttendanceEntity(date = date, studentId = studentId, status = status))
            }
        }
    }

    fun saveResult(
        id: Int = 0,
        studentId: Int,
        exam: String,
        subjects: List<SubjectMark>,
        existingDate: String? = null
    ) {
        if (subjects.isEmpty()) return
        viewModelScope.launch {
            val total = subjects.sumOf { it.mark }
            val avg = total / subjects.size.toDouble()
            val dateStr = existingDate ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            repository.insertResult(
                ResultEntity(
                    id = id,
                    studentId = studentId,
                    exam = exam,
                    subjectsJson = ResultEntity.serializeSubjects(subjects),
                    total = total,
                    avg = avg,
                    date = dateStr
                )
            )
        }
    }

    fun deleteResult(id: Int) {
        viewModelScope.launch {
            repository.deleteResult(id)
        }
    }

    fun addTeacher(
        name: String,
        subject: String,
        salary: Double,
        address: String = "",
        phone: String = "",
        photoUri: String = "",
        joinDate: String = ""
    ) {
        viewModelScope.launch {
            val effectiveJoinDate = if (joinDate.isNotBlank()) joinDate else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            repository.insertTeacher(
                TeacherEntity(
                    name = name,
                    subject = subject,
                    salary = salary,
                    address = address,
                    phone = phone,
                    photoUri = photoUri,
                    joinDate = effectiveJoinDate
                )
            )
        }
    }

    fun updateTeacher(teacher: TeacherEntity) {
        viewModelScope.launch {
            repository.insertTeacher(teacher)
        }
    }

    fun deleteTeacher(id: Int) {
        viewModelScope.launch {
            repository.deleteTeacher(id)
        }
    }

    fun addTeacherPayment(
        teacherId: Int,
        amount: Double,
        month: String,
        year: String,
        note: String = "",
        date: String? = null
    ) {
        viewModelScope.launch {
            val dateStr = date ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            repository.insertTeacherPayment(
                TeacherPaymentEntity(
                    teacherId = teacherId,
                    amount = amount,
                    month = month,
                    year = year,
                    date = dateStr,
                    note = note
                )
            )
        }
    }

    fun deleteTeacherPayment(id: Int) {
        viewModelScope.launch {
            repository.deleteTeacherPayment(id)
        }
    }

    fun addExpense(type: String, desc: String, amount: Double, customDate: String? = null) {
        viewModelScope.launch {
            val dateStr = customDate ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            repository.insertExpense(ExpenseEntity(type = type, desc = desc, amount = amount, date = dateStr))
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            repository.deleteExpense(id)
        }
    }

    fun addNotice(title: String, description: String) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            repository.insertNotice(NoticeEntity(title = title, description = description, date = dateStr))
        }
    }

    fun deleteNotice(id: Int) {
        viewModelScope.launch {
            repository.deleteNotice(id)
        }
    }

    fun addQuestionPaper(
        examName: String,
        className: String,
        subject: String,
        fullMarks: String,
        duration: String,
        instructions: String,
        questionsText: String
    ) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            repository.insertQuestionPaper(
                QuestionPaperEntity(
                    examName = examName,
                    className = className,
                    subject = subject,
                    fullMarks = fullMarks,
                    duration = duration,
                    instructions = instructions,
                    questionsText = questionsText,
                    date = dateStr
                )
            )
        }
    }

    fun deleteQuestionPaper(id: Int) {
        viewModelScope.launch {
            repository.deleteQuestionPaper(id)
        }
    }

    fun addClassRoutine(
        className: String,
        dayOfWeek: String,
        timeSlot: String,
        subject: String,
        teacherName: String
    ) {
        viewModelScope.launch {
            repository.insertClassRoutine(
                ClassRoutineEntity(
                    className = className,
                    dayOfWeek = dayOfWeek,
                    timeSlot = timeSlot,
                    subject = subject,
                    teacherName = teacherName
                )
            )
        }
    }

    fun deleteClassRoutine(id: Int) {
        viewModelScope.launch {
            repository.deleteClassRoutine(id)
        }
    }

    fun calculateGrade(mark: Double): String {
        return when {
            mark >= 80 -> "A+"
            mark >= 70 -> "A"
            mark >= 60 -> "A-"
            mark >= 50 -> "B"
            mark >= 40 -> "C"
            mark >= 33 -> "D"
            else -> "F"
        }
    }
}
