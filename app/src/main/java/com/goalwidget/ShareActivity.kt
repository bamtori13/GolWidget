package com.goalwidget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ShareActivity : AppCompatActivity() {

    private lateinit var vStatusDot: View
    private lateinit var tvStatus: TextView
    private lateinit var layoutGroupCode: LinearLayout
    private lateinit var tvGroupCode: TextView
    private lateinit var btnCopyCode: TextView
    private lateinit var btnLeave: TextView
    private lateinit var etNickname: EditText
    private lateinit var btnSaveNickname: TextView
    private lateinit var cardCreate: LinearLayout
    private lateinit var cardJoin: LinearLayout
    private lateinit var btnCreateGroup: TextView
    private lateinit var etInviteCode: EditText
    private lateinit var btnJoinGroup: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "가족 공유"

        vStatusDot = findViewById(R.id.v_status_dot)
        tvStatus = findViewById(R.id.tv_status)
        layoutGroupCode = findViewById(R.id.layout_group_code)
        tvGroupCode = findViewById(R.id.tv_group_code)
        btnCopyCode = findViewById(R.id.btn_copy_code)
        btnLeave = findViewById(R.id.btn_leave)
        etNickname = findViewById(R.id.et_nickname)
        btnSaveNickname = findViewById(R.id.btn_save_nickname)
        cardCreate = findViewById(R.id.card_create)
        cardJoin = findViewById(R.id.card_join)
        btnCreateGroup = findViewById(R.id.btn_create_group)
        etInviteCode = findViewById(R.id.et_invite_code)
        btnJoinGroup = findViewById(R.id.btn_join_group)

        // 닉네임 불러오기
        etNickname.setText(SyncManager.getNickname(this))

        btnSaveNickname.setOnClickListener {
            val name = etNickname.text.toString().trim()
            if (name.isEmpty()) { toast("닉네임을 입력하세요"); return@setOnClickListener }
            SyncManager.saveNickname(this, name)
            toast("닉네임 저장됨")
        }

        btnCopyCode.setOnClickListener {
            val code = tvGroupCode.text.toString()
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("초대코드", code))
            toast("코드가 복사되었습니다: $code")
        }

        btnCreateGroup.setOnClickListener { doCreateGroup() }
        btnJoinGroup.setOnClickListener { doJoinGroup() }
        btnLeave.setOnClickListener { confirmLeave() }

        refreshUI()
    }

    private fun refreshUI() {
        val synced = SyncManager.isSyncEnabled(this)
        val code = SyncManager.getGroupCode(this)

        if (synced && code != null) {
            // 공유 중
            vStatusDot.setBackgroundResource(R.drawable.fab_background)  // 파란 점
            tvStatus.text = "공유 중"
            tvStatus.setTextColor(getColor(R.color.primary))
            layoutGroupCode.visibility = View.VISIBLE
            tvGroupCode.text = code
            btnLeave.visibility = View.VISIBLE
            cardCreate.visibility = View.GONE
            cardJoin.visibility = View.GONE
        } else {
            // 비활성
            vStatusDot.setBackgroundColor(getColor(R.color.text_hint))
            tvStatus.text = "공유 비활성"
            tvStatus.setTextColor(getColor(R.color.text_secondary))
            layoutGroupCode.visibility = View.GONE
            btnLeave.visibility = View.GONE
            cardCreate.visibility = View.VISIBLE
            cardJoin.visibility = View.VISIBLE
        }
    }

    private fun doCreateGroup() {
        val nickname = etNickname.text.toString().trim().ifEmpty { "나" }
        SyncManager.saveNickname(this, nickname)

        val goals = GoalRepository.loadGoals(this)
        showLoading("그룹 생성 중...") { dismiss ->
            SyncManager.createGroup(
                ctx = this,
                nickname = nickname,
                goals = goals,
                onSuccess = { code ->
                    dismiss()
                    refreshUI()
                    AlertDialog.Builder(this)
                        .setTitle("그룹 생성 완료!")
                        .setMessage("초대 코드: $code\n\n이 코드를 가족에게 알려주세요.\n가족이 '초대 코드로 참여'에서 입력하면 실시간 공유가 시작됩니다.")
                        .setPositiveButton("확인", null)
                        .show()
                },
                onError = { e ->
                    dismiss()
                    toast("오류: ${e.message}")
                }
            )
        }
    }

    private fun doJoinGroup() {
        val code = etInviteCode.text.toString().trim().uppercase()
        if (code.length != 6) { toast("6자리 코드를 입력하세요"); return }
        val nickname = etNickname.text.toString().trim().ifEmpty { "나" }
        SyncManager.saveNickname(this, nickname)

        showLoading("참여 중...") { dismiss ->
            SyncManager.joinGroup(
                ctx = this,
                code = code,
                nickname = nickname,
                onSuccess = {
                    // Firebase .get() 콜백은 메인 스레드에서 호출됨
                    // dismiss() 안에도 runOnUiThread가 있으므로 중첩 금지
                    dismiss()
                    refreshUI()
                    toast("그룹에 참여했습니다!")
                },
                onError = { msg ->
                    dismiss()
                    toast(msg)
                }
            )
        }
    }

    private fun mergeRemoteGoals(remoteGoals: List<Goal>) {
        // 로컬에 없는 원격 목표는 추가, 있는 건 원격 데이터로 덮어씀
        remoteGoals.forEach { GoalRepository.saveGoal(this, it) }
        GoalWidgetProvider.updateAllWidgets(this)
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle("공유 그룹 나가기")
            .setMessage("그룹을 나가면 실시간 공유가 중단됩니다.\n기존 데이터는 기기에 남습니다.\n\n계속하시겠습니까?")
            .setPositiveButton("나가기") { _, _ ->
                SyncManager.leaveGroup(this) { refreshUI(); toast("공유를 종료했습니다") }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showLoading(msg: String, block: (dismiss: () -> Unit) -> Unit) {
        val dlg = AlertDialog.Builder(this)
            .setMessage(msg)
            .setCancelable(false)
            .create()
        dlg.show()
        // Firebase .get() / signInAnonymously 콜백은 메인 스레드에서 호출됨
        // dismiss는 단순히 다이얼로그 닫기만 하면 됨
        block { if (dlg.isShowing) dlg.dismiss() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
