package com.example.data

import com.example.data.dao.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

class MadrasaRepository(
    private val studentDao: StudentDao,
    private val feeDao: FeeDao,
    private val attendanceDao: AttendanceDao,
    private val resultDao: ResultDao,
    private val teacherDao: TeacherDao,
    private val teacherPaymentDao: TeacherPaymentDao,
    private val expenseDao: ExpenseDao,
    private val noticeDao: NoticeDao,
    private val questionPaperDao: QuestionPaperDao,
    private val classRoutineDao: ClassRoutineDao
) {
    val allStudents: Flow<List<StudentEntity>> = studentDao.getAllStudents()
    val allFees: Flow<List<FeeEntity>> = feeDao.getAllFees()
    val allResults: Flow<List<ResultEntity>> = resultDao.getAllResults()
    val allTeachers: Flow<List<TeacherEntity>> = teacherDao.getAllTeachers()
    val allTeacherPayments: Flow<List<TeacherPaymentEntity>> = teacherPaymentDao.getAllTeacherPayments()
    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val allNotices: Flow<List<NoticeEntity>> = noticeDao.getAllNotices()
    val allQuestionPapers: Flow<List<QuestionPaperEntity>> = questionPaperDao.getAllQuestionPapers()
    val allClassRoutines: Flow<List<ClassRoutineEntity>> = classRoutineDao.getAllClassRoutines()

    suspend fun insertStudent(student: StudentEntity) = studentDao.insertStudent(student)
    suspend fun deleteStudent(id: Int) = studentDao.deleteStudent(id)

    suspend fun insertFee(fee: FeeEntity) = feeDao.insertFee(fee)
    suspend fun deleteFee(id: Int) = feeDao.deleteFee(id)

    fun getAttendance(date: String): Flow<List<AttendanceEntity>> = attendanceDao.getAttendanceForDate(date)
    suspend fun insertAttendance(attendance: AttendanceEntity) = attendanceDao.insertAttendance(attendance)

    suspend fun insertResult(result: ResultEntity) = resultDao.insertResult(result)
    suspend fun deleteResult(id: Int) = resultDao.deleteResult(id)

    suspend fun insertTeacher(teacher: TeacherEntity) = teacherDao.insertTeacher(teacher)
    suspend fun deleteTeacher(id: Int) = teacherDao.deleteTeacher(id)

    fun getPaymentsForTeacher(teacherId: Int): Flow<List<TeacherPaymentEntity>> = teacherPaymentDao.getPaymentsForTeacher(teacherId)
    suspend fun insertTeacherPayment(payment: TeacherPaymentEntity) = teacherPaymentDao.insertTeacherPayment(payment)
    suspend fun deleteTeacherPayment(id: Int) = teacherPaymentDao.deleteTeacherPayment(id)

    suspend fun insertExpense(expense: ExpenseEntity) = expenseDao.insertExpense(expense)
    suspend fun deleteExpense(id: Int) = expenseDao.deleteExpense(id)

    suspend fun insertNotice(notice: NoticeEntity) = noticeDao.insertNotice(notice)
    suspend fun deleteNotice(id: Int) = noticeDao.deleteNotice(id)

    suspend fun insertQuestionPaper(paper: QuestionPaperEntity) = questionPaperDao.insertQuestionPaper(paper)
    suspend fun deleteQuestionPaper(id: Int) = questionPaperDao.deleteQuestionPaper(id)

    suspend fun insertClassRoutine(routine: ClassRoutineEntity) = classRoutineDao.insertClassRoutine(routine)
    suspend fun deleteClassRoutine(id: Int) = classRoutineDao.deleteClassRoutine(id)
}
