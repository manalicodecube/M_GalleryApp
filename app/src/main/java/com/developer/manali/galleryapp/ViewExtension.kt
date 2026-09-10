package com.developer.manali.galleryapp
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat


fun View.beInvisibleIf(beInvisible: Boolean) = if (beInvisible) beInvisible() else beVisible()

fun View.beVisibleIf(beVisible: Boolean) = if (beVisible) beVisible() else beGone()

fun View.beGoneIf(beGone: Boolean) = beVisibleIf(!beGone)

fun View.beInvisible() {
    visibility = View.INVISIBLE
}

fun View.beVisible() {
    this.visibility = View.VISIBLE
}

fun View.beGone() {
    this.visibility = View.GONE
}


fun View.showKeyboard() {
    try {
        val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    } catch (_: Exception) {
    }
}

fun View.hideKeyboard() {
    try {
        val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(this.windowToken, 0)
    } catch (_: Exception) {
    }
}

fun View.etRequestFocus() {
    try {
        this.postDelayed({
            if (this.isAttachedToWindow && this.isShown) {
                this.requestFocus()
            }
        }, 250)
    } catch (_: Exception) {
    }
}

fun View.etClearFocus() {
    try {
        this.postDelayed({
            if (this.isAttachedToWindow && this.isShown) {
                this.clearFocus()
            }
        }, 100)
    } catch (_: Exception) {
    }
}

fun View.setSingleClickListener(delayMillis: Long = 600L, onSafeClick: (View) -> Unit) {
    var lastClickTime = 0L
    this.setOnClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > delayMillis) {
            lastClickTime = currentTime
            try {
                onSafeClick(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}