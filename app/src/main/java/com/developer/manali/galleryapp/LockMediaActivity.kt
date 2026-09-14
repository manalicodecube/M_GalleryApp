package com.developer.manali.galleryapp

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.developer.manali.galleryapp.data.AppPreferences
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.databinding.ActivityLockMediaBinding
import com.developer.manali.galleryapp.ui.adapter.AlbumAdapter
import com.developer.manali.galleryapp.ui.adapter.PhotoGridAdapter
import com.developer.manali.galleryapp.data.PhotoListItem
import kotlinx.coroutines.launch

class LockMediaActivity : BaseActivity() {

    private lateinit var binding: ActivityLockMediaBinding
    private val mediaRepository = MediaRepository()
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var mediaAdapter: PhotoGridAdapter

    private var isSelectionMode: Boolean = false
    private var isEnteringSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLockMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLockMedia) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerViews()
        setupListeners()
        loadLockedData()

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
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
        val isList = prefs.isListView
        val span = if (isList) 1 else prefs.gridColumns
        if (isList != lastAppliedIsList || currentSpanCount != span) {
            currentSpanCount = span
            lastAppliedIsList = isList
            binding.rvLockedAlbums.layoutManager = GridLayoutManager(this, span)
            binding.rvLockedMedia.layoutManager = GridLayoutManager(this, span)
            albumAdapter.setListView(isList)
            mediaAdapter.setListView(isList)
            albumAdapter.notifyDataSetChanged()
            mediaAdapter.notifyDataSetChanged()
        }
        loadLockedData()
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

    private var currentSpanCount: Int = 3
    private var lastAppliedIsList: Boolean? = null

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

    fun updateGridColumns(newSpanCount: Int) {
        val span = newSpanCount.coerceIn(2, 7)
        if (currentSpanCount == span && lastAppliedIsList == false) return
        currentSpanCount = span
        val prefs = AppPreferences.getInstance(this)
        prefs.gridColumns = span
        prefs.photosGridColumns = span
        prefs.videosGridColumns = span
        prefs.viewType = AppPreferences.VIEW_TYPE_GRID
        prefs.albumsViewType = AppPreferences.VIEW_TYPE_GRID
        prefs.photosViewType = AppPreferences.VIEW_TYPE_GRID
        prefs.videosViewType = AppPreferences.VIEW_TYPE_GRID
        lastAppliedIsList = false

        val transition = androidx.transition.TransitionSet().apply {
            ordering = androidx.transition.TransitionSet.ORDERING_TOGETHER
            addTransition(androidx.transition.ChangeBounds())
            duration = 250
        }
        androidx.transition.TransitionManager.beginDelayedTransition(binding.scrollViewLockMedia, transition)

        val existingAlbumsManager = binding.rvLockedAlbums.layoutManager as? GridLayoutManager
        if (existingAlbumsManager != null) {
            existingAlbumsManager.spanCount = span
        } else {
            binding.rvLockedAlbums.layoutManager = GridLayoutManager(this, span)
        }

        val existingMediaManager = binding.rvLockedMedia.layoutManager as? GridLayoutManager
        if (existingMediaManager != null) {
            existingMediaManager.spanCount = span
        } else {
            binding.rvLockedMedia.layoutManager = GridLayoutManager(this, span)
        }

        albumAdapter.setListView(false)
        mediaAdapter.setListView(false)
        albumAdapter.notifyDataSetChanged()
        mediaAdapter.notifyDataSetChanged()

        sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.GRID_COLUMNS_CHANGED").apply {
            setPackage(packageName)
            putExtra("span_count", span)
        })
    }

    private val lockedMediaItems = ArrayList<MediaItem>()

    private val deleteLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val selectedMediaIds = mediaAdapter.getSelectedItems().map { it.id.toString() }
            Toast.makeText(this, getString(R.string.permanently_deleted_items, selectedMediaIds.size), Toast.LENGTH_SHORT).show()
            if (selectedMediaIds.isNotEmpty()) {
                val appPrefs = AppPreferences.getInstance(this)
                val set = appPrefs.getLockedMediaIds()
                set.removeAll(selectedMediaIds.toSet())
                appPrefs.setLockedMedia(selectedMediaIds, false)
            }
            exitSelectionMode()
            loadLockedData()
        }
    }

    private fun setupRecyclerViews() {
        val prefs = AppPreferences.getInstance(this)
        val isList = prefs.isListView
        currentSpanCount = if (isList) 1 else prefs.gridColumns
        lastAppliedIsList = isList

        binding.rvLockedAlbums.layoutManager =
            GridLayoutManager(this, currentSpanCount)
        albumAdapter = AlbumAdapter(
            onAlbumClick = { album ->
                if (!isSelectionMode) {
                    val intent = android.content.Intent(this, AlbumDetailActivity::class.java).apply {
                        putExtra("bucket_id", album.bucketId)
                        putExtra("bucket_name", album.bucketName)
                        putExtra("item_count", album.itemCount)
                    }
                    startActivity(intent)
                } else {
                    albumAdapter.toggleSelection(album)
                    onSelectionUpdated()
                }
            },
            onAlbumLongClick = { item ->
                if (!isSelectionMode) {
                    enterSelectionMode(initialAlbum = item)
                    updateSelectionHeader()
                } else {
                    onSelectionUpdated()
                }
            }
        )
        albumAdapter.setListView(isList)
        binding.rvLockedAlbums.setHasFixedSize(true)
        binding.rvLockedAlbums.setItemViewCacheSize(25)
        binding.rvLockedAlbums.adapter = albumAdapter

        binding.rvLockedMedia.setHasFixedSize(true)
        binding.rvLockedMedia.setItemViewCacheSize(25)
        binding.rvLockedMedia.layoutManager =
            GridLayoutManager(this, currentSpanCount)
        mediaAdapter = PhotoGridAdapter(
            onItemClick = { mediaItem ->
                if (!isSelectionMode) {
                    if (mediaItem.isVideo) {
                        val intent = android.content.Intent(this, VideoDetailActivity::class.java).apply {
                            putExtra("video_item", mediaItem)
                        }
                        startActivity(intent)
                    } else {
                        val position = lockedMediaItems.indexOfFirst { it.id == mediaItem.id }.coerceAtLeast(0)
                        com.developer.manali.galleryapp.data.MediaDataHolder.mediaList = lockedMediaItems
                        val intent = android.content.Intent(this, MediaDetailActivity::class.java).apply {
                            putExtra("current_position", position)
                            putExtra("media_item", mediaItem)
                            putExtra("media_uri", mediaItem.uri.toString())
                            putExtra("media_name", mediaItem.displayName)
                        }
                        startActivity(intent)
                    }
                }
            },
            onSelectClick = { }
        )
        mediaAdapter.setListView(isList)
        mediaAdapter.setOnItemLongClickListener { item ->
            if (!isSelectionMode) {
                enterSelectionMode(initialMedia = item)
                updateSelectionHeader()
            }
        }
        mediaAdapter.setOnSelectionChangedListener { _, _ -> onSelectionUpdated() }
        binding.rvLockedMedia.adapter = mediaAdapter

        com.developer.manali.galleryapp.util.PinchZoomGridHelper(
            context = this,
            getSpanCount = { currentSpanCount },
            onSpanCountChanged = { newSpan ->
                updateGridColumns(newSpan)
            }
        ).attachToRecyclerView(binding.rvLockedMedia)

        com.developer.manali.galleryapp.util.PinchZoomGridHelper(
            context = this,
            getSpanCount = { currentSpanCount },
            onSpanCountChanged = { newSpan ->
                updateGridColumns(newSpan)
            }
        ).attachToRecyclerView(binding.rvLockedAlbums)
    }

    private fun setupListeners() {
        binding.btnBackLockMedia.setOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }

        binding.btnMenuLockMedia.setOnClickListener {
            showRightMenuDialog()
        }

        binding.btnSelectAllLockMedia.setOnClickListener {
            val totalAlbums = albumAdapter.getAllAlbums().size
            val totalMedia = mediaAdapter.getAllMediaItems().size
            val selectedAlbums = albumAdapter.getSelectedItems().size
            val selectedMedia = mediaAdapter.getSelectedItems().size

            if (selectedAlbums == totalAlbums && selectedMedia == totalMedia) {
                albumAdapter.deselectAll()
                mediaAdapter.deselectAll()
            } else {
                albumAdapter.selectAll()
                mediaAdapter.selectAll()
            }
            updateSelectionHeader()
        }

        binding.actionSelectUnlock.setOnClickListener {
            val selectedAlbums = albumAdapter.getSelectedItems()
            val selectedMedia = mediaAdapter.getSelectedItems()

            if (selectedAlbums.isEmpty() && selectedMedia.isEmpty()) {
                Toast.makeText(
                    this,
                    getString(R.string.please_select_at_least_1_item_to_unlock), Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val appPrefs = AppPreferences.getInstance(this)

            if (selectedAlbums.isNotEmpty()) {
                val albumIds = selectedAlbums.map { it.bucketId }
                appPrefs.setLockedAlbums(albumIds, false)
            }
            if (selectedMedia.isNotEmpty()) {
                val mediaIds = selectedMedia.map { it.id.toString() }
                appPrefs.setLockedMedia(mediaIds, false)
            }

            Toast.makeText(
                this,
                getString(R.string.items_unlocked_successfully),
                Toast.LENGTH_SHORT
            ).show()
            exitSelectionMode()
            loadLockedData()
        }

        binding.actionSelectShare.setOnClickListener {
            shareSelectedMedia()
        }

        binding.actionSelectDelete.setOnClickListener {
            deleteSelectedMedia()
        }
    }

    private fun loadLockedData() {
        lifecycleScope.launch {
            binding.progressLockMedia.visibility = View.GONE

            val appPrefs = AppPreferences.getInstance(this@LockMediaActivity)
            val lockedAlbumIds = appPrefs.getLockedAlbumIds()
            val lockedMediaIds = appPrefs.getLockedMediaIds()

            val rawAlbums = mediaRepository.getAlbums(this@LockMediaActivity, false)
            val filteredAlbums = rawAlbums.filter { lockedAlbumIds.contains(it.bucketId) }

            val rawPhotos = mediaRepository.getPhotos(this@LockMediaActivity)
            val rawVideos = mediaRepository.getVideos(this@LockMediaActivity)

            val allMedia = mutableListOf<MediaItem>()
            allMedia.addAll(rawPhotos)
            allMedia.addAll(rawVideos)

            val filteredMedia = allMedia.filter { lockedMediaIds.contains(it.id.toString()) }
                .sortedByDescending { it.dateAdded }

            lockedMediaItems.clear()
            lockedMediaItems.addAll(filteredMedia)

            binding.progressLockMedia.visibility = View.GONE

            val hasAlbums = filteredAlbums.isNotEmpty()
            val hasMedia = filteredMedia.isNotEmpty()

            if (!hasAlbums && !hasMedia) {
                binding.layoutEmptyVault.visibility = View.VISIBLE
                binding.tvAlbumsHeader.visibility = View.GONE
                binding.rvLockedAlbums.visibility = View.GONE
                binding.tvMediaHeader.visibility = View.GONE
                binding.rvLockedMedia.visibility = View.GONE
            } else {
                binding.layoutEmptyVault.visibility = View.GONE

                if (hasAlbums) {
                    binding.tvAlbumsHeader.visibility = View.VISIBLE
                    binding.rvLockedAlbums.visibility = View.VISIBLE
                    albumAdapter.submitList(filteredAlbums)
                } else {
                    binding.tvAlbumsHeader.visibility = View.GONE
                    binding.rvLockedAlbums.visibility = View.GONE
                }

                if (hasMedia) {
                    binding.tvMediaHeader.visibility = View.VISIBLE
                    binding.rvLockedMedia.visibility = View.VISIBLE
                    val mediaListItems = filteredMedia.map { PhotoListItem.Media(it) }
                    mediaAdapter.submitList(mediaListItems)
                } else {
                    binding.tvMediaHeader.visibility = View.GONE
                    binding.rvLockedMedia.visibility = View.GONE
                }
            }
        }
    }
    private fun enterSelectionMode(initialAlbum: com.developer.manali.galleryapp.data.AlbumItem? = null, initialMedia: com.developer.manali.galleryapp.data.MediaItem? = null) {
        isEnteringSelectionMode = true
        try {
            isSelectionMode = true
            binding.btnBackLockMedia.setImageResource(R.drawable.ic_close)
            binding.btnSelectAllLockMedia.visibility = View.VISIBLE
            binding.btnMenuLockMedia.visibility = View.GONE
            binding.selectionBottomBarLockMedia.visibility = View.VISIBLE

            albumAdapter.enterSelectionMode(initialAlbum)
            mediaAdapter.enterSelectionMode(initialMedia)
        } finally {
            isEnteringSelectionMode = false
        }
    }

    private fun exitSelectionMode() {
        if (!isSelectionMode) return
        isSelectionMode = false

        binding.btnBackLockMedia.setImageResource(R.drawable.arrowback)
        binding.btnSelectAllLockMedia.visibility = View.GONE
        binding.btnMenuLockMedia.visibility = View.VISIBLE
        binding.tvLockMediaTitle.text = getString(R.string.vault)
        binding.selectionBottomBarLockMedia.visibility = View.GONE

        albumAdapter.exitSelectionMode()
        mediaAdapter.exitSelectionMode()
    }

    private fun updateSelectionHeader() {
        if (!isSelectionMode) return
        val count = albumAdapter.getSelectedItems().size + mediaAdapter.getSelectedItems().size

        if (count == 0 && (albumAdapter.getAllAlbums().isNotEmpty() || mediaAdapter.getAllMediaItems().isNotEmpty())) {

        }

        val titleText = if (count == 0) getString(R.string._0_selected) else getString(R.string.selected, count)
        binding.tvLockMediaTitle.text = titleText
        binding.btnBackLockMedia.setImageResource(R.drawable.ic_close)
        binding.btnSelectAllLockMedia.visibility = View.VISIBLE

        val alpha = if (count > 0) 1.0f else 0.4f
        binding.actionSelectUnlock.alpha = alpha
        binding.actionSelectUnlock.isEnabled = count > 0
    }

    fun onSelectionUpdated() {
        if (!isSelectionMode) return
        val count = albumAdapter.getSelectedItems().size + mediaAdapter.getSelectedItems().size
        if (count == 0 && !isEnteringSelectionMode) {
            exitSelectionMode()
            return
        }
        updateSelectionHeader()
    }

    private fun showRightMenuDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val dialogView = layoutInflater.inflate(R.layout.dialog_right_menu, null)
        dialog.setContentView(dialogView)

        val layoutSelect = dialogView.findViewById<View>(R.id.layoutMenuSelect)
        val layoutColumns = dialogView.findViewById<View>(R.id.layoutMenuColumns)
        val layoutViewType = dialogView.findViewById<View>(R.id.layoutMenuViewType)
        val layoutSortBy = dialogView.findViewById<View>(R.id.layoutMenuSortBy)

        layoutSortBy.visibility = View.GONE

        layoutSelect.setOnClickListener {
            dialog.dismiss()
            enterSelectionMode()
            updateSelectionHeader()
        }

        layoutColumns.setOnClickListener {
            dialog.dismiss()
            showColumnsDialog()
        }

        layoutViewType.setOnClickListener {
            dialog.dismiss()
            showViewTypeDialog()
        }

        dialog.show()

        dialog.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
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

            updateGridColumns(selectedCols)

            Toast.makeText(this, getString(R.string.applied, "$selectedCols Columns"), Toast.LENGTH_SHORT).show()
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
        var selectedType = appPrefs.isListView

        val rgViewType = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgViewType)

        if (selectedType) {
            rbList.isChecked = true
            rbGrid.isChecked = false
        } else {
            rbGrid.isChecked = true
            rbList.isChecked = false
        }

        fun applyType(isList: Boolean) {
            selectedType = isList
            val typeStr = if (isList) AppPreferences.VIEW_TYPE_LIST else AppPreferences.VIEW_TYPE_GRID
            appPrefs.viewType = typeStr
            appPrefs.photosViewType = typeStr
            appPrefs.videosViewType = typeStr
            appPrefs.albumsViewType = typeStr

            val span = if (isList) 1 else appPrefs.gridColumns
            currentSpanCount = span
            lastAppliedIsList = isList
            binding.rvLockedAlbums.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, span)
            binding.rvLockedMedia.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, span)

            albumAdapter.setListView(isList)
            mediaAdapter.setListView(isList)
            albumAdapter.notifyDataSetChanged()
            mediaAdapter.notifyDataSetChanged()

            if (!isList) {
                sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.GRID_COLUMNS_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("span_count", span)
                })
            }

            val displayLabel = if (isList) getString(R.string.list_view) else getString(R.string.grid_view)
            Toast.makeText(this, getString(R.string.applied, displayLabel), Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        layoutOptionGrid.setOnClickListener {
            rbGrid.isChecked = true
            rbList.isChecked = false
            applyType(false)
        }

        layoutOptionList.setOnClickListener {
            rbList.isChecked = true
            rbGrid.isChecked = false
            applyType(true)
        }

        rbGrid.setOnClickListener {
            rbGrid.isChecked = true
            rbList.isChecked = false
            applyType(false)
        }

        rbList.setOnClickListener {
            rbList.isChecked = true
            rbGrid.isChecked = false
            applyType(true)
        }

        bottomSheetDialog.show()
    }

    private fun shareSelectedMedia() {
        val selected = mediaAdapter.getSelectedItems()
        if (selected.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_select_at_least_1_item_to_share), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (selected.size == 1) {
                val item = selected[0]
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = item.mimeType.ifEmpty { if (item.isVideo) "video/*" else "image/*" }
                    putExtra(android.content.Intent.EXTRA_STREAM, item.uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(intent, getString(R.string.share_media)))
                exitSelectionMode()
            } else {
                val uris = ArrayList<android.net.Uri>()
                selected.forEach { uris.add(it.uri) }
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(intent, getString(R.string.share_items, selected.size)))
                exitSelectionMode()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.unable_to_share_selected_media), Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSelectedMedia() {
        val selectedMedia = mediaAdapter.getSelectedItems()

        if (selectedMedia.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_select_at_least_1_item_to_delete), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteDialogTitle)
        val tvSubtitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteDialogSubtitle)
        val tvName = dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteVideoName)
        val tvDetails = dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteVideoDetails)
        val ivIcon = dialogView.findViewById<android.widget.ImageView>(R.id.ivDeleteMediaIcon)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmDelete)

        val isSingle = selectedMedia.size == 1

        tvTitle?.text = if (isSingle) {
            if (selectedMedia[0].isVideo) getString(R.string.delete_video) else getString(R.string.delete_photo)
        } else {
            getString(R.string.delete_items, selectedMedia.size)
        }

        tvSubtitle?.text = getString(R.string.selected_item_s_will_be_permanently_deleted_from_storage)

        if (isSingle) {
            val item = selectedMedia[0]
            ivIcon?.setImageResource(if (item.isVideo) R.drawable.video else R.drawable.photo)
            tvName?.text = item.displayName
            val sizeStr = com.developer.manali.galleryapp.data.MediaRepository.formatFileSize(item.size)
            val durStr = if (item.isVideo) com.developer.manali.galleryapp.data.MediaRepository.formatDuration(item.duration) else ""
            tvDetails?.text = if (durStr.isNotEmpty() && durStr != "0:00") "$sizeStr • $durStr" else sizeStr
        } else {
            ivIcon?.setImageResource(R.drawable.delete)
            tvName?.text = getString(R.string.items_selected, selectedMedia.size)
            val totalSize = selectedMedia.sumOf { it.size }
            tvDetails?.text = com.developer.manali.galleryapp.data.MediaRepository.formatFileSize(totalSize)
        }

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm?.setOnClickListener {
            dialog.dismiss()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val uris = selectedMedia.map { it.uri }
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(contentResolver, uris)
                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    deleteLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.failed_to_initiate_deletion), Toast.LENGTH_SHORT).show()
                }
            } else {
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    var deletedCount = 0
                    for (item in selectedMedia) {
                        try {
                            val rows = contentResolver.delete(item.uri, null, null)
                            if (rows > 0) {
                                deletedCount++
                            }
                        } catch (e: Exception) { }
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@LockMediaActivity, getString(R.string.permanently_deleted_items, deletedCount), Toast.LENGTH_SHORT).show()
                        val selectedMediaIds = selectedMedia.map { it.id.toString() }
                        val appPrefs = AppPreferences.getInstance(this@LockMediaActivity)
                        val set = appPrefs.getLockedMediaIds()
                        set.removeAll(selectedMediaIds.toSet())
                        appPrefs.setLockedMedia(selectedMediaIds, false)
                        exitSelectionMode()
                        loadLockedData()
                    }
                }
            }
        }
        dialog.show()
    }
}
