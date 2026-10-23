package com.example.ads.nativead

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/** A lightweight glass sheen drawn above the native ad CTA without intercepting touches. */
internal class NativeAdCtaShimmerDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val area = RectF()
    private val clipPath = Path()
    private var phase = -0.7f

    private val animator = ValueAnimator.ofFloat(-0.7f, 1.7f).apply {
        duration = 1900L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        area.set(bounds)
        val radius = bounds.height() / 2f

        // A faint upper reflection gives the button a polished glass surface.
        paint.shader = LinearGradient(
            0f,
            bounds.top.toFloat(),
            0f,
            bounds.centerY().toFloat(),
            intArrayOf(0x30FFFFFF, 0x08FFFFFF),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(area, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.drawRect(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.centerY().toFloat(),
            paint
        )

        // A narrow diagonal highlight travels across the button continuously.
        val center = bounds.left + bounds.width() * phase
        val band = bounds.width() * 0.22f
        paint.shader = LinearGradient(
            center - band,
            bounds.bottom.toFloat(),
            center + band,
            bounds.top.toFloat(),
            intArrayOf(Color.TRANSPARENT, 0x70FFFFFF, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(area, radius, radius, paint)
        canvas.restore()
        paint.shader = null
    }

    fun start() {
        if (!animator.isStarted) animator.start()
    }

    fun stop() {
        animator.cancel()
        animator.removeAllUpdateListeners()
        callback = null
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in the Android framework")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
