package com.goalwidget

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import android.util.Log

/**
 * Firebase Realtime Database 동기화 매니저
 *
 * DB 구조:
 * /groups/{groupCode}/
 *   meta/
 *     createdAt: Long
 *     memberCount: Int
 *   goals/{goalId}/
 *     id, name, unit, directCurrent
 *     items/{itemId}/
 *       id, name, currentValue
 *
 * /users/{uid}/groupCode: String   ← 내가 속한 그룹 코드
 */
object SyncManager {

    private const val PREF_SYNC = "sync_prefs"
    private const val KEY_GROUP_CODE = "group_code"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_SYNC_ENABLED = "sync_enabled"

    private val auth get() = Firebase.auth

    // Firebase SDK가 google-services.json에서 읽은 databaseURL을 그대로 사용.
    // databaseURL 필드가 json에 없으면 직접 프로젝트ID로 구성.
    private val db by lazy {
        val options = com.google.firebase.FirebaseApp.getInstance().options
        val url = options.databaseUrl
            ?: "https://${options.projectId}-default-rtdb.firebaseio.com"
        Log.d("SyncManager", "DB URL: $url")
        com.google.firebase.database.FirebaseDatabase.getInstance(url).reference
    }

    // ── 로그인 (익명 로그인: 서버 없이 uid만 확보) ──────────────────────────
    fun ensureSignedIn(onDone: (uid: String) -> Unit, onError: (Exception) -> Unit) {
        val current = auth.currentUser
        if (current != null) {
            Log.d("SyncManager", "이미 로그인됨 - uid=${current.uid}")
            onDone(current.uid)
            return
        }
        Log.d("SyncManager", "익명 로그인 시도 중...")
        auth.signInAnonymously()
            .addOnSuccessListener {
                Log.d("SyncManager", "익명 로그인 성공 - uid=${it.user!!.uid}")
                onDone(it.user!!.uid)
            }
            .addOnFailureListener {
                Log.e("SyncManager", "익명 로그인 실패", it)
                onError(it)
            }
    }

    // ── 설정값 저장/로드 ────────────────────────────────────────────────────
    fun getGroupCode(ctx: Context): String? =
        ctx.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).getString(KEY_GROUP_CODE, null)

    fun getNickname(ctx: Context): String =
        ctx.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            .getString(KEY_NICKNAME, "나") ?: "나"

    fun isSyncEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            .getBoolean(KEY_SYNC_ENABLED, false)

    private fun saveGroupCode(ctx: Context, code: String?) {
        ctx.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            .edit().putString(KEY_GROUP_CODE, code).apply()
    }

    fun saveNickname(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            .edit().putString(KEY_NICKNAME, name).apply()
    }

    private fun setSyncEnabled(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SYNC_ENABLED, v).apply()
    }

    // ── 그룹 생성 ──────────────────────────────────────────────────────────
    fun createGroup(
        ctx: Context,
        nickname: String,
        goals: List<Goal>,
        onSuccess: (code: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedIn({ uid ->
            val code = generateCode()
            val groupRef = db.child("groups").child(code)

            // 메타 정보
            groupRef.child("meta").setValue(mapOf(
                "createdAt" to System.currentTimeMillis(),
                "creatorUid" to uid,
                "creatorNickname" to nickname
            ))

            // 현재 목표 전체 업로드
            goals.forEach { goal -> pushGoal(code, goal) }

            // 내 uid에 그룹 코드 기록
            db.child("users").child(uid).child("groupCode").setValue(code)

            saveGroupCode(ctx, code)
            saveNickname(ctx, nickname)
            setSyncEnabled(ctx, true)
            onSuccess(code)
        }, onError)
    }

    // ── 그룹 참여 ──────────────────────────────────────────────────────────
    fun joinGroup(
        ctx: Context,
        code: String,
        nickname: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val normalizedCode = code.trim().uppercase()
        Log.d("SyncManager", "joinGroup() 시작 - code=$normalizedCode")

        ensureSignedIn({ uid ->
            Log.d("SyncManager", "로그인 성공 - uid=$uid")
            Log.d("SyncManager", "Firebase DB 조회 시작: groups/$normalizedCode/meta")

            val ref = db.child("groups").child(normalizedCode).child("meta")
            Log.d("SyncManager", "DB 조회 시작: ${ref} (URL: ${db.toString()})")

            // 타임아웃: 10초 안에 응답 없으면 에러 처리
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var responded = false
            val timeoutRunnable = Runnable {
                if (!responded) {
                    responded = true
                    Log.e("SyncManager", "DB 조회 타임아웃 (10초) - DB URL을 확인하세요")
                    onError("연결 시간 초과\n\nFIREBASE_SETUP.md의 DB URL 설정을 확인하세요.\n현재 URL: ${db}")
                }
            }
            handler.postDelayed(timeoutRunnable, 10_000)

            ref.get()
                .addOnSuccessListener { snap ->
                    if (responded) return@addOnSuccessListener
                    responded = true
                    handler.removeCallbacks(timeoutRunnable)
                    Log.d("SyncManager", "DB 응답 수신 - exists=${snap.exists()}, value=${snap.value}")
                    if (!snap.exists()) {
                        val msg = "초대 코드 '$normalizedCode' 를 찾을 수 없습니다."
                        Log.w("SyncManager", msg)
                        onError(msg)
                        return@addOnSuccessListener
                    }
                    db.child("users").child(uid).child("groupCode").setValue(normalizedCode)
                    saveGroupCode(ctx, normalizedCode)
                    saveNickname(ctx, nickname)
                    setSyncEnabled(ctx, true)
                    Log.d("SyncManager", "joinGroup 성공 - onSuccess 호출")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    if (responded) return@addOnFailureListener
                    responded = true
                    handler.removeCallbacks(timeoutRunnable)
                    Log.e("SyncManager", "DB 조회 실패", e)
                    onError(e.message ?: "연결 오류")
                }
        }, { e ->
            Log.e("SyncManager", "익명 로그인 실패", e)
            onError(e.message ?: "로그인 오류")
        })
    }

    // ── 그룹 탈퇴 ──────────────────────────────────────────────────────────
    fun leaveGroup(ctx: Context, onDone: () -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.child("users").child(uid).child("groupCode").removeValue()
        }
        saveGroupCode(ctx, null)
        setSyncEnabled(ctx, false)
        onDone()
    }

    // ── 목표 업로드 (생성/수정) ────────────────────────────────────────────
    fun pushGoal(code: String, goal: Goal) {
        val goalMap = mutableMapOf<String, Any>(
            "id" to goal.id,
            "name" to goal.name,
            "unit" to goal.unit,
            "target" to goal.target,
            "directCurrent" to goal.directCurrent
        )
        val ref = db.child("groups").child(code).child("goals").child(goal.id)
        ref.setValue(goalMap)

        // 세부 항목
        val itemsRef = ref.child("items")
        if (goal.items.isEmpty()) {
            itemsRef.removeValue()
        } else {
            goal.items.forEach { item ->
                itemsRef.child(item.id).setValue(mapOf(
                    "id" to item.id,
                    "name" to item.name,
                    "currentValue" to item.currentValue
                ))
            }
        }
    }

    // ── 목표 삭제 ──────────────────────────────────────────────────────────
    fun deleteGoalRemote(code: String, goalId: String) {
        db.child("groups").child(code).child("goals").child(goalId).removeValue()
    }

    // ── 실시간 구독 (목표 목록 전체) ──────────────────────────────────────
    fun listenGoals(
        code: String,
        onUpdate: (List<Goal>) -> Unit
    ): ValueEventListener {
        val ref = db.child("groups").child(code).child("goals")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("SyncManager", "listenGoals onDataChange - ${snapshot.childrenCount}개 목표")
                val goals = mutableListOf<Goal>()
                for (goalSnap in snapshot.children) {
                    val goal = parseGoal(goalSnap) ?: continue
                    goals.add(goal)
                }
                onUpdate(goals)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("SyncManager", "listenGoals 취소됨 - code=${error.code} msg=${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeListener(code: String, listener: ValueEventListener) {
        db.child("groups").child(code).child("goals").removeEventListener(listener)
    }

    // ── DataSnapshot → Goal 파싱 ───────────────────────────────────────────
    private fun parseGoal(snap: DataSnapshot): Goal? {
        return try {
            val id = snap.child("id").getValue(String::class.java) ?: return null
            val name = snap.child("name").getValue(String::class.java) ?: ""
            val unit = snap.child("unit").getValue(String::class.java) ?: ""
            val target = snap.child("target").getValue(Double::class.java) ?: 0.0
            val directCurrent = snap.child("directCurrent").getValue(Double::class.java) ?: 0.0

            val items = mutableListOf<GoalItem>()
            for (itemSnap in snap.child("items").children) {
                val itemId = itemSnap.child("id").getValue(String::class.java) ?: continue
                val itemName = itemSnap.child("name").getValue(String::class.java) ?: ""
                val cur = itemSnap.child("currentValue").getValue(Double::class.java) ?: 0.0
                items.add(GoalItem(id = itemId, name = itemName, currentValue = cur))
            }

            Goal(id = id, name = name, unit = unit,
                target = target, directCurrent = directCurrent,
                items = items)
        } catch (e: Exception) { null }
    }

    // ── 6자리 대문자 코드 생성 ─────────────────────────────────────────────
    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 혼동 문자 제외
        return (1..6).map { chars.random() }.joinToString("")
    }
}
