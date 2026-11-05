package com.imagefolders.photoorganizer.mediagallery

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.SparseArray
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.imagefolders.photoorganizer.mediagallery.databinding.ActivityLanguageSelectBinding
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class LanguageSelectActivity : BaseActivity() {

    private var arrLanguageList: List<LanguagesModel> = ArrayList()
    private var languageSelectAdapter: LanguageSelectAdapter? = null
    private var isFromSplash = false
    private lateinit var btnClose: View
    private lateinit var imgDone: FrameLayout
    private lateinit var rvLanguageList: RecyclerView
    private lateinit var binding: ActivityLanguageSelectBinding
    private var isProcessingClick = false
    private var googleBannerAds: GoogleBannerAds? = null
    private var bannerAdView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLanguageSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        findViewById<View>(R.id.btnClose)?.setOnClickListener {
            finish()
        }
        btnClose = binding.btnClose
        btnClose.visibility = View.GONE
        imgDone = binding.imgDone
        rvLanguageList = binding.rvLanguageList

        initIntentParams()
        init()
    }

    private fun initIntentParams() {
        if (intent != null && intent.extras != null) {
            if (intent.extras!!.containsKey(Constant.IS_FROM_SPLASH)) {
                isFromSplash = intent.extras!!.getBoolean(Constant.IS_FROM_SPLASH)
            }
        }
    }

    private fun init() {
        imgDone.setOnClickListener {
            if (isProcessingClick) {
                return@setOnClickListener
            }

            isProcessingClick = true

            GoogleInterstitialAdsCall.loadAndShowInterstitial(
                this@LanguageSelectActivity,
                object : InterstitialAdCallback {
                    override fun onAdClose() {
                        proceedToNext()
                    }
                }
            )
        }

        setLanguageList()

        if (isFromSplash) {
            requestConsentAndLoadBanner()
        } else {
            loadBigBannerAd()
        }
    }

    private fun requestConsentAndLoadBanner() {
        if (!AdsUtils.isConnected(this)) {
            loadBigBannerAd()
            return
        }

        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)

        if (isDebuggable) {
            val debugSettings = ConsentDebugSettings.Builder(this)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }

        val params = paramsBuilder.build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    this
                ) { formError ->
                    if (isFinishing || isDestroyed) return@loadAndShowConsentFormIfRequired
                    if (formError != null) {
                        Toast.makeText(
                            this@LanguageSelectActivity,
                            "Consent failed: ${formError.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@LanguageSelectActivity,
                            "Consent gathered successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    loadBigBannerAd()
                }
            },
            { requestConsentError ->
                if (isFinishing || isDestroyed) return@requestConsentInfoUpdate
                Toast.makeText(
                    this@LanguageSelectActivity,
                    "Consent failed: ${requestConsentError.message}",
                    Toast.LENGTH_SHORT
                ).show()
                loadBigBannerAd()
            }
        )
    }

    private fun proceedToNext() {
        if (languageSelectAdapter != null) {
            val arrSelectedList =
                getSelectedListFromSparseArray(
                    languageSelectAdapter!!.sparseArray
                )
            if (arrSelectedList.isNotEmpty()) {
                val selectedLanguageCode: String = arrSelectedList[0].languageCode
                PreferencesUtility.getInstance(this@LanguageSelectActivity)
                    .setSelectedLanguageCode(selectedLanguageCode)

                Util.changeLanguage(this@LanguageSelectActivity)

                PreferencesUtility.getInstance(this@LanguageSelectActivity)
                    .setLanguageSelectionShown(true)

                if (!Util.hasAllPermissions(this@LanguageSelectActivity)) {
                    val intent = Intent(this@LanguageSelectActivity, PermissionActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } else {
                    val intent = Intent(this@LanguageSelectActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
                finish()
            } else {
                isProcessingClick = false
            }
        } else {
            isProcessingClick = false
        }
    }

    private fun loadBigBannerAd() {
        if (isFinishing || isDestroyed) return
        if (bannerAdView != null) return
        if (!AdsUtils.isConnected(this)) return

        BannerAdsManager.initialize(this)
        googleBannerAds = GoogleBannerAds()

        val incAds = binding.incAdsView
        googleBannerAds?.setupAdsViews(
            activity = this,
            skipAllBannerAds = false,
            showBannerShimmerLayout = true,
            rlMainGoogleBanner = incAds.rlMainGoogleAds,
            flSpaceLayout = incAds.flSpaceLayout,
            tvSpaceAds = incAds.tvSpaceAds,
            flShimmerGoogleBanner = incAds.flShimmerGoogleAds,
            flGoogleBanner = incAds.flGoogleAds
        )

        bannerAdView = AdView(this).apply {
            adUnitId = getString(R.string.admob_banner_big)
            setAdSize(AdsUtils.getBannerAdSize(this@LanguageSelectActivity))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    googleBannerAds?.onAdLoaded(this@LanguageSelectActivity, this@apply)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    super.onAdFailedToLoad(error)
                    googleBannerAds?.onAdFailedToLoad()
                }
            }
            val adRequest = AdRequest.Builder().build()
            loadAd(adRequest)
        }
    }

    override fun onDestroy() {
        bannerAdView?.destroy()
        bannerAdView = null
        super.onDestroy()
    }

    private fun setLanguageList() {
        arrLanguageList = Util.getAllLanguagesList(this)
        val sparseArray = getSelectedLanguageSparseArray(
            PreferencesUtility.getInstance(this).getSelectedLanguageCode()
        )
        languageSelectAdapter = LanguageSelectAdapter(this, arrLanguageList, sparseArray)
        rvLanguageList.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL,
            false
        )
        rvLanguageList.adapter = languageSelectAdapter
    }

    private fun getSelectedLanguageSparseArray(strLanguageCode: String): SparseArray<LanguagesModel> {
        val sparseArray = SparseArray<LanguagesModel>()

        arrLanguageList.forEachIndexed { index, languageModel ->
            if (languageModel.languageCode == strLanguageCode) {
                sparseArray.put(index, languageModel)
                return sparseArray
            }
        }
        if (arrLanguageList.isNotEmpty()) {
            sparseArray.put(0, arrLanguageList[0])
        }
        return sparseArray
    }

    companion object {
        fun getSelectedListFromSparseArray(sparseSelectedArray: SparseArray<LanguagesModel>?): List<LanguagesModel> {
            val arrSelectedList = mutableListOf<LanguagesModel>()
            sparseSelectedArray?.let {
                for (i in 0 until it.size()) {
                    arrSelectedList.add(it.valueAt(i))
                }
            }
            return arrSelectedList
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
