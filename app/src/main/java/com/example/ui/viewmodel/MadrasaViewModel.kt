package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlin.random.Random

data class BackupRestoreSummary(
    val totalInFile: Int,
    val addedOrUpdatedCount: Int,
    val skippedDuplicateCount: Int,
    val isDuplicateFile: Boolean,
    val backupDate: String = "",
    val institutionName: String = ""
)

class MadrasaViewModel(
    appContext: Context,
    private val repository: MadrasaRepository
) : ViewModel() {

    private val appCtx: Context = appContext.applicationContext

    private val _madrasaInfo = MutableStateFlow(MadrasaInfoStore.getInfo(appCtx))
    val madrasaInfo: StateFlow<MadrasaInfo> = _madrasaInfo.asStateFlow()

    private fun generateUniqueLocalId(existingIds: Set<Int>): Int {
        var candidate: Int
        do {
            candidate = Random.nextInt(10_000_000, 1_999_999_999)
        } while (candidate in existingIds)
        return candidate
    }

    private fun deterministicAttendanceId(date: String, studentId: Int): Int {
        val h = "${date.trim()}_$studentId".hashCode() and 0x7FFFFFFF
        return if (h == 0) 1 else h
    }

    fun updateMadrasaInfo(name: String, address: String, phone: String, logoPath: String? = null) {
        val updated = MadrasaInfoStore.saveInfo(appCtx, name, address, phone, logoPath)
        _madrasaInfo.value = updated
        if (logoPath != null) {
            com.example.util.MadrasaLogoUtil.cleanupOldLogoFiles(appCtx, updated.logoPath)
        }
    }

    fun updateMadrasaLogo(logoPath: String) {
        val updated = MadrasaInfoStore.saveLogoPath(appCtx, logoPath)
        _madrasaInfo.value = updated
        com.example.util.MadrasaLogoUtil.cleanupOldLogoFiles(appCtx, updated.logoPath)
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

    fun getAttendanceForDate(date: String): Flow<List<AttendanceEntity>> =
        repository.getAttendance(date)

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
        viewModelScope.launch(Dispatchers.IO) {
            val id = generateUniqueLocalId(students.value.map { it.id }.toSet())
            val entity = StudentEntity(
                id = id,
                name = name,
                father = father,
                phone = phone,
                cls = cls,
                fee = fee,
                photoUri = photoUri,
                roll = roll,
                electricBill = electricBill
            )
            repository.insertStudent(entity)
        }
    }

    fun updateStudent(student: StudentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertStudent(student)
        }
    }

    fun deleteStudent(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteStudent(id)
        }
    }

    fun addFee(studentId: Int, amount: Double, type: String, month: String, customDate: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = generateUniqueLocalId(fees.value.map { it.id }.toSet())
            val dateStr = customDate ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val entity = FeeEntity(
                id = id,
                studentId = studentId,
                amount = amount,
                type = type,
                month = month,
                date = dateStr
            )
            repository.insertFee(entity)
        }
    }

    fun deleteFee(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFee(id)
        }
    }

    fun saveAttendance(date: String, attendanceMap: Map<Int, String>) {
        if (attendanceMap.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val entries = attendanceMap.map { (studentId, status) ->
                AttendanceEntity(
                    id = deterministicAttendanceId(date, studentId),
                    date = date,
                    studentId = studentId,
                    status = status
                )
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
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedId = if (id > 0) id else generateUniqueLocalId(results.value.map { it.id }.toSet())
            val total = subjects.sumOf { it.mark }
            val avg = total / subjects.size.toDouble()
            val dateStr = existingDate ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val entity = ResultEntity(
                id = resolvedId,
                studentId = studentId,
                exam = exam,
                subjectsJson = ResultEntity.serializeSubjects(subjects),
                total = total,
                avg = avg,
                date = dateStr
            )
            repository.insertResult(entity)
        }
    }

    fun deleteResult(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val id = generateUniqueLocalId(teachers.value.map { it.id }.toSet())
            val effectiveJoinDate = if (joinDate.isNotBlank()) joinDate else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val entity = TeacherEntity(
                id = id,
                name = name,
                subject = subject,
                salary = salary,
                address = address,
                phone = phone,
                photoUri = photoUri,
                joinDate = effectiveJoinDate
            )
            repository.insertTeacher(entity)
        }
    }

    fun updateTeacher(teacher: TeacherEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTeacher(teacher)
        }
    }

    fun deleteTeacher(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val id = generateUniqueLocalId(teacherPayments.value.map { it.id }.toSet())
            val dateStr = date ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val entity = TeacherPaymentEntity(
                id = id,
                teacherId = teacherId,
                amount = amount,
                month = month,
                year = year,
                date = dateStr,
                note = note
            )
            repository.insertTeacherPayment(entity)
        }
    }

    fun deleteTeacherPayment(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTeacherPayment(id)
        }
    }

    fun addExpense(type: String, desc: String, amount: Double, customDate: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = generateUniqueLocalId(expenses.value.map { it.id }.toSet())
            val dateStr = customDate ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val entity = ExpenseEntity(id = id, type = type, desc = desc, amount = amount, date = dateStr)
            repository.insertExpense(entity)
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteExpense(id)
        }
    }

    fun addNotice(title: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = generateUniqueLocalId(notices.value.map { it.id }.toSet())
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val entity = NoticeEntity(id = id, title = title, description = description, date = dateStr)
            repository.insertNotice(entity)
        }
    }

    fun deleteNotice(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val id = generateUniqueLocalId(questionPapers.value.map { it.id }.toSet())
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val entity = QuestionPaperEntity(
                id = id,
                examName = examName,
                className = className,
                subject = subject,
                fullMarks = fullMarks,
                duration = duration,
                instructions = instructions,
                questionsText = questionsText,
                date = dateStr
            )
            repository.insertQuestionPaper(entity)
        }
    }

    fun deleteQuestionPaper(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val id = generateUniqueLocalId(classRoutines.value.map { it.id }.toSet())
            val entity = ClassRoutineEntity(
                id = id,
                className = className,
                dayOfWeek = dayOfWeek,
                timeSlot = timeSlot,
                subject = subject,
                teacherName = teacherName,
                duration = duration
            )
            repository.insertClassRoutine(entity)
        }
    }

    fun deleteClassRoutine(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
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

    suspend fun restoreFromBackupJson(jsonStr: String): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        try {
            val root = org.json.JSONObject(jsonStr)
            val recognizedKeys = listOf(
                "students", "fees", "results", "teachers", "teacherPayments",
                "expenses", "notices", "questionPapers", "classRoutines", "institution"
            )
            if (recognizedKeys.none { root.has(it) }) {
                return@withContext Result.failure(Exception("এটি সঠিক M PRO ব্যাকআপ JSON ফাইল নয়"))
            }

            val instName = root.optString("institution", "")
            val instAddr = root.optString("institutionAddress", "")
            val instPhone = root.optString("institutionPhone", "")
            val backupDate = root.optString("backupDate", "")
            val instLogoBase64 = root.optString("institutionLogoBase64", "")
            val restoredLogoPath = if (instLogoBase64.isNotBlank()) {
                com.example.util.MadrasaLogoUtil.saveLogoFromBase64(appCtx, instLogoBase64)
            } else {
                ""
            }
            if (instName.isNotBlank() || restoredLogoPath.isNotBlank()) {
                val savedInfo = MadrasaInfoStore.saveInfo(
                    appCtx,
                    name = instName.ifBlank { madrasaInfo.value.name },
                    address = instAddr.ifBlank { madrasaInfo.value.address },
                    phone = instPhone.ifBlank { madrasaInfo.value.phone },
                    logoPath = restoredLogoPath.takeIf { it.isNotBlank() }
                )
                _madrasaInfo.value = savedInfo
                if (restoredLogoPath.isNotBlank()) {
                    com.example.util.MadrasaLogoUtil.cleanupOldLogoFiles(appCtx, savedInfo.logoPath)
                }
            }

            fun norm(s: String): String = s.trim().lowercase()

            val rawStudents = buildList {
                val arr = root.optJSONArray("students") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
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

            val rawTeachers = buildList {
                val arr = root.optJSONArray("teachers") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
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

            val rawFees = buildList {
                val arr = root.optJSONArray("fees") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
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

            val rawResults = buildList {
                val arr = root.optJSONArray("results") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
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

            val rawPayments = buildList {
                val arr = root.optJSONArray("teacherPayments") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
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

            val rawExpenses = buildList {
                val arr = root.optJSONArray("expenses") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
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

            val rawNotices = buildList {
                val arr = root.optJSONArray("notices") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
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

            val rawPapers = buildList {
                val arr = root.optJSONArray("questionPapers") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        QuestionPaperEntity(
                            id = o.optInt("id", 0),
                            examName = o.optString("examName", ""),
                            className = o.optString("className", ""),
                            subject = o.optString("subject", ""),
                            fullMarks = o.optString("fullMarks", ""),
                            duration = o.optString("duration", ""),
                            instructions = o.optString("instructions", ""),
                            questionsText = o.optString("questionsText", ""),
                            date = o.optString("date", "")
                        )
                    )
                }
            }

            val rawRoutines = buildList {
                val arr = root.optJSONArray("classRoutines") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ClassRoutineEntity(
                            id = o.optInt("id", 0),
                            className = o.optString("className", ""),
                            dayOfWeek = o.optString("dayOfWeek", ""),
                            timeSlot = o.optString("timeSlot", ""),
                            subject = o.optString("subject", ""),
                            teacherName = o.optString("teacherName", ""),
                            duration = o.optString("duration", "")
                        )
                    )
                }
            }

            val totalInFile = rawStudents.size + rawTeachers.size + rawFees.size + rawResults.size +
                rawPayments.size + rawExpenses.size + rawNotices.size + rawPapers.size + rawRoutines.size

            var addedOrUpdated = 0
            var skippedDuplicates = 0

            repository.runInTransaction {
                // 1. Students (with ID remap & natural key deduplication)
                val existingStudents = repository.getAllStudentsOnce()
                val stuById = existingStudents.associateBy { it.id }.toMutableMap()
                fun studentKey(s: StudentEntity) =
                    "${norm(s.name)}|${norm(s.father)}|${s.phone.trim()}|${norm(s.cls)}|${norm(s.roll)}"
                val stuByKey = existingStudents.associateBy { studentKey(it) }.toMutableMap()
                val seenStuKeys = mutableSetOf<String>()
                var nextStuId = (existingStudents.maxOfOrNull { it.id } ?: 0) + 1
                val studentIdRemap = mutableMapOf<Int, Int>()
                val studentsToSave = mutableListOf<StudentEntity>()

                for (s in rawStudents) {
                    val key = studentKey(s)
                    if (!seenStuKeys.add(key)) {
                        skippedDuplicates++
                        continue
                    }
                    val match = stuByKey[key] ?: stuById[s.id]?.takeIf { studentKey(it) == key }
                    if (match != null) {
                        if (s.id > 0) studentIdRemap[s.id] = match.id
                        val candidate = s.copy(id = match.id)
                        if (candidate == match) {
                            skippedDuplicates++
                        } else {
                            studentsToSave.add(candidate)
                            stuById[match.id] = candidate
                            stuByKey[key] = candidate
                            addedOrUpdated++
                        }
                    } else {
                        val assignedId = if (s.id > 0 && !stuById.containsKey(s.id)) s.id else nextStuId++
                        if (s.id > 0) studentIdRemap[s.id] = assignedId
                        val candidate = s.copy(id = assignedId)
                        studentsToSave.add(candidate)
                        stuById[assignedId] = candidate
                        stuByKey[key] = candidate
                        addedOrUpdated++
                    }
                }
                if (studentsToSave.isNotEmpty()) repository.insertAllStudents(studentsToSave)

                // 2. Teachers (with ID remap & natural key deduplication)
                val existingTeachers = repository.getAllTeachersOnce()
                val teaById = existingTeachers.associateBy { it.id }.toMutableMap()
                fun teacherKey(t: TeacherEntity) = "${norm(t.name)}|${t.phone.trim()}|${norm(t.subject)}"
                val teaByKey = existingTeachers.associateBy { teacherKey(it) }.toMutableMap()
                val seenTeaKeys = mutableSetOf<String>()
                var nextTeaId = (existingTeachers.maxOfOrNull { it.id } ?: 0) + 1
                val teacherIdRemap = mutableMapOf<Int, Int>()
                val teachersToSave = mutableListOf<TeacherEntity>()

                for (t in rawTeachers) {
                    val key = teacherKey(t)
                    if (!seenTeaKeys.add(key)) {
                        skippedDuplicates++
                        continue
                    }
                    val match = teaByKey[key] ?: teaById[t.id]?.takeIf { teacherKey(it) == key }
                    if (match != null) {
                        if (t.id > 0) teacherIdRemap[t.id] = match.id
                        val candidate = t.copy(id = match.id)
                        if (candidate == match) {
                            skippedDuplicates++
                        } else {
                            teachersToSave.add(candidate)
                            teaById[match.id] = candidate
                            teaByKey[key] = candidate
                            addedOrUpdated++
                        }
                    } else {
                        val assignedId = if (t.id > 0 && !teaById.containsKey(t.id)) t.id else nextTeaId++
                        if (t.id > 0) teacherIdRemap[t.id] = assignedId
                        val candidate = t.copy(id = assignedId)
                        teachersToSave.add(candidate)
                        teaById[assignedId] = candidate
                        teaByKey[key] = candidate
                        addedOrUpdated++
                    }
                }
                if (teachersToSave.isNotEmpty()) repository.insertAllTeachers(teachersToSave)

                // 3. Fees
                val existingFees = repository.getAllFeesOnce()
                val feeById = existingFees.associateBy { it.id }.toMutableMap()
                fun feeKey(f: FeeEntity) = "${f.studentId}|${f.amount}|${norm(f.type)}|${norm(f.month)}|${f.date.trim()}"
                val feeByKey = existingFees.associateBy { feeKey(it) }.toMutableMap()
                val seenFeeKeys = mutableSetOf<String>()
                var nextFeeId = (existingFees.maxOfOrNull { it.id } ?: 0) + 1
                val feesToSave = mutableListOf<FeeEntity>()

                for (rawFee in rawFees) {
                    val mappedSid = studentIdRemap[rawFee.studentId] ?: rawFee.studentId
                    val f = rawFee.copy(studentId = mappedSid)
                    val key = feeKey(f)
                    if (!seenFeeKeys.add(key)) {
                        skippedDuplicates++
                        continue
                    }
                    val match = feeByKey[key] ?: feeById[f.id]?.takeIf { feeKey(it) == key }
                    if (match != null) {
                        val candidate = f.copy(id = match.id)
                        if (candidate == match) skippedDuplicates++ else {
                            feesToSave.add(candidate)
                            addedOrUpdated++
                        }
                    } else {
                        val assignedId = if (f.id > 0 && !feeById.containsKey(f.id)) f.id else nextFeeId++
                        val candidate = f.copy(id = assignedId)
                        feesToSave.add(candidate)
                        feeById[assignedId] = candidate
                        feeByKey[key] = candidate
                        addedOrUpdated++
                    }
                }
                if (feesToSave.isNotEmpty()) repository.insertAllFees(feesToSave)

                // 4. Results
                val existingResults = repository.getAllResultsOnce()
                val resById = existingResults.associateBy { it.id }.toMutableMap()
                fun resultKey(r: ResultEntity) = "${r.studentId}|${norm(r.exam)}|${r.subjectsJson.trim()}"
                val resByKey = existingResults.associateBy { resultKey(it) }.toMutableMap()
                val seenResKeys = mutableSetOf<String>()
                var nextResId = (existingResults.maxOfOrNull { it.id } ?: 0) + 1
                val resultsToSave = mutableListOf<ResultEntity>()

                for (rawRes in rawResults) {
                    val mappedSid = studentIdRemap[rawRes.studentId] ?: rawRes.studentId
                    val r = rawRes.copy(studentId = mappedSid)
                    val key = resultKey(r)
                    if (!seenResKeys.add(key)) {
                        skippedDuplicates++
                        continue
                    }
                    val match = resByKey[key] ?: resById[r.id]?.takeIf { resultKey(it) == key }
                    if (match != null) {
                        val candidate = r.copy(id = match.id)
                        if (candidate.studentId == match.studentId &&
                            candidate.exam == match.exam &&
                            candidate.subjectsJson == match.subjectsJson &&
                            candidate.total == match.total &&
                            candidate.avg == match.avg &&
                            candidate.date == match.date
                        ) {
                            skippedDuplicates++
                        } else {
                            resultsToSave.add(candidate)
                            addedOrUpdated++
                        }
                    } else {
                        val assignedId = if (r.id > 0 && !resById.containsKey(r.id)) r.id else nextResId++
                        val candidate = r.copy(id = assignedId)
                        resultsToSave.add(candidate)
                        resById[assignedId] = candidate
                        resByKey[key] = candidate
                        addedOrUpdated++
                    }
                }
                if (resultsToSave.isNotEmpty()) repository.insertAllResults(resultsToSave)

                // 5. Teacher Payments
                val existingPayments = repository.getAllTeacherPaymentsOnce()
                val payById = existingPayments.associateBy { it.id }.toMutableMap()
                fun payKey(p: TeacherPaymentEntity) =
                    "${p.teacherId}|${p.amount}|${norm(p.month)}|${p.year.trim()}|${p.date.trim()}"
                val payByKey = existingPayments.associateBy { payKey(it) }.toMutableMap()
                val seenPayKeys = mutableSetOf<String>()
                var nextPayId = (existingPayments.maxOfOrNull { it.id } ?: 0) + 1
                val paysToSave = mutableListOf<TeacherPaymentEntity>()

                for (rawPay in rawPayments) {
                    val mappedTid = teacherIdRemap[rawPay.teacherId] ?: rawPay.teacherId
                    val p = rawPay.copy(teacherId = mappedTid)
                    val key = payKey(p)
                    if (!seenPayKeys.add(key)) {
                        skippedDuplicates++
                        continue
                    }
                    val match = payByKey[key] ?: payById[p.id]?.takeIf { payKey(it) == key }
                    if (match != null) {
                        val candidate = p.copy(id = match.id)
                        if (candidate == match) skippedDuplicates++ else {
                            paysToSave.add(candidate)
                            addedOrUpdated++
                        }
                    } else {
                        val assignedId = if (p.id > 0 && !payById.containsKey(p.id)) p.id else nextPayId++
                        val candidate = p.copy(id = assignedId)
                        paysToSave.add(candidate)
                        payById[assignedId] = candidate
                        payByKey[key] = candidate
                        addedOrUpdated++
                    }
                }
                if (paysToSave.isNotEmpty()) repository.insertAllTeacherPayments(paysToSave)

                // 6. Expenses
                val existingExpenses = repository.getAllExpensesOnce()
                val expById = existingExpenses.associateBy { it.id }.toMutableMap()
                fun expKey(e: ExpenseEntity) = "${norm(e.type)}|${norm(e.desc)}|${e.amount}|${e.date.trim()}"
                val expByKey = existingExpenses.associateBy { expKey(it) }.toMutableMap()
                val seenExpKeys = mutableSetOf<String>()
                var nextExpId = (existingExpenses.maxOfOrNull { it.id } ?: 0) + 1
                val expsToSave = mutableListOf<ExpenseEntity>()

                for (e in rawExpenses) {
                    val key = expKey(e)
                    if (!seenExpKeys.add(key)) {
                        skippedDuplicates++
                        continue
                    }
                    val match = expByKey[key] ?: expById[e.id]?.takeIf { expKey(it) == key }
                    if (match != null) {
                        val candidate = e.copy(id = match.id)
                        if (candidate == match) skippedDuplicates++ else {
                            expsToSave.add(candidate)
                            addedOrUpdated++
                        }
                    } else {
                        val assignedId = if (e.id > 0 && !expById.containsKey(e.id)) e.id else nextExpId++
                        val candidate = e.copy(id = assignedId)
                        expsToSave.add(candidate)
                        expById[assignedId] = candidate
                        expByKey[key] = candidate
                        addedOrUpdated++
                    }
                }
                if (expsToSave.isNotEmpty()) repository.insertAllExpenses(expsToSave)

                // 7. Notices
                val existingNotices = repository.getAllNoticesOnce()
                val notById = existingNotices.associateBy { it.id }.toMutableMap()
                fun noticeKey(n: NoticeEntity) = "${norm(n.title)}|${n.description.trim()}|${n.date.trim()}"
                val notByKey = existingNotices.associateBy { noticeKey(it) }.toMutableMap()
                val seenNotKeys = mutableSetOf<String>()
                var nextNotId = (existingNotices.maxOfOrNull { it.id } ?: 0) + 1
                val noticesToSave = mutableListOf<NoticeEntity>()

                for (n in rawNotices) {
                    val key = noticeKey(n)
                    if (!seenNotKeys.add(key)) {
                        skippedDuplicates++
                        continue
                    }
                    val match = notByKey[key] ?: notById[n.id]?.takeIf { noticeKey(it) == key }
                    if (match != null) {
                        val candidate = n.copy(id = match.id)
                        if (candidate == match) skippedDuplicates++ else {
                            noticesToSave.add(candidate)
                            addedOrUpdated++
                        }
                    } else {
                        val assignedId = if (n.id > 0 && !notById.containsKey(n.id)) n.id else nextNotId++
                        val candidate = n.copy(id = assignedId)
                        noticesToSave.add(candidate)
                        notById[assignedId] = candidate
                        notByKey[key] = candidate
                        addedOrUpdated++
                    }
                }
                if (noticesToSave.isNotEmpty()) repository.insertAllNotices(noticesToSave)

                // 8. Question Papers
                val existingPapers = repository.getAllQuestionPapersOnce()
                val qpById = existingPapers.associateBy { it.id }.toMutableMap()
                fun qpKey(q: QuestionPaperEntity) =
                    "${norm(q.examName)}|${norm(q.className)}|${norm(q.subject)}|${q.date.trim()}|${q.questionsText.trim()}"
                val qpByKey = existingPapers.associateBy { qpKey(it) }.toMutableMap()
                val seenQpKeys = mutableSetOf<String>()
                var nextQpId = (existingPapers.maxOfOrNull { it.id } ?: 0) + 1
                val papersToSave = mutableListOf<QuestionPaperEntity>()

                for (q in rawPapers) {
                    val key = qpKey(q)
                    if (!seenQpKeys.add(key)) {
                        skippedDuplicates++
                        continue
                    }
                    val match = qpByKey[key] ?: qpById[q.id]?.takeIf { qpKey(it) == key }
                    if (match != null) {
                        val candidate = q.copy(id = match.id)
                        if (candidate == match) skippedDuplicates++ else {
                            papersToSave.add(candidate)
                            addedOrUpdated++
                        }
                    } else {
                        val assignedId = if (q.id > 0 && !qpById.containsKey(q.id)) q.id else nextQpId++
                        val candidate = q.copy(id = assignedId)
                        papersToSave.add(candidate)
                        qpById[assignedId] = candidate
                        qpByKey[key] = candidate
                        addedOrUpdated++
                    }
                }
                if (papersToSave.isNotEmpty()) repository.insertAllQuestionPapers(papersToSave)

                // 9. Class Routines
                val existingRoutines = repository.getAllClassRoutinesOnce()
                val crById = existingRoutines.associateBy { it.id }.toMutableMap()
                fun crKey(c: ClassRoutineEntity) =
                    "${norm(c.className)}|${norm(c.dayOfWeek)}|${norm(c.timeSlot)}|${norm(c.subject)}|${norm(c.teacherName)}"
                val crByKey = existingRoutines.associateBy { crKey(it) }.toMutableMap()
                val seenCrKeys = mutableSetOf<String>()
                var nextCrId = (existingRoutines.maxOfOrNull { it.id } ?: 0) + 1
                val routinesToSave = mutableListOf<ClassRoutineEntity>()

                for (c in rawRoutines) {
                    val key = crKey(c)
                    if (!seenCrKeys.add(key)) {
                        skippedDuplicates++
                        continue
                    }
                    val match = crByKey[key] ?: crById[c.id]?.takeIf { crKey(it) == key }
                    if (match != null) {
                        val candidate = c.copy(id = match.id)
                        if (candidate == match) skippedDuplicates++ else {
                            routinesToSave.add(candidate)
                            addedOrUpdated++
                        }
                    } else {
                        val assignedId = if (c.id > 0 && !crById.containsKey(c.id)) c.id else nextCrId++
                        val candidate = c.copy(id = assignedId)
                        routinesToSave.add(candidate)
                        crById[assignedId] = candidate
                        crByKey[key] = candidate
                        addedOrUpdated++
                    }
                }
                if (routinesToSave.isNotEmpty()) repository.insertAllClassRoutines(routinesToSave)
            }

            Result.success(
                BackupRestoreSummary(
                    totalInFile = totalInFile,
                    addedOrUpdatedCount = addedOrUpdated,
                    skippedDuplicateCount = skippedDuplicates,
                    isDuplicateFile = (totalInFile > 0 && addedOrUpdated == 0 && skippedDuplicates > 0),
                    backupDate = backupDate,
                    institutionName = instName
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

