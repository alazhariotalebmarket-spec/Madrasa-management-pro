package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MadrasaDatabase
import com.example.data.MadrasaInfo
import com.example.data.MadrasaInfoStore
import com.example.data.MadrasaRepository
import com.example.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MadrasaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MadrasaRepository

    init {
        val db = MadrasaDatabase.getDatabase(application)
        repository = MadrasaRepository(
            db,
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

    private val _madrasaInfo = MutableStateFlow(MadrasaInfoStore.getInfo(application))
    val madrasaInfo: StateFlow<MadrasaInfo> = _madrasaInfo.asStateFlow()

    fun updateMadrasaInfo(name: String, address: String, phone: String) {
        _madrasaInfo.value = MadrasaInfoStore.saveInfo(getApplication(), name, address, phone)
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
        if (attendanceMap.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val entries = attendanceMap.map { (studentId, status) ->
                AttendanceEntity(date = date, studentId = studentId, status = status)
            }
            repository.insertAllAttendance(entries)
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
        teacherName: String,
        duration: String = ""
    ) {
        viewModelScope.launch {
            repository.insertClassRoutine(
                ClassRoutineEntity(
                    className = className,
                    dayOfWeek = dayOfWeek,
                    timeSlot = timeSlot,
                    subject = subject,
                    teacherName = teacherName,
                    duration = duration
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

    suspend fun restoreFromBackupJson(jsonStr: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val root = org.json.JSONObject(jsonStr)
            var restoredCount = 0

            val instName = root.optString("institution", "")
            val instAddr = root.optString("institutionAddress", "")
            val instPhone = root.optString("institutionPhone", "")
            if (instName.isNotBlank()) {
                val savedInfo = MadrasaInfoStore.saveInfo(
                    getApplication(),
                    name = instName,
                    address = instAddr.ifBlank { madrasaInfo.value.address },
                    phone = instPhone.ifBlank { madrasaInfo.value.phone }
                )
                _madrasaInfo.value = savedInfo
            }

            val studentsList = buildList {
                val studentsArr = root.optJSONArray("students")
                if (studentsArr != null) {
                    for (i in 0 until studentsArr.length()) {
                        val o = studentsArr.getJSONObject(i)
                        add(
                            StudentEntity(
                                id = o.optInt("id", 0),
                                name = o.optString("name", ""),
                                father = o.optString("father", ""),
                                phone = o.optString("phone", ""),
                                cls = o.optString("cls", ""),
                                fee = o.optDouble("fee", 0.0),
                                photoUri = o.optString("photoUri", ""),
                                roll = o.optString("roll", ""),
                                electricBill = o.optDouble("electricBill", 0.0)
                            )
                        )
                    }
                }
            }
            restoredCount += studentsList.size

            val feesList = buildList {
                val feesArr = root.optJSONArray("fees")
                if (feesArr != null) {
                    for (i in 0 until feesArr.length()) {
                        val o = feesArr.getJSONObject(i)
                        add(
                            FeeEntity(
                                id = o.optInt("id", 0),
                                studentId = o.optInt("studentId", 0),
                                amount = o.optDouble("amount", 0.0),
                                type = o.optString("type", "মাসিক বেতন"),
                                month = o.optString("month", ""),
                                date = o.optString("date", "")
                            )
                        )
                    }
                }
            }
            restoredCount += feesList.size

            val resultsList = buildList {
                val resultsArr = root.optJSONArray("results")
                if (resultsArr != null) {
                    for (i in 0 until resultsArr.length()) {
                        val o = resultsArr.getJSONObject(i)
                        val subsArr = o.optJSONArray("subjects")
                        val subList = mutableListOf<SubjectMark>()
                        if (subsArr != null) {
                            for (j in 0 until subsArr.length()) {
                                val sObj = subsArr.getJSONObject(j)
                                subList.add(
                                    SubjectMark(
                                        name = sObj.optString("name", ""),
                                        mark = sObj.optDouble("mark", 0.0)
                                    )
                                )
                            }
                        }
                        add(
                            ResultEntity(
                                id = o.optInt("id", 0),
                                studentId = o.optInt("studentId", 0),
                                exam = o.optString("exam", ""),
                                subjectsJson = ResultEntity.serializeSubjects(subList),
                                total = o.optDouble("total", 0.0),
                                avg = o.optDouble("avg", 0.0),
                                date = o.optString("date", "")
                            )
                        )
                    }
                }
            }
            restoredCount += resultsList.size

            val teachersList = buildList {
                val teachersArr = root.optJSONArray("teachers")
                if (teachersArr != null) {
                    for (i in 0 until teachersArr.length()) {
                        val o = teachersArr.getJSONObject(i)
                        add(
                            TeacherEntity(
                                id = o.optInt("id", 0),
                                name = o.optString("name", ""),
                                subject = o.optString("subject", ""),
                                salary = o.optDouble("salary", 0.0),
                                address = o.optString("address", ""),
                                phone = o.optString("phone", ""),
                                photoUri = o.optString("photoUri", ""),
                                joinDate = o.optString("joinDate", "")
                            )
                        )
                    }
                }
            }
            restoredCount += teachersList.size

            val paymentsList = buildList {
                val tpArr = root.optJSONArray("teacherPayments")
                if (tpArr != null) {
                    for (i in 0 until tpArr.length()) {
                        val o = tpArr.getJSONObject(i)
                        add(
                            TeacherPaymentEntity(
                                id = o.optInt("id", 0),
                                teacherId = o.optInt("teacherId", 0),
                                amount = o.optDouble("amount", 0.0),
                                month = o.optString("month", ""),
                                year = o.optString("year", ""),
                                date = o.optString("date", ""),
                                note = o.optString("note", "")
                            )
                        )
                    }
                }
            }
            restoredCount += paymentsList.size

            val expensesList = buildList {
                val expArr = root.optJSONArray("expenses")
                if (expArr != null) {
                    for (i in 0 until expArr.length()) {
                        val o = expArr.getJSONObject(i)
                        add(
                            ExpenseEntity(
                                id = o.optInt("id", 0),
                                type = o.optString("type", "খরচ"),
                                desc = o.optString("desc", ""),
                                amount = o.optDouble("amount", 0.0),
                                date = o.optString("date", "")
                            )
                        )
                    }
                }
            }
            restoredCount += expensesList.size

            val noticesList = buildList {
                val noticesArr = root.optJSONArray("notices")
                if (noticesArr != null) {
                    for (i in 0 until noticesArr.length()) {
                        val o = noticesArr.getJSONObject(i)
                        add(
                            NoticeEntity(
                                id = o.optInt("id", 0),
                                title = o.optString("title", ""),
                                description = o.optString("description", ""),
                                date = o.optString("date", "")
                            )
                        )
                    }
                }
            }
            restoredCount += noticesList.size

            repository.runInTransaction {
                if (studentsList.isNotEmpty()) repository.insertAllStudents(studentsList)
                if (feesList.isNotEmpty()) repository.insertAllFees(feesList)
                if (resultsList.isNotEmpty()) repository.insertAllResults(resultsList)
                if (teachersList.isNotEmpty()) repository.insertAllTeachers(teachersList)
                if (paymentsList.isNotEmpty()) repository.insertAllTeacherPayments(paymentsList)
                if (expensesList.isNotEmpty()) repository.insertAllExpenses(expensesList)
                if (noticesList.isNotEmpty()) repository.insertAllNotices(noticesList)
            }

            Result.success(restoredCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

