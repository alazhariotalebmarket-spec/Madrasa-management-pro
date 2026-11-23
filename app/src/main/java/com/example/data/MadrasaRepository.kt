package com.example.data

import androidx.room.withTransaction
import com.example.data.dao.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

class MadrasaRepository(
    private val db: MadrasaDatabase,
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

    suspend fun <R> runInTransaction(block: suspend () -> R): R = db.withTransaction(block)

    suspend fun getAllStudentsOnce(): List<StudentEntity> = studentDao.getAllStudentsOnce()
    suspend fun insertStudent(student: StudentEntity) = studentDao.insertStudent(student)
    suspend fun insertAllStudents(students: List<StudentEntity>) = studentDao.insertAllStudents(students)
    suspend fun deleteStudent(id: Int) = studentDao.deleteStudent(id)

    suspend fun getAllFeesOnce(): List<FeeEntity> = feeDao.getAllFeesOnce()
    suspend fun insertFee(fee: FeeEntity) = feeDao.insertFee(fee)
    suspend fun insertAllFees(fees: List<FeeEntity>) = feeDao.insertAllFees(fees)
    suspend fun deleteFee(id: Int) = feeDao.deleteFee(id)

    fun getAttendance(date: String): Flow<List<AttendanceEntity>> = attendanceDao.getAttendanceForDate(date)
    suspend fun getAllAttendanceOnce(): List<AttendanceEntity> = attendanceDao.getAllAttendanceOnce()
    suspend fun insertAttendance(attendance: AttendanceEntity) = attendanceDao.insertAttendance(attendance)
    suspend fun insertAllAttendance(items: List<AttendanceEntity>) = attendanceDao.insertAllAttendance(items)
    suspend fun deleteAttendance(id: Int) = attendanceDao.deleteAttendance(id)

    suspend fun getAllResultsOnce(): List<ResultEntity> = resultDao.getAllResultsOnce()
    suspend fun insertResult(result: ResultEntity) = resultDao.insertResult(result)
    suspend fun insertAllResults(results: List<ResultEntity>) = resultDao.insertAllResults(results)
    suspend fun deleteResult(id: Int) = resultDao.deleteResult(id)

    suspend fun getAllTeachersOnce(): List<TeacherEntity> = teacherDao.getAllTeachersOnce()
    suspend fun insertTeacher(teacher: TeacherEntity) = teacherDao.insertTeacher(teacher)
    suspend fun insertAllTeachers(teachers: List<TeacherEntity>) = teacherDao.insertAllTeachers(teachers)
    suspend fun deleteTeacher(id: Int) = teacherDao.deleteTeacher(id)

    suspend fun getAllTeacherPaymentsOnce(): List<TeacherPaymentEntity> = teacherPaymentDao.getAllTeacherPaymentsOnce()
    fun getPaymentsForTeacher(teacherId: Int): Flow<List<TeacherPaymentEntity>> = teacherPaymentDao.getPaymentsForTeacher(teacherId)
    suspend fun insertTeacherPayment(payment: TeacherPaymentEntity) = teacherPaymentDao.insertTeacherPayment(payment)
    suspend fun insertAllTeacherPayments(payments: List<TeacherPaymentEntity>) = teacherPaymentDao.insertAllTeacherPayments(payments)
    suspend fun deleteTeacherPayment(id: Int) = teacherPaymentDao.deleteTeacherPayment(id)

    suspend fun getAllExpensesOnce(): List<ExpenseEntity> = expenseDao.getAllExpensesOnce()
    suspend fun insertExpense(expense: ExpenseEntity) = expenseDao.insertExpense(expense)
    suspend fun insertAllExpenses(expenses: List<ExpenseEntity>) = expenseDao.insertAllExpenses(expenses)
    suspend fun deleteExpense(id: Int) = expenseDao.deleteExpense(id)

    suspend fun getAllNoticesOnce(): List<NoticeEntity> = noticeDao.getAllNoticesOnce()
    suspend fun insertNotice(notice: NoticeEntity) = noticeDao.insertNotice(notice)
    suspend fun insertAllNotices(notices: List<NoticeEntity>) = noticeDao.insertAllNotices(notices)
    suspend fun deleteNotice(id: Int) = noticeDao.deleteNotice(id)

    suspend fun getAllQuestionPapersOnce(): List<QuestionPaperEntity> = questionPaperDao.getAllQuestionPapersOnce()
    suspend fun insertQuestionPaper(paper: QuestionPaperEntity) = questionPaperDao.insertQuestionPaper(paper)
    suspend fun insertAllQuestionPapers(papers: List<QuestionPaperEntity>) = questionPaperDao.insertAllQuestionPapers(papers)
    suspend fun deleteQuestionPaper(id: Int) = questionPaperDao.deleteQuestionPaper(id)

    suspend fun getAllClassRoutinesOnce(): List<ClassRoutineEntity> = classRoutineDao.getAllClassRoutinesOnce()
    suspend fun insertClassRoutine(routine: ClassRoutineEntity) = classRoutineDao.insertClassRoutine(routine)
    suspend fun insertAllClassRoutines(routines: List<ClassRoutineEntity>) = classRoutineDao.insertAllClassRoutines(routines)
    suspend fun deleteClassRoutine(id: Int) = classRoutineDao.deleteClassRoutine(id)
}
