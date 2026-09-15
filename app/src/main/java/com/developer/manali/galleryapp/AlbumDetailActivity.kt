package com.developer.manali.galleryapp

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.developer.manali.galleryapp.data.AppPreferences
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.databinding.ActivityAlbumDetailBinding
import com.developer.manali.galleryapp.ui.adapter.AlbumMediaAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AlbumDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityAlbumDetailBinding
    private val mediaRepository = MediaRepository()
    private lateinit var mediaAdapter: AlbumMediaAdapter

    var isSelectionMode: Boolean = false
        private set

    private var isEnteringSelectionMode = false

    private val gridColumnsReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.developer.manali.galleryapp.GRID_COLUMNS_CHANGED") {
                val span = intent.getIntExtra("span_count", -1)
                if (span in 2..7) {
                    updateGridColumns(span)
                }
            }
        }
    }

    private var bucketId: String = ""
    private var bucketName: String = "Album"
    private var currentSpanCount: Int = 3
    private var isFromVault = false

    private var pendingDirectoriesToCheck: List<String> = emptyList()

    private val deleteLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingDirectoriesToCheck.forEach { dirPath ->
                try {
                    val f = File(dirPath)
                    if (f.exists() && f.isDirectory && f.list()?.isEmpty() == true) {
                        f.delete()
                    }
                } catch (e: Exception) {}
            }
            pendingDirectoriesToCheck = emptyList()
            Toast.makeText(this, getString(R.string.items_permanently_deleted), Toast.LENGTH_SHORT).show()
            exitSelectionMode()
            com.developer.manali.galleryapp.data.MediaRepository.clearCache()
            loadAlbumMedia()
        }
    }

    private val passwordSetupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            lockSelectedMediaDirectly()
        }
    }

    private fun lockSelectedMediaDirectly() {
        val selected = mediaAdapter.getSelectedItems()
        if (selected.isNotEmpty()) {
            val appPrefs = AppPreferences.getInstance(this)
            val selectedIds = selected.map { it.id.toString() }
            appPrefs.setLockedMedia(selectedIds, true)
            Toast.makeText(this, getString(R.string.moved_to_vault), Toast.LENGTH_SHORT).show()
            exitSelectionMode()
            loadAlbumMedia()
        }
    }

    private val moveDeleteLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {

        }
        loadAlbumMedia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAlbumDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootAlbumDetail) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bucketId = intent.getStringExtra("bucket_id") ?: ""
        bucketName = intent.getStringExtra("bucket_name") ?: "Album"
        isFromVault = intent.getBooleanExtra("is_from_vault", false)

        binding.tvAlbumDetailTitle.text = bucketName

        currentSpanCount = if (AppPreferences.getInstance(this).isListView) 1 else AppPreferences.getInstance(this).photosGridColumns
        setupRecyclerView()
        setupListeners()
        loadAlbumMedia()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val prefs = AppPreferences.getInstance(this)
        applyViewType()
        if (!prefs.isListView && currentSpanCount != prefs.gridColumns) {
            updateGridColumns(prefs.gridColumns)
        }
        loadAlbumMedia()
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

    private fun setupRecyclerView() {
        val isList = AppPreferences.getInstance(this).isListView
        currentSpanCount = if (isList) 1 else AppPreferences.getInstance(this).gridColumns
//        binding.rvAlbumDetail.setHasFixedSize(true)
        binding.rvAlbumDetail.setItemViewCacheSize(25)
        binding.rvAlbumDetail.layoutManager = GridLayoutManager(this, currentSpanCount)
        mediaAdapter = AlbumMediaAdapter { mediaItem ->
            val clickAction = {
                if (mediaItem.isVideo) {
                    playVideo(mediaItem)
                } else {
                    openPhotoDetail(mediaItem)
                }
            }
            if (AdCounter.shouldShowAd()) {
                GoogleInterstitialAdsCall.loadAndShowInterstitial(
                    this,
                    object : InterstitialAdCallback {
                        override fun onAdClose() {
                            clickAction()
                        }
                    }
                )
            } else {
                clickAction()
            }
        }
        mediaAdapter.setListView(isList)

        mediaAdapter.setOnSelectionChangedListener { count, items ->
            onSelectionUpdated(count, items)
        }
        mediaAdapter.setOnItemLongClickListener { item ->
            if (!isSelectionMode) {
                enterSelectionMode(item)
            }
        }

        binding.rvAlbumDetail.adapter = mediaAdapter

        com.developer.manali.galleryapp.util.PinchZoomGridHelper(
            context = this,
            getSpanCount = { currentSpanCount },
            onSpanCountChanged = { newSpan ->
                updateGridColumns(newSpan)
            }
        ).attachToRecyclerView(binding.rvAlbumDetail)
    }

    private var lastAppliedSpan: Int = -1
    private var lastAppliedIsList: Boolean? = null

    fun applyViewType(viewType: String? = null) {
        val prefs = AppPreferences.getInstance(this)
        val effectiveType = viewType ?: prefs.viewType
        val isList = effectiveType == AppPreferences.VIEW_TYPE_LIST
        val span = if (isList) 1 else prefs.gridColumns
        currentSpanCount = span

        if (lastAppliedSpan == span && lastAppliedIsList == isList) {
            return
        }
        val savedState = binding.rvAlbumDetail.layoutManager?.onSaveInstanceState()
        lastAppliedSpan = span
        lastAppliedIsList = isList

        mediaAdapter.setListView(isList)
        binding.rvAlbumDetail.layoutManager = GridLayoutManager(this, span)
        if (savedState != null) {
            binding.rvAlbumDetail.layoutManager?.onRestoreInstanceState(savedState)
        }
    }

    fun updateGridColumns(newSpanCount: Int) {
        val span = newSpanCount.coerceIn(2, 7)
        if (currentSpanCount == span && lastAppliedIsList == false) return
        currentSpanCount = span
        val prefs = AppPreferences.getInstance(this)
        prefs.gridColumns = span
        prefs.photosGridColumns = span
        prefs.videosGridColumns = span
        prefs.viewType = AppPreferences.VIEW_TYPE_GRID
        prefs.photosViewType = AppPreferences.VIEW_TYPE_GRID
        prefs.videosViewType = AppPreferences.VIEW_TYPE_GRID

        val savedState = binding.rvAlbumDetail.layoutManager?.onSaveInstanceState()
        lastAppliedSpan = span
        lastAppliedIsList = false

        mediaAdapter.setListView(false)

        val transition = androidx.transition.TransitionSet().apply {
            ordering = androidx.transition.TransitionSet.ORDERING_TOGETHER
            addTransition(androidx.transition.ChangeBounds())
            duration = 250
        }
        androidx.transition.TransitionManager.beginDelayedTransition(binding.rvAlbumDetail, transition)

        val existingManager = binding.rvAlbumDetail.layoutManager as? GridLayoutManager
        if (existingManager != null) {
            existingManager.spanCount = span
        } else {
            binding.rvAlbumDetail.layoutManager = GridLayoutManager(this, span)
            if (savedState != null) {
                binding.rvAlbumDetail.layoutManager?.onRestoreInstanceState(savedState)
            }
        }
        mediaAdapter.notifyDataSetChanged()

        sendBroadcast(Intent("com.developer.manali.galleryapp.GRID_COLUMNS_CHANGED").apply {
            setPackage(packageName)
            putExtra("span_count", span)
        })
    }

    private fun setupListeners() {
        binding.btnBackAlbumDetail.setOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }

        binding.btnSelectAllAlbumDetail.setOnClickListener {
            toggleSelectAll()
        }

        binding.btnMenuAlbumDetail.setOnClickListener {
            showAlbumMenuDialog()
        }

        setupSelectionBottomBar()
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
        var selectedType = appPrefs.viewType

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
            appPrefs.viewType = type
            appPrefs.photosViewType = type
            appPrefs.videosViewType = type
            applyViewType(type)

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
        val currentSpan = appPrefs.gridColumns

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

            appPrefs.gridColumns = selectedCols
            appPrefs.photosGridColumns = selectedCols
            appPrefs.videosGridColumns = selectedCols
            appPrefs.viewType = AppPreferences.VIEW_TYPE_GRID

            applyViewType(AppPreferences.VIEW_TYPE_GRID)
            updateGridColumns(selectedCols)
            Toast.makeText(this, getString(R.string.columns_applied, selectedCols), Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private val albumMediaItems = ArrayList<MediaItem>()

    private fun loadAlbumMedia() {
        lifecycleScope.launch {
            binding.progressAlbumDetail.visibility = View.VISIBLE
            val rawMediaItems = mediaRepository.getMediaForAlbum(this@AlbumDetailActivity, bucketId, bucketName)
            val lockedMedia = AppPreferences.getInstance(this@AlbumDetailActivity).getLockedMediaIds()
            val mediaItems = rawMediaItems.filter { !lockedMedia.contains(it.id.toString()) }

            val appPrefs = AppPreferences.getInstance(this@AlbumDetailActivity)
            val sortedMediaItems = when (appPrefs.sortBy) {
                AppPreferences.SORT_NEWEST -> mediaItems.sortedByDescending { it.dateAdded }
                AppPreferences.SORT_OLDEST -> mediaItems.sortedBy { it.dateAdded }
                AppPreferences.SORT_NAME_ASC -> mediaItems.sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                )
                AppPreferences.SORT_NAME_DESC -> mediaItems.sortedWith(
                    compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                )
                else -> mediaItems.sortedByDescending { it.dateAdded }
            }

            albumMediaItems.clear()
            albumMediaItems.addAll(sortedMediaItems)

            binding.progressAlbumDetail.visibility = View.GONE
            binding.swipeRefreshAlbumDetail.isRefreshing = false

            val totalSize = sortedMediaItems.sumOf { it.size }
            val formattedCount = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(sortedMediaItems.size)
            val countStr = if (sortedMediaItems.size == 1) getString(R.string._1_item) else getString(R.string.items,formattedCount)
            val sizeStr = MediaRepository.formatFileSize(totalSize)
            binding.tvAlbumDetailCount.text = "$countStr • $sizeStr"

            if (sortedMediaItems.isEmpty()) {
                binding.layoutEmptyAlbumDetail.visibility = View.VISIBLE
                binding.rvAlbumDetail.visibility = View.GONE
            } else {
                binding.layoutEmptyAlbumDetail.visibility = View.GONE
                binding.rvAlbumDetail.visibility = View.VISIBLE
                mediaAdapter.submitList(sortedMediaItems)
            }
        }
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

        binding.actionSelectMoveToVault.setOnClickListener {
            val selected = mediaAdapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                val appPrefs = AppPreferences.getInstance(this)
                if (appPrefs.appLockPin.isEmpty()) {
                    val intent = Intent(this, LockscreenActivity::class.java)
                    passwordSetupLauncher.launch(intent)
                } else {
                    lockSelectedMediaDirectly()
                }
            } else {
                Toast.makeText(this, getString(R.string.please_select_at_least_1_item_to_move), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun enterSelectionMode(initialItem: MediaItem? = null) {
        isEnteringSelectionMode = true
        try {
            isSelectionMode = true

            binding.btnBackAlbumDetail.setImageResource(R.drawable.ic_close)
            binding.btnMenuAlbumDetail.visibility = View.GONE
            binding.btnSelectAllAlbumDetail.visibility = View.VISIBLE

            binding.selectionBottomBarContainer.visibility = View.VISIBLE

            mediaAdapter.enterSelectionMode(initialItem)
            val initialList = if (initialItem != null) listOf(initialItem) else emptyList()
            updateSelectionHeader(initialList.size, initialList)
        } finally {
            isEnteringSelectionMode = false
        }
    }

    fun exitSelectionMode() {
        if (!isSelectionMode) return
        isSelectionMode = false

        binding.btnBackAlbumDetail.setImageResource(R.drawable.arrowback)
        binding.btnSelectAllAlbumDetail.visibility = View.GONE
        binding.btnMenuAlbumDetail.visibility = View.VISIBLE
        binding.tvAlbumDetailTitle.text = bucketName

        binding.selectionBottomBarContainer.visibility = View.GONE

        mediaAdapter.exitSelectionMode()
        loadAlbumMedia()
    }

    private fun onSelectionUpdated(count: Int, items: List<MediaItem>) {
        if (!isSelectionMode) return
        
        if (count == 0 && !isEnteringSelectionMode) {
            exitSelectionMode()
            return
        }
        
        updateSelectionHeader(count, items)
    }

    private fun updateSelectionHeader(count: Int, items: List<MediaItem>) {
        val titleText = if (count == 0) getString(R.string._0_selected) else getString(R.string.selected,count)
        binding.tvAlbumDetailTitle.text = titleText

        if (count > 0) {
            val totalBytes = items.sumOf { it.size }
            val sizeStr = MediaRepository.formatFileSize(totalBytes)
            binding.tvAlbumDetailCount.text = getString(R.string.selected,sizeStr)

            val appPrefs = AppPreferences.getInstance(this)
            val allFav = items.all { appPrefs.isFavorite(it.id) }
            if (allFav) {
                binding.ivSelectFavorite.setImageResource(R.drawable.ic_heart_filled)
                binding.ivSelectFavorite.imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.lumina_primary)
                )
                binding.tvSelectFavorite.text = getString(R.string.unfavorite)
            } else {
                binding.ivSelectFavorite.setImageResource(R.drawable.favorite)
                binding.ivSelectFavorite.imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.lumina_text_title)
                )
                binding.tvSelectFavorite.text = getString(R.string.favorite)
            }
        } else {
            binding.tvAlbumDetailCount.text = getString(R.string.tap_items_to_select)
            binding.ivSelectFavorite.setImageResource(R.drawable.favorite)
            binding.ivSelectFavorite.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.lumina_text_title)
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
        val selected = mediaAdapter.getSelectedItems()
        if (selected.isEmpty()) {
            Toast.makeText(this,
                getString(R.string.please_select_at_least_1_item), Toast.LENGTH_SHORT).show()
            return
        }

        val appPrefs = AppPreferences.getInstance(this)
        val selectedIds = selected.map { it.id }
        val allAreFavorites = selectedIds.all { appPrefs.isFavorite(it) }

        if (allAreFavorites) {
            appPrefs.setFavorites(selectedIds, false)
            Toast.makeText(this,
                getString(R.string.removed_item_s_from_favorites, selected.size), Toast.LENGTH_SHORT).show()
        } else {
            appPrefs.setFavorites(selectedIds, true)
            Toast.makeText(this,
                getString(R.string.added_item_s_to_favorites, selected.size), Toast.LENGTH_SHORT).show()
        }

        exitSelectionMode()
    }

    private fun toggleSelectAll() {
        val currentSelected = mediaAdapter.getSelectedItems()
        val allItems = mediaAdapter.getAllMediaItems()
        if (currentSelected.size == allItems.size && allItems.isNotEmpty()) {
            mediaAdapter.deselectAll()
        } else {
            mediaAdapter.selectAll()
        }
    }

    private fun shareSelectedMedia() {
        val selected = mediaAdapter.getSelectedItems()
        if (selected.isEmpty()) {
            Toast.makeText(this,
                getString(R.string.please_select_at_least_1_item_to_share), Toast.LENGTH_SHORT).show()
            return
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
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.unable_to_share_media), Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSelectedMedia() {
        val selected = mediaAdapter.getSelectedItems()
        if (selected.isEmpty()) {
            Toast.makeText(this,
                getString(R.string.please_select_at_least_1_item_to_delete), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        val dialog = AlertDialog.Builder(this)
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

        val isSingle = selected.size == 1
        tvTitle.text = if (isSingle) {
            if (selected[0].isVideo) getString(R.string.delete_video) else getString(R.string.delete_photo)
        } else {
            getString(R.string.delete_items, selected.size)
        }
        tvSubtitle.text =
            getString(R.string.selected_item_s_will_be_permanently_deleted_from_storage)

        if (isSingle) {
            val item = selected[0]
            ivIcon.setImageResource(if (item.isVideo) R.drawable.video else R.drawable.photo)
            tvName.text = item.displayName
            val sizeStr = MediaRepository.formatFileSize(item.size)
            val durStr = if (item.isVideo) MediaRepository.formatDuration(item.duration) else ""
            tvDetails.text = if (durStr.isNotEmpty() && durStr != "0:00") "$sizeStr • $durStr" else sizeStr
        } else {
            ivIcon.setImageResource(R.drawable.delete)
            tvName.text = getString(R.string.items_selected, selected.size)
            val totalSize = selected.sumOf { it.size }
            tvDetails.text = MediaRepository.formatFileSize(totalSize)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val uris = selected.map { it.uri }
                    pendingDirectoriesToCheck = selected.mapNotNull { 
                        try { File(it.path).parent } catch(e: Exception) { null } 
                    }.distinct()
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(contentResolver, uris)
                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    deleteLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.failed_to_initiate_deletion), Toast.LENGTH_SHORT).show()
                }
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    var deletedCount = 0
                    val deletedPaths = mutableListOf<String>()

                    for (item in selected) {
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

                    if (deletedPaths.isNotEmpty()) {
                        try {
                            android.media.MediaScannerConnection.scanFile(
                                this@AlbumDetailActivity,
                                deletedPaths.toTypedArray(),
                                null
                            ) { _, _ -> }
                        } catch (_: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        com.developer.manali.galleryapp.data.MediaRepository.clearCache()
                        Toast.makeText(this@AlbumDetailActivity,
                            getString(R.string.permanently_deleted_items, deletedCount), Toast.LENGTH_SHORT).show()
                        exitSelectionMode()
                        loadAlbumMedia()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun moveSelectedMedia() {
        val selected = mediaAdapter.getSelectedItems()
        if (selected.isEmpty()) {
            Toast.makeText(this,
                getString(R.string.please_select_at_least_1_item_to_move), Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_move_to_album, null)
        bottomSheetDialog.setContentView(dialogView)

        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvMoveSubtitle)
        val rvMoveAlbums = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvMoveAlbums)
        val progress = dialogView.findViewById<View>(R.id.progressMoveAlbums)
        val layoutCreateNew = dialogView.findViewById<View>(R.id.layoutCreateNewAlbumMove)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelMoveDialog)

        tvSubtitle.text = getString(R.string.move_item_s_to_album, selected.size)
        rvMoveAlbums.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val albums = mediaRepository.getAlbums(this@AlbumDetailActivity)
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
                if (com.developer.manali.galleryapp.data.MediaRepository.albumExists(this, name)) {
                    Toast.makeText(this, getString(R.string.album_already_exists), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                executeMove(null, name, selected)
            } else {
                Toast.makeText(this, getString(R.string.please_enter_album_name), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showAlbumMenuDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_right_menu, null)
        dialog.setContentView(dialogView)

        val layoutSelect = dialogView.findViewById<View>(R.id.layoutMenuSelect)
        val layoutColumns = dialogView.findViewById<View>(R.id.layoutMenuColumns)
        val layoutViewType = dialogView.findViewById<View>(R.id.layoutMenuViewType)
        val layoutSortBy = dialogView.findViewById<View>(R.id.layoutMenuSortBy)

        val isListView = AppPreferences.getInstance(this).isListView
        if (isListView) {
            layoutColumns.visibility = View.GONE
        } else {
            layoutColumns.visibility = View.VISIBLE
        }

        layoutSelect.setOnClickListener {
            dialog.dismiss()
            enterSelectionMode()
        }

        layoutColumns.setOnClickListener {
            dialog.dismiss()
            showColumnsDialog()
        }

        layoutViewType.setOnClickListener {
            dialog.dismiss()
            showViewTypeDialog()
        }

        layoutSortBy.setOnClickListener {
            dialog.dismiss()
            showSortByDialog()
        }

        dialog.show()

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            val density = resources.displayMetrics.density
            val widthPx = (210 * density).toInt()
            val marginX = (16 * density).toInt()
            val marginY = (56 * density).toInt()

            val params = window.attributes
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.END
            params.x = marginX
            params.y = marginY
            params.width = widthPx
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = params
            window.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.decorView.setPadding(0, 0, 0, 0)
        }
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
        var selectedSort = appPrefs.sortBy

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

        rbNewest.setOnClickListener { updateSelection(AppPreferences.SORT_NEWEST) }
        rbOldest.setOnClickListener { updateSelection(AppPreferences.SORT_OLDEST) }
        rbNameAsc.setOnClickListener { updateSelection(AppPreferences.SORT_NAME_ASC) }
        rbNameDesc.setOnClickListener { updateSelection(AppPreferences.SORT_NAME_DESC) }

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnDone.setOnClickListener {
            appPrefs.sortBy = selectedSort
            loadAlbumMedia()
            val sortLabel = when (selectedSort) {
                AppPreferences.SORT_NEWEST -> getString(R.string.newest_on_top)
                AppPreferences.SORT_OLDEST -> getString(R.string.oldest_on_top)
                AppPreferences.SORT_NAME_ASC -> getString(R.string.name_a_z)
                AppPreferences.SORT_NAME_DESC -> getString(R.string.name_z_a)
                else -> getString(R.string.sorted)
            }
            Toast.makeText(this, getString(R.string.applied, sortLabel), Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun executeMove(targetAlbum: com.developer.manali.galleryapp.data.AlbumItem?, targetAlbumName: String, selected: List<MediaItem>) {
        if (selected.isEmpty()) return

        if (targetAlbum == null) {
            com.developer.manali.galleryapp.data.AppPreferences.getInstance(this).addCreatedAlbum(targetAlbumName)
        }

        val movedIds = selected.map { it.id }.toSet()

        albumMediaItems.removeAll { movedIds.contains(it.id) }
        mediaAdapter.submitList(ArrayList(albumMediaItems))

        val remainingSize = albumMediaItems.sumOf { it.size }
        val formattedCount = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(albumMediaItems.size)
        val countStr = if (albumMediaItems.size == 1) getString(R.string._1_item)else getString(
            R.string.items,
            formattedCount
        )
        val sizeStr = MediaRepository.formatFileSize(remainingSize)
        binding.tvAlbumDetailCount.text = "$countStr • $sizeStr"

        if (albumMediaItems.isEmpty()) {
            binding.layoutEmptyAlbumDetail.visibility = View.VISIBLE
            binding.rvAlbumDetail.visibility = View.GONE
        }

        exitSelectionMode()

        lifecycleScope.launch(Dispatchers.IO) {
            val (movedCount, pendingUris) = mediaRepository.moveMediaItems(this@AlbumDetailActivity, selected, targetAlbum, targetAlbumName)

            withContext(Dispatchers.Main) {
                sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))

                if (pendingUris.isNotEmpty() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(contentResolver, pendingUris)
                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        moveDeleteLauncher.launch(intentSenderRequest)
                    } catch (e: Exception) {

                    }
                }
                Toast.makeText(this@AlbumDetailActivity,
                    getString(R.string.moved_item_s_to, movedCount, targetAlbumName), Toast.LENGTH_SHORT).show()
                loadAlbumMedia()
            }
        }
    }


    private fun openPhotoDetail(mediaItem: MediaItem) {
        val position = albumMediaItems.indexOfFirst { it.id == mediaItem.id }.coerceAtLeast(0)
        com.developer.manali.galleryapp.data.MediaDataHolder.mediaList = albumMediaItems
        val intent = Intent(this, MediaDetailActivity::class.java).apply {
            putExtra("current_position", position)
            putExtra("media_item", mediaItem)
            putExtra("media_uri", mediaItem.uri.toString())
            putExtra("media_name", mediaItem.displayName)
            putExtra("is_video", mediaItem.isVideo)
            putExtra("is_from_vault", isFromVault)
        }
        startActivity(intent)
    }

    private fun playVideo(video: MediaItem) {
        val intent = Intent(this, VideoDetailActivity::class.java).apply {
            putExtra("video_item", video)
            putExtra("is_from_vault", isFromVault)
        }
        startActivity(intent)
    }
}
