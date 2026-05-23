package com.goalwidget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
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

class DetailActivity : AppCompatActivity() {

    private var goalId: String = ""
    private lateinit var goal: Goal

    private lateinit var tvTitle: TextView
    private lateinit var tvRate: TextView
    private lateinit var tvCurrent: TextView
    private lateinit var tvTarget: TextView
    private lateinit var vProgress: View
    private lateinit var rvItems: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnAdd: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        goalId = intent.getStringExtra("goal_id") ?: ""
        if (goalId.isEmpty()) { finish(); return }

        tvTitle = findViewById(R.id.tv_detail_title)
        tvRate = findViewById(R.id.tv_detail_rate)
        tvCurrent = findViewById(R.id.tv_detail_current)
        tvTarget = findViewById(R.id.tv_detail_target)
        vProgress = findViewById(R.id.v_detail_progress)
        rvItems = findViewById(R.id.rv_items)
        tvEmpty = findViewById(R.id.tv_items_empty)
        btnAdd = findViewById(R.id.btn_add_item)

        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = ItemAdapter()

        btnAdd.setOnClickListener { showItemDialog(null) }
    }

    override fun onResume() {
        super.onResume()
        loadGoal()
    }

    private fun loadGoal() {
        val loaded = GoalRepository.getGoal(this, goalId)
        if (loaded == null) { finish(); return }
        goal = loaded
        refreshUI()
    }

    // 저장 시 Firebase도 동시에 업데이트
    private fun saveGoalSynced() {
        GoalRepository.saveGoal(this, goal)
        val code = SyncManager.getGroupCode(this)
        if (code != null && SyncManager.isSyncEnabled(this)) {
            SyncManager.pushGoal(code, goal)
        }
        GoalWidgetProvider.updateAllWidgets(this)
    }

    private fun refreshUI() {
        title = goal.name
        tvTitle.text = goal.name
        val rate = goal.achievementRate
        tvRate.text = "${rate.roundToInt()}%"
        val unit = if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""
        tvCurrent.text = "달성: ${GoalWidgetProvider.formatNum(goal.totalCurrent)}$unit"
        tvTarget.text = "목표: ${GoalWidgetProvider.formatNum(goal.target)}$unit"

        val progressParent = vProgress.parent as FrameLayout
        progressParent.post {
            val w = (progressParent.width * rate / 100.0).toInt()
            vProgress.layoutParams = vProgress.layoutParams.also { it.width = w }
        }

        // 세부 항목이 없을 때 직접 달성값 수정 버튼 표시 조정
        rvItems.adapter?.notifyDataSetChanged()

        val showItems = goal.items.isNotEmpty()
        tvEmpty.visibility = if (showItems) View.GONE else View.VISIBLE
        rvItems.visibility = if (showItems) View.VISIBLE else View.GONE

        // 세부 항목이 없으면 직접 달성값 편집 안내
        tvEmpty.text = if (goal.target > 0 && !showItems)
            "달성: ${GoalWidgetProvider.formatNum(goal.directCurrent)} / 목표: ${GoalWidgetProvider.formatNum(goal.target)}${if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""}\n\n세부 항목을 추가하거나\n아래 버튼으로 달성값을 직접 입력하세요."
        else
            "세부 항목이 없습니다.\n+ 버튼으로 항목을 추가하세요."

        GoalWidgetProvider.updateAllWidgets(this)
    }

    private fun showItemDialog(existing: GoalItem?) {
        val view = layoutInflater.inflate(R.layout.dialog_item_edit, null)
        val tvDlgTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val etName = view.findViewById<EditText>(R.id.et_item_name)
        val etValue = view.findViewById<EditText>(R.id.et_item_value)
        val btnCancel = view.findViewById<TextView>(R.id.btn_dialog_cancel)
        val btnSave = view.findViewById<TextView>(R.id.btn_dialog_save)

        if (existing != null) {
            tvDlgTitle.text = "항목 편집"
            etName.setText(existing.name)
            etValue.setText(GoalWidgetProvider.formatNum(existing.currentValue))
        } else {
            tvDlgTitle.text = "항목 추가"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) { etName.error = "항목명을 입력하세요"; return@setOnClickListener }
            val value = etValue.text.toString().replace(",", "").toDoubleOrNull() ?: 0.0

            if (existing != null) {
                existing.name = name; existing.currentValue = value
            } else {
                goal.items.add(GoalItem(name = name, currentValue = value))
            }
            GoalRepository.saveGoal(this, goal)
            dialog.dismiss()
            refreshUI()
        }
        dialog.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    inner class ItemAdapter : RecyclerView.Adapter<ItemAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tv_item_name)
            val tvValues: TextView = v.findViewById(R.id.tv_item_values)

            val btnEdit: TextView = v.findViewById(R.id.btn_edit)
            val btnDelete: TextView = v.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_detail, parent, false)
            return VH(v)
        }

        override fun getItemCount() = goal.items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = goal.items[position]
            holder.tvName.text = item.name
            val unit = if (goal.unit.isNotEmpty()) " ${goal.unit}" else ""
            holder.tvValues.text = "${GoalWidgetProvider.formatNum(item.currentValue)}/$unit "

            holder.btnEdit.setOnClickListener { showItemDialog(item) }
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@DetailActivity)
                    .setMessage("'${item.name}' 항목을 삭제할까요?")
                    .setPositiveButton("삭제") { _, _ ->
                        goal.items.removeAt(holder.adapterPosition)
                        saveGoalSynced()
                        refreshUI()
                    }
                    .setNegativeButton("취소", null).show()
            }
        }
    }
}
