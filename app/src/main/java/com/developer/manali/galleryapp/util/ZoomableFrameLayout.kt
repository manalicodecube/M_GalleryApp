package com.developer.manali.galleryapp.util

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

class ZoomableFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var scale = 1.0f
    private var translateX = 0f
    private var translateY = 0f

    private val minScale = 1.0f
    private val maxScale = 5.0f

    private var isScaling = false
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val prevScale = scale
            scale *= scaleFactor
            scale = scale.coerceIn(minScale * 0.8f, maxScale * 1.2f)

            val factor = scale / prevScale

            val focusX = detector.focusX
            val focusY = detector.focusY

            translateX -= (focusX - translateX) * (factor - 1)
            translateY -= (focusY - translateY) * (factor - 1)

            applyTransform()
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            if (scale < minScale) {
                animateZoom(scale, minScale, translateX, translateY, 0f, 0f)
            } else if (scale > maxScale) {
                val targetTx = translateX - (detector.focusX - translateX) * (maxScale / scale - 1)
                val targetTy = translateY - (detector.focusY - translateY) * (maxScale / scale - 1)
                animateZoom(scale, maxScale, translateX, translateY, targetTx, targetTy)
            } else {
                checkBounds(true)
            }
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scale > 1.0f && !isScaling) {
                translateX -= distanceX
                translateY -= distanceY
                checkBounds(false)
                applyTransform()

                val canScrollHorizontally = canScrollHorizontally(-distanceX.toInt())
                parent?.requestDisallowInterceptTouchEvent(canScrollHorizontally)
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > 1.2f) {
                animateZoom(scale, 1.0f, translateX, translateY, 0f, 0f)
            } else {
                val targetScale = 2.5f
                val targetTx = -(e.x * (targetScale - 1))
                val targetTy = -(e.y * (targetScale - 1))
                animateZoom(scale, targetScale, translateX, translateY, targetTx, targetTy)
            }
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            performClick()
            return true
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1 || scale > 1.0f) {
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                if (scale > 1.0f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isScaling) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (scale < minScale) {
                        animateZoom(scale, minScale, translateX, translateY, 0f, 0f)
                    }
                }
            }
        }
        return true
    }

    private fun checkBounds(animate: Boolean) {
        if (childCount == 0) return
        val child = getChildAt(0)
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val scaledWidth = child.width * scale
        val scaledHeight = child.height * scale

        var targetTx = translateX
        var targetTy = translateY

        if (scaledWidth <= viewWidth) {
            targetTx = (viewWidth - scaledWidth) / 2f
        } else {
            val minTx = viewWidth - scaledWidth
            targetTx = translateX.coerceIn(minTx, 0f)
        }

        if (scaledHeight <= viewHeight) {
            targetTy = (viewHeight - scaledHeight) / 2f
        } else {
            val minTy = viewHeight - scaledHeight
            targetTy = translateY.coerceIn(minTy, 0f)
        }

        if (animate && (targetTx != translateX || targetTy != translateY)) {
            animateZoom(scale, scale, translateX, translateY, targetTx, targetTy)
        } else {
            translateX = targetTx
            translateY = targetTy
        }
    }

    private fun applyTransform() {
        if (childCount == 0) return
        val child = getChildAt(0)
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scale
        child.scaleY = scale
        child.translationX = translateX
        child.translationY = translateY
    }

    private fun animateZoom(
        fromScale: Float, toScale: Float,
        fromTx: Float, fromTy: Float,
        toTx: Float, toTy: Float
    ) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()

            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                scale = fromScale + (toScale - fromScale) * fraction
                translateX = fromTx + (toTx - fromTx) * fraction
                translateY = fromTy + (toTy - fromTy) * fraction
                
                if (fraction == 1f) {
                    checkBounds(false)
                }
                applyTransform()
            }
        }
        animator.start()
    }
}
