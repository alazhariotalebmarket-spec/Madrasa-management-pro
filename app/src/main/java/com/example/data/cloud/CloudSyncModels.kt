package com.example.data.cloud

import android.util.Log
import androidx.annotation.Keep
import com.example.data.entity.*
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@Keep
@IgnoreExtraProperties
data class CloudInstitutionProfile(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val logoBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudStudent(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val name: String = "",
    val father: String = "",
    val phone: String = "",
    val cls: String = "",
    val fee: Double = 0.0,
    val photoUri: String = "",
    val roll: String = "",
    val electricBill: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): StudentEntity = StudentEntity(
        id = localId.toInt(),
        name = name,
        father = father,
        phone = phone,
        cls = cls,
        fee = fee,
        photoUri = photoUri,
        roll = roll,
        electricBill = electricBill
    )
}

@Keep
@IgnoreExtraProperties
data class CloudFee(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val studentId: Long = 0L,
    val amount: Double = 0.0,
    val type: String = "",
    val month: String = "",
    val date: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): FeeEntity = FeeEntity(
        id = localId.toInt(),
        studentId = studentId.toInt(),
        amount = amount,
        type = type,
        month = month,
        date = date
    )
}

@Keep
@IgnoreExtraProperties
data class CloudAttendance(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val date: String = "",
    val studentId: Long = 0L,
    val status: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): AttendanceEntity = AttendanceEntity(
        id = localId.toInt(),
        date = date,
        studentId = studentId.toInt(),
        status = status
    )
}

@Keep
@IgnoreExtraProperties
data class CloudSubjectMark(
    val name: String = "",
    val mark: Double = 0.0
)

@Keep
@IgnoreExtraProperties
data class CloudResult(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val studentId: Long = 0L,
    val exam: String = "",
    val subjects: List<CloudSubjectMark> = emptyList(),
    val total: Double = 0.0,
    val avg: Double = 0.0,
    val date: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): ResultEntity {
        val mapped = subjects.map { SubjectMark(name = it.name, mark = it.mark) }
        return ResultEntity(
            id = localId.toInt(),
            studentId = studentId.toInt(),
            exam = exam,
            subjectsJson = ResultEntity.serializeSubjects(mapped),
            total = total,
            avg = avg,
            date = date
        )
    }
}

@Keep
@IgnoreExtraProperties
data class CloudTeacher(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val name: String = "",
    val subject: String = "",
    val salary: Double = 0.0,
    val address: String = "",
    val phone: String = "",
    val photoUri: String = "",
    val joinDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): TeacherEntity = TeacherEntity(
        id = localId.toInt(),
        name = name,
        subject = subject,
        salary = salary,
        address = address,
        phone = phone,
        photoUri = photoUri,
        joinDate = joinDate
    )
}

@Keep
@IgnoreExtraProperties
data class CloudTeacherPayment(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val teacherId: Long = 0L,
    val amount: Double = 0.0,
    val month: String = "",
    val year: String = "",
    val date: String = "",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): TeacherPaymentEntity = TeacherPaymentEntity(
        id = localId.toInt(),
        teacherId = teacherId.toInt(),
        amount = amount,
        month = month,
        year = year,
        date = date,
        note = note
    )
}

@Keep
@IgnoreExtraProperties
data class CloudExpense(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val type: String = "",
    val desc: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): ExpenseEntity = ExpenseEntity(
        id = localId.toInt(),
        type = type,
        desc = desc,
        amount = amount,
        date = date
    )
}

@Keep
@IgnoreExtraProperties
data class CloudNotice(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val title: String = "",
    val description: String = "",
    val date: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): NoticeEntity = NoticeEntity(
        id = localId.toInt(),
        title = title,
        description = description,
        date = date
    )
}

@Keep
@IgnoreExtraProperties
data class CloudQuestionPaper(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val examName: String = "",
    val className: String = "",
    val subject: String = "",
    val fullMarks: String = "",
    val duration: String = "",
    val instructions: String = "",
    val questionsText: String = "",
    val date: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): QuestionPaperEntity = QuestionPaperEntity(
        id = localId.toInt(),
        examName = examName,
        className = className,
        subject = subject,
        fullMarks = fullMarks,
        duration = duration,
        instructions = instructions,
        questionsText = questionsText,
        date = date
    )
}

@Keep
@IgnoreExtraProperties
data class CloudClassRoutine(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val className: String = "",
    val dayOfWeek: String = "",
    val timeSlot: String = "",
    val subject: String = "",
    val teacherName: String = "",
    val duration: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): ClassRoutineEntity = ClassRoutineEntity(
        id = localId.toInt(),
        className = className,
        dayOfWeek = dayOfWeek,
        timeSlot = timeSlot,
        subject = subject,
        teacherName = teacherName,
        duration = duration
    )
}

enum class SyncConnectionState {
    IDLE,
    SYNCING,
    LIVE,
    OFFLINE_PENDING,
    ERROR
}

data class CloudSyncStatus(
    val state: SyncConnectionState = SyncConnectionState.IDLE,
    val userEmail: String = "",
    val lastSyncedTime: String = "",
    val message: String = "সিঙ্ক প্রস্তুত হচ্ছে...",
    val errorMessage: String? = null
)
