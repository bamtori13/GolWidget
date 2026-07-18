package com.goalwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import java.text.DecimalFormat
import java.text.NumberFormat
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

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views    = RemoteViews(context.packageName, R.layout.goal_widget)
            val goalId   = GoalRepository.getWidgetGoalId(context, widgetId)
            val goal     = goalId?.takeIf { it.isNotEmpty() }
	    ?.let { GoalRepository.getGoal(context, it) }
            val dp       = context.resources.displayMetrics.density

            val rate: Double
            val goalColor: Int

            if (goal != null) {
                rate      = goal.achievementRate
                goalColor = goal.resolveColor()
                views.setTextViewText(R.id.tv_widget_title, goal.name)
                views.setTextViewText(R.id.tv_widget_rate, "${rate.roundToInt()}%")
                val valueText = buildString {
                    append(NumberFormat.getInstance().format(goal.target-goal.totalCurrent))
                    append(" ( ")
                    append(NumberFormat.getInstance().format(goal.totalCurrent))
                    append(" / ")
                    append(NumberFormat.getInstance().format(goal.target))
                    append(" ) ")
                    if (goal.unit.isNotEmpty()) append(" ${goal.unit}")
                }
                views.setTextViewText(R.id.tv_widget_value, valueText)
            } else {
                rate      = 0.0
                goalColor = 0xFF3B82F6.toInt()
                views.setTextViewText(R.id.tv_widget_title, "목표 없음")
                views.setTextViewText(R.id.tv_widget_rate, "0%")
                views.setTextViewText(R.id.tv_widget_rate, "탭하여 목표 설정")
            }

            // 달성률 텍스트를 목표 색상으로
            views.setTextColor(R.id.tv_widget_rate, goalColor)
            // 프로그레스 바: Bitmap으로 직접 그려서 ImageView에 표시
            val barW  = (280 * dp).toInt()
            val barH  = (10 * dp).toInt()
            val bmp   = drawProgressBitmap(barW, barH, rate.toFloat() / 100f, goalColor, dp)
            views.setImageViewBitmap(R.id.iv_progress_bar, bmp)

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

        /** 프로그레스 바를 Bitmap으로 직접 그림 (RemoteViews 색상 제약 우회) */
        private fun drawProgressBitmap(w: Int, h: Int, progress: Float, color: Int, dp: Float): Bitmap {
            val bmp    = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val radius = h / 2f
            val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

            // 배경 트랙
            paint.color = 0xFF334155.toInt()
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)

            // 채움
            val fillW = (w * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)
            if (fillW > 0) {
                paint.color = color
                canvas.drawRoundRect(RectF(0f, 0f, fillW, h.toFloat()), radius, radius, paint)
            }
            return bmp
        }

        fun formatNum(v: Double): String =
            if (v == v.toLong().toDouble()) v.toLong().toString()
            else "%.1f".format(v)
    }
}
