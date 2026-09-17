package com.infodevelop54.fueloverlay

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

object AppearanceApplier {

    fun applyStatic(root: View, a: WidgetAppearance, density: Float) {
        val container     = root.findViewById<LinearLayout>(R.id.container) ?: return
        val textSpent     = root.findViewById<TextView>(R.id.textSpent)
        val textRemaining = root.findViewById<TextView>(R.id.textRemaining)
        val textMileage   = root.findViewById<TextView>(R.id.textMileage)
        val textRange     = root.findViewById<TextView>(R.id.textRange)

        val icSpent     = root.findViewById<ImageView>(R.id.icSpent)
        val icRemaining = root.findViewById<ImageView>(R.id.icRemaining)
        val icMileage   = root.findViewById<ImageView>(R.id.icMileage)
        val icRange     = root.findViewById<ImageView>(R.id.icRange)

        val pad = (a.paddingDp * density).toInt()
        container.setPadding(pad, pad, pad, pad)
        container.background = GradientDrawable().apply {
            setColor(safeColor(a.backgroundColor, Color.BLACK))
            cornerRadius = a.cornerRadiusDp * density
        }

        val tc = safeColor(a.textColor, Color.WHITE)

        listOf(textSpent, textRemaining, textMileage, textRange).forEach { tv ->
            tv?.apply { setTextColor(tc); textSize = a.textSizeSp }
        }

        val iconSize = (a.textSizeSp * density * 1.15f).toInt().coerceAtLeast(12)

        listOf(
            icSpent     to VectorIconLibrary.get("spent",     a.spentIconIndex),
            icRemaining to VectorIconLibrary.get("remaining", a.remainingIconIndex),
            icMileage   to VectorIconLibrary.get("mileage",   a.mileageIconIndex),
            icRange     to VectorIconLibrary.get("range",     a.rangeIconIndex)
        ).forEach { (iv, def) ->
            iv ?: return@forEach
            iv.setImageDrawable(VectorIconLibrary.createDrawable(def.pathData, tc))
            iv.layoutParams = iv.layoutParams.apply {
                width = iconSize; height = iconSize
            }
            iv.requestLayout()
        }

        textSpent?.visibility     = if (a.showSpent) View.VISIBLE else View.GONE
        textRemaining?.visibility = if (a.showRemaining) View.VISIBLE else View.GONE
        icSpent?.visibility       = if (a.showSpent) View.VISIBLE else View.GONE
        icRemaining?.visibility   = if (a.showRemaining) View.VISIBLE else View.GONE
    }

    fun applyScale(
        root: View, a: WidgetAppearance,
        remainingLiters: Float, tankLiters: Float, density: Float
    ) {
        val container = root.findViewById<LinearLayout>(R.id.scaleContainer) ?: return

        val segments = (tankLiters / 5f).roundToInt().coerceIn(4, 20)
        val expectedChildren = segments * 2 - 1

        if (container.childCount != expectedChildren) {
            container.removeAllViews()
            val segLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val divLp = LinearLayout.LayoutParams(
                (a.dividerThicknessDp * density).toInt().coerceAtLeast(1),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            val dividerColor = safeColor(a.dividerColor, Color.BLACK)
            for (i in 0 until segments) {
                container.addView(View(container.context).apply { layoutParams = segLp })
                if (i < segments - 1) {
                    container.addView(View(container.context).apply {
                        layoutParams = divLp; setBackgroundColor(dividerColor)
                    })
                }
            }
        }

        container.layoutParams = container.layoutParams.apply {
            width = (a.scaleWidthDp * density).toInt()
            height = (a.scaleHeightDp * density).toInt()
        }

        val fillRatio = if (tankLiters > 0f) (remainingLiters / tankLiters).coerceIn(0f, 1f) else 0f

        val levelColor = when {
            fillRatio > 0.50f -> safeColor(a.scaleColor, Color.WHITE)
            fillRatio > 0.20f -> Color.parseColor("#FF9800")
            else              -> Color.parseColor("#F44336")
        }
        val emptyColor = Color.parseColor("#33FFFFFF")

        var idx = 0
        for (i in 0 until segments) {
            val segStart = i.toFloat() / segments
            val segEnd = (i + 1).toFloat() / segments
            val segFill = ((fillRatio - segStart) / (segEnd - segStart)).coerceIn(0f, 1f)
            (container.getChildAt(idx) as? View)?.setBackgroundColor(blend(emptyColor, levelColor, segFill))
            idx++
            if (i < segments - 1) idx++
        }
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        val r = (Color.red(from)   * (1 - t) + Color.red(to)   * t).toInt()
        val g = (Color.green(from) * (1 - t) + Color.green(to) * t).toInt()
        val b = (Color.blue(from)  * (1 - t) + Color.blue(to)  * t).toInt()
        val a = (Color.alpha(from) * (1 - t) + Color.alpha(to) * t).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun safeColor(hex: String, fallback: Int): Int =
        try { Color.parseColor(hex) } catch (_: Exception) { fallback }
}