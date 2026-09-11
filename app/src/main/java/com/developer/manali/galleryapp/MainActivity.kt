package com.developer.manali.galleryapp

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.developer.manali.galleryapp.data.AppPreferences
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.databinding.ActivityMainBinding
import com.developer.manali.galleryapp.ui.adapter.MainPagerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: MainPagerAdapter
    private val mediaRepository = MediaRepository()

    val currentAlbumsFragment: com.developer.manali.galleryapp.ui.fragment.AlbumsFragment?
        get() = (supportFragmentManager.fragments.firstOrNull { it is com.developer.manali.galleryapp.ui.fragment.AlbumsFragment } as? com.developer.manali.galleryapp.ui.fragment.AlbumsFragment)
            ?: if (::pagerAdapter.isInitialized) pagerAdapter.albumsFragment else null

    val currentPhotosFragment: com.developer.manali.galleryapp.ui.fragment.PhotosFragment?
        get() = (supportFragmentManager.fragments.firstOrNull { it is com.developer.manali.galleryapp.ui.fragment.PhotosFragment } as? com.developer.manali.galleryapp.ui.fragment.PhotosFragment)
            ?: if (::pagerAdapter.isInitialized) pagerAdapter.photosFragment else null

    val currentVideosFragment: com.developer.manali.galleryapp.ui.fragment.VideosFragment?
        get() = (supportFragmentManager.fragments.firstOrNull { it is com.developer.manali.galleryapp.ui.fragment.VideosFragment } as? com.developer.manali.galleryapp.ui.fragment.VideosFragment)
            ?: if (::pagerAdapter.isInitialized) pagerAdapter.videosFragment else null

    private var googleBannerAds: GoogleBannerAds? = null
    private var bannerAdView: AdView? = null

    var isSelectionMode: Boolean = false
        private set

    private var isEnteringSelectionMode = false

    private val gridColumnsReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.developer.manali.galleryapp.GRID_COLUMNS_CHANGED") {
                val span = intent.getIntExtra("span_count", -1)
                if (span in 2..7) {
                    currentPhotosFragment?.updateGridColumns(span)
                    currentVideosFragment?.updateGridColumns(span)
                }
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        openOriginalDeviceCamera()
    }

    private var pendingDirectoriesToCheck: List<String> = emptyList()
    private var isDeletingAlbums: Boolean = false

    private fun deleteDirectoryRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteDirectoryRecursively(child)
            }
        }
        file.delete()
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingDirectoriesToCheck.forEach { dirPath ->
                try {
                    val f = File(dirPath)
                    if (f.exists() && f.isDirectory) {
                        if (isDeletingAlbums) {
                            deleteDirectoryRecursively(f)
                        } else {
                            if (f.list()?.isEmpty() == true) {
                                f.delete()
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            pendingDirectoriesToCheck = emptyList()
            isDeletingAlbums = false
            Toast.makeText(this, getString(R.string.items_permanently_deleted), Toast.LENGTH_SHORT).show()
            exitSelectionMode()
            com.developer.manali.galleryapp.data.MediaRepository.clearCache()
            refreshAllFragments()
        }
    }

    private val passwordSetupLauncherForAlbums = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            lockSelectedAlbumsDirectly()
        }
    }

    private val passwordSetupLauncherForMedia = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            lockSelectedMediaDirectly()
        }
    }

    private val moveDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {

            com.developer.manali.galleryapp.data.MediaRepository.clearCache()
        }
        refreshAllFragments()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViewPager()
        setupBottomNav()
        setupTopBarMenus()
        setupSelectionBottomBar()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(Gravity.START)) {
                    binding.drawerLayout.closeDrawer(Gravity.START)
                } else if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        setupNavigationDrawer()
        loadBigBannerAd()
    }

    private var cachedMediaStats: com.developer.manali.galleryapp.data.MediaStats? = null

    override fun onResume() {
        super.onResume()
        loadMediaStats()
        refreshAllFragments()
        try {
            val filter = android.content.IntentFilter("com.developer.manali.galleryapp.GRID_COLUMNS_CHANGED")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(gridColumnsReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(gridColumnsReceiver, filter)
            }
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(gridColumnsReceiver)
        } catch (_: Exception) {}
    }

    private fun loadBigBannerAd() {
        if (!AdsUtils.isConnected(this)) return

        BannerAdsManager.initialize(this)
        googleBannerAds = GoogleBannerAds()


        bannerAdView = AdView(this).apply {
            adUnitId = getString(R.string.admob_banner)
            setAdSize(com.google.android.gms.ads.AdSize.BANNER)
            adListener = object : com.google.android.gms.ads.AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    binding.clAdView.removeAllViews()
                    binding.clAdView.addView(this@apply)
                    binding.clAdView.visibility = View.VISIBLE
                }

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    super.onAdFailedToLoad(error)
                    binding.clAdView.visibility = View.GONE
                }
            }
            val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
            loadAd(adRequest)
        }
    }

    override fun onDestroy() {
        bannerAdView?.destroy()
        bannerAdView = null
        super.onDestroy()
    }

    fun loadMediaStats() {
        lifecycleScope.launch {
            val stats = mediaRepository.getMediaStats(this@MainActivity)
            cachedMediaStats = stats
            updateSubtitleForCurrentTab()
        }
    }

    private fun updateSubtitleForCurrentTab() {
        val stats = cachedMediaStats ?: return
        val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.US)
        when (binding.viewPagerMain.currentItem) {
            0 -> {
                val albumStr = if (stats.albumCount == 1) getString(R.string._1_album) else "${nf.format(stats.albumCount)} Albums"
                val sizeStr = MediaRepository.formatFileSize(stats.totalSize)
                binding.tvMainSubtitle.text = "$albumStr • $sizeStr"
            }
            1 -> {
                val countStr = if (stats.photoCount == 1) getString(R.string._1_photo) else "${nf.format(stats.photoCount)} Photos"
                val sizeStr = MediaRepository.formatFileSize(stats.photoSize)
                binding.tvMainSubtitle.text = "$countStr • $sizeStr"
            }
            2 -> {
                val countStr = if (stats.videoCount == 1) getString(R.string._1_video) else "${nf.format(stats.videoCount)} Videos"
                val sizeStr = MediaRepository.formatFileSize(stats.videoSize)
                binding.tvMainSubtitle.text = "$countStr • $sizeStr"
            }
        }
    }

    private fun setupViewPager() {
        pagerAdapter = MainPagerAdapter(this)
        binding.viewPagerMain.adapter = pagerAdapter
        binding.viewPagerMain.offscreenPageLimit = 3

        binding.viewPagerMain.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateTabState(position)
            }
        })
    }

    private fun setupBottomNav() {
        binding.tabAlbums.setOnClickListener {
            binding.viewPagerMain.currentItem = 0
        }

        binding.tabPhotos.setOnClickListener {
            binding.viewPagerMain.currentItem = 1
        }

        binding.tabVideos.setOnClickListener {
            binding.viewPagerMain.currentItem = 2
        }
    }

    private fun setupTopBarMenus() {
        binding.btnAddAlbum.setOnClickListener {
            if (currentAlbumsFragment?.isAdded == true) {
                currentAlbumsFragment?.showCreateAlbumDialog()
            } else {
                showCreateAlbumDialog()
            }
        }


        binding.btnCamera.setOnClickListener {
            openOriginalDeviceCamera()
        }

        binding.btnMenuLeft.setOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                binding.drawerLayout.openDrawer(Gravity.START)
            }
        }

        binding.btnSelectAll.setOnClickListener {
            toggleSelectAll()
        }

        binding.btnMenuRight.setOnClickListener {
            showRightMenuDialog()
        }
    }
    private fun setupNavigationDrawer() {
        binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {
                updateDrawerStats()
            }
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        val sideMenu = binding.sideMenuDrawer

        sideMenu.menuCardPhotos.setOnClickListener {
            binding.viewPagerMain.currentItem = 1
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        sideMenu.menuCardVideos.setOnClickListener {
            binding.viewPagerMain.currentItem = 2
            binding.drawerLayout.closeDrawer(Gravity.START)
        }

        sideMenu.menuCardAlbums.setOnClickListener {
            binding.viewPagerMain.currentItem = 0
            binding.drawerLayout.closeDrawer(Gravity.START)
        }

        sideMenu.menuItemFavorites.setOnClickListener {
            binding.drawerLayout.closeDrawer(Gravity.START)
            val intent = Intent(this, FavoriteActivity::class.java)
            startActivity(intent)
        }


        val prefs = PreferencesUtility.getInstance(this)
        val switch = sideMenu.switchNightMode

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )

        val trackColors = intArrayOf(
            ContextCompat.getColor(this, R.color.lumina_primary),
            Color.parseColor("#D1D1D6")
        )

        val thumbColors = intArrayOf(
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#9CA3AF")
        )

        switch.trackTintList = ColorStateList(states, trackColors)
        switch.thumbTintList = ColorStateList(states, thumbColors)

        switch.isChecked = prefs.isNightMode()

        switch.setOnCheckedChangeListener { _, isChecked ->
            val currentMode = prefs.isNightMode()
            if (currentMode != isChecked) {
                prefs.setNightMode(isChecked)
                val targetMode = if (isChecked) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
                if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                    AppCompatDelegate.setDefaultNightMode(targetMode)
                }
                recreate()
            }
            binding.drawerLayout.closeDrawer(Gravity.START)
        }



        sideMenu.menuItemLanguage.setOnClickListener {
            binding.drawerLayout.closeDrawer(Gravity.START)
            val intent = Intent(this, LanguageSelectActivity::class.java)
            startActivity(intent)
        }

        sideMenu.menuItemLock?.setOnClickListener {
            binding.drawerLayout.closeDrawer(Gravity.START)
            val intent = Intent(this, LockscreenActivity::class.java)
            startActivity(intent)
        }

        sideMenu.menuItemShareApp.setOnClickListener {
            binding.drawerLayout.closeDrawer(Gravity.START)
            shareApp()
        }

        sideMenu.menuItemRateUs.setOnClickListener {
            binding.drawerLayout.closeDrawer(Gravity.START)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            }
        }

        sideMenu.menuItemPrivacyPolicy.setOnClickListener {
            binding.drawerLayout.closeDrawer(Gravity.START)
            showPrivacyPolicyDialog()
        }

        sideMenu.menuItemSettings.setOnClickListener {
            binding.drawerLayout.closeDrawer(Gravity.START)
            Toast.makeText(this, getString(R.string.settings), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDrawerStats() {
        lifecycleScope.launch {
            val stats = mediaRepository.getMediaStats(this@MainActivity)
            cachedMediaStats = stats
            updateSubtitleForCurrentTab()

            val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.US)
            val sideMenu = binding.sideMenuDrawer
            sideMenu.tvDrawerPhotoCount.text = nf.format(stats.photoCount)
            sideMenu.tvDrawerAlbumCount.text = nf.format(stats.albumCount)
            sideMenu.tvDrawerVideoCount.text = nf.format(stats.videoCount)

            sideMenu.tvDrawerTotalStorageSize.text = MediaRepository.formatFileSize(stats.totalSize)
            val itemsFormatted = nf.format(stats.totalCount)
            val itemLabel = if (stats.totalCount == 1) getString(R.string._1_total_media_item) else getString(
                R.string.total_media_items, itemsFormatted
            )
            sideMenu.tvDrawerTotalItemCount.text = itemLabel
        }
    }


    private fun shareApp() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, " Gallery")
                putExtra(Intent.EXTRA_TEXT, "Check out Gallery app to manage your photos and videos seamlessly: https://play.google.com/store/apps/details?id=$packageName")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.unable_to_share_app), Toast.LENGTH_SHORT).show()
        }
    }



    private fun showPrivacyPolicyDialog() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://policies.google.com/privacy")
        )
        startActivity(intent)
    }


    private fun openOriginalDeviceCamera() {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(this,
                    getString(R.string.could_not_open_camera_app), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateAlbumDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_album, null)
        dialog.setContentView(dialogView)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etNewAlbumName)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelAlbum)
        val btnCreate = dialogView.findViewById<View>(R.id.btnCreateAlbum)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isNotEmpty()) {
                binding.viewPagerMain.currentItem = 0
                currentAlbumsFragment?.createNewAlbum(name, this)
                dialog.dismiss()
            } else {
                Toast.makeText(this,
                    getString(R.string.please_enter_an_album_name), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
        etName.requestFocus()
    }


    private fun showRightMenuDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val dialogView = layoutInflater.inflate(R.layout.dialog_right_menu, null)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            decorView.setBackgroundColor(Color.TRANSPARENT)
            setDimAmount(0f)
        }

        val currentTab = binding.viewPagerMain.currentItem
        val layoutSelect = dialogView.findViewById<View>(R.id.layoutMenuSelect)
        val dividerSelect = dialogView.findViewById<View>(R.id.dividerMenuSelect)
        val layoutColumns = dialogView.findViewById<View>(R.id.layoutMenuColumns)
        val dividerColumns = dialogView.findViewById<View>(R.id.dividerMenuColumns)

        val appPrefs = AppPreferences.getInstance(this)
        val isGrid = when (currentTab) {
            0 -> !appPrefs.isAlbumsListView
            2 -> !appPrefs.isVideosListView
            else -> !appPrefs.isPhotosListView
        }

        layoutSelect.visibility = View.VISIBLE
        dividerSelect?.visibility = View.GONE

        if (isGrid) {
            layoutColumns.visibility = View.VISIBLE
            dividerColumns?.visibility = View.GONE
        } else {
            layoutColumns.visibility = View.GONE
            dividerColumns?.visibility = View.GONE
        }

        layoutSelect.setOnClickListener {
            dialog.dismiss()
            enterSelectionMode()
        }

        layoutColumns.setOnClickListener {
            dialog.dismiss()
            showColumnsDialog()
        }

        dialogView.findViewById<View>(R.id.layoutMenuViewType).setOnClickListener {
            dialog.dismiss()
            showViewTypeDialog()
        }

        dialogView.findViewById<View>(R.id.layoutMenuSortBy).setOnClickListener {
            dialog.dismiss()
            showSortByDialog()
        }

        dialog.show()

        dialog.window?.let { window ->
            val density = resources.displayMetrics.density
            val widthPx = (210 * density).toInt()
            val marginX = (16 * density).toInt()
            val marginY = (56 * density).toInt()

            val params = window.attributes
            params.gravity = Gravity.TOP or Gravity.END
            params.x = marginX
            params.y = marginY
            params.width = widthPx
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = params
            window.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.decorView.setPadding(0, 0, 0, 0)
        }
    }


    private fun showColumnsDialog() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_columns, null)
        bottomSheetDialog.setContentView(dialogView)

        val rgColumns = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgColumns)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelColumns)
        val btnOk = dialogView.findViewById<View>(R.id.btnOkColumns)

        val rb2 = dialogView.findViewById<android.widget.RadioButton>(R.id.rbColumn2)
        val rb3 = dialogView.findViewById<android.widget.RadioButton>(R.id.rbColumn3)
        val rb4 = dialogView.findViewById<android.widget.RadioButton>(R.id.rbColumn4)

        rb2?.setOnClickListener { rgColumns.check(R.id.rbColumn2) }
        rb3?.setOnClickListener { rgColumns.check(R.id.rbColumn3) }
        rb4?.setOnClickListener { rgColumns.check(R.id.rbColumn4) }

        val appPrefs = AppPreferences.getInstance(this)
        val currentTab = binding.viewPagerMain.currentItem

        val currentSpan = when (currentTab) {
            0 -> appPrefs.albumsGridColumns
            else -> appPrefs.gridColumns
        }

        when (currentSpan) {
            2 -> rgColumns.check(R.id.rbColumn2)
            3 -> rgColumns.check(R.id.rbColumn3)
            4 -> rgColumns.check(R.id.rbColumn4)
            else -> rgColumns.check(R.id.rbColumn3)
        }

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnOk.setOnClickListener {
            val selectedCols = when (rgColumns.checkedRadioButtonId) {
                R.id.rbColumn2 -> 2
                R.id.rbColumn3 -> 3
                R.id.rbColumn4 -> 4
                else -> currentSpan
            }

            if (currentTab == 0) {
                appPrefs.albumsGridColumns = selectedCols
                appPrefs.albumsViewType = AppPreferences.VIEW_TYPE_GRID
                currentAlbumsFragment?.applyViewType(AppPreferences.VIEW_TYPE_GRID)
                currentAlbumsFragment?.updateGridColumns(selectedCols)
            } else {
                appPrefs.gridColumns = selectedCols
                appPrefs.photosGridColumns = selectedCols
                appPrefs.videosGridColumns = selectedCols
                appPrefs.viewType = AppPreferences.VIEW_TYPE_GRID
                appPrefs.photosViewType = AppPreferences.VIEW_TYPE_GRID
                appPrefs.videosViewType = AppPreferences.VIEW_TYPE_GRID
                currentPhotosFragment?.applyViewType(AppPreferences.VIEW_TYPE_GRID)
                currentPhotosFragment?.updateGridColumns(selectedCols)
                currentVideosFragment?.applyViewType(AppPreferences.VIEW_TYPE_GRID)
                currentVideosFragment?.updateGridColumns(selectedCols)
            }

            Toast.makeText(this, getString(R.string.columns_applied, selectedCols), Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun showViewTypeDialog() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_view_type, null)
        bottomSheetDialog.setContentView(dialogView)

        val rbGrid = dialogView.findViewById<android.widget.RadioButton>(R.id.rbViewTypeGrid)
        val rbList = dialogView.findViewById<android.widget.RadioButton>(R.id.rbViewTypeList)
        val layoutOptionGrid = dialogView.findViewById<View>(R.id.layoutOptionGrid)
        val layoutOptionList = dialogView.findViewById<View>(R.id.layoutOptionList)

        val appPrefs = AppPreferences.getInstance(this)
        val currentTab = binding.viewPagerMain.currentItem

        var selectedType = when (currentTab) {
            0 -> appPrefs.albumsViewType
            2 -> appPrefs.videosViewType
            else -> appPrefs.photosViewType
        }

        val rgViewType = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgViewType)

        if (selectedType == AppPreferences.VIEW_TYPE_LIST) {
            rbList.isChecked = true
            rbGrid.isChecked = false
        } else {
            rbGrid.isChecked = true
            rbList.isChecked = false
        }

        fun applyType(type: String) {
            selectedType = type
            when (currentTab) {
                0 -> {
                    appPrefs.albumsViewType = type
                    currentAlbumsFragment?.applyViewType(type)
                }
                else -> {
                    appPrefs.viewType = type
                    appPrefs.photosViewType = type
                    appPrefs.videosViewType = type
                    currentPhotosFragment?.applyViewType(type)
                    currentVideosFragment?.applyViewType(type)
                }
            }
            val displayLabel = if (type == AppPreferences.VIEW_TYPE_LIST) getString(R.string.list_view) else getString(R.string.grid_view)
            Toast.makeText(this, getString(R.string.applied, displayLabel), Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        layoutOptionGrid.setOnClickListener {
            rbGrid.isChecked = true
            rbList.isChecked = false
            applyType(AppPreferences.VIEW_TYPE_GRID)
        }

        layoutOptionList.setOnClickListener {
            rbList.isChecked = true
            rbGrid.isChecked = false
            applyType(AppPreferences.VIEW_TYPE_LIST)
        }

        rbGrid.setOnClickListener {
            rbGrid.isChecked = true
            rbList.isChecked = false
            applyType(AppPreferences.VIEW_TYPE_GRID)
        }

        rbList.setOnClickListener {
            rbList.isChecked = true
            rbGrid.isChecked = false
            applyType(AppPreferences.VIEW_TYPE_LIST)
        }

        bottomSheetDialog.show()
    }

    private fun showSortByDialog() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_sort_by, null)
        bottomSheetDialog.setContentView(dialogView)

        val rbNewest = dialogView.findViewById<android.widget.RadioButton>(R.id.rbSortNewest)
        val rbOldest = dialogView.findViewById<android.widget.RadioButton>(R.id.rbSortOldest)
        val rbNameAsc = dialogView.findViewById<android.widget.RadioButton>(R.id.rbSortNameAsc)
        val rbNameDesc = dialogView.findViewById<android.widget.RadioButton>(R.id.rbSortNameDesc)

        val layoutNewest = dialogView.findViewById<View>(R.id.layoutOptionNewest)
        val layoutOldest = dialogView.findViewById<View>(R.id.layoutOptionOldest)
        val layoutNameAsc = dialogView.findViewById<View>(R.id.layoutOptionNameAsc)
        val layoutNameDesc = dialogView.findViewById<View>(R.id.layoutOptionNameDesc)

        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelSort)
        val btnDone = dialogView.findViewById<View>(R.id.btnDoneSort)

        val appPrefs = AppPreferences.getInstance(this)
        val currentTab = binding.viewPagerMain.currentItem

        var selectedSort = when (currentTab) {
            0 -> appPrefs.albumsSortBy
            2 -> appPrefs.videosSortBy
            else -> appPrefs.photosSortBy
        }

        fun updateSelection(sortKey: String) {
            selectedSort = sortKey
            rbNewest.isChecked = (sortKey == AppPreferences.SORT_NEWEST)
            rbOldest.isChecked = (sortKey == AppPreferences.SORT_OLDEST)
            rbNameAsc.isChecked = (sortKey == AppPreferences.SORT_NAME_ASC)
            rbNameDesc.isChecked = (sortKey == AppPreferences.SORT_NAME_DESC)
        }

        updateSelection(selectedSort)

        layoutNewest.setOnClickListener { updateSelection(AppPreferences.SORT_NEWEST) }
        layoutOldest.setOnClickListener { updateSelection(AppPreferences.SORT_OLDEST) }
        layoutNameAsc.setOnClickListener { updateSelection(AppPreferences.SORT_NAME_ASC) }
        layoutNameDesc.setOnClickListener { updateSelection(AppPreferences.SORT_NAME_DESC) }

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnDone.setOnClickListener {
            when (currentTab) {
                0 -> {
                    appPrefs.albumsSortBy = selectedSort
                    currentAlbumsFragment?.applySort(selectedSort)
                }
                2 -> {
                    appPrefs.videosSortBy = selectedSort
                    currentVideosFragment?.refreshData()
                }
                else -> {
                    appPrefs.photosSortBy = selectedSort
                    currentPhotosFragment?.refreshData()
                }
            }

            val sortLabel = when (selectedSort) {
                AppPreferences.SORT_NEWEST -> getString(R.string.newest_on_top)
                AppPreferences.SORT_OLDEST -> getString(R.string.oldest_on_top)
                AppPreferences.SORT_NAME_ASC -> if (currentTab == 0) getString(R.string.album_name_a_z)else getString(
                    R.string.name_a_z
                )
                AppPreferences.SORT_NAME_DESC -> if (currentTab == 0) getString(R.string.album_name_z_a) else getString(
                    R.string.name_z_a
                )
                else -> getString(R.string.sorted)
            }
            Toast.makeText(this, getString(R.string.applied, sortLabel), Toast.LENGTH_SHORT).show()

            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun updateTabState(position: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.lumina_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.lumina_text_secondary)

        setTabColor(binding.ivTabAlbums, binding.tvTabAlbums, inactiveColor)
        setTabColor(binding.ivTabPhotos, binding.tvTabPhotos, inactiveColor)
        setTabColor(binding.ivTabVideos, binding.tvTabVideos, inactiveColor)

        when (position) {
            0 -> {
                setTabColor(binding.ivTabAlbums, binding.tvTabAlbums, activeColor)
                binding.tvMainTitle.text = getString(R.string.albums)
                binding.btnAddAlbum.visibility = View.VISIBLE
                binding.btnCamera.visibility = View.VISIBLE
                binding.btnMenuRight.visibility = View.VISIBLE
            }
            1 -> {
                setTabColor(binding.ivTabPhotos, binding.tvTabPhotos, activeColor)
                binding.tvMainTitle.text =  getString(R.string.photos)
                binding.btnAddAlbum.visibility = View.GONE
                binding.btnCamera.visibility = View.GONE
                binding.btnMenuRight.visibility = View.VISIBLE
            }
            2 -> {
                setTabColor(binding.ivTabVideos, binding.tvTabVideos, activeColor)
                binding.tvMainTitle.text = getString(R.string.videos)
                binding.btnAddAlbum.visibility = View.GONE
                binding.btnCamera.visibility = View.GONE
                binding.btnMenuRight.visibility = View.VISIBLE
            }
        }
        updateSubtitleForCurrentTab()
    }

    private fun setTabColor(iv: ImageView, tv: TextView, color: Int) {
        iv.imageTintList = ColorStateList.valueOf(color)
        tv.setTextColor(color)
    }

    private fun setupSelectionBottomBar() {
        binding.actionSelectShare.setOnClickListener {
            shareSelectedMedia()
        }

        binding.actionSelectDelete.setOnClickListener {
            deleteSelectedMedia()
        }

        binding.actionSelectMove.setOnClickListener {
            moveSelectedMedia()
        }

        binding.actionSelectFavorite.setOnClickListener {
            toggleSelectedFavorites()
        }

        binding.actionSelectMoveToVault.setOnClickListener { moveSelectedMediaToVault() }

        binding.actionAlbumShare.setOnClickListener { shareSelectedMedia() }
        binding.actionAlbumDelete.setOnClickListener { deleteSelectedMedia() }
        binding.actionAlbumMove.setOnClickListener { moveSelectedMedia() }
        binding.actionAlbumMoveToVault.setOnClickListener {
            val selectedAlbums = currentAlbumsFragment?.getSelectedItems() ?: emptyList()
            if (selectedAlbums.isNotEmpty()) {
                val appPrefs = AppPreferences.getInstance(this)
                if (appPrefs.appLockPin.isEmpty()) {
                    val intent = Intent(this, LockscreenActivity::class.java)
                    passwordSetupLauncherForAlbums.launch(intent)
                } else {
                    lockSelectedAlbumsDirectly()
                }
            }
        }
    }

    private fun lockSelectedAlbumsDirectly() {
        val selectedAlbums = currentAlbumsFragment?.getSelectedItems() ?: emptyList()
        if (selectedAlbums.isNotEmpty()) {
            val appPrefs = AppPreferences.getInstance(this)
            val selectedIds = selectedAlbums.map { it.bucketId }
            appPrefs.setLockedAlbums(selectedIds, true)
            Toast.makeText(this, getString(R.string.moved_to_vault), Toast.LENGTH_SHORT).show()
            MediaRepository.clearCache()
            exitSelectionMode()
            refreshAllFragments()
        }
    }

    private fun moveSelectedMediaToVault() {
        val appPrefs = AppPreferences.getInstance(this)
        if (appPrefs.appLockPin.isEmpty()) {
            val intent = Intent(this, LockscreenActivity::class.java)
            passwordSetupLauncherForMedia.launch(intent)
        } else {
            lockSelectedMediaDirectly()
        }
    }

    private fun lockSelectedMediaDirectly() {
        val selectedMedia = if (binding.viewPagerMain.currentItem == 1) {
            currentPhotosFragment?.getSelectedItems() ?: emptyList()
        } else if (binding.viewPagerMain.currentItem == 2) {
            currentVideosFragment?.getSelectedItems() ?: emptyList()
        } else {
            emptyList()
        }
        
        if (selectedMedia.isNotEmpty()) {
            val appPrefs = AppPreferences.getInstance(this)
            val selectedIds = selectedMedia.map { it.id.toString() }
            appPrefs.setLockedMedia(selectedIds, true)
            Toast.makeText(this, getString(R.string.moved_to_vault), Toast.LENGTH_SHORT).show()
            MediaRepository.clearCache()
            exitSelectionMode()
            refreshAllFragments()
        }
    }

    fun enterSelectionMode(initialItem: MediaItem? = null) {
        val currentTab = binding.viewPagerMain.currentItem
        isEnteringSelectionMode = true
        try {
            isSelectionMode = true
            binding.btnMenuLeft.setImageResource(R.drawable.ic_close)
            binding.btnAddAlbum.visibility = View.GONE
            binding.btnCamera.visibility = View.GONE

            if (currentTab == 0) {
                enterAlbumSelectionMode(null)
                return
            }

            binding.btnMenuRight.visibility = View.GONE
            binding.btnSelectAll.visibility = View.VISIBLE
            binding.bottomNavigationContainer.visibility = View.GONE
            binding.selectionBottomBarContainer.visibility = View.VISIBLE
            binding.albumSelectionBottomBarContainer.visibility = View.GONE

            if (currentTab == 1) {
                currentPhotosFragment?.enterSelectionMode(initialItem)
            } else if (currentTab == 2) {
                currentVideosFragment?.enterSelectionMode(initialItem)
            }

            val initialList = if (initialItem != null) listOf(initialItem) else emptyList()
            updateSelectionHeader(initialList.size, initialList)
        } finally {
            isEnteringSelectionMode = false
        }
    }

    fun enterAlbumSelectionMode(initialAlbum: com.developer.manali.galleryapp.data.AlbumItem? = null) {
        val wasEntering = isEnteringSelectionMode
        isEnteringSelectionMode = true
        try {
            isSelectionMode = true
            binding.btnMenuLeft.setImageResource(R.drawable.ic_close)
            binding.btnAddAlbum.visibility = View.GONE
            binding.btnCamera.visibility = View.GONE

            binding.btnMenuRight.visibility = View.GONE
            binding.btnSelectAll.visibility = View.VISIBLE
            binding.bottomNavigationContainer.visibility = View.GONE
            binding.selectionBottomBarContainer.visibility = View.GONE
            binding.albumSelectionBottomBarContainer.visibility = View.VISIBLE

            binding.actionAlbumShare.alpha = 0.4f
            binding.actionAlbumDelete.alpha = 0.4f
            binding.actionAlbumMove.alpha = 0.4f
            binding.actionAlbumMoveToVault.alpha = 0.4f
            binding.actionAlbumShare.isEnabled = false
            binding.actionAlbumDelete.isEnabled = false
            binding.actionAlbumMove.isEnabled = false
            binding.actionAlbumMoveToVault.isEnabled = false

            currentAlbumsFragment?.enterSelectionMode(initialAlbum)
            val selected = currentAlbumsFragment?.getSelectedItems() ?: emptyList()
            onAlbumSelectionUpdated(selected.size, selected)
        } finally {
            if (!wasEntering) {
                isEnteringSelectionMode = false
            }
        }
    }

    fun exitSelectionMode() {
        if (!isSelectionMode) return
        isSelectionMode = false

        binding.btnMenuLeft.setImageResource(R.drawable.sort)
        binding.btnSelectAll.visibility = View.GONE
        binding.btnMenuRight.visibility = View.VISIBLE

        binding.albumSelectionBottomBarContainer.visibility = View.GONE
        binding.selectionBottomBarContainer.visibility = View.GONE
        binding.bottomNavigationContainer.visibility = View.VISIBLE

        if (currentAlbumsFragment?.isAdded == true) {
            currentAlbumsFragment?.exitSelectionMode()
        }
        currentPhotosFragment?.exitSelectionMode()
        currentVideosFragment?.exitSelectionMode()

        updateTabState(binding.viewPagerMain.currentItem)
    }

    fun onAlbumSelectionUpdated(count: Int, items: List<com.developer.manali.galleryapp.data.AlbumItem>) {
        if (!isSelectionMode || binding.viewPagerMain.currentItem != 0) return
        if (count == 0 && !isEnteringSelectionMode) {
            exitSelectionMode()
            return
        }

        val titleText = getString(R.string.selected, count)
        binding.tvMainTitle.text = titleText
        binding.tvMainSubtitle.text = getString(R.string.tap_items_to_select)

        val totalCount = currentAlbumsFragment?.getAllMediaItems()?.size ?: 0
        if (count > 0 && totalCount > 0 && count == totalCount) {
            binding.btnSelectAll.setImageResource(R.drawable.ic_select_checked)
            binding.btnSelectAll.imageTintList = null
        } else {
            binding.btnSelectAll.setImageResource(R.drawable.select)
            binding.btnSelectAll.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.lumina_text_title)
            )
        }

        val hasSelection = count > 0
        val alpha = if (hasSelection) 1.0f else 0.4f
        binding.actionAlbumShare.alpha = alpha
        binding.actionAlbumDelete.alpha = alpha
        binding.actionAlbumMove.alpha = alpha
        binding.actionAlbumMoveToVault.alpha = alpha
        
        binding.actionAlbumShare.isEnabled = hasSelection
        binding.actionAlbumDelete.isEnabled = hasSelection
        binding.actionAlbumMove.isEnabled = hasSelection
        binding.actionAlbumMoveToVault.isEnabled = hasSelection
    }

    fun onSelectionUpdated(count: Int, items: List<MediaItem>) {
        if (!isSelectionMode) return
        if (count == 0 && !isEnteringSelectionMode) {
            exitSelectionMode()
            return
        }
        updateSelectionHeader(count, items)
    }

    fun onItemLongPressed(item: MediaItem) {
        if (!isSelectionMode) {
            enterSelectionMode(item)
        }
    }

    private fun updateSelectionHeader(count: Int, items: List<MediaItem>) {
        val titleText = getString(R.string.selected, count)
        binding.tvMainTitle.text = titleText

        val currentTab = binding.viewPagerMain.currentItem
        val totalCount = when (currentTab) {
            1 -> currentPhotosFragment?.getAllMediaItems()?.size ?: 0
            2 -> currentVideosFragment?.getAllMediaItems()?.size ?: 0
            else -> 0
        }

        if (count > 0 && totalCount > 0 && count == totalCount) {
            binding.btnSelectAll.setImageResource(R.drawable.ic_select_checked)
            binding.btnSelectAll.imageTintList = null
        } else {
            binding.btnSelectAll.setImageResource(R.drawable.select)
            binding.btnSelectAll.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.lumina_text_title)
            )
        }

        if (count > 0) {
            val totalBytes = items.sumOf { it.size }
            val sizeStr = MediaRepository.formatFileSize(totalBytes)
            binding.tvMainSubtitle.text = getString(R.string.selected, sizeStr)

            val appPrefs = AppPreferences.getInstance(this)
            val allFav = items.all { appPrefs.isFavorite(it.id) }
            if (allFav) {
                binding.ivSelectFavorite.setImageResource(R.drawable.ic_heart_filled)
                binding.ivSelectFavorite.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.lumina_primary)
                )
                binding.tvSelectFavorite.text = getString(R.string.unfavorite)
            } else {
                binding.ivSelectFavorite.setImageResource(R.drawable.favorite)
                binding.ivSelectFavorite.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.lumina_text_title)
                )
                binding.tvSelectFavorite.text = getString(R.string.favorite)
            }
        } else {
            binding.tvMainSubtitle.text = getString(R.string.tap_items_to_select)
            binding.ivSelectFavorite.setImageResource(R.drawable.favorite)
            binding.ivSelectFavorite.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.lumina_text_title)
            )
            binding.tvSelectFavorite.text = getString(R.string.favorite)
        }

        val hasSelection = count > 0
        val alpha = if (hasSelection) 1.0f else 0.4f
        binding.actionSelectShare.alpha = alpha
        binding.actionSelectDelete.alpha = alpha
        binding.actionSelectMove.alpha = alpha
        binding.actionSelectFavorite.alpha = alpha
        
        binding.actionSelectShare.isEnabled = hasSelection
        binding.actionSelectDelete.isEnabled = hasSelection
        binding.actionSelectMove.isEnabled = hasSelection
        binding.actionSelectFavorite.isEnabled = hasSelection
    }

    private fun toggleSelectedFavorites() {
        lifecycleScope.launch {
            val selected = getCurrentlySelectedItemsAsync()
            withContext(Dispatchers.Main) {
                if (selected.isEmpty()) {
                    Toast.makeText(this@MainActivity, getString(R.string.please_select_at_least_1_item), Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val appPrefs = AppPreferences.getInstance(this@MainActivity)
                val selectedIds = selected.map { it.id }
                val allAreFavorites = selectedIds.all { appPrefs.isFavorite(it) }

                if (allAreFavorites) {
                    appPrefs.setFavorites(selectedIds, false)
                    Toast.makeText(this@MainActivity, getString(R.string.removed_item_s_from_favorites,selected.size), Toast.LENGTH_SHORT).show()
                } else {
                    appPrefs.setFavorites(selectedIds, true)
                    Toast.makeText(this@MainActivity, getString(R.string.added_item_s_to_favorites,selected.size), Toast.LENGTH_SHORT).show()
                }

                exitSelectionMode()
                refreshAllFragments()
            }
        }
    }

    private fun toggleSelectAll() {
        if (binding.viewPagerMain.currentItem == 0) {
            val currentSelected = currentAlbumsFragment?.getSelectedItems() ?: emptyList()
            val allAlbums = currentAlbumsFragment?.getAllMediaItems() ?: emptyList()
            if (currentSelected.size == allAlbums.size && allAlbums.isNotEmpty()) {
                currentAlbumsFragment?.deselectAll()
            } else {
                currentAlbumsFragment?.selectAll()
            }
        } else if (binding.viewPagerMain.currentItem == 1) {
            val currentSelected = currentPhotosFragment?.getSelectedItems() ?: emptyList()
            val allPhotos = currentPhotosFragment?.getAllMediaItems() ?: emptyList()
            if (currentSelected.size == allPhotos.size && allPhotos.isNotEmpty()) {
                currentPhotosFragment?.deselectAll()
            } else {
                currentPhotosFragment?.selectAll()
            }
        } else if (binding.viewPagerMain.currentItem == 2) {
            val currentSelected = currentVideosFragment?.getSelectedItems() ?: emptyList()
            val allVideos = currentVideosFragment?.getAllMediaItems() ?: emptyList()
            if (currentSelected.size == allVideos.size && allVideos.isNotEmpty()) {
                currentVideosFragment?.deselectAll()
            } else {
                currentVideosFragment?.selectAll()
            }
        }
    }

    private suspend fun getCurrentlySelectedItemsAsync(): List<MediaItem> {
        return when (binding.viewPagerMain.currentItem) {
            0 -> {
                val selectedAlbumIds = currentAlbumsFragment?.getSelectedItems()?.map { it.bucketId } ?: emptyList()
                val photos = mediaRepository.getPhotos(this@MainActivity)
                val videos = mediaRepository.getVideos(this@MainActivity)
                val allMedia = photos + videos
                allMedia.filter { selectedAlbumIds.contains(it.bucketId) }
            }
            1 -> currentPhotosFragment?.getSelectedItems() ?: emptyList()
            2 -> currentVideosFragment?.getSelectedItems() ?: emptyList()
            else -> emptyList()
        }
    }

    private fun shareSelectedMedia() {
        lifecycleScope.launch {
            val selected = getCurrentlySelectedItemsAsync()
            withContext(Dispatchers.Main) {
                if (selected.isEmpty()) {
                    Toast.makeText(this@MainActivity, getString(R.string.please_select_at_least_1_item_to_share), Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                try {
                    if (selected.size == 1) {
                        val item = selected[0]
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = item.mimeType.ifEmpty { if (item.isVideo) "video/*" else "image/*" }
                            putExtra(Intent.EXTRA_STREAM, item.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.share_media)))
                    } else {
                        val uris = ArrayList<Uri>()
                        selected.forEach { uris.add(it.uri) }
                        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.share_items,selected.size)))
                    }
                    exitSelectionMode()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.unable_to_share_selected_media), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteSelectedMedia() {
        lifecycleScope.launch {
            val currentTab = binding.viewPagerMain.currentItem
            val selectedMedia = getCurrentlySelectedItemsAsync()
            val selectedAlbums = if (currentTab == 0) currentAlbumsFragment?.getSelectedItems() ?: emptyList() else emptyList()

            val resolvedDirs = mutableListOf<String>()
            if (currentTab == 0) {
                selectedAlbums.forEach { album ->
                    val dirFile = mediaRepository.getAlbumDirectory(this@MainActivity, album.bucketId, album.bucketName)
                    if (dirFile.exists() && dirFile.isDirectory) {
                        resolvedDirs.add(dirFile.absolutePath)
                    }
                }
            } else {
                selectedMedia.mapNotNull { 
                    try { File(it.path).parent } catch(e: Exception) { null } 
                }.distinct().forEach { resolvedDirs.add(it) }
            }

            withContext(Dispatchers.Main) {
                if (selectedMedia.isEmpty() && selectedAlbums.isEmpty()) {
                    Toast.makeText(this@MainActivity,getString(R.string.please_select_at_least_1_item_to_delete), Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
                val dialog = AlertDialog.Builder(this@MainActivity)
                    .setView(dialogView)
                    .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDeleteDialogTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvDeleteDialogSubtitle)
        val tvName = dialogView.findViewById<TextView>(R.id.tvDeleteVideoName)
        val tvDetails = dialogView.findViewById<TextView>(R.id.tvDeleteVideoDetails)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivDeleteMediaIcon)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmDelete)

        val isSingle = if (currentTab == 0) selectedAlbums.size == 1 else selectedMedia.size == 1
        tvTitle.text = if (currentTab == 0) {
            if (isSingle) getString(R.string.delete_album) else getString(
                R.string.delete_albums,
                selectedAlbums.size
            )
        } else if (isSingle) {
            if (selectedMedia.isNotEmpty() && selectedMedia[0].isVideo)  getString(R.string.delete_video) else getString(R.string.delete_photo)
        } else {
            getString(R.string.delete_items, selectedMedia.size)
        }
        tvSubtitle.text = getString(R.string.selected_item_s_will_be_permanently_deleted_from_storage)

        if (currentTab == 0) {
            if (isSingle) {
                ivIcon.setImageResource(R.drawable.folder)
                tvName.text = selectedAlbums[0].bucketName
                tvDetails.text = getString(R.string.items,selectedAlbums[0].itemCount)
            } else {
                ivIcon.setImageResource(R.drawable.delete)
                tvName.text = getString(R.string.albums_selected, selectedAlbums.size)
                tvDetails.text = getString(R.string.total_items, selectedMedia.size)
            }
        } else {
            if (isSingle && selectedMedia.isNotEmpty()) {
                val item = selectedMedia[0]
                ivIcon.setImageResource(if (item.isVideo) R.drawable.video else R.drawable.photo)
                tvName.text = item.displayName
                val sizeStr = MediaRepository.formatFileSize(item.size)
                val durStr = if (item.isVideo) MediaRepository.formatDuration(item.duration) else ""
                tvDetails.text = if (durStr.isNotEmpty() && durStr != "0:00") "$sizeStr • $durStr" else sizeStr
            } else {
                ivIcon.setImageResource(R.drawable.delete)
                tvName.text = getString(R.string.items_selected,selectedMedia.size)
                val totalSize = selectedMedia.sumOf { it.size }
                tvDetails.text = MediaRepository.formatFileSize(totalSize)
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            isDeletingAlbums = (currentTab == 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val uris = selectedMedia.map { it.uri }
                    if (uris.isNotEmpty()) {
                        pendingDirectoriesToCheck = resolvedDirs
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(contentResolver, uris)
                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        deleteLauncher.launch(intentSenderRequest)
                    } else {
                        lifecycleScope.launch(Dispatchers.IO) {
                            resolvedDirs.forEach { dirPath ->
                                try {
                                    val f = File(dirPath)
                                    if (f.exists() && f.isDirectory) {
                                        deleteDirectoryRecursively(f)
                                    }
                                } catch (e: Exception) {}
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, getString(R.string.items_permanently_deleted), Toast.LENGTH_SHORT).show()
                                exitSelectionMode()
                                refreshAllFragments()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_initiate_deletion), Toast.LENGTH_SHORT).show()
                }
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    var deletedCount = 0
                    val deletedPaths = mutableListOf<String>()

                    for (item in selectedMedia) {
                        try {
                            val rows = contentResolver.delete(item.uri, null, null)
                            if (rows > 0) {
                                deletedCount++
                            } else if (item.path.isNotEmpty()) {
                                val f = File(item.path)
                                if (f.exists() && f.delete()) deletedCount++
                            }
                            if (item.path.isNotEmpty()) {
                                deletedPaths.add(item.path)
                                try {
                                    val parent = File(item.path).parentFile
                                    if (parent != null && parent.exists() && parent.isDirectory && parent.list()?.isEmpty() == true) {
                                        parent.delete()
                                    }
                                } catch (e: Exception) {}
                            }
                        } catch (e: Exception) {
                            if (item.path.isNotEmpty()) {
                                try {
                                    val f = File(item.path)
                                    if (f.exists() && f.delete()) {
                                        deletedCount++
                                        deletedPaths.add(item.path)
                                        val parent = f.parentFile
                                        if (parent != null && parent.exists() && parent.isDirectory && parent.list()?.isEmpty() == true) {
                                            parent.delete()
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    
                    if (currentTab == 0) {
                        resolvedDirs.forEach { dirPath ->
                            try {
                                val f = File(dirPath)
                                if (f.exists() && f.isDirectory) {
                                    deleteDirectoryRecursively(f)
                                }
                            } catch (e: Exception) {}
                        }
                    }

                    if (deletedPaths.isNotEmpty()) {
                        try {
                            android.media.MediaScannerConnection.scanFile(
                                this@MainActivity,
                                deletedPaths.toTypedArray(),
                                null
                            ) { _, _ -> }
                        } catch (_: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        com.developer.manali.galleryapp.data.MediaRepository.clearCache()
                        Toast.makeText(this@MainActivity, getString(R.string.permanently_deleted_items, deletedCount), Toast.LENGTH_SHORT).show()
                        exitSelectionMode()
                        refreshAllFragments()
                    }
                }
            }
        }

                dialog.show()
            }
        }
    }

    private fun moveSelectedMedia() {
        lifecycleScope.launch {
            val selected = getCurrentlySelectedItemsAsync()
            withContext(Dispatchers.Main) {
                if (selected.isEmpty()) {
                    Toast.makeText(this@MainActivity, getString(R.string.please_select_at_least_1_item_to_move), Toast.LENGTH_SHORT).show()
                    return@withContext
                }

        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this@MainActivity)
        val dialogView = layoutInflater.inflate(R.layout.dialog_move_to_album, null)
        bottomSheetDialog.setContentView(dialogView)

        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvMoveSubtitle)
        val rvMoveAlbums = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvMoveAlbums)
        val progress = dialogView.findViewById<View>(R.id.progressMoveAlbums)
        val layoutCreateNew = dialogView.findViewById<View>(R.id.layoutCreateNewAlbumMove)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelMoveDialog)

        tvSubtitle.text = getString(R.string.move_item_s_to_album,selected.size)
        rvMoveAlbums.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)

        progress.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val albums = mediaRepository.getAlbums(this@MainActivity)
            progress.visibility = View.GONE

            val adapter = com.developer.manali.galleryapp.ui.adapter.MoveAlbumAdapter(albums) { targetAlbum ->
                bottomSheetDialog.dismiss()
                executeMove(targetAlbum, targetAlbum.bucketName, selected)
            }
            rvMoveAlbums.adapter = adapter
        }

        layoutCreateNew.setOnClickListener {
            bottomSheetDialog.dismiss()
            showCreateAlbumAndMoveDialog(selected)
        }

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

            bottomSheetDialog.show()
        }
    }
}

    private fun showCreateAlbumAndMoveDialog(selected: List<MediaItem>) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_album, null)
        dialog.setContentView(dialogView)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etNewAlbumName)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelAlbum)
        val btnCreate = dialogView.findViewById<View>(R.id.btnCreateAlbum)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isNotEmpty()) {
                dialog.dismiss()
                executeMove(null, name, selected)
            } else {
                Toast.makeText(this, getString(R.string.please_enter_album_name), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun executeMove(targetAlbum: com.developer.manali.galleryapp.data.AlbumItem?, targetAlbumName: String, selected: List<MediaItem>) {
        if (selected.isEmpty()) return

        if (targetAlbum == null) {
            com.developer.manali.galleryapp.data.AppPreferences.getInstance(this).addCreatedAlbum(targetAlbumName)
        }

        if (binding.viewPagerMain.currentItem == 1) {
            currentPhotosFragment?.removeItems(selected)
        } else if (binding.viewPagerMain.currentItem == 2) {
            currentVideosFragment?.removeItems(selected)
        }

        exitSelectionMode()

        lifecycleScope.launch(Dispatchers.IO) {
            val (movedCount, pendingUris) = mediaRepository.moveMediaItems(this@MainActivity, selected, targetAlbum, targetAlbumName)

            withContext(Dispatchers.Main) {
                sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))
                
                if (pendingUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, pendingUris)
                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        moveDeleteLauncher.launch(intentSenderRequest)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                Toast.makeText(this@MainActivity, "Moved $movedCount item(s) to \"$targetAlbumName\"", Toast.LENGTH_SHORT).show()
                loadMediaStats()
                refreshAllFragments()
            }
        }
    }

    private fun showSelectionMoreMenu() {
        val options = arrayOf(getString(R.string.select_all), getString(R.string.deselect_all))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.selection_options))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (binding.viewPagerMain.currentItem == 1) {
                            currentPhotosFragment?.selectAll()
                        } else if (binding.viewPagerMain.currentItem == 2) {
                            currentVideosFragment?.selectAll()
                        }
                    }
                    1 -> {
                        if (binding.viewPagerMain.currentItem == 1) {
                            currentPhotosFragment?.deselectAll()
                        } else if (binding.viewPagerMain.currentItem == 2) {
                            currentVideosFragment?.deselectAll()
                        }
                    }
                }
            }
            .show()
    }

    private fun refreshAllFragments() {
        loadMediaStats()
        sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))
        currentAlbumsFragment?.refreshData()
        currentPhotosFragment?.refreshData()
        currentVideosFragment?.refreshData()
    }
}