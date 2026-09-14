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
                scaleFactor = 1.0f
                isScaling = false
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                if (kotlin.math.abs(1.0f - scaleFactor) > 0.05f) {
                    isScaling = true
                }
                val currentSpan = getSpanCount()
                if (scaleFactor > 1.20f && currentSpan > minSpan) {
                    val newSpan = (currentSpan - 1).coerceAtLeast(minSpan)
                    if (newSpan != currentSpan) {
                        onSpanCountChanged(newSpan)
                        scaleFactor = 1.0f
                    }
                } else if (scaleFactor < 0.83f && (currentSpan < maxSpan || currentSpan <= 1)) {
                    val newSpan = if (currentSpan <= 1) minSpan else (currentSpan + 1).coerceAtMost(maxSpan)
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
                if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL || e.pointerCount < 2) {
                    isScaling = false
                    scaleFactor = 1.0f
                }
                if (e.pointerCount >= 2) {
                    scaleDetector.onTouchEvent(e)
                    if (isScaling) {
                        rv.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL || e.pointerCount < 2) {
                    isScaling = false
                    scaleFactor = 1.0f
                }
                if (e.pointerCount >= 2 || isScaling) {
                    scaleDetector.onTouchEvent(e)
                    if (isScaling) {
                        rv.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
        })
    }
}