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
import kotlin.math.roundToInt
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fabAdd: View
    private val goals = mutableListOf<Goal>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = "목표 달성 관리"
        Log.d("MainActivity :", "title")

        recyclerView = findViewById(R.id.rv_goals)
        tvEmpty = findViewById(R.id.tv_empty)
        fabAdd = findViewById(R.id.fab_add)

        recyclerView.layoutManager = LinearLayoutManager(this)
        Log.d("MainActivity :", "layout")
        recyclerView.adapter = GoalAdapter()
        Log.d("MainActivity :", "goaladapter")
        fabAdd.setOnClickListener { showCreateGoalDialog() }
        Log.d("MainActivity :", "showDialog")
    }

    override fun onResume() {
        super.onResume()
        refreshGoals()
    }

    private fun refreshGoals() {
        goals.clear()
        goals.addAll(GoalRepository.loadGoals(this))
        recyclerView.adapter?.notifyDataSetChanged()
        tvEmpty.visibility = if (goals.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (goals.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateGoalDialog(existing: Goal? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_create_goal, null)
        val tvDlgTitle = view.findViewById<TextView>(R.id.tv_dialog_goal_title)
        val etName = view.findViewById<EditText>(R.id.et_goal_name)
        val etUnit = view.findViewById<EditText>(R.id.et_goal_unit)
        val etTarget = view.findViewById<EditText>(R.id.et_goal_target)
        val etCurrent = view.findViewById<EditText>(R.id.et_goal_current)
        val btnCancel = view.findViewById<TextView>(R.id.btn_goal_cancel)
        val btnSave = view.findViewById<TextView>(R.id.btn_goal_save)

        if (existing != null) {
            tvDlgTitle.text = "목표 편집"
            btnSave.text = "저장"
            etName.setText(existing.name)
            etUnit.setText(existing.unit)
            // 세부 항목이 없을 때만 직접값 표시
            if (existing.items.isEmpty()) {
                if (existing.target > 0)
                    etTarget.setText(GoalWidgetProvider.formatNum(existing.target))
                if (existing.directCurrent > 0)
                    etCurrent.setText(GoalWidgetProvider.formatNum(existing.directCurrent))
            } else {
                // 세부 항목 있으면 합산값 표시(편집 불가 안내)
                etTarget.setText(GoalWidgetProvider.formatNum(existing.target))
                etTarget.hint = "세부 항목 합계로 자동 계산됨"
                etCurrent.setText(GoalWidgetProvider.formatNum(existing.totalCurrent))
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) { etName.error = "목표 이름을 입력하세요"; return@setOnClickListener }

            val targetStr = etTarget.text.toString().trim().replace(",", "")
            val target = targetStr.toDoubleOrNull()
            if (target == null || target <= 0) {
                etTarget.error = "0보다 큰 목표값을 입력하세요"
                return@setOnClickListener
            }
            val current = etCurrent.text.toString().trim().replace(",", "").toDoubleOrNull() ?: 0.0

            if (existing != null) {
                existing.name = name
                existing.unit = etUnit.text.toString().trim()
                existing.target = target
                if (existing.items.isEmpty()) {
                    existing.directCurrent = current
                }
                GoalRepository.saveGoal(this, existing)
            } else {
                val goal = Goal(
                    name = name,
                    unit = etUnit.text.toString().trim(),
                    target = target,
                    directCurrent = current
                )
                GoalRepository.saveGoal(this, goal)
            }
            dialog.dismiss()
            refreshGoals()
            GoalWidgetProvider.updateAllWidgets(this)
        }
        dialog.show()
    }

    inner class GoalAdapter : RecyclerView.Adapter<GoalAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_goal_name)
            val tvRate: TextView = v.findViewById(R.id.tv_goal_rate)
            val tvValues: TextView = v.findViewById(R.id.tv_goal_values)
            val tvUnit: TextView = v.findViewById(R.id.tv_goal_unit)
            val vFill: View = v.findViewById(R.id.v_goal_progress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_goal, parent, false)
            return VH(v)
        }

        override fun getItemCount() = goals.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val goal = goals[position]
            holder.tvName.text = goal.name
            val rate = goal.achievementRate
            holder.tvRate.text = "${rate.roundToInt()}%"

            val color = when {
                rate >= 80 -> getColor(R.color.accent)
                rate >= 50 -> getColor(R.color.progress_medium)
                else -> getColor(R.color.progress_low)
            }
            holder.tvRate.setTextColor(color)

            val unit = if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""
            holder.tvValues.text = "달성 ${GoalWidgetProvider.formatNum(goal.totalCurrent)}$unit  /  목표 ${GoalWidgetProvider.formatNum(goal.target)}$unit"
            holder.tvUnit.text = ""

            // 프로그레스 바 (LinearLayout weight 방식)
            val container = holder.vFill.parent as FrameLayout
            container.post {
                val w = (container.width * rate / 100.0).toInt()
                holder.vFill.layoutParams = holder.vFill.layoutParams.also { it.width = w }
            }

            // 탭 → 세부 화면
            holder.itemView.setOnClickListener {
                startActivity(Intent(this@MainActivity, DetailActivity::class.java)
                    .putExtra("goal_id", goal.id))
            }

            // 길게 탭 → 편집 or 삭제
            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(goal.name)
                    .setItems(arrayOf("편집", "삭제")) { _, which ->
                        when (which) {
                            0 -> showCreateGoalDialog(goal)
                            1 -> AlertDialog.Builder(this@MainActivity)
                                .setMessage("'${goal.name}' 목표를 삭제하시겠습니까?")
                                .setPositiveButton("삭제") { _, _ ->
                                    GoalRepository.deleteGoal(this@MainActivity, goal.id)
                                    refreshGoals()
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
