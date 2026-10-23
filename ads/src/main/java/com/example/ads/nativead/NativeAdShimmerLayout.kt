package com.example.ads.nativead

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.example.ads.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView

/** Measures the host's actual template, but draws a glass skeleton instead of its content. */
internal class NativeAdShimmerLayout(
    context: Context,
    private val templateView: View
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = Rect()
    private val bounds = RectF()
    private var phase = -1f
    private var disposed = false
    private var aggregatedVisible = false
    private var cardGradient: LinearGradient? = null
    private val shimmerColors = intArrayOf(Color.TRANSPARENT, 0xDFFFFFFF.toInt(), Color.TRANSPARENT)
    private val animator = ValueAnimator.ofFloat(-1f, 2f).apply {
        duration = 1600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        // Retain the template's dimensions, constraints and text metrics for measurement only.
        addView(templateView)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cardGradient = LinearGradient(
            0f, 0f, w.toFloat(), h.coerceAtLeast(1).toFloat(),
            intArrayOf(0xFFF5F9FF.toInt(), 0xFFE4EDF8.toInt(), 0xFFF8FAFF.toInt()),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Intentionally do not draw the unbound ad template, text or button underneath.
        if (width == 0 || height == 0 || disposed) return
        val radius = 16f * density
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.shader = cardGradient
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null
        paint.color = 0xCCFFFFFF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        bounds.inset(density / 2, density / 2)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.style = Paint.Style.FILL

        val center = width * phase
        val band = width * 0.5f
        val sheen = LinearGradient(
            center - band, 0f, center + band, height.toFloat(),
            shimmerColors, null, Shader.TileMode.CLAMP
        )
        drawAssets(canvas, templateView, sheen)
    }

    private fun drawAssets(canvas: Canvas, view: View, sheen: Shader) {
        if (view.visibility != VISIBLE) return
        if (view is TextView || view is ImageView || view is MediaView || view.id == R.id.ad_call_to_action) {
            rect.set(0, 0, view.width, view.height)
            offsetDescendantRectToMyCoords(view, rect)
            bounds.set(rect)
            val isText = view is TextView && view !is Button && view.id != R.id.ad_call_to_action
            if (isText) {
                val lineHeight = minOf(bounds.height(), 12f * density)
                val centerY = bounds.centerY()
                bounds.top = centerY - lineHeight / 2
                bounds.bottom = centerY + lineHeight / 2
                bounds.right = bounds.left + bounds.width() * 0.86f
            }
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            val radius = minOf(10f * density, bounds.height() / 2)
            paint.shader = null
            paint.color = 0xFFD6E1EF.toInt()
            canvas.drawRoundRect(bounds, radius, radius, paint)
            paint.color = Color.WHITE
            paint.shader = sheen
            canvas.drawRoundRect(bounds, radius, radius, paint)
            paint.shader = null
        } else if (view is ViewGroup) {
            for (index in 0 until view.childCount) drawAssets(canvas, view.getChildAt(index), sheen)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean = true

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        aggregatedVisible = isVisible
        updateAnimation()
    }

    private fun updateAnimation() {
        if (!disposed && isAttachedToWindow && aggregatedVisible) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        animator.cancel()
        animator.removeAllUpdateListeners()
        (templateView as? NativeAdView)?.destroy()
        removeAllViews()
    }
}
