package com.developer.manali.galleryapp.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.developer.manali.galleryapp.MediaDetailActivity
import com.developer.manali.galleryapp.R
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.data.PhotoListItem
import com.developer.manali.galleryapp.databinding.FragmentPhotosBinding
import com.developer.manali.galleryapp.ui.adapter.PhotoGridAdapter
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
        val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(requireContext())
        currentSpanCount = if (appPrefs.isListView) 1 else appPrefs.photosGridColumns
        setupRecyclerView()
        setupSwipeRefresh()
        loadPhotos()
    }

    override fun onResume() {
        super.onResume()
        context?.let {
            val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(it)
            if (!prefs.isPhotosListView && currentSpanCount != prefs.gridColumns) {
                updateGridColumns(prefs.gridColumns)
            } else {
                applyViewType()
            }
        }
        if (::photoAdapter.isInitialized) {
            photoAdapter.notifyDataSetChanged()
        }
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
                val clickAction = {
                    openMediaDetail(mediaItem)
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
        photoAdapter.setListView(com.developer.manali.galleryapp.data.AppPreferences.getInstance(requireContext()).isPhotosListView)

        photoAdapter.setOnSelectionChangedListener { count, items ->
            (activity as? com.developer.manali.galleryapp.MainActivity)?.onSelectionUpdated(count, items)
        }
        photoAdapter.setOnItemLongClickListener { item ->
            (activity as? com.developer.manali.galleryapp.MainActivity)?.onItemLongPressed(item)
        }

        binding.rvPhotos.layoutManager = gridLayoutManager
        binding.rvPhotos.adapter = photoAdapter

        com.developer.manali.galleryapp.util.PinchZoomGridHelper(
            context = requireContext(),
            getSpanCount = { currentSpanCount },
            onSpanCountChanged = { newSpan ->
                updateGridColumns(newSpan)
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
            (activity as? com.developer.manali.galleryapp.MainActivity)?.onSelectionUpdated(selected.size, selected)
        }
    }

    fun deselectAll() {
        if (::photoAdapter.isInitialized) {
            photoAdapter.deselectAll()
            val selected = photoAdapter.getSelectedItems()
            (activity as? com.developer.manali.galleryapp.MainActivity)?.onSelectionUpdated(selected.size, selected)
        }
    }

    fun getSelectedItems(): List<MediaItem> {
        return if (::photoAdapter.isInitialized) photoAdapter.getSelectedItems() else emptyList()
    }

    fun getAllMediaItems(): List<MediaItem> {
        return if (::photoAdapter.isInitialized) photoAdapter.getAllMediaItems() else emptyList()
    }

    fun removeItems(itemsToRemove: List<MediaItem>) {
        if (::photoAdapter.isInitialized) {
            photoAdapter.removeItems(itemsToRemove)
        }
    }

    fun isSelectionMode(): Boolean {
        return if (::photoAdapter.isInitialized) photoAdapter.isSelectionMode else false
    }

    private var lastAppliedSpan: Int = -1
    private var lastAppliedIsList: Boolean? = null

    fun applyViewType(viewType: String? = null) {
        val context = context ?: return
        val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(context)
        val effectiveType = viewType ?: prefs.photosViewType
        val isList = effectiveType == com.developer.manali.galleryapp.data.AppPreferences.VIEW_TYPE_LIST
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
        if (currentSpanCount == span) return
        currentSpanCount = span
        var oldSpan = -1
        context?.let {
            val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(it)
            oldSpan = prefs.gridColumns
            prefs.gridColumns = span
            prefs.photosGridColumns = span
            prefs.videosGridColumns = span
            prefs.photosViewType = com.developer.manali.galleryapp.data.AppPreferences.VIEW_TYPE_GRID
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
        }
        if (oldSpan != span) {
            context?.sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.GRID_COLUMNS_CHANGED").apply {
                putExtra("span_count", span)
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshPhotos.setColorSchemeResources(
            com.developer.manali.galleryapp.R.color.lumina_primary
        )
        binding.swipeRefreshPhotos.setOnRefreshListener {
            loadPhotos()
        }
    }

    fun refreshData() {
        if (_binding != null) {
            loadPhotos()
        }
    }

    private val allPhotoItems = ArrayList<MediaItem>()

    private fun loadPhotos() {
        val safeContext = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding != null && photoAdapter.itemCount == 0 && !com.developer.manali.galleryapp.data.MediaRepository.hasCachedPhotos()) {
                binding.progressPhotos.visibility = View.VISIBLE
            }
            val internalStorageText = getString(R.string.internal_storage)
            val (sortedPhotos, groupedItems) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val rawPhotosList = mediaRepository.getPhotos(safeContext)
                val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(safeContext)
                val lockedAlbums = appPrefs.getLockedAlbumIds()
                val lockedMedia = appPrefs.getLockedMediaIds()
                val rawPhotos = rawPhotosList.filter { 
                    !lockedAlbums.contains(it.bucketId) && !lockedMedia.contains(it.id.toString())
                }

                val sortBy = appPrefs.photosSortBy
                val sorted = when (sortBy) {
                    com.developer.manali.galleryapp.data.AppPreferences.SORT_OLDEST -> rawPhotos.sortedBy { it.dateAdded }
                    com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_ASC -> rawPhotos.sortedWith(
                        compareBy<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.bucketName.ifEmpty { internalStorageText } }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    )
                    com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_DESC -> rawPhotos.sortedWith(
                        compareByDescending<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.bucketName.ifEmpty { internalStorageText } }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    )
                    else -> rawPhotos.sortedByDescending { it.dateAdded }
                }

                val items = mutableListOf<PhotoListItem>()
                var currentHeader = ""

                for (photo in sorted) {
                    val headerTitle = when (sortBy) {
                        com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_ASC,
                        com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_DESC -> {
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
        com.developer.manali.galleryapp.data.MediaDataHolder.mediaList = allPhotoItems
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
