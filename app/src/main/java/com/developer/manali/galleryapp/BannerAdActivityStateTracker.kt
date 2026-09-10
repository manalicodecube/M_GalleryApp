package com.developer.manali.galleryapp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BannerAdActivityStateTracker {
    private val _isCallerIdActivityOpen = MutableStateFlow<Boolean>(false)
    val isCallerIdActivityOpen: StateFlow<Boolean> = _isCallerIdActivityOpen

    fun setOpen(open: Boolean) {
        _isCallerIdActivityOpen.value = open
    }
}