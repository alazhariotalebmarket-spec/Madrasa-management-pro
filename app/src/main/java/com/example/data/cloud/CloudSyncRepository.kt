package com.example.data.cloud

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.MadrasaInfo
import com.example.data.MadrasaInfoStore
import com.example.data.MadrasaRepository
import com.example.data.entity.*
import com.example.util.MadrasaLogoUtil
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val localRepo: MadrasaRepository,
    context: Context,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context, localRepo: MadrasaRepository) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        localRepo = localRepo,
        context = context.applicationContext
    )

    private val appContext: Context = context.applicationContext

    private val _syncStatus = MutableStateFlow(CloudSyncStatus())
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private var syncJob: Job? = null
    private var activeUid: String? = null

    private val knownIdsCache = ConcurrentHashMap<String, MutableSet<String>>()
    private val pendingWriteIds = ConcurrentHashMap<String, MutableSet<String>>()

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before syncing with Firestore.")
    }

    private fun prefsFor(uid: String) =
        appContext.getSharedPreferences("mpro_cloud_sync_$uid", Context.MODE_PRIVATE)

    private fun getKnownIds(uid: String, col: String): MutableSet<String> {
        val key = "${uid}_$col"
        return knownIdsCache.getOrPut(key) {
            val saved = prefsFor(uid).getStringSet("known_$col", emptySet()) ?: emptySet()
            ConcurrentHashMap.newKeySet<String>().apply { addAll(saved) }
        }
    }

    private fun addKnownId(uid: String, col: String, docId: String) {
        val set = getKnownIds(uid, col)
        if (set.add(docId)) {
            prefsFor(uid).edit().putStringSet("known_$col", set.toSet()).apply()
        }
    }

    private fun addKnownIds(uid: String, col: String, docIds: Collection<String>) {
        if (docIds.isEmpty()) return
        val set = getKnownIds(uid, col)
        if (set.addAll(docIds)) {
            prefsFor(uid).edit().putStringSet("known_$col", set.toSet()).apply()
        }
    }

    private fun removeKnownId(uid: String, col: String, docId: String) {
        val set = getKnownIds(uid, col)
        if (set.remove(docId)) {
            prefsFor(uid).edit().putStringSet("known_$col", set.toSet()).apply()
        }
    }

    private fun markPending(col: String, docId: String) {
        pendingWriteIds.getOrPut(col) { ConcurrentHashMap.newKeySet() }.add(docId)
    }

    private fun clearPending(col: String, docId: String) {
        pendingWriteIds[col]?.remove(docId)
    }

    private fun isPending(col: String, docId: String): Boolean {
        return pendingWriteIds[col]?.contains(docId) == true
    }

    private fun formattedNow(): String =
        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())

    private fun sha256(input: String): String {
        if (input.isBlank()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun markLiveOrCached(email: String, fromCache: Boolean, hasPendingWrites: Boolean) {
        val time = formattedNow()
        val state = if (fromCache && hasPendingWrites) {
            SyncConnectionState.OFFLINE_PENDING
        } else {
            SyncConnectionState.LIVE
        }
        val msg = when (state) {
            SyncConnectionState.OFFLINE_PENDING -> "অফলাইন পরিবর্তন সংরক্ষিত (অনলাইন হলে সিঙ্ক হবে)"
            else -> "মাল্টি-ডিভাইস লাইভ সিঙ্ক চালু আছে"
        }
        _syncStatus.value = CloudSyncStatus(
            state = state,
            userEmail = email,
            lastSyncedTime = time,
            message = msg,
            errorMessage = null
        )
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    fun startSync(
        uid: String,
        parentScope: CoroutineScope,
        onInstitutionSynced: (MadrasaInfo) -> Unit
    ) {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return
        if (activeUid == uid && syncJob?.isActive == true) return

        stopSync()
        activeUid = uid
        val email = auth.currentUser?.email.orEmpty()
        _syncStatus.value = CloudSyncStatus(
            state = SyncConnectionState.SYNCING,
            userEmail = email,
            lastSyncedTime = prefsFor(uid).getString("last_sync_time", "") ?: "",
            message = "ক্লাউড ডেটা সিঙ্ক হচ্ছে...",
            errorMessage = null
        )

        val supervisor = SupervisorJob(parentScope.coroutineContext[Job])
        syncJob = supervisor
        val syncScope = CoroutineScope(parentScope.coroutineContext + Dispatchers.IO + supervisor)
        val userDoc = db.collection("users").document(uid)

        // 1. Institution Profile listener
        syncScope.launch {
            val docRef = userDoc.collection("institution").document("profile")
            val path = docRef.path
            docRef.snapshots()
                .map { snap ->
                    val exists = snap.exists()
                    val profile = if (exists) {
                        snap.toObject(
                            CloudInstitutionProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                    Triple(exists, profile, snap.metadata)
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                        _syncStatus.value = _syncStatus.value.copy(
                            state = SyncConnectionState.ERROR,
                            errorMessage = fsErr?.localizedMessage ?: error.localizedMessage
                        )
                    }
                }
                .collect { (exists, profile, metadata) ->
                    if (!exists) {
                        val localInfo = MadrasaInfoStore.getInfo(appContext)
                        pushInstitutionToCloud(uid, localInfo)
                    } else if (profile != null && profile.name.isNotBlank()) {
                        addKnownId(uid, "institution", "profile")
                        val prefs = prefsFor(uid)
                        val prevLogoHash = prefs.getString("synced_logo_hash", "") ?: ""
                        val incomingHash = sha256(profile.logoBase64)
                        var updatedLogoPath: String? = null
                        if (profile.logoBase64.isNotBlank() && incomingHash != prevLogoHash) {
                            val savedPath = MadrasaLogoUtil.saveLogoFromBase64(appContext, profile.logoBase64)
                            if (savedPath.isNotBlank()) {
                                updatedLogoPath = savedPath
                                prefs.edit().putString("synced_logo_hash", incomingHash).apply()
                            }
                        } else if (profile.logoBase64.isBlank() && prevLogoHash.isNotBlank()) {
                            updatedLogoPath = ""
                            prefs.edit().putString("synced_logo_hash", "").apply()
                        }

                        val currentLocal = MadrasaInfoStore.getInfo(appContext)
                        val saved = MadrasaInfoStore.saveInfo(
                            appContext,
                            name = profile.name,
                            address = profile.address.ifBlank { currentLocal.address },
                            phone = profile.phone.ifBlank { currentLocal.phone },
                            logoPath = updatedLogoPath
                        )
                        if (updatedLogoPath != null) {
                            MadrasaLogoUtil.cleanupOldLogoFiles(appContext, saved.logoPath)
                        }
                        onInstitutionSynced(saved)
                        markLiveOrCached(email, metadata.isFromCache, metadata.hasPendingWrites())
                    }
                }
        }

        // 2. Students
        launchCollectionSync(
            scope = syncScope,
            uid = uid,
            email = email,
            col = "students",
            query = userDoc.collection("students"),
            mapper = { snap ->
                snap.toObjects(CloudStudent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.localId > 0L }
                    .map { it.docId to it.toEntity() }
            },
            getAllLocal = { localRepo.getAllStudentsOnce() },
            entityId = { it.id },
            insertAllLocal = { localRepo.insertAllStudents(it) },
            deleteLocal = { localRepo.deleteStudent(it) },
            pushLocal = { upsertStudentToCloud(uid, it) }
        )

        // 3. Fees
        launchCollectionSync(
            scope = syncScope,
            uid = uid,
            email = email,
            col = "fees",
            query = userDoc.collection("fees"),
            mapper = { snap ->
                snap.toObjects(CloudFee::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.localId > 0L && it.studentId > 0L }
                    .map { it.docId to it.toEntity() }
            },
            getAllLocal = { localRepo.getAllFeesOnce() },
            entityId = { it.id },
            insertAllLocal = { localRepo.insertAllFees(it) },
            deleteLocal = { localRepo.deleteFee(it) },
            pushLocal = { upsertFeeToCloud(uid, it) }
        )

        // 4. Attendance
        syncScope.launch {
            val col = "attendance"
            val path = userDoc.collection(col).path
            userDoc.collection(col).snapshots()
                .map { snap ->
                    val items = snap.toObjects(
                        CloudAttendance::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.localId > 0L && it.studentId > 0L && it.date.isNotBlank() }
                        .map { it.docId to it.toEntity() }
                    Triple(items, snap.metadata.isFromCache, snap.metadata.hasPendingWrites())
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                }
                .collect { (remotePairs, fromCache, hasPending) ->
                    val localList = localRepo.getAllAttendanceOnce()
                    val localByDocId = localList.associateBy { attendanceDocId(it.date, it.studentId) }
                    val remoteDocIds = remotePairs.map { it.first }.toSet()
                    val knownIds = getKnownIds(uid, col)

                    val toInsert = remotePairs.mapNotNull { (docId, entity) ->
                        val cur = localByDocId[docId]
                        if (cur == null || cur != entity) entity else null
                    }
                    if (toInsert.isNotEmpty()) {
                        localRepo.insertAllAttendance(toInsert)
                    }
                    addKnownIds(uid, col, remoteDocIds)

                    for (local in localList) {
                        val docId = attendanceDocId(local.date, local.studentId)
                        if (docId !in remoteDocIds) {
                            if (docId !in knownIds) {
                                upsertAttendanceToCloud(uid, local)
                            } else if (!hasPending && !isPending(col, docId)) {
                                localRepo.deleteAttendance(local.id)
                                removeKnownId(uid, col, docId)
                            }
                        }
                    }
                    markLiveOrCached(email, fromCache, hasPending)
                }
        }

        // 5. Results
        launchCollectionSync(
            scope = syncScope,
            uid = uid,
            email = email,
            col = "results",
            query = userDoc.collection("results"),
            mapper = { snap ->
                snap.toObjects(CloudResult::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.localId > 0L && it.studentId > 0L && it.subjects.isNotEmpty() }
                    .map { it.docId to it.toEntity() }
            },
            getAllLocal = { localRepo.getAllResultsOnce() },
            entityId = { it.id },
            insertAllLocal = { localRepo.insertAllResults(it) },
            deleteLocal = { localRepo.deleteResult(it) },
            pushLocal = { upsertResultToCloud(uid, it) }
        )

        // 6. Teachers
        launchCollectionSync(
            scope = syncScope,
            uid = uid,
            email = email,
            col = "teachers",
            query = userDoc.collection("teachers"),
            mapper = { snap ->
                snap.toObjects(CloudTeacher::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.localId > 0L }
                    .map { it.docId to it.toEntity() }
            },
            getAllLocal = { localRepo.getAllTeachersOnce() },
            entityId = { it.id },
            insertAllLocal = { localRepo.insertAllTeachers(it) },
            deleteLocal = { localRepo.deleteTeacher(it) },
            pushLocal = { upsertTeacherToCloud(uid, it) }
        )

        // 7. Teacher Payments
        launchCollectionSync(
            scope = syncScope,
            uid = uid,
            email = email,
            col = "teacherPayments",
            query = userDoc.collection("teacherPayments"),
            mapper = { snap ->
                snap.toObjects(
                    CloudTeacherPayment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.localId > 0L && it.teacherId > 0L }
                    .map { it.docId to it.toEntity() }
            },
            getAllLocal = { localRepo.getAllTeacherPaymentsOnce() },
            entityId = { it.id },
            insertAllLocal = { localRepo.insertAllTeacherPayments(it) },
            deleteLocal = { localRepo.deleteTeacherPayment(it) },
            pushLocal = { upsertTeacherPaymentToCloud(uid, it) }
        )

        // 8. Expenses
        launchCollectionSync(
            scope = syncScope,
            uid = uid,
            email = email,
            col = "expenses",
            query = userDoc.collection("expenses"),
            mapper = { snap ->
                snap.toObjects(CloudExpense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.localId > 0L }
                    .map { it.docId to it.toEntity() }
            },
            getAllLocal = { localRepo.getAllExpensesOnce() },
            entityId = { it.id },
            insertAllLocal = { localRepo.insertAllExpenses(it) },
            deleteLocal = { localRepo.deleteExpense(it) },
            pushLocal = { upsertExpenseToCloud(uid, it) }
        )

        // 9. Notices
        launchCollectionSync(
            scope = syncScope,
            uid = uid,
            email = email,
            col = "notices",
            query = userDoc.collection("notices"),
            mapper = { snap ->
                snap.toObjects(CloudNotice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.localId > 0L }
                    .map { it.docId to it.toEntity() }
            },
            getAllLocal = { localRepo.getAllNoticesOnce() },
            entityId = { it.id },
            insertAllLocal = { localRepo.insertAllNotices(it) },
            deleteLocal = { localRepo.deleteNotice(it) },
            pushLocal = { upsertNoticeToCloud(uid, it) }
        )

        // 10. Question Papers
        launchCollectionSync(
            scope = syncScope,
            uid = uid,
            email = email,
            col = "questionPapers",
            query = userDoc.collection("questionPapers"),
            mapper = { snap ->
                snap.toObjects(
                    CloudQuestionPaper::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.localId > 0L }
                    .map { it.docId to it.toEntity() }
            },
            getAllLocal = { localRepo.getAllQuestionPapersOnce() },
            entityId = { it.id },
            insertAllLocal = { localRepo.insertAllQuestionPapers(it) },
            deleteLocal = { localRepo.deleteQuestionPaper(it) },
            pushLocal = { upsertQuestionPaperToCloud(uid, it) }
        )

        // 11. Class Routines
        launchCollectionSync(
            scope = syncScope,
            uid = uid,
            email = email,
            col = "classRoutines",
            query = userDoc.collection("classRoutines"),
            mapper = { snap ->
                snap.toObjects(
                    CloudClassRoutine::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.localId > 0L }
                    .map { it.docId to it.toEntity() }
            },
            getAllLocal = { localRepo.getAllClassRoutinesOnce() },
            entityId = { it.id },
            insertAllLocal = { localRepo.insertAllClassRoutines(it) },
            deleteLocal = { localRepo.deleteClassRoutine(it) },
            pushLocal = { upsertClassRoutineToCloud(uid, it) }
        )
    }

    private fun <T> launchCollectionSync(
        scope: CoroutineScope,
        uid: String,
        email: String,
        col: String,
        query: com.google.firebase.firestore.CollectionReference,
        mapper: (com.google.firebase.firestore.QuerySnapshot) -> List<Pair<String, T>>,
        getAllLocal: suspend () -> List<T>,
        entityId: (T) -> Int,
        insertAllLocal: suspend (List<T>) -> Unit,
        deleteLocal: suspend (Int) -> Unit,
        pushLocal: suspend (T) -> Result<Unit>
    ) {
        val path = query.path
        scope.launch {
            query.snapshots()
                .map { snap ->
                    Triple(mapper(snap), snap.metadata.isFromCache, snap.metadata.hasPendingWrites())
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                        _syncStatus.value = _syncStatus.value.copy(
                            state = SyncConnectionState.ERROR,
                            errorMessage = fsErr?.localizedMessage ?: error.localizedMessage
                        )
                    }
                }
                .collect { (remotePairs, fromCache, hasPending) ->
                    val localList = getAllLocal()
                    val localById = localList.associateBy { entityId(it) }
                    val remoteDocIds = remotePairs.map { it.first }.toSet()
                    val knownIds = getKnownIds(uid, col)

                    val toInsert = remotePairs.mapNotNull { (_, entity) ->
                        val id = entityId(entity)
                        val cur = localById[id]
                        if (cur == null || cur != entity) entity else null
                    }
                    if (toInsert.isNotEmpty()) {
                        insertAllLocal(toInsert)
                    }
                    addKnownIds(uid, col, remoteDocIds)

                    for (local in localList) {
                        val docId = recDocId(entityId(local))
                        if (docId !in remoteDocIds) {
                            if (docId !in knownIds) {
                                pushLocal(local)
                            } else if (!hasPending && !isPending(col, docId)) {
                                deleteLocal(entityId(local))
                                removeKnownId(uid, col, docId)
                            }
                        }
                    }

                    val now = formattedNow()
                    prefsFor(uid).edit().putString("last_sync_time", now).apply()
                    markLiveOrCached(email, fromCache, hasPending)
                }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
        activeUid = null
        _syncStatus.value = CloudSyncStatus(state = SyncConnectionState.IDLE)
    }

    private suspend fun upsertDocument(
        uid: String,
        col: String,
        docId: String,
        mutableFields: Map<String, Any>
    ): Result<Unit> {
        val docRef = db.collection("users").document(uid).collection(col).document(docId)
        val path = docRef.path
        markPending(col, docId)
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user mismatch; refusing cross-user write." }
            val knownIds = getKnownIds(uid, col)
            if (docId in knownIds) {
                try {
                    val updatePayload = mutableFields.toMutableMap().apply {
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    docRef.update(updatePayload).await()
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                        val createPayload = mutableFields.toMutableMap().apply {
                            put("userId", uid)
                            put("createdAt", FieldValue.serverTimestamp())
                            put("updatedAt", FieldValue.serverTimestamp())
                        }
                        docRef.set(createPayload).await()
                    } else {
                        throw e
                    }
                }
            } else {
                val createPayload = mutableFields.toMutableMap().apply {
                    put("userId", uid)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(createPayload).await()
                addKnownId(uid, col, docId)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        } finally {
            clearPending(col, docId)
        }
    }

    private suspend fun deleteDocument(uid: String, col: String, docId: String): Result<Unit> {
        val docRef = db.collection("users").document(uid).collection(col).document(docId)
        val path = docRef.path
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user mismatch; refusing cross-user delete." }
            removeKnownId(uid, col, docId)
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Entity Push / Delete Methods ---

    suspend fun pushInstitutionToCloud(uid: String, info: MadrasaInfo): Result<Unit> {
        val compactLogo = MadrasaLogoUtil.readLogoAsCompactBase64(info.logoPath)
        val logoHash = sha256(compactLogo)
        prefsFor(uid).edit().putString("synced_logo_hash", logoHash).apply()
        val fields = mapOf<String, Any>(
            "name" to info.name.trim().ifBlank { MadrasaInfoStore.DEFAULT_NAME }.take(200),
            "address" to info.address.trim().take(300),
            "phone" to info.phone.trim().take(40),
            "logoBase64" to compactLogo.take(600000)
        )
        return upsertDocument(uid, "institution", "profile", fields)
    }

    suspend fun upsertStudentToCloud(uid: String, s: StudentEntity): Result<Unit> {
        if (s.id <= 0) return Result.failure(IllegalArgumentException("Invalid student id"))
        val fields = mapOf<String, Any>(
            "localId" to s.id.toLong(),
            "name" to s.name.trim().ifBlank { "ছাত্র" }.take(150),
            "father" to s.father.trim().take(150),
            "phone" to s.phone.trim().take(40),
            "cls" to s.cls.trim().take(80),
            "fee" to s.fee.coerceAtLeast(0.0),
            "photoUri" to s.photoUri.trim().take(500),
            "roll" to s.roll.trim().take(40),
            "electricBill" to s.electricBill.coerceAtLeast(0.0)
        )
        return upsertDocument(uid, "students", recDocId(s.id), fields)
    }

    suspend fun deleteStudentFromCloud(uid: String, id: Int): Result<Unit> =
        deleteDocument(uid, "students", recDocId(id))

    suspend fun upsertFeeToCloud(uid: String, f: FeeEntity): Result<Unit> {
        if (f.id <= 0 || f.studentId <= 0) return Result.failure(IllegalArgumentException("Invalid fee id"))
        val fields = mapOf<String, Any>(
            "localId" to f.id.toLong(),
            "studentId" to f.studentId.toLong(),
            "amount" to f.amount.coerceAtLeast(0.0),
            "type" to f.type.trim().take(100),
            "month" to f.month.trim().take(60),
            "date" to f.date.trim().take(40)
        )
        return upsertDocument(uid, "fees", recDocId(f.id), fields)
    }

    suspend fun deleteFeeFromCloud(uid: String, id: Int): Result<Unit> =
        deleteDocument(uid, "fees", recDocId(id))

    suspend fun upsertAttendanceToCloud(uid: String, a: AttendanceEntity): Result<Unit> {
        val localId = if (a.id > 0) a.id else deterministicAttendanceId(a.date, a.studentId)
        if (a.studentId <= 0 || a.date.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid attendance record"))
        }
        val fields = mapOf<String, Any>(
            "localId" to localId.toLong(),
            "date" to a.date.trim().take(40),
            "studentId" to a.studentId.toLong(),
            "status" to a.status.trim().ifBlank { "উপস্থিত" }.take(40)
        )
        return upsertDocument(uid, "attendance", attendanceDocId(a.date, a.studentId), fields)
    }

    suspend fun upsertResultToCloud(uid: String, r: ResultEntity): Result<Unit> {
        val subjects = r.getSubjectList().take(40).map { sub ->
            mapOf<String, Any>(
                "name" to sub.name.trim().ifBlank { "বিষয়" }.take(100),
                "mark" to sub.mark.coerceAtLeast(0.0)
            )
        }
        if (r.id <= 0 || r.studentId <= 0 || subjects.isEmpty()) {
            return Result.failure(IllegalArgumentException("Invalid result record"))
        }
        val fields = mapOf<String, Any>(
            "localId" to r.id.toLong(),
            "studentId" to r.studentId.toLong(),
            "exam" to r.exam.trim().ifBlank { "পরীক্ষা" }.take(100),
            "subjects" to subjects,
            "total" to r.total.coerceAtLeast(0.0),
            "avg" to r.avg.coerceAtLeast(0.0),
            "date" to r.date.trim().take(40)
        )
        return upsertDocument(uid, "results", recDocId(r.id), fields)
    }

    suspend fun deleteResultFromCloud(uid: String, id: Int): Result<Unit> =
        deleteDocument(uid, "results", recDocId(id))

    suspend fun upsertTeacherToCloud(uid: String, t: TeacherEntity): Result<Unit> {
        if (t.id <= 0) return Result.failure(IllegalArgumentException("Invalid teacher id"))
        val fields = mapOf<String, Any>(
            "localId" to t.id.toLong(),
            "name" to t.name.trim().ifBlank { "শিক্ষক" }.take(150),
            "subject" to t.subject.trim().take(150),
            "salary" to t.salary.coerceAtLeast(0.0),
            "address" to t.address.trim().take(300),
            "phone" to t.phone.trim().take(40),
            "photoUri" to t.photoUri.trim().take(500),
            "joinDate" to t.joinDate.trim().take(40)
        )
        return upsertDocument(uid, "teachers", recDocId(t.id), fields)
    }

    suspend fun deleteTeacherFromCloud(uid: String, id: Int): Result<Unit> =
        deleteDocument(uid, "teachers", recDocId(id))

    suspend fun upsertTeacherPaymentToCloud(uid: String, p: TeacherPaymentEntity): Result<Unit> {
        if (p.id <= 0 || p.teacherId <= 0) return Result.failure(IllegalArgumentException("Invalid payment id"))
        val fields = mapOf<String, Any>(
            "localId" to p.id.toLong(),
            "teacherId" to p.teacherId.toLong(),
            "amount" to p.amount.coerceAtLeast(0.0),
            "month" to p.month.trim().take(60),
            "year" to p.year.trim().take(20),
            "date" to p.date.trim().take(40),
            "note" to p.note.trim().take(300)
        )
        return upsertDocument(uid, "teacherPayments", recDocId(p.id), fields)
    }

    suspend fun deleteTeacherPaymentFromCloud(uid: String, id: Int): Result<Unit> =
        deleteDocument(uid, "teacherPayments", recDocId(id))

    suspend fun upsertExpenseToCloud(uid: String, e: ExpenseEntity): Result<Unit> {
        if (e.id <= 0) return Result.failure(IllegalArgumentException("Invalid expense id"))
        val fields = mapOf<String, Any>(
            "localId" to e.id.toLong(),
            "type" to e.type.trim().ifBlank { "খরচ" }.take(80),
            "desc" to e.desc.trim().take(400),
            "amount" to e.amount.coerceAtLeast(0.0),
            "date" to e.date.trim().take(40)
        )
        return upsertDocument(uid, "expenses", recDocId(e.id), fields)
    }

    suspend fun deleteExpenseFromCloud(uid: String, id: Int): Result<Unit> =
        deleteDocument(uid, "expenses", recDocId(id))

    suspend fun upsertNoticeToCloud(uid: String, n: NoticeEntity): Result<Unit> {
        if (n.id <= 0) return Result.failure(IllegalArgumentException("Invalid notice id"))
        val fields = mapOf<String, Any>(
            "localId" to n.id.toLong(),
            "title" to n.title.trim().ifBlank { "নোটিশ" }.take(200),
            "description" to n.description.trim().take(3000),
            "date" to n.date.trim().take(40)
        )
        return upsertDocument(uid, "notices", recDocId(n.id), fields)
    }

    suspend fun deleteNoticeFromCloud(uid: String, id: Int): Result<Unit> =
        deleteDocument(uid, "notices", recDocId(id))

    suspend fun upsertQuestionPaperToCloud(uid: String, q: QuestionPaperEntity): Result<Unit> {
        if (q.id <= 0) return Result.failure(IllegalArgumentException("Invalid question paper id"))
        val fields = mapOf<String, Any>(
            "localId" to q.id.toLong(),
            "examName" to q.examName.trim().ifBlank { "পরীক্ষা" }.take(150),
            "className" to q.className.trim().take(100),
            "subject" to q.subject.trim().take(150),
            "fullMarks" to q.fullMarks.trim().take(40),
            "duration" to q.duration.trim().take(60),
            "instructions" to q.instructions.trim().take(1000),
            "questionsText" to q.questionsText.trim().take(20000),
            "date" to q.date.trim().take(40)
        )
        return upsertDocument(uid, "questionPapers", recDocId(q.id), fields)
    }

    suspend fun deleteQuestionPaperFromCloud(uid: String, id: Int): Result<Unit> =
        deleteDocument(uid, "questionPapers", recDocId(id))

    suspend fun upsertClassRoutineToCloud(uid: String, c: ClassRoutineEntity): Result<Unit> {
        if (c.id <= 0) return Result.failure(IllegalArgumentException("Invalid routine id"))
        val fields = mapOf<String, Any>(
            "localId" to c.id.toLong(),
            "className" to c.className.trim().ifBlank { "শ্রেণী" }.take(100),
            "dayOfWeek" to c.dayOfWeek.trim().take(60),
            "timeSlot" to c.timeSlot.trim().take(80),
            "subject" to c.subject.trim().take(150),
            "teacherName" to c.teacherName.trim().take(150),
            "duration" to c.duration.trim().take(60)
        )
        return upsertDocument(uid, "classRoutines", recDocId(c.id), fields)
    }

    suspend fun deleteClassRoutineFromCloud(uid: String, id: Int): Result<Unit> =
        deleteDocument(uid, "classRoutines", recDocId(id))

    suspend fun pushAllLocalDataToCloud(uid: String): Result<Int> {
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "User mismatch" }
            _syncStatus.value = _syncStatus.value.copy(
                state = SyncConnectionState.SYNCING,
                message = "সব তথ্য ক্লাউডে সিঙ্ক হচ্ছে..."
            )
            var pushedCount = 0
            pushInstitutionToCloud(uid, MadrasaInfoStore.getInfo(appContext)).onSuccess { pushedCount++ }
            localRepo.getAllStudentsOnce().forEach {
                upsertStudentToCloud(uid, it).onSuccess { pushedCount++ }
            }
            localRepo.getAllFeesOnce().forEach {
                upsertFeeToCloud(uid, it).onSuccess { pushedCount++ }
            }
            localRepo.getAllAttendanceOnce().forEach {
                upsertAttendanceToCloud(uid, it).onSuccess { pushedCount++ }
            }
            localRepo.getAllResultsOnce().forEach {
                upsertResultToCloud(uid, it).onSuccess { pushedCount++ }
            }
            localRepo.getAllTeachersOnce().forEach {
                upsertTeacherToCloud(uid, it).onSuccess { pushedCount++ }
            }
            localRepo.getAllTeacherPaymentsOnce().forEach {
                upsertTeacherPaymentToCloud(uid, it).onSuccess { pushedCount++ }
            }
            localRepo.getAllExpensesOnce().forEach {
                upsertExpenseToCloud(uid, it).onSuccess { pushedCount++ }
            }
            localRepo.getAllNoticesOnce().forEach {
                upsertNoticeToCloud(uid, it).onSuccess { pushedCount++ }
            }
            localRepo.getAllQuestionPapersOnce().forEach {
                upsertQuestionPaperToCloud(uid, it).onSuccess { pushedCount++ }
            }
            localRepo.getAllClassRoutinesOnce().forEach {
                upsertClassRoutineToCloud(uid, it).onSuccess { pushedCount++ }
            }
            val now = formattedNow()
            prefsFor(uid).edit().putString("last_sync_time", now).apply()
            _syncStatus.value = CloudSyncStatus(
                state = SyncConnectionState.LIVE,
                userEmail = auth.currentUser?.email.orEmpty(),
                lastSyncedTime = now,
                message = "মাল্টি-ডিভাইস লাইভ সিঙ্ক চালু আছে",
                errorMessage = null
            )
            Result.success(pushedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CloudSync", "Manual sync push failed", e)
            _syncStatus.value = _syncStatus.value.copy(
                state = SyncConnectionState.ERROR,
                errorMessage = e.localizedMessage
            )
            Result.failure(e)
        }
    }

    companion object {
        fun recDocId(localId: Int): String = "rec_$localId"

        fun attendanceDocId(date: String, studentId: Int): String {
            val cleanDate = date.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
            return "att_${cleanDate}_$studentId"
        }

        fun deterministicAttendanceId(date: String, studentId: Int): Int {
            val h = "${date.trim()}_$studentId".hashCode() and 0x7FFFFFFF
            return if (h == 0) 1 else h
        }
    }
}
