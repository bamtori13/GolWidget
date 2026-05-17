package com.goalwidget

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class GoalItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var currentValue: Double = 0.0,
) {

}

data class Goal(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var unit: String = "",
    // 직접 입력한 총 목표값 (세부 항목이 없을 때 사용)
    var target: Double = 0.0,
    // 직접 입력한 현재 달성값 (세부 항목이 없을 때 사용)
    var directCurrent: Double = 0.0,
    var items: MutableList<GoalItem> = mutableListOf()
) {
    // 세부 항목이 있으면 항목 합산, 없으면 directTarget/directCurrent 사용
    val totalCurrent: Double
        get() = if (items.isNotEmpty()) items.sumOf { it.currentValue } else directCurrent

    val achievementRate: Double
        get() = if (target > 0) (totalCurrent / target * 100).coerceAtMost(100.0) else 0.0
}

object GoalRepository {
    private val gson = Gson()
    private const val PREF_GOALS = "goals_data"
    private const val KEY_GOALS = "goals_list"

    fun loadGoals(context: android.content.Context): MutableList<Goal> {
        val prefs = context.getSharedPreferences(PREF_GOALS, android.content.Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_GOALS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Goal>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveGoals(context: android.content.Context, goals: List<Goal>) {
        val prefs = context.getSharedPreferences(PREF_GOALS, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GOALS, gson.toJson(goals)).apply()
    }

    fun getGoal(context: android.content.Context, goalId: String): Goal? =
        loadGoals(context).find { it.id == goalId }

    fun saveGoal(context: android.content.Context, goal: Goal) {
        val goals = loadGoals(context)
        val idx = goals.indexOfFirst { it.id == goal.id }
        if (idx >= 0) goals[idx] = goal else goals.add(goal)
        saveGoals(context, goals)
    }

    fun deleteGoal(context: android.content.Context, goalId: String) {
        val goals = loadGoals(context)
        goals.removeAll { it.id == goalId }
        saveGoals(context, goals)
    }

    fun getWidgetGoalId(context: android.content.Context, widgetId: Int): String? {
        val prefs = context.getSharedPreferences(PREF_GOALS, android.content.Context.MODE_PRIVATE)
        return prefs.getString("widget_$widgetId", null)
    }

    fun setWidgetGoalId(context: android.content.Context, widgetId: Int, goalId: String) {
        val prefs = context.getSharedPreferences(PREF_GOALS, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("widget_$widgetId", goalId).apply()
    }

    fun removeWidgetGoalId(context: android.content.Context, widgetId: Int) {
        val prefs = context.getSharedPreferences(PREF_GOALS, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove("widget_$widgetId").apply()
    }
}
