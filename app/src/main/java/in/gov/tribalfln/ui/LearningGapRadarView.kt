package `in`.gov.tribalfln.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * LearningGapRadarView — Custom radar chart visualization for displaying
 * student competency mastery levels across multiple dimensions.
 * Used on the home dashboard to show class-wide learning gaps.
 */
class LearningGapRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var scores = FloatArray(0)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        style = Paint.Style.FILL
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CAF50.toInt()
        style = Paint.Style.FILL
        alpha = 128
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9E9E9E.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF424242.toInt()
        textSize = 28f
    }

    /**
     * Set the mastery scores for each competency dimension.
     * Values should be between 0.0 and 1.0.
     */
    fun setScores(scores: FloatArray) {
        this.scores = scores.copyOf()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (scores.isEmpty()) return

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.7f
        val sides = scores.size

        if (sides < 3) return

        // Draw background polygon
        val bgPath = android.graphics.Path()
        for (i in 0 until sides) {
            val angle = (2 * Math.PI * i / sides - Math.PI / 2).toFloat()
            val x = cx + radius * kotlin.math.cos(angle)
            val y = cy + radius * kotlin.math.sin(angle)
            if (i == 0) bgPath.moveTo(x, y) else bgPath.lineTo(x, y)
        }
        bgPath.close()
        canvas.drawPath(bgPath, bgPaint)

        // Draw grid lines
        for (level in 1..4) {
            val r = radius * level / 4f
            val gridPath = android.graphics.Path()
            for (i in 0 until sides) {
                val angle = (2 * Math.PI * i / sides - Math.PI / 2).toFloat()
                val x = cx + r * kotlin.math.cos(angle)
                val y = cy + r * kotlin.math.sin(angle)
                if (i == 0) gridPath.moveTo(x, y) else gridPath.lineTo(x, y)
            }
            gridPath.close()
            canvas.drawPath(gridPath, linePaint)
        }

        // Draw score polygon
        val scorePath = android.graphics.Path()
        for (i in 0 until sides) {
            val angle = (2 * Math.PI * i / sides - Math.PI / 2).toFloat()
            val score = scores[i].coerceIn(0f, 1f)
            val r = radius * score
            val x = cx + r * kotlin.math.cos(angle)
            val y = cy + r * kotlin.math.sin(angle)
            if (i == 0) scorePath.moveTo(x, y) else scorePath.lineTo(x, y)
        }
        scorePath.close()
        canvas.drawPath(scorePath, scorePaint)
    }
}
