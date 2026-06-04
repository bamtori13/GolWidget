package com.goalwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
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

class DetailActivity : AppCompatActivity() {

    private var goalId     = ""
    private var goalLoaded = false
    private lateinit var goal: Goal

    private lateinit var tvTitle:   TextView
    private lateinit var tvRate:    TextView
    private lateinit var tvCurrent: TextView
    private lateinit var tvTarget:  TextView
    private lateinit var vProgress: View
    private lateinit var rvItems:   RecyclerView
    private lateinit var tvEmpty:   TextView
    private lateinit var btnAdd:    LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        goalId = intent.getStringExtra("goal_id") ?: ""
        if (goalId.isEmpty()) { finish(); return }

        tvTitle   = findViewById(R.id.tv_detail_title)
        tvRate    = findViewById(R.id.tv_detail_rate)
        tvCurrent = findViewById(R.id.tv_detail_current)
        tvTarget  = findViewById(R.id.tv_detail_target)
        vProgress = findViewById(R.id.v_detail_progress)
        rvItems   = findViewById(R.id.rv_items)
        tvEmpty   = findViewById(R.id.tv_items_empty)
        btnAdd    = findViewById(R.id.btn_add_item)

        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = ItemAdapter()

        btnAdd.setOnClickListener { showItemDialog(null) }
        findViewById<TextView>(R.id.btn_widget_opacity).setOnClickListener { showOpacityDialog() }
    }

    override fun onResume() {
        super.onResume()
        if (!goalLoaded) {
            val loaded = GoalRepository.getGoal(this, goalId)
            if (loaded == null) { finish(); return }
            goal = loaded; goalLoaded = true
        }
        refreshUI()
    }

    private fun saveGoalSynced() {
        GoalRepository.saveGoal(this, goal)
        val code = SyncManager.getGroupCode(this)
        if (code != null && SyncManager.isSyncEnabled(this)) SyncManager.pushGoal(code, goal)
        GoalWidgetProvider.updateAllWidgets(this)
    }

    private fun refreshUI() {
        val color = goal.resolveColor()
        val unit  = if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""
        val rate  = goal.achievementRate

        title = goal.name
        tvTitle.text   = goal.name
        tvRate.text    = "${rate.roundToInt()}%"
        tvCurrent.text = "달성: ${GoalWidgetProvider.formatNum(goal.totalCurrent)}$unit"
        tvTarget.text  = "목표: ${GoalWidgetProvider.formatNum(goal.totalTarget)}$unit"

        // 헤더 색상 적용
        findViewById<LinearLayout>(R.id.header_card).setBackgroundColor(color)

        // 프로그레스 바 색상
        val progressParent = vProgress.parent as FrameLayout
        vProgress.setBackgroundColor(color)
        progressParent.post {
            val w = (progressParent.width * rate / 100.0).toInt()
            vProgress.layoutParams = vProgress.layoutParams.also { it.width = w }
        }

        rvItems.adapter?.notifyDataSetChanged()
        val hasItems = goal.items.isNotEmpty()
        rvItems.visibility = if (hasItems) View.VISIBLE else View.GONE
        tvEmpty.visibility = if (hasItems) View.GONE   else View.VISIBLE
        tvEmpty.text = if (!hasItems && goal.directTarget > 0)
            "달성 ${GoalWidgetProvider.formatNum(goal.directCurrent)} / 목표 ${GoalWidgetProvider.formatNum(goal.directTarget)}$unit\n\n아래 버튼으로 세부 항목을 추가하세요."
        else "세부 항목이 없습니다.\n아래 버튼으로 항목을 추가하세요."
    }

    private fun showItemDialog(existing: GoalItem?) {
        val view      = layoutInflater.inflate(R.layout.dialog_item_edit, null)
        val tvTitle   = view.findViewById<TextView>(R.id.tv_dialog_title)
        val etName    = view.findViewById<EditText>(R.id.et_item_name)
        val etValue   = view.findViewById<EditText>(R.id.et_item_value)
        val etTarget  = view.findViewById<EditText>(R.id.et_item_target)
        val btnCancel = view.findViewById<TextView>(R.id.btn_dialog_cancel)
        val btnSave   = view.findViewById<TextView>(R.id.btn_dialog_save)

        if (existing != null) {
            tvTitle.text = "항목 편집"
            etName.setText(existing.name);  etName.setSelection(etName.text.length)
            etValue.setText(GoalWidgetProvider.formatNum(existing.currentValue))
            etValue.setSelection(etValue.text.length)   // ← 커서 끝으로
            etTarget.setText(GoalWidgetProvider.formatNum(existing.targetValue))
            etTarget.setSelection(etTarget.text.length) // ← 커서 끝으로
        } else {
            tvTitle.text = "항목 추가"
        }

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name   = etName.text.toString().trim()
            if (name.isEmpty()) { etName.error = "항목명을 입력하세요"; return@setOnClickListener }
            val value  = etValue.text.toString().toDoubleOrNull() ?: 0.0
            val target = etTarget.text.toString().toDoubleOrNull() ?: 0.0
            if (target <= 0) { etTarget.error = "0보다 큰 목표 수치를 입력하세요"; return@setOnClickListener }

            if (existing != null) {
                val idx = goal.items.indexOfFirst { it.id == existing.id }
                if (idx >= 0) goal.items[idx] = goal.items[idx].copy(
                    name = name, currentValue = value, targetValue = target)
            } else {
                goal.items.add(GoalItem(name = name, currentValue = value, targetValue = target))
            }
            saveGoalSynced(); dialog.dismiss(); refreshUI()
        }
        dialog.show()
    }

    private fun showOpacityDialog() {
        val manager = AppWidgetManager.getInstance(this)
        val allIds  = manager.getAppWidgetIds(ComponentName(this, GoalWidgetProvider::class.java))
        val linked  = allIds.filter { GoalRepository.getWidgetGoalId(this, it) == goalId }

        if (linked.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("이 목표와 연결된 위젯이 없습니다.\n홈 화면에서 위젯을 먼저 추가해주세요.")
                .setPositiveButton("확인", null).show()
            return
        }

        val view          = layoutInflater.inflate(R.layout.dialog_opacity, null)
        val previewLayout = view.findViewById<LinearLayout>(R.id.dlg_preview_widget)
        val previewTitle  = view.findViewById<TextView>(R.id.dlg_preview_title)
        val previewRate   = view.findViewById<TextView>(R.id.dlg_preview_rate)
        val previewPb     = view.findViewById<android.widget.ProgressBar>(R.id.dlg_preview_progress)
        val seekbar       = view.findViewById<SeekBar>(R.id.dlg_seekbar_opacity)
        val tvOpacity     = view.findViewById<TextView>(R.id.dlg_tv_opacity)
        val btnCancel     = view.findViewById<TextView>(R.id.dlg_btn_cancel)
        val btnApply      = view.findViewById<TextView>(R.id.dlg_btn_apply)

        val initOpacity = GoalRepository.getWidgetOpacity(this, linked.first())
        seekbar.progress = initOpacity; tvOpacity.text = "$initOpacity%"
        previewTitle.text = goal.name
        previewRate.text  = "${goal.achievementRate.roundToInt()}%"
        previewPb.progress = goal.achievementRate.roundToInt()

        fun applyPreview(opacity: Int) {
            val alpha = (opacity / 100.0 * 255).toInt().coerceIn(0, 255)
            previewLayout.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = resources.displayMetrics.density * 16
                setColor((alpha shl 24) or 0x1E293B)
            }
        }
        applyPreview(initOpacity)

        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                val op = p.coerceAtLeast(5); tvOpacity.text = "$op%"; applyPreview(op)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnApply.setOnClickListener {
            val opacity = seekbar.progress.coerceAtLeast(5)
            linked.forEach { wid ->
                GoalRepository.setWidgetOpacity(this, wid, opacity)
                GoalWidgetProvider.updateWidget(this, manager, wid)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    inner class ItemAdapter : RecyclerView.Adapter<ItemAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName:    TextView = v.findViewById(R.id.tv_item_name)
            val tvValues:  TextView = v.findViewById(R.id.tv_item_values)
            val vFill:     View     = v.findViewById(R.id.v_item_progress)
            val btnEdit:   TextView = v.findViewById(R.id.btn_edit)
            val btnDelete: TextView = v.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_detail, parent, false))

        override fun getItemCount() = goal.items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item  = goal.items[position]
            val rate  = item.achievementRate
            val unit  = if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""
            val color = goal.resolveColor()

            holder.tvName.text   = item.name
            holder.tvValues.text = "${GoalWidgetProvider.formatNum(item.currentValue)} / ${GoalWidgetProvider.formatNum(item.targetValue)}$unit  (${rate.roundToInt()}%)"

            val container = holder.vFill.parent as FrameLayout
            container.post {
                val w = (container.width * rate / 100.0).toInt()
                holder.vFill.layoutParams = holder.vFill.layoutParams.also { it.width = w }
                holder.vFill.setBackgroundColor(color)
            }

            holder.btnEdit.setOnClickListener { showItemDialog(item) }
            holder.btnDelete.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
                AlertDialog.Builder(this@DetailActivity)
                    .setMessage("'${item.name}' 항목을 삭제할까요?")
                    .setPositiveButton("삭제") { _, _ ->
                        goal.items.removeAt(pos); saveGoalSynced(); refreshUI()
                    }.setNegativeButton("취소", null).show()
            }
        }
    }
}
