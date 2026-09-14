package com.developer.manali.galleryapp

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.developer.manali.galleryapp.data.AlbumItem
import com.developer.manali.galleryapp.data.AppPreferences
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.databinding.ActivityMediaDetailBinding

import com.developer.manali.galleryapp.ui.adapter.MediaPagerAdapter
import com.developer.manali.galleryapp.ui.adapter.MoveAlbumAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityMediaDetailBinding
    private lateinit var mediaPagerAdapter: MediaPagerAdapter
    private var bannerAdView: AdView? = null

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onMediaDeletedSuccess()
        }
    }

    private var pendingRenameItem: MediaItem? = null
    private var pendingRenameName: String? = null

    private val renameLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val item = pendingRenameItem
            val name = pendingRenameName
            if (item != null && name != null) {
                executeRename(item, name)
            }
        } else {
            Toast.makeText(this, getString(R.string.permission_required_to_rename), Toast.LENGTH_SHORT).show()
        }
        pendingRenameItem = null
        pendingRenameName = null
    }

    private val passwordSetupLauncherForVault = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            moveToVaultDirectly()
        }
    }

    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        currentMediaItem?.let { item ->
            if (item.path.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(item.path),
                    null
                ) { _, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        val pos = binding.viewPagerMediaDetail.currentItem
                        if (pos in 0 until mediaList.size) {
                            mediaPagerAdapter.notifyItemChanged(pos)
                        }
                    }
                }
            }
        }
    }
    private val startEditorForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data: Intent? = result.data
            val editedImageUri: Uri? = data?.data
            val originalPath = currentMediaItem?.path

            val pathsToScan = mutableListOf<String>()
            if (!originalPath.isNullOrEmpty()) {
                pathsToScan.add(originalPath)
                try {
                    File(originalPath).parentFile?.let { parent ->
                        parent.listFiles()?.forEach { file ->
                            if (file.isFile && (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true) || file.extension.equals("png", true) || file.extension.equals("webp", true))) {
                                pathsToScan.add(file.absolutePath)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (editedImageUri != null) {
                try {
                    val p = getRealFilePathFromUri(editedImageUri)
                    if (!p.isNullOrEmpty()) pathsToScan.add(p)
                } catch (_: Exception) {}
            }

            try {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                picturesDir.listFiles()?.forEach { f ->
                    if (f.isFile && (f.extension.equals("jpg", true) || f.extension.equals("jpeg", true) || f.extension.equals("png", true) || f.extension.equals("webp", true))) {
                        pathsToScan.add(f.absolutePath)
                    }
                }
            } catch (_: Exception) {}

            com.developer.manali.galleryapp.data.MediaRepository.clearCache()

            if (pathsToScan.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    this,
                    pathsToScan.distinct().toTypedArray(),
                    null
                ) { _, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        sendBroadcast(Intent("com.developer.manali.galleryapp.MEDIA_UPDATED"))
                        sendBroadcast(Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))
                        loadMediaData()
                    }
                }
            } else {
                sendBroadcast(Intent("com.developer.manali.galleryapp.MEDIA_UPDATED"))
                sendBroadcast(Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))
                loadMediaData()
            }
        }
    private val mediaRepository = MediaRepository()
    private val mediaList = ArrayList<MediaItem>()
    private var currentPosition: Int = 0

    private val currentMediaItem: MediaItem?
        get() = mediaPagerAdapter.getItem(binding.viewPagerMediaDetail.currentItem)

    private val favoriteIds = HashSet<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMediaDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootMediaDetail) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViewPager()
        setupListeners()
        loadMediaData()
        loadBigBannerAd()
    }

    private fun loadBigBannerAd() {
        if (!AdsUtils.isConnected(this)) return

        bannerAdView = AdView(this).apply {
            adUnitId = getString(R.string.admob_banner)
            setAdSize(AdSize.BANNER)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    binding.clAdView.removeAllViews()
                    binding.clAdView.addView(this@apply)
                    binding.clAdView.visibility = View.VISIBLE
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    super.onAdFailedToLoad(error)
                    binding.clAdView.visibility = View.GONE
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

    private fun setupViewPager() {
        mediaPagerAdapter = MediaPagerAdapter { videoItem ->
            playVideo(videoItem)
        }
        binding.viewPagerMediaDetail.adapter = mediaPagerAdapter

        binding.viewPagerMediaDetail.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                updateUIForPosition(position)
                resetNonCurrentZoomViews(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_SETTLING || state == ViewPager2.SCROLL_STATE_IDLE) {
                    resetNonCurrentZoomViews(binding.viewPagerMediaDetail.currentItem)
                }
            }
        })
    }

    private fun resetNonCurrentZoomViews(currentPos: Int) {
        val recyclerView =
            binding.viewPagerMediaDetail.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
                ?: return
        val childCount = recyclerView.childCount
        for (i in 0 until childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child)
            if (holder is MediaPagerAdapter.MediaPagerViewHolder && holder.bindingAdapterPosition != currentPos) {
                holder.resetZoom()
            }
        }
    }

    private fun loadMediaData() {
        val passedList: List<MediaItem>? = com.developer.manali.galleryapp.data.MediaDataHolder.mediaList
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("media_list", MediaItem::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("media_list")
            }

        val passedPosition = intent.getIntExtra("current_position", 0)

        if (!passedList.isNullOrEmpty()) {
            mediaList.clear()
            mediaList.addAll(passedList)
            mediaPagerAdapter.submitList(mediaList)
            binding.viewPagerMediaDetail.setCurrentItem(
                passedPosition.coerceIn(
                    0,
                    mediaList.size - 1
                ), false
            )
            updateUIForPosition(passedPosition)
        } else {
            val singleItem: MediaItem? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("media_item", MediaItem::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("media_item")
                }

            lifecycleScope.launch {
                val allPhotos = mediaRepository.getPhotos(this@MediaDetailActivity)
                if (allPhotos.isNotEmpty()) {
                    mediaList.clear()
                    mediaList.addAll(allPhotos)
                    mediaPagerAdapter.submitList(mediaList)

                    val index = if (singleItem != null) {
                        allPhotos.indexOfFirst { it.id == singleItem.id }.coerceAtLeast(0)
                    } else 0

                    binding.viewPagerMediaDetail.setCurrentItem(index, false)
                    updateUIForPosition(index)
                } else if (singleItem != null) {
                    mediaList.clear()
                    mediaList.add(singleItem)
                    mediaPagerAdapter.submitList(mediaList)
                    updateUIForPosition(0)
                }
            }
        }
    }

    private fun updateUIForPosition(position: Int) {
        val item = mediaPagerAdapter.getItem(position) ?: return
        binding.tvDetailTitle.text = item.bucketName

        val dateFormatted = MediaRepository.formatExactDate(item.dateAdded)
        val counterText = "${position + 1} of ${mediaList.size}"
        binding.tvDetailSubtitle.text = if (dateFormatted.isNotEmpty()) "$counterText • $dateFormatted" else counterText

        val isFav = AppPreferences.getInstance(this).isFavorite(item.id)
        if (isFav) {
            binding.ivFavoriteDetail.setImageResource(R.drawable.ic_heart_filled)
            binding.ivFavoriteDetail.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.red)
            )
        } else {
            binding.ivFavoriteDetail.setImageResource(R.drawable.favorite)
            binding.ivFavoriteDetail.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.black)
            )
        }
    }

    private fun setupListeners() {
        binding.btnBackDetail.setOnClickListener {
            finish()
        }

        binding.ivFavoriteDetail.setOnClickListener {
            toggleFavorite()
        }

        binding.ivInfoDetail.setOnClickListener {
            showMediaInfoDialog()
        }

        binding.btnShareDetail.setOnClickListener {
            shareMedia()
        }

        binding.btnEditDetail.setOnClickListener {
            val item = currentMediaItem ?: return@setOnClickListener
            val uri = item.uri
            Log.d("VVV", "setupClickListeners: $uri")
            if (uri != null) {
                openGooglePhotosEditor(uri)
            } else {
                Toast.makeText(this, getString(R.string.image_not_available), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnMoveDetail.setOnClickListener {
            showMoveDialog()
        }

        binding.btnDeleteDetail.setOnClickListener {
            showDeleteConfirmDialog()
        }

        binding.btnMenuDetail.setOnClickListener {
            showRightMenuDialog()
        }
    }

    private val moveDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
        }
    }



    private fun openGooglePhotosEditor(imageUri: Uri) {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(imageUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val packageManager = packageManager
            val isGooglePhotosInstalled = try {
                packageManager.getPackageInfo("com.google.android.apps.photos", 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            if (isGooglePhotosInstalled) {
                setPackage("com.google.android.apps.photos")
            }
        }
        try {
            startEditorForResult.launch(intent)
        } catch (e: Exception) {
            try {
                val genericIntent = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(imageUri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                startEditorForResult.launch(Intent.createChooser(genericIntent, "Edit Image"))
            } catch (e2: Exception) {
                Toast.makeText(this, getString(R.string.no_app_found_to_open_this_file), Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun toggleFavorite() {
        val item = currentMediaItem ?: return
        val isFav = AppPreferences.getInstance(this).toggleFavorite(item.id)
        if (isFav) {
            binding.ivFavoriteDetail.setImageResource(R.drawable.ic_heart_filled)
            binding.ivFavoriteDetail.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.lumina_primary)
            )
            Toast.makeText(this, getString(R.string.added_to_favorites), Toast.LENGTH_SHORT).show()
        } else {
            binding.ivFavoriteDetail.setImageResource(R.drawable.favorite)
            binding.ivFavoriteDetail.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.black)
            )
            Toast.makeText(this, getString(R.string.removed_from_favorites), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareMedia() {
        val item = currentMediaItem ?: return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (item.isVideo) "video/*" else "image/*"
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_media)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.unable_to_share_media), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog() {
        val item = currentMediaItem ?: return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_rename_media, null)
        dialog.setContentView(dialogView)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvRenameDialogTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvRenameDialogSubtitle)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivRenameDialogIcon)
        val etName = dialogView.findViewById<EditText>(R.id.etRenameItemName)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelRename)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmRename)

        tvTitle.text = if (item.isVideo) getString(R.string.rename_video)else getString(R.string.rename_photo)
        tvSubtitle.text = getString(R.string.enter_a_new_name_for_this_file)
        ivIcon.setImageResource(R.drawable.edit)

        val currentName = item.displayName
        val baseName =
            if (currentName.contains(".")) currentName.substringBeforeLast(".") else currentName
        etName.setText(baseName)
        etName.setSelection(baseName.length)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val enteredName = etName.text.toString().trim()
            if (enteredName.isEmpty()) {
                Toast.makeText(this,
                    getString(R.string.please_enter_a_valid_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            executeRename(item, enteredName)
        }

        dialog.show()
    }

    private fun executeRename(item: MediaItem, enteredName: String) {
        lifecycleScope.launch {
            when (val result = mediaRepository.renameMediaItem(this@MediaDetailActivity, item, enteredName)) {
                is MediaRepository.RenameResult.Success -> {
                    val updatedItem = result.updatedItem
                    val pos = binding.viewPagerMediaDetail.currentItem
                    if (pos in 0 until mediaList.size) {
                        mediaList[pos] = updatedItem
                        com.developer.manali.galleryapp.data.MediaDataHolder.mediaList = mediaList
                        mediaPagerAdapter.submitList(ArrayList(mediaList))
                        binding.tvDetailTitle.text = updatedItem.displayName
                    }
                    sendBroadcast(Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))
                    setResult(RESULT_OK)
                    Toast.makeText(
                        this@MediaDetailActivity,
                        getString(R.string.renamed_to, updatedItem.displayName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is MediaRepository.RenameResult.PermissionRequired -> {
                    pendingRenameItem = item
                    pendingRenameName = enteredName
                    val intentSenderRequest =
                        androidx.activity.result.IntentSenderRequest.Builder(result.intentSender).build()
                    renameLauncher.launch(intentSenderRequest)
                }
                is MediaRepository.RenameResult.Failed -> {
                    Toast.makeText(
                        this@MediaDetailActivity,
                        getString(R.string.failed_to_rename_file),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    private fun showMoveDialog() {
        val item = currentMediaItem ?: return

        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_move_to_album, null)
        bottomSheetDialog.setContentView(dialogView)

        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvMoveSubtitle)
        val rvMoveAlbums =
            dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvMoveAlbums)
        val progress = dialogView.findViewById<View>(R.id.progressMoveAlbums)
        val layoutCreateNew = dialogView.findViewById<View>(R.id.layoutCreateNewAlbumMove)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelMoveDialog)

        tvSubtitle.text = getString(R.string.move_to_album)
        rvMoveAlbums.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val albums = mediaRepository.getAlbums(this@MediaDetailActivity)
            progress.visibility = View.GONE

            val adapter = MoveAlbumAdapter(albums) { targetAlbum ->
                bottomSheetDialog.dismiss()
                executeMove(targetAlbum, targetAlbum.bucketName, listOf(item))
            }
            rvMoveAlbums.adapter = adapter
        }

        layoutCreateNew.setOnClickListener {
            bottomSheetDialog.dismiss()
            showCreateAlbumAndMoveDialog(listOf(item))
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

        val etName = dialogView.findViewById<EditText>(R.id.etNewAlbumName)
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

    private fun executeMove(
        targetAlbum: AlbumItem?,
        targetAlbumName: kotlin.String,
        selected: List<MediaItem>
    ) {
        if (selected.isEmpty()) return

        if (targetAlbum == null) {
            com.developer.manali.galleryapp.data.AppPreferences.getInstance(this).addCreatedAlbum(targetAlbumName)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val (movedCount, pendingUris) = mediaRepository.moveMediaItems(
                this@MediaDetailActivity,
                selected,
                targetAlbum,
                targetAlbumName
            )

            withContext(Dispatchers.Main) {
                if (pendingUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, pendingUris)
                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        moveDeleteLauncher.launch(intentSenderRequest)
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                Toast.makeText(
                    this@MediaDetailActivity,
                    "Moved $movedCount item(s) to \"$targetAlbumName\"",
                    Toast.LENGTH_SHORT
                ).show()

                sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))

                val pos = binding.viewPagerMediaDetail.currentItem
                if (pos in 0 until mediaList.size) {
                    mediaList.removeAt(pos)
                    mediaPagerAdapter.submitList(ArrayList(mediaList))
                    if (mediaList.isEmpty()) {
                        finish()
                    } else {
                        val nextPos = pos.coerceIn(0, mediaList.size - 1)
                        binding.viewPagerMediaDetail.setCurrentItem(nextPos, false)
                        updateUIForPosition(nextPos)
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmDialog() {
        val item = currentMediaItem ?: return

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            decorView.setBackgroundColor(Color.TRANSPARENT)
        }

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDeleteDialogTitle)
        val tvName = dialogView.findViewById<TextView>(R.id.tvDeleteVideoName)
        val tvDetails = dialogView.findViewById<TextView>(R.id.tvDeleteVideoDetails)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivDeleteMediaIcon)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmDelete)

        tvTitle.text = if (item.isVideo) getString(R.string.delete_video) else getString(R.string.delete_photo)
        ivIcon.setImageResource(if (item.isVideo) R.drawable.video else R.drawable.photo)
        tvName.text = item.displayName
        val sizeStr = MediaRepository.formatFileSize(item.size)
        val durStr = if (item.isVideo) MediaRepository.formatDuration(item.duration) else ""
        tvDetails.text =
            if (durStr.isNotEmpty() && durStr != "0:00") "$sizeStr • $durStr" else sizeStr

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            deleteCurrentMedia()
        }

        dialog.show()

        dialog.window?.let { window ->
            val density = resources.displayMetrics.density
            val widthPx = (320 * density).toInt()

            val params = window.attributes
            params.gravity = Gravity.CENTER
            params.width = widthPx
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = params
            window.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
    private fun deleteCurrentMedia() {
        val item = currentMediaItem ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            var isDeleted = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent =
                        MediaStore.createDeleteRequest(contentResolver, listOf(item.uri))
                    val intentSenderRequest =
                        androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender)
                            .build()
                    withContext(Dispatchers.Main) {
                        deleteLauncher.launch(intentSenderRequest)
                    }
                    return@launch
                } else {
                    val rows = contentResolver.delete(item.uri, null, null)
                    if (rows > 0) isDeleted = true
                }
            } catch (sec: android.app.RecoverableSecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val intentSenderRequest =
                        androidx.activity.result.IntentSenderRequest.Builder(sec.userAction.actionIntent.intentSender)
                            .build()
                    withContext(Dispatchers.Main) {
                        deleteLauncher.launch(intentSenderRequest)
                    }
                    return@launch
                }
            } catch (_: Exception) {
            }

            if (item.path.isNotEmpty()) {
                try {
                    val f = File(item.path)
                    if (f.exists() && f.delete()) {
                        isDeleted = true
                    }
                } catch (_: Exception) {
                }
            }

            try {
                if (item.path.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(
                        applicationContext,
                        arrayOf(item.path),
                        null,
                        null
                    )
                }
            } catch (_: Exception) {
            }

            withContext(Dispatchers.Main) {
                onMediaDeletedSuccess()
            }
        }
    }

    private fun onMediaDeletedSuccess() {
        com.developer.manali.galleryapp.data.MediaRepository.clearCache()
        val pos = binding.viewPagerMediaDetail.currentItem
        if (pos in 0 until mediaList.size) {
            val removedItem = mediaList.removeAt(pos)
            AppPreferences.getInstance(this).removeFavorite(removedItem.id)
            mediaPagerAdapter.submitList(ArrayList(mediaList))
            setResult(RESULT_OK)
            Toast.makeText(this, getString(R.string.deleted_permanently), Toast.LENGTH_SHORT).show()
            if (mediaList.isEmpty()) {
                finish()
            } else {
                val nextPos = pos.coerceIn(0, mediaList.size - 1)
                binding.viewPagerMediaDetail.setCurrentItem(nextPos, false)
                updateUIForPosition(nextPos)
            }
        }
    }

    private fun showRightMenuDialog() {
        val item = currentMediaItem ?: return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val dialogView = layoutInflater.inflate(R.layout.dialog_detail_more_menu, null)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            decorView.setBackgroundColor(Color.TRANSPARENT)
        }

        dialogView.findViewById<View>(R.id.layoutDetailMenuRename).setOnClickListener {
            dialog.dismiss()
            showRenameDialog()
        }

        val layoutWallpaper = dialogView.findViewById<View>(R.id.layoutDetailMenuWallpaper)
        if (item.isVideo) {
            layoutWallpaper.visibility = View.GONE
        } else {
            layoutWallpaper.visibility = View.VISIBLE
            layoutWallpaper.setOnClickListener {
                dialog.dismiss()
                showWallpaperOptionsDialog(item)
            }
        }

        dialogView.findViewById<View>(R.id.layoutDetailMenuCopy).setOnClickListener {
            dialog.dismiss()
            showCopyDialog()
        }

        dialogView.findViewById<View>(R.id.layoutDetailMenuOpenWith).setOnClickListener {
            dialog.dismiss()
            openWith(item)
        }

        val layoutVault = dialogView.findViewById<View>(R.id.layoutDetailMenuMovetoVault)
        layoutVault.visibility = View.GONE
        layoutVault.setOnClickListener {
            dialog.dismiss()
            moveToVault()
        }

        dialog.show()

        dialog.window?.let { window ->
            val density = resources.displayMetrics.density
            val widthPx = (220 * density).toInt()
            val marginX = (16 * density).toInt()
            val gapY = (12 * density).toInt()


            val viewPager = findViewById<View>(R.id.viewPagerMediaDetail)

            if (viewPager != null) {
                val location = IntArray(2)
                viewPager.getLocationOnScreen(location)
                val pagerTop = location[1]
                val pagerBottom = pagerTop + viewPager.height
                val screenHeight = resources.displayMetrics.heightPixels

                val distanceFromBottom = screenHeight - pagerBottom + gapY

                val params = window.attributes
                params.gravity = Gravity.BOTTOM or Gravity.END
                params.x = marginX
                params.y = distanceFromBottom
                params.width = widthPx
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                window.attributes = params
                window.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
                window.decorView.setPadding(0, 0, 0, 0)
            } else {

                val params = window.attributes
                params.gravity = Gravity.BOTTOM or Gravity.END
                params.x = marginX
                params.y = (98 * density).toInt()
                params.width = widthPx
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                window.attributes = params
                window.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
                window.decorView.setPadding(0, 0, 0, 0)
            }
        }
    }
    private fun showWallpaperOptionsDialog(item: MediaItem) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_wallpaper, null)
        bottomSheetDialog.setContentView(dialogView)

        val layoutHome = dialogView.findViewById<LinearLayout>(R.id.layoutWallpaperHome)
        val layoutLock = dialogView.findViewById<LinearLayout>(R.id.layoutWallpaperLock)
        val layoutBoth = dialogView.findViewById<LinearLayout>(R.id.layoutWallpaperBoth)

        val ivHome = dialogView.findViewById<ImageView>(R.id.ivWallpaperHome)
        val ivLock = dialogView.findViewById<ImageView>(R.id.ivWallpaperLock)
        val ivBoth = dialogView.findViewById<ImageView>(R.id.ivWallpaperBoth)

        val rbHome = dialogView.findViewById<RadioButton>(R.id.rbWallpaperHome)
        val rbLock = dialogView.findViewById<RadioButton>(R.id.rbWallpaperLock)
        val rbBoth = dialogView.findViewById<RadioButton>(R.id.rbWallpaperBoth)

        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelWallpaper)
        val btnApply = dialogView.findViewById<View>(R.id.btnApplyWallpaper)
        val progress = dialogView.findViewById<View>(R.id.progressWallpaperApply)
        val layoutActions = dialogView.findViewById<View>(R.id.layoutWallpaperActions)

        val prefs = PreferencesUtility.getInstance(this)
        var selectedTarget = prefs.getLastWallpaperTarget()

        fun updateSelection(target: Int) {
            selectedTarget = target
            rbHome.isChecked = (target == 1)
            rbLock.isChecked = (target == 2)
            rbBoth.isChecked = (target == 3)

            val primaryColor = ContextCompat.getColor(this, R.color.lumina_primary)
            val titleColor = ContextCompat.getColor(this, R.color.lumina_text_title)

            ivHome.imageTintList =
                ColorStateList.valueOf(if (target == 1) primaryColor else titleColor)
            ivLock.imageTintList =
                ColorStateList.valueOf(if (target == 2) primaryColor else titleColor)
            ivBoth.imageTintList =
                ColorStateList.valueOf(if (target == 3) primaryColor else titleColor)
        }

        updateSelection(selectedTarget)

        layoutHome.setOnClickListener { updateSelection(1) }
        layoutLock.setOnClickListener { updateSelection(2) }
        layoutBoth.setOnClickListener { updateSelection(3) }

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnApply.setOnClickListener {
            progress.visibility = View.VISIBLE
            layoutActions.visibility = View.GONE
            bottomSheetDialog.setCancelable(false)

            val flag = when (selectedTarget) {
                1 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        android.app.WallpaperManager.FLAG_SYSTEM
                    } else 0
                }

                2 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        android.app.WallpaperManager.FLAG_LOCK
                    } else 0
                }

                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK
                    } else 0
                }
            }

            lifecycleScope.launch {
                val success = mediaRepository.applyWallpaper(this@MediaDetailActivity, item, flag)
                progress.visibility = View.GONE
                bottomSheetDialog.dismiss()

                if (success) {
                    prefs.setLastWallpaperTarget(selectedTarget)
                    val targetName = when (selectedTarget) {
                        1 -> getString(R.string.home_screen)
                        2 -> getString(R.string.lock_screen)
                        else -> getString(R.string.home_and_lock_screens)
                    }
                    Toast.makeText(
                        this@MediaDetailActivity,
                        getString(R.string.wallpaper_set_on_successfully, targetName),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    try {
                        val wallpaperManager =
                            android.app.WallpaperManager.getInstance(this@MediaDetailActivity)
                        val cropIntent = wallpaperManager.getCropAndSetWallpaperIntent(item.uri)
                        startActivity(cropIntent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            this@MediaDetailActivity,
                            getString(R.string.failed_to_apply_wallpaper),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        bottomSheetDialog.show()
    }

    private fun openWith(item: MediaItem) {
        try {
            val mime = if (item.isVideo) {
                if (item.mimeType.isNotEmpty()) item.mimeType else "video/*"
            } else {
                if (item.mimeType.isNotEmpty()) item.mimeType else "image/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (e: Exception) {
            Toast.makeText(this,
                getString(R.string.no_app_found_to_open_this_file), Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveToVault() {
        val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(this)
        if (appPrefs.appLockPin.isEmpty()) {
            val intent = Intent(this, LockscreenActivity::class.java)
            passwordSetupLauncherForVault.launch(intent)
        } else {
            moveToVaultDirectly()
        }
    }

    private fun moveToVaultDirectly() {
        val pos = binding.viewPagerMediaDetail.currentItem
        if (pos in 0 until mediaList.size) {
            val item = mediaList[pos]
            val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(this)
            appPrefs.setLockedMedia(listOf(item.id.toString()), true)
            Toast.makeText(this, getString(R.string.moved_to_vault), Toast.LENGTH_SHORT).show()
            
            val removedItem = mediaList.removeAt(pos)
            appPrefs.removeFavorite(removedItem.id)
            mediaPagerAdapter.submitList(ArrayList(mediaList))
            setResult(RESULT_OK)
            
            if (mediaList.isEmpty()) {
                finish()
            } else {
                val nextPos = pos.coerceIn(0, mediaList.size - 1)
                binding.viewPagerMediaDetail.setCurrentItem(nextPos, false)
                updateUIForPosition(nextPos)
            }
        }
    }

    private fun showCopyDialog() {
        val item = currentMediaItem ?: return

        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_move_to_album, null)
        bottomSheetDialog.setContentView(dialogView)

        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvMoveSubtitle)
        val rvMoveAlbums =
            dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvMoveAlbums)
        val progress = dialogView.findViewById<View>(R.id.progressMoveAlbums)
        val layoutCreateNew = dialogView.findViewById<View>(R.id.layoutCreateNewAlbumMove)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelMoveDialog)

        tvSubtitle.text = getString(R.string.copy_to_album)
        rvMoveAlbums.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val albums = mediaRepository.getAlbums(this@MediaDetailActivity)
            progress.visibility = View.GONE

            val adapter = MoveAlbumAdapter(albums) { targetAlbum ->
                bottomSheetDialog.dismiss()
                executeCopy(targetAlbum, targetAlbum.bucketName, listOf(item))
            }
            rvMoveAlbums.adapter = adapter
        }

        layoutCreateNew.setOnClickListener {
            bottomSheetDialog.dismiss()
            showCreateAlbumAndCopyDialog(listOf(item))
        }

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun showCreateAlbumAndCopyDialog(selected: List<MediaItem>) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_album, null)
        dialog.setContentView(dialogView)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val etName = dialogView.findViewById<EditText>(R.id.etNewAlbumName)
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
                executeCopy(null, name, selected)
            } else {
                Toast.makeText(this, getString(R.string.please_enter_album_name), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun executeCopy(
        targetAlbum: AlbumItem?,
        targetAlbumName: kotlin.String,
        selected: List<MediaItem>
    ) {
        if (selected.isEmpty()) return

        if (targetAlbum == null) {
            com.developer.manali.galleryapp.data.AppPreferences.getInstance(this).addCreatedAlbum(targetAlbumName)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val copiedCount = mediaRepository.copyMediaItems(
                this@MediaDetailActivity,
                selected,
                targetAlbum,
                targetAlbumName
            )

            withContext(Dispatchers.Main) {
                sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))
                if (copiedCount > 0) {
                    Toast.makeText(
                        this@MediaDetailActivity,
                        getString(R.string.copied_to, targetAlbumName),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MediaDetailActivity,
                        getString(R.string.failed_to_copy_file),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
    }

    private fun showMediaInfoDialog() {
        val item = currentMediaItem ?: return
        val name = item.displayName
        val uri = item.uri
        val path = getRealFilePath(item)

        var sizeBytes = item.size
        if (sizeBytes <= 0) {
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    sizeBytes = pfd.statSize
                }
            } catch (_: Exception) {
            }
        }
        if (sizeBytes <= 0) {
            try {
                val file = File(path)
                if (file.exists()) {
                    sizeBytes = file.length()
                }
            } catch (_: Exception) {
            }
        }
        val sizeStr = formatFileSize(sizeBytes)

        var timestamp = item.dateAdded
        if (timestamp <= 0) {
            try {
                val projection =
                    arrayOf(
                        MediaStore.MediaColumns.DATE_MODIFIED,
                        MediaStore.MediaColumns.DATE_ADDED
                    )
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        if (modIdx != -1) {
                            timestamp = cursor.getLong(modIdx)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        val dateStr = if (timestamp > 0) {
            val millis = if (timestamp > 100000000000L) timestamp else timestamp * 1000L
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
        } else {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        }

        val resolutionStr = getRealResolution(item)

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_media_info, null)

        dialogView.findViewById<TextView>(R.id.tvInfoName).text = name
        dialogView.findViewById<TextView>(R.id.tvInfoDate).text = dateStr
        dialogView.findViewById<TextView>(R.id.tvInfoResolution).text = resolutionStr
        dialogView.findViewById<TextView>(R.id.tvInfoSize).text = sizeStr
        dialogView.findViewById<TextView>(R.id.tvInfoPath).text = path

        dialogView.findViewById<android.view.View>(R.id.btnInfoDone).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun getRealFilePathFromUri(uri: Uri): String? {
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIndex != -1) {
                        val data = cursor.getString(dataIndex)
                        if (!data.isNullOrEmpty() && data.startsWith("/")) {
                            return data
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (relIndex != -1 && nameIndex != -1) {
                            val relPath = cursor.getString(relIndex) ?: ""
                            val name = cursor.getString(nameIndex) ?: ""
                            val base = Environment.getExternalStorageDirectory().absolutePath
                            return "$base/$relPath$name".replace("//", "/")
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun getRealFilePath(item: MediaItem): kotlin.String {
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME
            )
            contentResolver.query(item.uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIndex != -1) {
                        val data = cursor.getString(dataIndex)
                        if (!data.isNullOrEmpty() && data.startsWith("/")) {
                            return data
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (relIndex != -1 && nameIndex != -1) {
                            val relPath = cursor.getString(relIndex) ?: ""
                            val name = cursor.getString(nameIndex) ?: item.displayName
                            val base =
                                android.os.Environment.getExternalStorageDirectory().absolutePath
                            return "$base/$relPath$name".replace("//", "/")
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (item.path.isNotEmpty() && item.path.startsWith("/")) {
            return item.path
        }
        val storageRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
        val folder = if (item.bucketName.isNotEmpty()) item.bucketName else "Camera"
        return "$storageRoot/DCIM/$folder/${item.displayName}"
    }

    private fun getRealResolution(item: MediaItem): kotlin.String {
        try {
            if (item.isVideo) {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(this, item.uri)
                val width =
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height =
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotation =
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?: "0"
                retriever.release()
                if (!width.isNullOrEmpty() && !height.isNullOrEmpty()) {
                    val w = width.toIntOrNull() ?: 0
                    val h = height.toIntOrNull() ?: 0
                    return if (rotation == "90" || rotation == "270") {
                        "$h x $w"
                    } else {
                        "$w x $h"
                    }
                }
            } else {
                val projection = arrayOf(
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.ORIENTATION
                )
                contentResolver.query(item.uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val wIdx = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                        val hIdx = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                        val oIdx = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
                        val w = if (wIdx != -1) cursor.getInt(wIdx) else 0
                        val h = if (hIdx != -1) cursor.getInt(hIdx) else 0
                        val orientation = if (oIdx != -1) cursor.getInt(oIdx) else 0
                        if (w > 0 && h > 0) {
                            return if (orientation == 90 || orientation == 270) "$h x $w" else "$w x $h"
                        }
                    }
                }

                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                contentResolver.openInputStream(item.uri)?.use { inputStream ->
                    android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                }
                if (options.outWidth > 0 && options.outHeight > 0) {
                    return "${options.outWidth} x ${options.outHeight}"
                }
            }
        } catch (_: Exception) {
        }
        return "1920 x 1080"
    }

    private fun playVideo(video: MediaItem) {
        val intent = Intent(this, VideoDetailActivity::class.java).apply {
            putExtra("video_item", video)
        }
        startActivity(intent)
    }

    private fun formatFileSize(sizeBytes: Long): kotlin.String? {
        if (sizeBytes <= 0) return "Unknown"
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.getDefault(), "%.2f MB", mb)
        } else {
            String.format(Locale.getDefault(), "%.2f KB", kb)
        }
    }
}
