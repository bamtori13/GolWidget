package com.goalwidget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.LinearLayout

object ColorPalette {

    // 앱에서 쓸 색상 목록 (hex)
    val COLORS = listOf(
        "#3B82F6", // 파랑 (기본)
        "#10B981", // 초록
        "#F59E0B", // 주황
        "#EF4444", // 빨강
        "#8B5CF6", // 보라
        "#EC4899", // 핑크
        "#06B6D4", // 하늘
        "#84CC16", // 라임
        "#F97316", // 오렌지
        "#6B7280"  // 회색
    )

    /**
     * color_palette LinearLayout에 색상 원 버튼들을 동적으로 추가.
     * selectedHex: 현재 선택된 색상, onSelect: 색상 선택 콜백
     */
    fun setup(
        ctx: Context,
        container: LinearLayout,
        selectedHex: String,
        onSelect: (hex: String) -> Unit
    ) {
        container.removeAllViews()
        val dp = ctx.resources.displayMetrics.density
        val size = (36 * dp).toInt()
        val margin = (6 * dp).toInt()

        COLORS.forEach { hex ->
            val isSelected = hex.equals(selectedHex, ignoreCase = true)
                || (selectedHex.isEmpty() && hex == COLORS[0])

            val circle = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = margin
                }
                background = makeCircle(hex, isSelected, dp)
                tag = hex
                setOnClickListener {
                    // 모든 원 선택 해제 후 이것만 선택 표시
                    for (i in 0 until container.childCount) {
                        val v = container.getChildAt(i)
                        v.background = makeCircle(v.tag as String, false, dp)
                    }
                    background = makeCircle(hex, true, dp)
                    onSelect(hex)
                }
            }
            container.addView(circle)
        }
    }

    private fun makeCircle(hex: String, selected: Boolean, dp: Float): android.graphics.drawable.Drawable {
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            try { setColor(Color.parseColor(hex)) }
            catch (e: Exception) { setColor(Color.BLUE) }
        }
        return if (selected) {
            val border = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((3 * dp).toInt(), Color.parseColor("#0F172A"))
            }
            LayerDrawable(arrayOf(fill, border))
        } else fill
    }

    fun progressDrawable(colorInt: Int): android.graphics.drawable.Drawable {
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5f
            setColor(colorInt)
        }
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5f
            setColor(Color.parseColor("#334155"))
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(bg, fill))
    }
}
