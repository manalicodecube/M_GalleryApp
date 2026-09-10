package com.developer.manali.galleryapp

import android.content.Intent
import android.os.Bundle
import android.util.SparseArray
import android.view.View
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.developer.manali.galleryapp.databinding.ActivityLanguageSelectBinding
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

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
        findViewById<View>(R.id.btnClose)?.setOnClickListener {
            finish()
        }
        btnClose = binding.btnClose
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
        loadBigBannerAd()
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasImages = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            val hasVideos = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_MEDIA_VIDEO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            hasImages && hasVideos
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
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

                if (isFromSplash && !hasRequiredPermissions()) {
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
            setAdSize(getBannerAdSize())
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

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
