package com.imagefolders.photoorganizer.mediagallery.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.imagefolders.photoorganizer.mediagallery.MediaDetailActivity
import com.imagefolders.photoorganizer.mediagallery.R
import com.imagefolders.photoorganizer.mediagallery.data.MediaItem
import com.imagefolders.photoorganizer.mediagallery.data.MediaRepository
import com.imagefolders.photoorganizer.mediagallery.data.PhotoListItem
import com.imagefolders.photoorganizer.mediagallery.databinding.FragmentPhotosBinding
import com.imagefolders.photoorganizer.mediagallery.ui.adapter.PhotoGridAdapter
import kotlinx.coroutines.launch

class PhotosFragment : Fragment() {

    private var _binding: FragmentPhotosBinding? = null
    private val binding get() = _binding!!

    private val mediaRepository = MediaRepository()
    private lateinit var photoAdapter: PhotoGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appPrefs = com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.getInstance(requireContext())
        currentSpanCount = if (appPrefs.isListView) 1 else appPrefs.photosGridColumns
        setupRecyclerView()
        setupSwipeRefresh()
        loadPhotos()
    }

    private val mediaUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.action
            if (action == "com.imagefolders.photoorganizer.mediagallery.MEDIA_UPDATED" ||
                action == "com.imagefolders.photoorganizer.mediagallery.ALBUMS_UPDATED") {
                refreshData()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val filter = android.content.IntentFilter().apply {
                addAction("com.imagefolders.photoorganizer.mediagallery.MEDIA_UPDATED")
                addAction("com.imagefolders.photoorganizer.mediagallery.ALBUMS_UPDATED")
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(mediaUpdateReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                requireContext().registerReceiver(mediaUpdateReceiver, filter)
            }
        } catch (_: Exception) {}

        context?.let {
            val prefs = com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.getInstance(it)
            applyViewType(prefs.photosViewType)
            if (!prefs.isPhotosListView && currentSpanCount != prefs.gridColumns) {
                updateGridColumns(prefs.gridColumns)
            }
        }
        loadPhotos()
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
                return if (photoAdapter.getItemViewType(position) == PhotoGridAdapter.TYPE_HEADER) {
                    gridLayoutManager.spanCount
                } else {
                    1
                }
            }
        }

        photoAdapter = PhotoGridAdapter(
            onItemClick = { mediaItem ->
                if (com.imagefolders.photoorganizer.mediagallery.AdCounter.isProcessingAd) return@PhotoGridAdapter
                val clickAction = {
                    openMediaDetail(mediaItem)
                }
                if (com.imagefolders.photoorganizer.mediagallery.AdCounter.shouldShowAd()) {
                    com.imagefolders.photoorganizer.mediagallery.GoogleInterstitialAdsCall.loadAndShowInterstitial(
                        requireActivity(),
                        object : com.imagefolders.photoorganizer.mediagallery.InterstitialAdCallback {
                            override fun onAdClose() {
                                com.imagefolders.photoorganizer.mediagallery.AdCounter.onAdFinished()
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
        photoAdapter.setListView(com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.getInstance(requireContext()).isPhotosListView)

        photoAdapter.setOnSelectionChangedListener { count, items ->
            (activity as? com.imagefolders.photoorganizer.mediagallery.MainActivity)?.onSelectionUpdated(count, items)
        }
        photoAdapter.setOnItemLongClickListener { item ->
            (activity as? com.imagefolders.photoorganizer.mediagallery.MainActivity)?.onItemLongPressed(item)
        }

        binding.rvPhotos.setHasFixedSize(true)
        binding.rvPhotos.setItemViewCacheSize(25)
        binding.rvPhotos.layoutManager = gridLayoutManager
        binding.rvPhotos.adapter = photoAdapter

        com.imagefolders.photoorganizer.mediagallery.util.PinchZoomGridHelper(
            context = requireContext(),
            getSpanCount = { currentSpanCount },
            onSpanCountChanged = { newSpan ->
                updateGridColumns(newSpan)
                (activity as? com.imagefolders.photoorganizer.mediagallery.MainActivity)?.onGridColumnsChanged(newSpan)
            }
        ).attachToRecyclerView(binding.rvPhotos)
    }

    fun enterSelectionMode(initialItem: MediaItem? = null) {
        if (::photoAdapter.isInitialized) {
            photoAdapter.enterSelectionMode(initialItem)
        }
    }

    fun exitSelectionMode() {
        if (::photoAdapter.isInitialized) {
            photoAdapter.exitSelectionMode()
        }
    }

    fun selectAll() {
        if (::photoAdapter.isInitialized) {
            photoAdapter.selectAll()
            val selected = photoAdapter.getSelectedItems()
            (activity as? com.imagefolders.photoorganizer.mediagallery.MainActivity)?.onSelectionUpdated(selected.size, selected)
        }
    }

    fun deselectAll() {
        if (::photoAdapter.isInitialized) {
            photoAdapter.deselectAll()
            val selected = photoAdapter.getSelectedItems()
            (activity as? com.imagefolders.photoorganizer.mediagallery.MainActivity)?.onSelectionUpdated(selected.size, selected)
        }
    }

    fun getSelectedItems(): List<MediaItem> {
        return if (::photoAdapter.isInitialized) photoAdapter.getSelectedItems() else emptyList()
    }

    fun getAllMediaItems(): List<MediaItem> {
        return if (::photoAdapter.isInitialized) photoAdapter.getAllMediaItems() else emptyList()
    }

    fun removeItems(itemsToRemove: List<MediaItem>) {
        val idsToRemove = itemsToRemove.map { it.id }.toSet()
        allPhotoItems.removeAll { idsToRemove.contains(it.id) }
        if (::photoAdapter.isInitialized) {
            photoAdapter.removeItems(itemsToRemove)
        }
        (activity as? com.imagefolders.photoorganizer.mediagallery.MainActivity)?.onPhotosDataLoaded(allPhotoItems.size, allPhotoItems.sumOf { it.size })
    }

    fun isSelectionMode(): Boolean {
        return if (::photoAdapter.isInitialized) photoAdapter.isSelectionMode else false
    }

    private var lastAppliedSpan: Int = -1
    private var lastAppliedIsList: Boolean? = null

    fun applyViewType(viewType: String? = null) {
        val context = context ?: return
        val prefs = com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.getInstance(context)
        val effectiveType = viewType ?: prefs.photosViewType
        val isList = effectiveType == com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.VIEW_TYPE_LIST
        val span = if (isList) 1 else prefs.photosGridColumns
        currentSpanCount = span
        if (_binding != null && ::photoAdapter.isInitialized) {
            val savedState = binding.rvPhotos.layoutManager?.onSaveInstanceState()
            lastAppliedSpan = span
            lastAppliedIsList = isList

            photoAdapter.setListView(isList)
            val gridLayoutManager = GridLayoutManager(context, span)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (photoAdapter.getItemViewType(position) == PhotoGridAdapter.TYPE_HEADER) {
                        gridLayoutManager.spanCount
                    } else {
                        1
                    }
                }
            }
            binding.rvPhotos.layoutManager = gridLayoutManager
            if (savedState != null) {
                binding.rvPhotos.layoutManager?.onRestoreInstanceState(savedState)
            }
            photoAdapter.notifyDataSetChanged()
        }
    }

    fun updateGridColumns(newSpanCount: Int) {
        val span = newSpanCount.coerceIn(2, 7)
        if (currentSpanCount == span && lastAppliedIsList == false) return
        currentSpanCount = span
        context?.let {
            val prefs = com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.getInstance(it)
            prefs.gridColumns = span
            prefs.photosGridColumns = span
            prefs.videosGridColumns = span
            prefs.viewType = com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.VIEW_TYPE_GRID
            prefs.photosViewType = com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.VIEW_TYPE_GRID
            prefs.videosViewType = com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.VIEW_TYPE_GRID
        }
        if (_binding != null && ::photoAdapter.isInitialized) {
            lastAppliedSpan = span
            lastAppliedIsList = false
            photoAdapter.setListView(false)

            val transition = androidx.transition.TransitionSet().apply {
                ordering = androidx.transition.TransitionSet.ORDERING_TOGETHER
                addTransition(androidx.transition.ChangeBounds())
                duration = 250
            }
            androidx.transition.TransitionManager.beginDelayedTransition(binding.rvPhotos, transition)

            val existingManager = binding.rvPhotos.layoutManager as? GridLayoutManager
            if (existingManager != null) {
                existingManager.spanCount = span
            } else {
                val gridLayoutManager = GridLayoutManager(requireContext(), span)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (photoAdapter.getItemViewType(position) == PhotoGridAdapter.TYPE_HEADER) {
                            gridLayoutManager.spanCount
                        } else {
                            1
                        }
                    }
                }
                binding.rvPhotos.layoutManager = gridLayoutManager
            }
            photoAdapter.notifyDataSetChanged()
        }
        context?.sendBroadcast(android.content.Intent("com.imagefolders.photoorganizer.mediagallery.GRID_COLUMNS_CHANGED").apply {
            setPackage(context?.packageName)
            putExtra("span_count", span)
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshPhotos.setColorSchemeResources(
            com.imagefolders.photoorganizer.mediagallery.R.color.lumina_primary
        )
        binding.swipeRefreshPhotos.setOnRefreshListener {
            loadPhotos()
        }
    }

    fun refreshData() {
        if (_binding != null) {
            applyViewType()
            loadPhotos()
        }
    }

    private val allPhotoItems = ArrayList<MediaItem>()

    private fun loadPhotos() {
        val safeContext = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding != null && photoAdapter.itemCount == 0 && !com.imagefolders.photoorganizer.mediagallery.data.MediaRepository.hasCachedPhotos()) {
                binding.progressPhotos.visibility = View.VISIBLE
            }
            val internalStorageText = getString(R.string.internal_storage)
            val (sortedPhotos, groupedItems) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val rawPhotosList = mediaRepository.getPhotos(safeContext)
                val appPrefs = com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.getInstance(safeContext)
                val lockedAlbums = appPrefs.getLockedAlbumIds()
                val lockedMedia = appPrefs.getLockedMediaIds()
                val rawPhotos = rawPhotosList.filter { 
                    !lockedAlbums.contains(it.bucketId) && !lockedAlbums.contains(it.bucketName) && !lockedMedia.contains(it.id.toString())
                }

                val sortBy = appPrefs.photosSortBy
                val sorted = when (sortBy) {
                    com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.SORT_OLDEST -> rawPhotos.sortedBy { it.dateAdded }
                    com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.SORT_NAME_ASC -> rawPhotos.sortedWith(
                        compareBy<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.bucketName.ifEmpty { internalStorageText } }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    )
                    com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.SORT_NAME_DESC -> rawPhotos.sortedWith(
                        compareByDescending<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.bucketName.ifEmpty { internalStorageText } }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    )
                    else -> rawPhotos.sortedByDescending { it.dateAdded }
                }

                val items = mutableListOf<PhotoListItem>()
                var currentHeader = ""

                for (photo in sorted) {
                    val headerTitle = when (sortBy) {
                        com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.SORT_NAME_ASC,
                        com.imagefolders.photoorganizer.mediagallery.data.AppPreferences.SORT_NAME_DESC -> {
                            photo.bucketName.ifEmpty { internalStorageText }
                        }
                        else -> photo.dateHeader
                    }

                    if (headerTitle != currentHeader) {
                        currentHeader = headerTitle
                        items.add(PhotoListItem.Header(currentHeader))
                    }
                    items.add(PhotoListItem.Media(photo))
                }
                Pair(sorted, items)
            }

            allPhotoItems.clear()
            allPhotoItems.addAll(sortedPhotos)
            (activity as? com.imagefolders.photoorganizer.mediagallery.MainActivity)?.onPhotosDataLoaded(allPhotoItems.size, allPhotoItems.sumOf { it.size })

            if (_binding != null) {
                binding.progressPhotos.visibility = View.GONE
                binding.swipeRefreshPhotos.isRefreshing = false

                if (groupedItems.isEmpty()) {
                    binding.layoutEmptyPhotos.visibility = View.VISIBLE
                    binding.rvPhotos.visibility = View.GONE
                } else {
                    binding.layoutEmptyPhotos.visibility = View.GONE
                    binding.rvPhotos.visibility = View.VISIBLE
                    photoAdapter.submitList(groupedItems)
                }
            }
        }
    }

    private fun openMediaDetail(mediaItem: MediaItem) {
        val position = allPhotoItems.indexOfFirst { it.id == mediaItem.id }.coerceAtLeast(0)
        com.imagefolders.photoorganizer.mediagallery.data.MediaDataHolder.mediaList = allPhotoItems
        val intent = Intent(requireContext(), MediaDetailActivity::class.java).apply {
            putExtra("current_position", position)
            putExtra("media_item", mediaItem)
            putExtra("media_uri", mediaItem.uri.toString())
            putExtra("media_name", mediaItem.displayName)
            putExtra("is_video", mediaItem.isVideo)
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
        fun newInstance() = PhotosFragment()
    }
}
