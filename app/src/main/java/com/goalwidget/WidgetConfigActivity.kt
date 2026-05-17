package com.goalwidget

import android.appwidget.AppWidgetManager
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

class WidgetConfigActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val goals = mutableListOf<Goal>()
    private lateinit var rvGoals: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnCreateNew: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 위젯 ID 획득 - 없으면 CANCELED로 종료
        widgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 반드시 CANCELED 먼저 설정 (백버튼 시 위젯 추가 취소)
        setResult(RESULT_CANCELED)

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_widget_config)
        title = "위젯 목표 선택"

        rvGoals = findViewById(R.id.rv_config_goals)
        tvEmpty = findViewById(R.id.tv_config_empty)
        btnCreateNew = findViewById(R.id.btn_create_new)

        rvGoals.layoutManager = LinearLayoutManager(this)
        rvGoals.adapter = GoalSelectAdapter()

        btnCreateNew.setOnClickListener { showCreateGoalDialog() }

        loadGoals()
    }

    private fun loadGoals() {
        goals.clear()
        goals.addAll(GoalRepository.loadGoals(this))
        rvGoals.adapter?.notifyDataSetChanged()
        tvEmpty.visibility = if (goals.isEmpty()) View.VISIBLE else View.GONE
        rvGoals.visibility = if (goals.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun selectGoal(goal: Goal) {
        GoalRepository.setWidgetGoalId(this, widgetId, goal.id)
        GoalWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)

        // 성공 결과 반환
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun showCreateGoalDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_create_goal, null)
        val etName = view.findViewById<EditText>(R.id.et_goal_name)
        val etUnit = view.findViewById<EditText>(R.id.et_goal_unit)
        val etTarget = view.findViewById<EditText>(R.id.et_goal_target)
        val etCurrent = view.findViewById<EditText>(R.id.et_goal_current)
        val btnCancel = view.findViewById<TextView>(R.id.btn_goal_cancel)
        val btnSave = view.findViewById<TextView>(R.id.btn_goal_save)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) { etName.error = "목표 이름을 입력하세요"; return@setOnClickListener }
            val target = etTarget.text.toString().toDoubleOrNull()
            if (target == null || target <= 0) { etTarget.error = "0보다 큰 목표값을 입력하세요"; return@setOnClickListener }
            val current = etCurrent.text.toString().toDoubleOrNull() ?: 0.0

            val goal = Goal(
                name = name,
                unit = etUnit.text.toString().trim(),
                target = target,
                directCurrent = current
            )
            GoalRepository.saveGoal(this, goal)
            dialog.dismiss()
            selectGoal(goal)
        }
        dialog.show()
    }

    inner class GoalSelectAdapter : RecyclerView.Adapter<GoalSelectAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_goal_name)
            val tvRate: TextView = v.findViewById(R.id.tv_goal_rate)
            val tvValues: TextView = v.findViewById(R.id.tv_goal_values)
            val tvUnit: TextView = v.findViewById(R.id.tv_goal_unit)
            val vFill: View = v.findViewById(R.id.v_goal_progress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_goal, parent, false)
            return VH(v)
        }

        override fun getItemCount() = goals.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val goal = goals[position]
            holder.tvName.text = goal.name
            val rate = goal.achievementRate
            holder.tvRate.text = "${rate.roundToInt()}%"
            val unit = if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""
            holder.tvValues.text = "${GoalWidgetProvider.formatNum(goal.totalCurrent)}$unit / ${GoalWidgetProvider.formatNum(goal.target)}$unit"
            holder.tvUnit.text = ""

            val container = holder.vFill.parent as FrameLayout
            container.post {
                val w = (container.width * rate / 100.0).toInt()
                holder.vFill.layoutParams = holder.vFill.layoutParams.also { it.width = w }
            }

            holder.itemView.setOnClickListener { selectGoal(goal) }
        }
    }
}
