package com.goalwidget

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.ValueEventListener
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fabAdd: FrameLayout
    private lateinit var fabShare: FrameLayout
    private lateinit var bannerSync: LinearLayout
    private lateinit var tvSyncBanner: TextView

    private val goals = mutableListOf<Goal>()
    private var syncListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "목표 달성 관리"

        recyclerView  = findViewById(R.id.rv_goals)
        tvEmpty       = findViewById(R.id.tv_empty)
        fabAdd        = findViewById(R.id.fab_add)
        fabShare      = findViewById(R.id.fab_share)
        bannerSync    = findViewById(R.id.banner_sync)
        tvSyncBanner  = findViewById(R.id.tv_sync_banner)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = GoalAdapter()

        fabAdd.setOnClickListener { showGoalDialog() }
        fabShare.setOnClickListener { startActivity(Intent(this, ShareActivity::class.java)) }
        bannerSync.setOnClickListener { startActivity(Intent(this, ShareActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refreshGoals(); updateSyncBanner(); startSyncIfNeeded()
    }

    override fun onPause() { super.onPause(); stopSync() }

    private fun startSyncIfNeeded() {
        val code = SyncManager.getGroupCode(this) ?: return
        if (!SyncManager.isSyncEnabled(this)) return
        syncListener = SyncManager.listenGoals(code) { remoteGoals ->
            runOnUiThread {
                remoteGoals.forEach { GoalRepository.saveGoal(this, it) }
                refreshGoals(); GoalWidgetProvider.updateAllWidgets(this)
            }
        }
    }

    private fun stopSync() {
        val code = SyncManager.getGroupCode(this)
        syncListener?.let { if (code != null) SyncManager.removeListener(code, it) }
        syncListener = null
    }

    private fun updateSyncBanner() {
        if (SyncManager.isSyncEnabled(this)) {
            tvSyncBanner.text = "● 가족 공유 중  (코드: ${SyncManager.getGroupCode(this) ?: ""})"
            bannerSync.visibility = View.VISIBLE
        } else bannerSync.visibility = View.GONE
    }

    private fun refreshGoals() {
        goals.clear(); goals.addAll(GoalRepository.loadGoals(this))
        recyclerView.adapter?.notifyDataSetChanged()
        tvEmpty.visibility     = if (goals.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (goals.isEmpty()) View.GONE   else View.VISIBLE
    }

    private fun saveGoalSynced(goal: Goal) {
        GoalRepository.saveGoal(this, goal)
        val code = SyncManager.getGroupCode(this)
        if (code != null && SyncManager.isSyncEnabled(this)) SyncManager.pushGoal(code, goal)
    }

    private fun deleteGoalSynced(goalId: String) {
        GoalRepository.deleteGoal(this, goalId)
        val code = SyncManager.getGroupCode(this)
        if (code != null && SyncManager.isSyncEnabled(this)) SyncManager.deleteGoalRemote(code, goalId)
    }

    // ── 목표 생성/편집 다이얼로그 ────────────────────────────────────────
    fun showGoalDialog(existing: Goal? = null) {
        val view       = layoutInflater.inflate(R.layout.dialog_create_goal, null)
        val tvTitle    = view.findViewById<TextView>(R.id.tv_dialog_goal_title)
        val etName     = view.findViewById<EditText>(R.id.et_goal_name)
        val etUnit     = view.findViewById<EditText>(R.id.et_goal_unit)
        val etTarget   = view.findViewById<EditText>(R.id.et_goal_target)
        val etCurrent  = view.findViewById<EditText>(R.id.et_goal_current)
        val palette    = view.findViewById<LinearLayout>(R.id.color_palette)
        val btnCancel  = view.findViewById<TextView>(R.id.btn_goal_cancel)
        val btnSave    = view.findViewById<TextView>(R.id.btn_goal_save)

        var selectedColor = existing?.colorHex ?: ColorPalette.COLORS[0]

        // 색상 팔레트 설정
        ColorPalette.setup(this, palette, selectedColor) { hex -> selectedColor = hex }

        if (existing != null) {
            tvTitle.text  = "목표 편집"; btnSave.text = "저장"
            etName.setText(existing.name);  etName.setSelection(etName.text.length)
            etUnit.setText(existing.unit);  etUnit.setSelection(etUnit.text.length)
            val hasItems = existing.items.isNotEmpty()
            if (!hasItems) {
                if (existing.directTarget > 0) {
                    etTarget.setText(GoalWidgetProvider.formatNum(existing.directTarget))
                    etTarget.setSelection(etTarget.text.length)  // ← 커서 끝으로
                }
                if (existing.directCurrent > 0) {
                    etCurrent.setText(GoalWidgetProvider.formatNum(existing.directCurrent))
                    etCurrent.setSelection(etCurrent.text.length)  // ← 커서 끝으로
                }
            } else {
                etTarget.setText(GoalWidgetProvider.formatNum(existing.totalTarget))
                etTarget.isEnabled = false; etTarget.hint = "세부 항목 합계로 자동 계산됨"
                etCurrent.setText(GoalWidgetProvider.formatNum(existing.totalCurrent))
                etCurrent.isEnabled = false
            }
        }

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name     = etName.text.toString().trim()
            if (name.isEmpty()) { etName.error = "목표 이름을 입력하세요"; return@setOnClickListener }

            val hasItems = existing?.items?.isNotEmpty() == true
            val target: Double
            if (hasItems) {
                target = existing!!.totalTarget
            } else {
                val parsed = etTarget.text.toString().toDoubleOrNull()
                if (parsed == null || parsed <= 0) { etTarget.error = "0보다 큰 목표값을 입력하세요"; return@setOnClickListener }
                target = parsed
            }
            val current = if (hasItems) existing!!.totalCurrent
                          else etCurrent.text.toString().toDoubleOrNull() ?: 0.0

            val goal = if (existing != null) {
                existing.apply {
                    this.name = name; this.unit = etUnit.text.toString().trim()
                    this.colorHex = selectedColor
                    if (!hasItems) { directTarget = target; directCurrent = current }
                }
            } else {
                Goal(name = name, unit = etUnit.text.toString().trim(),
                    directTarget = target, directCurrent = current, colorHex = selectedColor)
            }

            saveGoalSynced(goal)
            dialog.dismiss(); refreshGoals(); GoalWidgetProvider.updateAllWidgets(this)
        }
        dialog.show()
    }

    // ── RecyclerView 어댑터 ──────────────────────────────────────────────
    inner class GoalAdapter : RecyclerView.Adapter<GoalAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName:   TextView = v.findViewById(R.id.tv_goal_name)
            val tvRate:   TextView = v.findViewById(R.id.tv_goal_rate)
            val tvValues: TextView = v.findViewById(R.id.tv_goal_values)
            val tvUnit:   TextView = v.findViewById(R.id.tv_goal_unit)
            val vFill:    View     = v.findViewById(R.id.v_goal_progress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_goal, parent, false))

        override fun getItemCount() = goals.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val goal  = goals[position]
            val rate  = goal.achievementRate
            val color = goal.resolveColor()
            val unit  = if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""

            holder.tvName.text   = goal.name
            holder.tvRate.text   = "${rate.roundToInt()}%"
            holder.tvRate.setTextColor(color)
            holder.tvValues.text = "달성 ${GoalWidgetProvider.formatNum(goal.totalCurrent)}$unit  /  목표 ${GoalWidgetProvider.formatNum(goal.totalTarget)}$unit"
            holder.tvUnit.text   = ""

            // 프로그레스 바 색상 적용
            val container = holder.vFill.parent as FrameLayout
            container.post {
                val w = (container.width * rate / 100.0).toInt()
                holder.vFill.layoutParams = holder.vFill.layoutParams.also { it.width = w }
                holder.vFill.setBackgroundColor(color)
            }

            holder.itemView.setOnClickListener {
                startActivity(Intent(this@MainActivity, DetailActivity::class.java)
                    .putExtra("goal_id", goal.id))
            }
            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(goal.name)
                    .setItems(arrayOf("편집", "삭제")) { _, which ->
                        when (which) {
                            0 -> showGoalDialog(goal)
                            1 -> AlertDialog.Builder(this@MainActivity)
                                .setMessage("'${goal.name}' 목표를 삭제하시겠습니까?")
                                .setPositiveButton("삭제") { _, _ ->
                                    deleteGoalSynced(goal.id); refreshGoals()
                                    GoalWidgetProvider.updateAllWidgets(this@MainActivity)
                                }
                                .setNegativeButton("취소", null).show()
                        }
                    }.show()
                true
            }
        }
    }
}
