package com.goalwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.DecimalFormat
import kotlin.math.roundToInt

class GoalWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            GoalRepository.removeWidgetGoalId(context, widgetId)
        }
    }

    companion object {

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GoalWidgetProvider::class.java)
            )
            for (id in ids) updateWidget(context, manager, id)
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.goal_widget)

            val goalId = GoalRepository.getWidgetGoalId(context, widgetId)
            val goal = goalId?.takeIf { it.isNotEmpty() }
                ?.let { GoalRepository.getGoal(context, it) }

            val rate: Double
            if (goal != null) {
                rate = goal.achievementRate
                views.setTextViewText(R.id.tv_widget_title, goal.name)
                views.setTextViewText(R.id.tv_widget_rate, "${rate.roundToInt()}%")
                val valueText = buildString {
                    append(formatNum(goal.totalCurrent))
                    append(" / ")
                    append(formatNum(goal.target))
                    if (goal.unit.isNotEmpty()) append(" ${goal.unit}")
                }
                views.setTextViewText(R.id.tv_widget_value, valueText)
            } else {
                rate = 0.0
                views.setTextViewText(R.id.tv_widget_title, "목표 없음")
                views.setTextViewText(R.id.tv_widget_rate, "0%")
                views.setTextViewText(R.id.tv_widget_value, "탭하여 목표 설정")
            }

            // LinearLayout weight 방식으로 프로그레스 바 조절
            val progress = rate.roundToInt().coerceIn(0, 100)
            views.setProgressBar(R.id.progress_widget, 100, progress, false)

            // 탭 → DetailActivity
            val tapIntent = Intent(context, DetailActivity::class.java).apply {
                putExtra("goal_id", goalId ?: "")
                putExtra("widget_id", widgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, widgetId, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private val numberFormat = DecimalFormat("#,##0.#")

        fun formatNum(v: Double): String = numberFormat.format(v)
    }
}
