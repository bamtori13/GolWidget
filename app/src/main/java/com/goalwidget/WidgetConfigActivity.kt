package com.goalwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

class WidgetConfigActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val goals = mutableListOf<Goal>()
    private var selectedGoal: Goal? = null
    private var isReconfigure = false  // 길게 눌러 재설정 시 true → 목표 선택만 하고 바로 적용

    // Step 1 views
    private lateinit var stepSelectGoal: LinearLayout
    private lateinit var rvGoals: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnCreateNew: LinearLayout

    // Step 2 views
    private lateinit var stepOpacity: LinearLayout
    private lateinit var tvSelectedGoalName: TextView
    private lateinit var previewWidget: LinearLayout
    private lateinit var previewTitle: TextView
    private lateinit var previewValue: TextView
    private lateinit var previewRate: TextView
    private lateinit var seekbarOpacity: SeekBar
    private lateinit var tvOpacityValue: TextView
    private lateinit var btnOpacityConfirm: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED)

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        isReconfigure = intent.getBooleanExtra("reconfigure", false)
        if (isReconfigure) title = "표시할 목표 변경"

        setContentView(R.layout.activity_widget_config)
        title = "위젯 설정"

        // Step 1
        stepSelectGoal = findViewById(R.id.step_select_goal)
        rvGoals = findViewById(R.id.rv_config_goals)
        tvEmpty = findViewById(R.id.tv_config_empty)
        btnCreateNew = findViewById(R.id.btn_create_new)

        // Step 2
        stepOpacity = findViewById(R.id.step_opacity)
        tvSelectedGoalName = findViewById(R.id.tv_selected_goal_name)
        previewWidget = findViewById(R.id.preview_widget)
        previewTitle = findViewById(R.id.preview_title)
        previewValue = findViewById(R.id.preview_value)
        previewRate = findViewById(R.id.preview_rate)
        seekbarOpacity = findViewById(R.id.seekbar_opacity)
        tvOpacityValue = findViewById(R.id.tv_opacity_value)
        btnOpacityConfirm = findViewById(R.id.btn_opacity_confirm)

        rvGoals.layoutManager = LinearLayoutManager(this)
        rvGoals.adapter = GoalSelectAdapter()
        btnCreateNew.setOnClickListener { showCreateGoalDialog() }

        // 슬라이더 변경 시 미리보기 실시간 업데이트
        seekbarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val opacity = progress.coerceAtLeast(5) // 최소 5% (완전 투명 방지)
                tvOpacityValue.text = "$opacity%"
                updatePreview(opacity)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnOpacityConfirm.setOnClickListener { confirmWidget() }

        loadGoals()
    }

    private fun loadGoals() {
        goals.clear()
        goals.addAll(GoalRepository.loadGoals(this))
        rvGoals.adapter?.notifyDataSetChanged()
        tvEmpty.visibility = if (goals.isEmpty()) View.VISIBLE else View.GONE
        rvGoals.visibility = if (goals.isEmpty()) View.GONE else View.VISIBLE
    }

    // Step 1 완료: 목표 선택 → reconfigure면 바로 적용, 아니면 Step 2(투명도)로
    private fun goToOpacityStep(goal: Goal) {
        if (isReconfigure) {
            // 길게 눌러 재설정: 투명도는 유지하고 목표만 교체
            GoalRepository.setWidgetGoalId(this, widgetId, goal.id)
            GoalWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
            return
        }
        selectedGoal = goal

        // 이전에 저장된 투명도가 있으면 불러옴
        val savedOpacity = GoalRepository.getWidgetOpacity(this, widgetId)
        seekbarOpacity.progress = savedOpacity
        tvOpacityValue.text = "$savedOpacity%"

        // 미리보기 초기값 세팅
        val unit = if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""
        previewTitle.text = goal.name
        previewValue.text = "${GoalWidgetProvider.formatNum(goal.totalCurrent)} / ${GoalWidgetProvider.formatNum(goal.totalTarget)}$unit"
        previewRate.text = "${goal.achievementRate.roundToInt()}%"
        tvSelectedGoalName.text = "선택된 목표: ${goal.name}"

        updatePreview(savedOpacity)

        // Step 전환
        stepSelectGoal.visibility = View.GONE
        stepOpacity.visibility = View.VISIBLE
        title = "투명도 설정"
    }

    // 미리보기 배경색 알파 실시간 반영
    private fun updatePreview(opacity: Int) {
        val alpha = (opacity / 100.0 * 255).toInt().coerceIn(0, 255)
        val bgColor = (alpha shl 24) or 0x1E293B
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.displayMetrics.density * 16
            setColor(bgColor)
        }
        previewWidget.background = drawable
    }

    // Step 2 완료: 투명도 저장 → 위젯 등록
    private fun confirmWidget() {
        val goal = selectedGoal ?: return
        val opacity = seekbarOpacity.progress.coerceAtLeast(5)

        GoalRepository.setWidgetOpacity(this, widgetId, opacity)
        GoalRepository.setWidgetGoalId(this, widgetId, goal.id)
        GoalWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)

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

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) { etName.error = "목표 이름을 입력하세요"; return@setOnClickListener }
            val target = etTarget.text.toString().toDoubleOrNull()
            if (target == null || target <= 0) { etTarget.error = "0보다 큰 목표값을 입력하세요"; return@setOnClickListener }
            val current = etCurrent.text.toString().toDoubleOrNull() ?: 0.0
            val goal = Goal(name = name, unit = etUnit.text.toString().trim(),
                directTarget = target, directCurrent = current)
            GoalRepository.saveGoal(this, goal)
            dialog.dismiss()
            goToOpacityStep(goal)
        }
        dialog.show()
    }

    // 뒤로가기: Step 2 → Step 1
    override fun onBackPressed() {
        if (stepOpacity.visibility == View.VISIBLE) {
            stepOpacity.visibility = View.GONE
            stepSelectGoal.visibility = View.VISIBLE
            title = "위젯 설정"
        } else {
            super.onBackPressed()
        }
    }

    inner class GoalSelectAdapter : RecyclerView.Adapter<GoalSelectAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_goal_name)
            val tvRate: TextView = v.findViewById(R.id.tv_goal_rate)
            val tvValues: TextView = v.findViewById(R.id.tv_goal_values)
            val tvUnit: TextView = v.findViewById(R.id.tv_goal_unit)
            val vFill: View = v.findViewById(R.id.v_goal_progress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_goal, parent, false))

        override fun getItemCount() = goals.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val goal = goals[position]
            holder.tvName.text = goal.name
            val rate = goal.achievementRate
            holder.tvRate.text = "${rate.roundToInt()}%"
            val unit = if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""
            holder.tvValues.text = "${GoalWidgetProvider.formatNum(goal.totalCurrent)}$unit / ${GoalWidgetProvider.formatNum(goal.totalTarget)}$unit"
            holder.tvUnit.text = ""
            val container = holder.vFill.parent as FrameLayout
            container.post {
                val w = (container.width * rate / 100.0).toInt()
                holder.vFill.layoutParams = holder.vFill.layoutParams.also { it.width = w }
            }
            holder.itemView.setOnClickListener { goToOpacityStep(goal) }
        }
    }
}
