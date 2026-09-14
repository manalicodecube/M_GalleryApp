package com.developer.manali.galleryapp.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.developer.manali.galleryapp.R
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.data.VideoListItem
import com.developer.manali.galleryapp.databinding.FragmentVideosBinding
import com.developer.manali.galleryapp.ui.adapter.VideoGridAdapter
import kotlinx.coroutines.launch

class VideosFragment : Fragment() {

    private var _binding: FragmentVideosBinding? = null
    private val binding get() = _binding!!

    private val mediaRepository = MediaRepository()
    private lateinit var videoAdapter: VideoGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(requireContext())
        currentSpanCount = if (appPrefs.isListView) 1 else appPrefs.videosGridColumns
        setupRecyclerView()
        setupSwipeRefresh()
        loadVideos()
    }

    private val mediaUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.action
            if (action == "com.developer.manali.galleryapp.MEDIA_UPDATED" ||
                action == "com.developer.manali.galleryapp.ALBUMS_UPDATED") {
                refreshData()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val filter = android.content.IntentFilter().apply {
                addAction("com.developer.manali.galleryapp.MEDIA_UPDATED")
                addAction("com.developer.manali.galleryapp.ALBUMS_UPDATED")
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(mediaUpdateReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                requireContext().registerReceiver(mediaUpdateReceiver, filter)
            }
        } catch (_: Exception) {}

        context?.let {
            val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(it)
            applyViewType(prefs.videosViewType)
            if (!prefs.isVideosListView && currentSpanCount != prefs.gridColumns) {
                updateGridColumns(prefs.gridColumns)
            }
        }
        loadVideos()
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(mediaUpdateReceiver)
        } catch (_: Exception) {}
    }

    var currentSpanCount: Int = 3
        private set

    private fun setupRecyclerView() {
        val gridLayoutManager = GridLayoutManager(requireContext(), currentSpanCount)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (videoAdapter.getItemViewType(position) == VideoGridAdapter.TYPE_HEADER) {
                    gridLayoutManager.spanCount
                } else {
                    1
                }
            }
        }

        videoAdapter = VideoGridAdapter(
            onVideoClick = { video ->
                val clickAction = {
                    playVideo(video)
                }
                if (com.developer.manali.galleryapp.AdCounter.shouldShowAd()) {
                    com.developer.manali.galleryapp.GoogleInterstitialAdsCall.loadAndShowInterstitial(
                        requireActivity(),
                        object : com.developer.manali.galleryapp.InterstitialAdCallback {
                            override fun onAdClose() {
                                clickAction()
                            }
                        }
                    )
                } else {
                    clickAction()
                }
            },
            onSelectClick = { _ ->

            }
        )
        videoAdapter.setListView(com.developer.manali.galleryapp.data.AppPreferences.getInstance(requireContext()).isVideosListView)

        videoAdapter.setOnSelectionChangedListener { count, items ->
            (activity as? com.developer.manali.galleryapp.MainActivity)?.onSelectionUpdated(count, items)
        }
        videoAdapter.setOnItemLongClickListener { item ->
            (activity as? com.developer.manali.galleryapp.MainActivity)?.onItemLongPressed(item)
        }

        binding.rvVideos.setHasFixedSize(true)
        binding.rvVideos.setItemViewCacheSize(25)
        binding.rvVideos.layoutManager = gridLayoutManager
        binding.rvVideos.adapter = videoAdapter

        com.developer.manali.galleryapp.util.PinchZoomGridHelper(
            context = requireContext(),
            getSpanCount = { currentSpanCount },
            onSpanCountChanged = { newSpan ->
                updateGridColumns(newSpan)
                (activity as? com.developer.manali.galleryapp.MainActivity)?.onGridColumnsChanged(newSpan)
            }
        ).attachToRecyclerView(binding.rvVideos)
    }

    fun enterSelectionMode(initialItem: MediaItem? = null) {
        if (::videoAdapter.isInitialized) {
            videoAdapter.enterSelectionMode(initialItem)
        }
    }

    fun exitSelectionMode() {
        if (::videoAdapter.isInitialized) {
            videoAdapter.exitSelectionMode()
        }
    }

    fun selectAll() {
        if (::videoAdapter.isInitialized) {
            videoAdapter.selectAll()
            val selected = videoAdapter.getSelectedItems()
            (activity as? com.developer.manali.galleryapp.MainActivity)?.onSelectionUpdated(selected.size, selected)
        }
    }

    fun deselectAll() {
        if (::videoAdapter.isInitialized) {
            videoAdapter.deselectAll()
            val selected = videoAdapter.getSelectedItems()
            (activity as? com.developer.manali.galleryapp.MainActivity)?.onSelectionUpdated(selected.size, selected)
        }
    }

    fun getSelectedItems(): List<MediaItem> {
        return if (::videoAdapter.isInitialized) videoAdapter.getSelectedItems() else emptyList()
    }

    fun getAllMediaItems(): List<MediaItem> {
        return if (::videoAdapter.isInitialized) videoAdapter.getAllMediaItems() else emptyList()
    }

    fun removeItems(itemsToRemove: List<MediaItem>) {
        if (::videoAdapter.isInitialized) {
            videoAdapter.removeItems(itemsToRemove)
        }
    }

    fun isSelectionMode(): Boolean {
        return if (::videoAdapter.isInitialized) videoAdapter.isSelectionMode else false
    }

    private var lastAppliedSpan: Int = -1
    private var lastAppliedIsList: Boolean? = null

    fun applyViewType(viewType: String? = null) {
        val context = context ?: return
        val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(context)
        val effectiveType = viewType ?: prefs.videosViewType
        val isList = effectiveType == com.developer.manali.galleryapp.data.AppPreferences.VIEW_TYPE_LIST
        val span = if (isList) 1 else prefs.videosGridColumns
        currentSpanCount = span
        if (_binding != null && ::videoAdapter.isInitialized) {
            val savedState = binding.rvVideos.layoutManager?.onSaveInstanceState()
            lastAppliedSpan = span
            lastAppliedIsList = isList

            videoAdapter.setListView(isList)
            val gridLayoutManager = GridLayoutManager(context, span)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (videoAdapter.getItemViewType(position) == VideoGridAdapter.TYPE_HEADER) {
                        gridLayoutManager.spanCount
                    } else {
                        1
                    }
                }
            }
            binding.rvVideos.layoutManager = gridLayoutManager
            if (savedState != null) {
                binding.rvVideos.layoutManager?.onRestoreInstanceState(savedState)
            }
            videoAdapter.notifyDataSetChanged()
        }
    }

    fun updateGridColumns(newSpanCount: Int) {
        val span = newSpanCount.coerceIn(2, 7)
        if (currentSpanCount == span && lastAppliedIsList == false) return
        currentSpanCount = span
        context?.let {
            val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(it)
            prefs.gridColumns = span
            prefs.photosGridColumns = span
            prefs.videosGridColumns = span
            prefs.viewType = com.developer.manali.galleryapp.data.AppPreferences.VIEW_TYPE_GRID
            prefs.photosViewType = com.developer.manali.galleryapp.data.AppPreferences.VIEW_TYPE_GRID
            prefs.videosViewType = com.developer.manali.galleryapp.data.AppPreferences.VIEW_TYPE_GRID
        }
        if (_binding != null && ::videoAdapter.isInitialized) {
            lastAppliedSpan = span
            lastAppliedIsList = false
            videoAdapter.setListView(false)

            val transition = androidx.transition.TransitionSet().apply {
                ordering = androidx.transition.TransitionSet.ORDERING_TOGETHER
                addTransition(androidx.transition.ChangeBounds())
                duration = 250
            }
            androidx.transition.TransitionManager.beginDelayedTransition(binding.rvVideos, transition)

            val existingManager = binding.rvVideos.layoutManager as? GridLayoutManager
            if (existingManager != null) {
                existingManager.spanCount = span
            } else {
                val gridLayoutManager = GridLayoutManager(requireContext(), span)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (videoAdapter.getItemViewType(position) == VideoGridAdapter.TYPE_HEADER) {
                            gridLayoutManager.spanCount
                        } else {
                            1
                        }
                    }
                }
                binding.rvVideos.layoutManager = gridLayoutManager
            }
            videoAdapter.notifyDataSetChanged()
        }
        context?.sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.GRID_COLUMNS_CHANGED").apply {
            setPackage(context?.packageName)
            putExtra("span_count", span)
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshVideos.setColorSchemeResources(
            com.developer.manali.galleryapp.R.color.lumina_primary
        )
        binding.swipeRefreshVideos.setOnRefreshListener {
            loadVideos()
        }
    }

    fun refreshData() {
        if (_binding != null) {
            applyViewType()
            loadVideos()
        }
    }

    private val allVideoItems = ArrayList<MediaItem>()

    private fun loadVideos() {
        val safeContext = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding != null && videoAdapter.itemCount == 0 && !com.developer.manali.galleryapp.data.MediaRepository.hasCachedVideos()) {
                binding.progressVideos.visibility = View.VISIBLE
            }
            val internalStorageText = getString(R.string.internal_storage)
            val (sortedVideos, items) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val rawVideosList = mediaRepository.getVideos(safeContext)
                val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(safeContext)
                val lockedAlbums = appPrefs.getLockedAlbumIds()
                val lockedMedia = appPrefs.getLockedMediaIds()
                val rawVideos = rawVideosList.filter { 
                    !lockedAlbums.contains(it.bucketId) && !lockedMedia.contains(it.id.toString())
                }

                val sortBy = appPrefs.videosSortBy
                val sorted = when (sortBy) {
                    com.developer.manali.galleryapp.data.AppPreferences.SORT_OLDEST -> rawVideos.sortedBy { it.dateAdded }
                    com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_ASC -> rawVideos.sortedWith(
                        compareBy<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.bucketName.ifEmpty { internalStorageText } }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    )
                    com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_DESC -> rawVideos.sortedWith(
                        compareByDescending<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.bucketName.ifEmpty { internalStorageText } }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    )
                    else -> rawVideos.sortedByDescending { it.dateAdded }
                }

                val listItems = mutableListOf<VideoListItem>()
                var currentHeader = ""

                for (video in sorted) {
                    val headerTitle = when (sortBy) {
                        com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_ASC,
                        com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_DESC -> {
                            video.bucketName.ifEmpty { internalStorageText }
                        }
                        else -> video.dateHeader
                    }

                    if (headerTitle != currentHeader) {
                        currentHeader = headerTitle
                        listItems.add(VideoListItem.Header(currentHeader))
                    }
                    listItems.add(VideoListItem.Video(video))
                }
                Pair(sorted, listItems)
            }

            allVideoItems.clear()
            allVideoItems.addAll(sortedVideos)

            if (_binding != null) {
                binding.progressVideos.visibility = View.GONE
                binding.swipeRefreshVideos.isRefreshing = false

                if (items.isEmpty()) {
                    binding.layoutEmptyVideos.visibility = View.VISIBLE
                    binding.rvVideos.visibility = View.GONE
                } else {
                    binding.layoutEmptyVideos.visibility = View.GONE
                    binding.rvVideos.visibility = View.VISIBLE
                    videoAdapter.submitList(items)
                }
            }
        }
    }

    private fun playVideo(video: MediaItem) {
        val position = allVideoItems.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        com.developer.manali.galleryapp.data.MediaDataHolder.videoList = allVideoItems
        val intent = Intent(requireContext(), com.developer.manali.galleryapp.VideoDetailActivity::class.java).apply {
            putExtra("current_position", position)
            putExtra("video_item", video)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun loadMedia() {
        TODO("Not yet implemented")
    }

    companion object {
        fun newInstance() = VideosFragment()
    }
}
