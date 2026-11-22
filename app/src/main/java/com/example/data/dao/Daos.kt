package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY id DESC")
    fun getAllStudents(): Flow<List<StudentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStudents(students: List<StudentEntity>)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudent(id: Int)
}

@Dao
interface FeeDao {
    @Query("SELECT * FROM fees ORDER BY id DESC")
    fun getAllFees(): Flow<List<FeeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFee(fee: FeeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFees(fees: List<FeeEntity>)

    @Query("DELETE FROM fees WHERE id = :id")
    suspend fun deleteFee(id: Int)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: AttendanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAttendance(items: List<AttendanceEntity>)
}

@Dao
interface ResultDao {
    @Query("SELECT * FROM results ORDER BY id DESC")
    fun getAllResults(): Flow<List<ResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllResults(results: List<ResultEntity>)

    @Query("DELETE FROM results WHERE id = :id")
    suspend fun deleteResult(id: Int)
}

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers ORDER BY id DESC")
    fun getAllTeachers(): Flow<List<TeacherEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacher(teacher: TeacherEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTeachers(teachers: List<TeacherEntity>)

    @Query("DELETE FROM teachers WHERE id = :id")
    suspend fun deleteTeacher(id: Int)
}

@Dao
interface TeacherPaymentDao {
    @Query("SELECT * FROM teacher_payments ORDER BY id DESC")
    fun getAllTeacherPayments(): Flow<List<TeacherPaymentEntity>>

    @Query("SELECT * FROM teacher_payments WHERE teacherId = :teacherId ORDER BY id DESC")
    fun getPaymentsForTeacher(teacherId: Int): Flow<List<TeacherPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacherPayment(payment: TeacherPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTeacherPayments(payments: List<TeacherPaymentEntity>)

    @Query("DELETE FROM teacher_payments WHERE id = :id")
    suspend fun deleteTeacherPayment(id: Int)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY id DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllExpenses(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Int)
}

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notices ORDER BY id DESC")
    fun getAllNotices(): Flow<List<NoticeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotice(notice: NoticeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllNotices(notices: List<NoticeEntity>)

    @Query("DELETE FROM notices WHERE id = :id")
    suspend fun deleteNotice(id: Int)
}

@Dao
interface QuestionPaperDao {
    @Query("SELECT * FROM question_papers ORDER BY id DESC")
    fun getAllQuestionPapers(): Flow<List<QuestionPaperEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionPaper(paper: QuestionPaperEntity)

    @Query("DELETE FROM question_papers WHERE id = :id")
    suspend fun deleteQuestionPaper(id: Int)
}

@Dao
interface ClassRoutineDao {
    @Query("SELECT * FROM class_routines ORDER BY id ASC")
    fun getAllClassRoutines(): Flow<List<ClassRoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassRoutine(routine: ClassRoutineEntity)

    @Query("DELETE FROM class_routines WHERE id = :id")
    suspend fun deleteClassRoutine(id: Int)
}
