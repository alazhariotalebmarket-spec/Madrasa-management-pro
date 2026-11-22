package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.entity.*

@Database(
    entities = [
        StudentEntity::class,
        FeeEntity::class,
        AttendanceEntity::class,
        ResultEntity::class,
        TeacherEntity::class,
        TeacherPaymentEntity::class,
        ExpenseEntity::class,
        NoticeEntity::class,
        QuestionPaperEntity::class,
        ClassRoutineEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class MadrasaDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun feeDao(): FeeDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun resultDao(): ResultDao
    abstract fun teacherDao(): TeacherDao
    abstract fun teacherPaymentDao(): TeacherPaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun noticeDao(): NoticeDao
    abstract fun questionPaperDao(): QuestionPaperDao
    abstract fun classRoutineDao(): ClassRoutineDao

    companion object {
        @Volatile
        private var INSTANCE: MadrasaDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Students table additions
                try {
                    db.execSQL("ALTER TABLE students ADD COLUMN roll TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE students ADD COLUMN electricBill REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}

                // Teachers table additions
                try {
                    db.execSQL("ALTER TABLE teachers ADD COLUMN address TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE teachers ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE teachers ADD COLUMN photoUri TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE teachers ADD COLUMN joinDate TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}

                // Expenses table addition
                try {
                    db.execSQL("ALTER TABLE expenses ADD COLUMN date TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}

                // New teacher_payments table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS teacher_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        teacherId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        month TEXT NOT NULL,
                        year TEXT NOT NULL,
                        date TEXT NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS question_papers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        examName TEXT NOT NULL,
                        className TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        fullMarks TEXT NOT NULL,
                        duration TEXT NOT NULL,
                        instructions TEXT NOT NULL,
                        questionsText TEXT NOT NULL,
                        date TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS class_routines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        className TEXT NOT NULL,
                        dayOfWeek TEXT NOT NULL,
                        timeSlot TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        teacherName TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE class_routines ADD COLUMN duration TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): MadrasaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MadrasaDatabase::class.java,
                    "madrasa_database"
                )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
