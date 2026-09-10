package com.developer.manali.galleryapp

import android.content.Context
import com.developer.manali.galleryapp.WICController.isShowedWicController
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.AdapterStatus
import com.google.android.gms.ads.initialization.InitializationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BannerAdsManager {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    val isReadyToLoad: Boolean get() = _isReady.value

    private val _status = MutableStateFlow<InitializationStatus?>(null)
    val status: StateFlow<InitializationStatus?> = _status.asStateFlow()

    fun initialize(context: Context) {
        if (::appContext.isInitialized && _isReady.value) return
        appContext = context.applicationContext

        scope.launch(Dispatchers.Default) {
            val status = try {
                MobileAds.getInitializationStatus()
            } catch (_: IllegalStateException) {
                null
            }

            val alreadyInitialized = status?.adapterStatusMap?.values?.any {
                it.initializationState == AdapterStatus.State.READY
            } ?: false

            if (alreadyInitialized) {
                _isReady.value = true
                _status.value = status
            } else {
                withContext(Dispatchers.Main) {
                    try {
                        MobileAds.initialize(appContext) { initStatus ->
                            _status.value = initStatus
                            val isReady = initStatus.adapterStatusMap.values.any {
                                it.initializationState == AdapterStatus.State.READY
                            }
                            _isReady.value = isReady
                            if (isReady && isShowedWicController()) {
                                BannerIdAds.preLoadCallerIdAds(context)
                            }
                        }
                    } catch (e: Exception) {
                        _isReady.value = false
                    }
                }
            }
        }
    }
}