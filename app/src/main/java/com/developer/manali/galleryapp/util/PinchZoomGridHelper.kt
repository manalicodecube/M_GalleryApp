package com.developer.manali.galleryapp.util

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView

class PinchZoomGridHelper(
    context: Context,
    private val minSpan: Int = 2,
    private val maxSpan: Int = 7,
    private val getSpanCount: () -> Int,
    private val onSpanCountChanged: (Int) -> Unit
) {
    private var scaleFactor = 1.0f
    private var isScaling = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (getSpanCount() <= 1) {
                    isScaling = false
                    return false
                }
                scaleFactor = 1.0f
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (getSpanCount() <= 1) return false

                scaleFactor *= detector.scaleFactor
                val currentSpan = getSpanCount()
                if (scaleFactor > 1.22f && currentSpan > minSpan) {
                    val newSpan = (currentSpan - 1).coerceAtLeast(minSpan)
                    if (newSpan != currentSpan) {
                        onSpanCountChanged(newSpan)
                        scaleFactor = 1.0f
                    }
                } else if (scaleFactor < 0.82f && currentSpan < maxSpan) {
                    val newSpan = (currentSpan + 1).coerceAtMost(maxSpan)
                    if (newSpan != currentSpan) {
                        onSpanCountChanged(newSpan)
                        scaleFactor = 1.0f
                    }
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                scaleFactor = 1.0f
            }
        }
    )

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (getSpanCount() <= 1) return false

                if (e.pointerCount >= 2) {
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                    scaleDetector.onTouchEvent(e)
                    return isScaling
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (getSpanCount() <= 1) return

                if (e.pointerCount >= 2 || isScaling) {
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                    scaleDetector.onTouchEvent(e)
                }
            }
        })
    }
}