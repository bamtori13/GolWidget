package com.goalwidget

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class GoalItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var currentValue: Double = 0.0,
    var targetValue: Double = 100.0
) {
    val achievementRate: Double
        get() = if (targetValue > 0) (currentValue / targetValue * 100).coerceAtMost(100.0) else 0.0
}

data class Goal(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var unit: String = "",
    var directTarget: Double = 0.0,
    var directCurrent: Double = 0.0,
    var items: MutableList<GoalItem> = mutableListOf(),
    // 목표 색상 (ARGB int, 기본값 0 = 미설정 → 기본 파란색 사용)
    var colorHex: String = ""
) {
    val totalCurrent: Double
        get() = if (items.isNotEmpty()) items.sumOf { it.currentValue } else directCurrent

    val totalTarget: Double
        get() = if (items.isNotEmpty()) items.sumOf { it.targetValue } else directTarget

    val achievementRate: Double
        get() = if (totalTarget > 0) (totalCurrent / totalTarget * 100).coerceAtMost(100.0) else 0.0

    // 색상 int 반환 (없으면 기본 파란색)
    fun resolveColor(): Int =
        if (colorHex.isNotEmpty()) {
            try { android.graphics.Color.parseColor(colorHex) }
            catch (e: Exception) { 0xFF3B82F6.toInt() }
        } else 0xFF3B82F6.toInt()
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
        } catch (e: Exception) { mutableListOf() }
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
        prefs.edit().remove("widget_$widgetId").remove("widget_opacity_$widgetId").apply()
    }

    fun getWidgetOpacity(context: android.content.Context, widgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREF_GOALS, android.content.Context.MODE_PRIVATE)
        return prefs.getInt("widget_opacity_$widgetId", 90)
    }

    fun setWidgetOpacity(context: android.content.Context, widgetId: Int, opacity: Int) {
        val prefs = context.getSharedPreferences(PREF_GOALS, android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("widget_opacity_$widgetId", opacity.coerceIn(0, 100)).apply()
    }
}
