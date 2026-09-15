package com.developer.manali.galleryapp

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.databinding.ActivityVideoDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressWarnings("all")
class VideoDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityVideoDetailBinding

    private val mediaRepository = MediaRepository()
    private val videoList = ArrayList<MediaItem>()
    private var currentPosition: Int = 0
    private var isFromVault = false

    private val currentVideoItem: MediaItem?
        get() = if (currentPosition in 0 until videoList.size) videoList[currentPosition] else null

    private val deleteLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onVideoDeletedSuccess()
        }
    }

    private var pendingRenameItem: MediaItem? = null
    private var pendingRenameName: String? = null

    private val renameLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
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
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            moveToVaultDirectly()
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var isControlsVisible = true
    private var isSeeking = false
    private var isLooping = false
    private var isFillScreen = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private val hideControlsHandler = Handler(Looper.getMainLooper())

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (binding.videoView.isPlaying && !isSeeking) {
                val current = binding.videoView.currentPosition
                val duration = binding.videoView.duration
                if (duration > 0) {
                    binding.seekBarVideo.max = duration
                    binding.seekBarVideo.progress = current
                    binding.tvCurrentTime.text = MediaRepository.formatDuration(current.toLong())
                    binding.tvTotalDuration.text = MediaRepository.formatDuration(duration.toLong())
                }
            }
            progressHandler.postDelayed(this, 250)
        }
    }

    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isFromVault = intent.getBooleanExtra("is_from_vault", false)

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityVideoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootVideoDetail) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBarVideoDetail.setPadding(
                16,
                systemBars.top + 16,
                16,
                16
            )
            binding.bottomBarVideoDetail.setPadding(
                16,
                16,
                16,
                systemBars.bottom + 20
            )
            insets
        }

        setupListeners()
        loadVideoData()
    }

    private fun loadVideoData() {
        val passedList: List<MediaItem>? = com.developer.manali.galleryapp.data.MediaDataHolder.videoList
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("video_list", MediaItem::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("video_list")
            }

        val passedPosition = intent.getIntExtra("current_position", 0)

        if (!passedList.isNullOrEmpty()) {
            videoList.clear()
            videoList.addAll(passedList)
            currentPosition = passedPosition.coerceIn(0, videoList.size - 1)
            playVideoAtCurrentPosition()
        } else {
            val singleItem: MediaItem? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("video_item", MediaItem::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("video_item")
            }

            lifecycleScope.launch {
                val allVideos = mediaRepository.getVideos(this@VideoDetailActivity)
                if (allVideos.isNotEmpty()) {
                    videoList.clear()
                    videoList.addAll(allVideos)
                    currentPosition = if (singleItem != null) {
                        allVideos.indexOfFirst { it.id == singleItem.id }.coerceAtLeast(0)
                    } else 0
                    playVideoAtCurrentPosition()
                } else if (singleItem != null) {
                    videoList.clear()
                    videoList.add(singleItem)
                    currentPosition = 0
                    playVideoAtCurrentPosition()
                } else {
                    Toast.makeText(this@VideoDetailActivity,
                        getString(R.string.no_video_to_play), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private var equalizer: android.media.audiofx.Equalizer? = null

    private fun playVideoAtCurrentPosition() {
        val item = currentVideoItem ?: return

        binding.tvVideoDetailTitle.text = item.displayName
        val dateFormatted = MediaRepository.formatExactDate(item.dateAdded)
        val counterText = "${currentPosition + 1} of ${videoList.size}"
        binding.tvVideoIndexCounter.text = if (dateFormatted.isNotEmpty()) "$counterText • $dateFormatted" else counterText

        updateNavButtons()

        binding.seekBarVideo.progress = 0
        binding.tvCurrentTime.text = "0:00"
        binding.tvTotalDuration.text = if (item.duration > 0) MediaRepository.formatDuration(item.duration) else "0:00"

        binding.progressBuffering.visibility = View.VISIBLE

        binding.videoView.stopPlayback()
        binding.videoView.setVideoURI(item.uri)

        binding.videoView.setOnPreparedListener { mp ->
            mediaPlayer = mp
            mp.isLooping = isLooping
            
            try {
                equalizer?.release()
                equalizer = android.media.audiofx.Equalizer(0, mp.audioSessionId)
                val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(this)
                val isEnabled = prefs.isEqualizerEnabled
                equalizer?.enabled = isEnabled
                
                if (isEnabled) {
                    val numBands = equalizer?.numberOfBands ?: 0
                    for (i in 0 until numBands) {
                        val band = i.toShort()
                        val level = prefs.getEqualizerBandLevel(band)
                        val min = equalizer?.bandLevelRange?.get(0) ?: -1500
                        val max = equalizer?.bandLevelRange?.get(1) ?: 1500
                        val safeLevel = level.coerceIn(min, max)
                        equalizer?.setBandLevel(band, safeLevel)
                    }
                }
            } catch (e: Exception) {
                equalizer = null
            }

            binding.progressBuffering.visibility = View.GONE

            val dur = mp.duration
            if (dur > 0) {
                binding.seekBarVideo.max = dur
                binding.tvTotalDuration.text = MediaRepository.formatDuration(dur.toLong())
            }

            applyVideoScaling()
            binding.videoView.start()
            binding.btnPlayPauseCenter.setImageResource(R.drawable.pause)

            progressHandler.removeCallbacks(updateProgressRunnable)
            progressHandler.post(updateProgressRunnable)

            scheduleHideControls()
        }

        binding.videoView.setOnCompletionListener {
            binding.btnPlayPauseCenter.setImageResource(R.drawable.play)
            showControls()
            if (!isLooping && currentPosition < videoList.size - 1) {

                playNextVideo()
            }
        }

        binding.videoView.setOnErrorListener { _, _, _ ->
            binding.progressBuffering.visibility = View.GONE
            Toast.makeText(this,
                getString(R.string.cannot_play_this_video_format), Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun updateNavButtons() {
        val hasPrev = currentPosition > 0
        val hasNext = currentPosition < videoList.size - 1

        binding.btnPrevVideo.isEnabled = hasPrev
        binding.btnPrevVideo.alpha = if (hasPrev) 1.0f else 0.35f

        binding.btnNextVideo.isEnabled = hasNext
        binding.btnNextVideo.alpha = if (hasNext) 1.0f else 0.35f
    }

    private fun playPreviousVideo() {
        if (currentPosition > 0) {
            currentPosition--
            playVideoAtCurrentPosition()
        }
    }

    private fun playNextVideo() {
        if (currentPosition < videoList.size - 1) {
            currentPosition++
            playVideoAtCurrentPosition()
        }
    }

    private fun togglePlayPause() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            binding.btnPlayPauseCenter.setImageResource(R.drawable.play)
            hideControlsHandler.removeCallbacks(hideControlsRunnable)
            showControls()
        } else {
            binding.videoView.start()
            binding.btnPlayPauseCenter.setImageResource(R.drawable.pause)
            scheduleHideControls()
        }
    }

    private fun seekRelative(offsetMs: Int) {
        val current = binding.videoView.currentPosition
        val duration = binding.videoView.duration
        val target = (current + offsetMs).coerceIn(0, duration)
        binding.videoView.seekTo(target)
        binding.seekBarVideo.progress = target
        binding.tvCurrentTime.text = MediaRepository.formatDuration(target.toLong())
        scheduleHideControls()
    }

    private fun applyVideoScaling() {
        val mp = mediaPlayer ?: return
        try {
            val videoWidth = mp.videoWidth
            val videoHeight = mp.videoHeight
            if (videoWidth > 0 && videoHeight > 0) {
                val containerWidth = binding.videoContainer.width
                val containerHeight = binding.videoContainer.height

                val params = binding.videoView.layoutParams as FrameLayout.LayoutParams

                if (isFillScreen && containerWidth > 0 && containerHeight > 0) {
                    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
                    val containerAspect = containerWidth.toFloat() / containerHeight.toFloat()

                    if (containerAspect > videoAspect) {
                        params.width = containerWidth
                        params.height = (containerWidth / videoAspect).toInt()
                    } else {
                        params.width = (containerHeight * videoAspect).toInt()
                        params.height = containerHeight
                    }
                } else {
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                binding.videoView.layoutParams = params
            }
        } catch (_: Exception) {}
    }

    private fun toggleAspectRatio() {
        isFillScreen = !isFillScreen
        applyVideoScaling()
        Toast.makeText(
            this,
            if (isFillScreen) getString(R.string.fill_screen) else getString(R.string.fit_screen),
            Toast.LENGTH_SHORT
        ).show()
        scheduleHideControls()
    }

    private fun showControls() {
        if (!isControlsVisible) {
            isControlsVisible = true
            binding.layoutControlsOverlay.animate()
                .alpha(1.0f)
                .setDuration(200)
                .withStartAction {
                    binding.layoutControlsOverlay.visibility = View.VISIBLE
                }
                .start()
        }
        scheduleHideControls()
    }

    private fun hideControls() {
        if (isControlsVisible && binding.videoView.isPlaying) {
            isControlsVisible = false
            binding.layoutControlsOverlay.animate()
                .alpha(0.0f)
                .setDuration(250)
                .withEndAction {
                    binding.layoutControlsOverlay.visibility = View.GONE
                }
                .start()
        }
    }

    private fun toggleControls() {
        if (isControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun scheduleHideControls() {
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        if (binding.videoView.isPlaying) {
            hideControlsHandler.postDelayed(hideControlsRunnable, 3500)
        }
    }

    private fun setupListeners() {
        // Back
        binding.btnBackVideoDetail.setOnClickListener {
            finish()
        }

        binding.videoContainer.setOnClickListener {
            toggleControls()
        }

        binding.btnPlayPauseCenter.setOnClickListener {
            togglePlayPause()
        }

        binding.btnPrevVideo.setOnClickListener {
            playPreviousVideo()
        }

        binding.btnNextVideo.setOnClickListener {
            playNextVideo()
        }

        binding.btnRewind10.setOnClickListener {
            seekRelative(-10000)
        }

        binding.btnForward10.setOnClickListener {
            seekRelative(10000)
        }

        binding.ivAspectRatio.setOnClickListener {
            toggleAspectRatio()
        }

        // Share
        binding.ivShareVideo.setOnClickListener {
            shareCurrentVideo()
        }

        // Delete
        binding.ivDeleteVideo.setOnClickListener {
            showDeleteConfirmDialog()
        }

        binding.ivInfoVideo.setOnClickListener {
            showVideoInfoDialog()
        }

        binding.ivEqualizerVideo.setOnClickListener {
            showEqualizerDialog()
        }

        binding.ivMenuVideo.setOnClickListener {
            showVideoMoreMenuDialog()
        }

        binding.seekBarVideo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = MediaRepository.formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
                hideControlsHandler.removeCallbacks(hideControlsRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    binding.videoView.seekTo(it.progress)
                }
                isSeeking = false
                scheduleHideControls()
            }
        })
    }

    private fun shareCurrentVideo() {
        val item = currentVideoItem ?: return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType.ifEmpty { "video/*" }
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_video)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.unable_to_share_video), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmDialog() {
        val item = currentVideoItem ?: return

        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        bottomSheetDialog.setContentView(dialogView)

        val tvName = dialogView.findViewById<TextView>(R.id.tvDeleteVideoName)
        val tvDetails = dialogView.findViewById<TextView>(R.id.tvDeleteVideoDetails)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmDelete)

        tvName.text = item.displayName
        val sizeStr = MediaRepository.formatFileSize(item.size)
        val durStr = MediaRepository.formatDuration(item.duration)
        tvDetails.text = if (durStr.isNotEmpty() && durStr != "0:00") "$sizeStr • $durStr" else sizeStr

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            bottomSheetDialog.dismiss()
            deleteCurrentVideo()
        }

        bottomSheetDialog.show()
    }

    private fun deleteCurrentVideo() {
        val item = currentVideoItem ?: return
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var isDeleted = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(item.uri))
                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        deleteLauncher.launch(intentSenderRequest)
                    }
                    return@launch
                } else {
                    val rows = contentResolver.delete(item.uri, null, null)
                    if (rows > 0) isDeleted = true
                }
            } catch (sec: android.app.RecoverableSecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(sec.userAction.actionIntent.intentSender).build()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        deleteLauncher.launch(intentSenderRequest)
                    }
                    return@launch
                }
            } catch (_: Exception) {}

            if (!isDeleted && item.path.isNotEmpty()) {
                try {
                    val f = File(item.path)
                    if (f.exists() && f.delete()) {
                        isDeleted = true
                    }
                } catch (_: Exception) {}
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onVideoDeletedSuccess()
            }
        }
    }

    private fun onVideoDeletedSuccess() {
        com.developer.manali.galleryapp.data.MediaRepository.clearCache()
        if (currentPosition in 0 until videoList.size) {
            videoList.removeAt(currentPosition)
            if (videoList.isEmpty()) {
                Toast.makeText(this,
                    getString(R.string.video_permanently_deleted), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                currentPosition = currentPosition.coerceIn(0, videoList.size - 1)
                playVideoAtCurrentPosition()
                Toast.makeText(this, getString(R.string.video_permanently_deleted), Toast.LENGTH_SHORT).show()
            }
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
        val item = currentVideoItem ?: return
        val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(this)
        appPrefs.setLockedMedia(listOf(item.id.toString()), true)
        Toast.makeText(this, getString(R.string.moved_to_vault), Toast.LENGTH_SHORT).show()
        
        if (currentPosition in 0 until videoList.size) {
            videoList.removeAt(currentPosition)
            if (videoList.isEmpty()) {
                finish()
            } else {
                currentPosition = currentPosition.coerceIn(0, videoList.size - 1)
                playVideoAtCurrentPosition()
            }
        }
    }

    private fun openVideoWith(item: MediaItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, item.mimeType.ifEmpty { "video/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (e: Exception) {
            Toast.makeText(this,
                getString(R.string.no_external_video_player_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVideoMoreMenuDialog() {
        val item = currentVideoItem ?: return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val dialogView = layoutInflater.inflate(R.layout.dialog_video_more_menu, null)
        dialog.setContentView(dialogView)

        dialogView.findViewById<View>(R.id.layoutVideoMenuShare).setOnClickListener {
            dialog.dismiss()
            shareCurrentVideo()
        }

        dialogView.findViewById<View>(R.id.layoutVideoMenuDelete).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmDialog()
        }

        dialogView.findViewById<View>(R.id.layoutVideoMenuInfo).setOnClickListener {
            dialog.dismiss()
            showVideoInfoDialog()
        }

        val layoutVault = dialogView.findViewById<View>(R.id.layoutVideoMenuMoveToVault)
        if (isFromVault) {
            layoutVault.visibility = View.GONE
        } else {
            layoutVault.visibility = View.VISIBLE
            layoutVault.setOnClickListener {
                dialog.dismiss()
                moveToVault()
            }
        }

        dialogView.findViewById<View>(R.id.layoutVideoMenuRename).setOnClickListener {
            dialog.dismiss()
            showRenameDialog()
        }

        dialogView.findViewById<View>(R.id.layoutVideoMenuOpenWith).setOnClickListener {
            dialog.dismiss()
            openVideoWith(item)
        }

        dialog.show()

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            val density = resources.displayMetrics.density
            val widthPx = (220 * density).toInt()
            val marginX = (16 * density).toInt()
            val marginY = (68 * density).toInt()

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

    private fun showMoreMenu(anchor: View) {
        val item = currentVideoItem ?: return
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, if (isLooping) getString(R.string.disable_loop) else getString(R.string.loop_video))
        popup.menu.add(0, 2, 1, getString(R.string.open_in_external_app))
        popup.menu.add(0, 3, 2, getString(R.string.rename_video))
        popup.menu.add(0, 4, 3, getString(R.string.video_details))

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    isLooping = !isLooping
                    mediaPlayer?.isLooping = isLooping
                    Toast.makeText(this, if (isLooping) getString(R.string.loop_enabled) else getString(
                        R.string.loop_disabled
                    ), Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(item.uri, item.mimeType.ifEmpty { "video/*" })
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this,
                            getString(R.string.no_external_video_player_found), Toast.LENGTH_SHORT).show()
                    }
                }
                3 -> {
                    showRenameDialog()
                }
                4 -> {
                    showVideoInfoDialog()
                }
            }
            true
        }
        popup.show()
    }

    private fun showRenameDialog() {
        val item = currentVideoItem ?: return
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

        tvTitle.text = getString(R.string.rename_video)
        tvSubtitle.text = getString(R.string.enter_a_new_name_for_this_video)
        ivIcon.setImageResource(R.drawable.edit)

        val currentName = item.displayName
        val baseName = if (currentName.contains(".")) currentName.substringBeforeLast(".") else currentName
        etName.setText(baseName)
        etName.setSelection(baseName.length)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val enteredName = etName.text.toString().trim()
            if (enteredName.isEmpty()) {
                Toast.makeText(this,
                    getString(R.string.please_enter_a_valid_video_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            executeRename(item, enteredName)
        }

        dialog.show()
    }

    private fun executeRename(item: MediaItem, enteredName: String) {
        lifecycleScope.launch {
            when (val result = mediaRepository.renameMediaItem(this@VideoDetailActivity, item, enteredName)) {
                is MediaRepository.RenameResult.Success -> {
                    val updatedItem = result.updatedItem
                    if (currentPosition in 0 until videoList.size) {
                        videoList[currentPosition] = updatedItem
                        com.developer.manali.galleryapp.data.MediaDataHolder.mediaList = videoList
                        binding.tvVideoDetailTitle.text = updatedItem.displayName
                    }
                    sendBroadcast(Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))
                    setResult(RESULT_OK)
                    Toast.makeText(
                        this@VideoDetailActivity,
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
                        this@VideoDetailActivity,
                        getString(R.string.failed_to_rename_video),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showVideoInfoDialog() {
        val item = currentVideoItem ?: return
        val name = item.displayName
        val uri = item.uri
        val path = getRealFilePath(item)

        var sizeBytes = item.size
        if (sizeBytes <= 0) {
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    sizeBytes = pfd.statSize
                }
            } catch (_: Exception) {}
        }
        if (sizeBytes <= 0) {
            try {
                val file = File(path)
                if (file.exists()) {
                    sizeBytes = file.length()
                }
            } catch (_: Exception) {}
        }
        val sizeStr = MediaRepository.formatFileSize(sizeBytes)

        var timestamp = item.dateAdded
        if (timestamp <= 0) {
            try {
                val projection = arrayOf(MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATE_ADDED)
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        if (modIdx != -1) {
                            timestamp = cursor.getLong(modIdx)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        val dateStr = if (timestamp > 0) {
            val millis = if (timestamp > 100000000000L) timestamp else timestamp * 1000L
            SimpleDateFormat("dd MMM, yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
        } else {
            SimpleDateFormat("dd MMM, yyyy, hh:mm a", Locale.getDefault()).format(Date())
        }

        val resolutionStr = getVideoResolution(item)

        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_media_info, null)

        dialogView.findViewById<TextView>(R.id.tvInfoName).text = name
        dialogView.findViewById<TextView>(R.id.tvInfoDate).text = dateStr
        dialogView.findViewById<TextView>(R.id.tvInfoResolution).text = resolutionStr
        dialogView.findViewById<TextView>(R.id.tvInfoSize).text = sizeStr
        dialogView.findViewById<TextView>(R.id.tvInfoPath).text = path

        dialogView.findViewById<View>(R.id.btnInfoDone).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun getVideoResolution(item: MediaItem): String {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, item.uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            retriever.release()
            if (!width.isNullOrEmpty() && !height.isNullOrEmpty()) {
                return "${width}x${height}"
            }
        } catch (_: Exception) {}
        return "1920x1080"
    }

    private fun getRealFilePath(item: MediaItem): String {
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
                            val base = android.os.Environment.getExternalStorageDirectory().absolutePath
                            return "$base/$relPath$name".replace("//", "/")
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return item.path
    }

    override fun onPause() {
        super.onPause()
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            binding.btnPlayPauseCenter.setImageResource(R.drawable.play)
        }
        progressHandler.removeCallbacks(updateProgressRunnable)
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (binding.videoView.duration > 0) {
            progressHandler.post(updateProgressRunnable)
        }
    }

    private fun showEqualizerDialog() {
        if (mediaPlayer == null) {
            Toast.makeText(this,
                getString(R.string.please_wait_for_video_to_load), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (equalizer == null) {
            Toast.makeText(this,
                getString(R.string.equalizer_not_supported_on_this_device), Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(this)
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_equalizer, null)
        dialog.setContentView(dialogView)

        val switchEq = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchEqualizer)
        val layoutBands = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutEqualizerBands)
        val btnDone = dialogView.findViewById<View>(R.id.btnEqualizerDone)

        switchEq.isChecked = prefs.isEqualizerEnabled
        
        val minLevel = equalizer!!.bandLevelRange[0]
        val maxLevel = equalizer!!.bandLevelRange[1]
        val numBands = equalizer!!.numberOfBands

        for (i in 0 until numBands) {
            val band = i.toShort()
            val freq = equalizer!!.getCenterFreq(band)
            val freqText = if (freq < 1000000) "${freq / 1000} Hz" else "${freq / 1000000} kHz"

            val bandView = layoutInflater.inflate(R.layout.item_equalizer_band, layoutBands, false)
            val tvFreq = bandView.findViewById<TextView>(R.id.tvBandFreq)
            val seekBar = bandView.findViewById<SeekBar>(R.id.seekBarBand)
            val tvGain = bandView.findViewById<TextView>(R.id.tvBandGain)

            tvFreq.text = freqText
            seekBar.max = maxLevel - minLevel
            val currentLevel = equalizer!!.getBandLevel(band)
            seekBar.progress = currentLevel - minLevel
            seekBar.isEnabled = switchEq.isChecked
            
            val initialGain = currentLevel / 100
            tvGain.text = if (initialGain > 0) "+$initialGain dB" else "$initialGain dB"

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && switchEq.isChecked) {
                        val level = (progress + minLevel).toShort()
                        val gain = level / 100
                        tvGain.text = if (gain > 0) "+$gain dB" else "$gain dB"
                        try {
                            equalizer!!.setBandLevel(band, level)
                            prefs.setEqualizerBandLevel(band, level)
                        } catch (_: Exception) {}
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            layoutBands.addView(bandView)
        }

        switchEq.setOnCheckedChangeListener { _, isChecked ->
            prefs.isEqualizerEnabled = isChecked
            equalizer!!.enabled = isChecked
            
            for (j in 0 until layoutBands.childCount) {
                val child = layoutBands.getChildAt(j)
                val sb = child.findViewById<SeekBar>(R.id.seekBarBand)
                sb?.isEnabled = isChecked
            }
        }

        btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacks(updateProgressRunnable)
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        binding.videoView.stopPlayback()
        try {
            equalizer?.release()
        } catch (_: Exception) {}
    }
}
