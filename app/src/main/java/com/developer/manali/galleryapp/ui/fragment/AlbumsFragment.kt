package com.developer.manali.galleryapp.ui.fragment

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.developer.manali.galleryapp.R
import com.developer.manali.galleryapp.data.AlbumItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.databinding.FragmentAlbumsBinding
import com.developer.manali.galleryapp.ui.adapter.AlbumAdapter
import kotlinx.coroutines.launch
import java.io.File

class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!

    private val mediaRepository = MediaRepository()
    private lateinit var albumAdapter: AlbumAdapter

    private val allAlbums = mutableListOf<AlbumItem>()
    private var currentSearchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearch()
        loadAlbums()
    }

    private val albumsUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.developer.manali.galleryapp.ALBUMS_UPDATED") {
                refreshData()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(albumsUpdateReceiver, android.content.IntentFilter("com.developer.manali.galleryapp.ALBUMS_UPDATED"), android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(albumsUpdateReceiver, android.content.IntentFilter("com.developer.manali.galleryapp.ALBUMS_UPDATED"))
        }
        applyViewType()
        loadAlbums()
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(albumsUpdateReceiver)
    }

    private fun setupRecyclerView() {
        val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(requireContext())
        val isList = prefs.isAlbumsListView
        val span = if (isList) 1 else prefs.albumsGridColumns
        lastAppliedSpan = span
        lastAppliedIsList = isList

        albumAdapter = AlbumAdapter(
            onAlbumClick = { album ->
                if (album.isHidden) {
                    Toast.makeText(requireContext(),
                        getString(R.string.hidden_album_is_locked), Toast.LENGTH_SHORT).show()
                } else {
                    val clickAction = {
                        val intent = android.content.Intent(requireContext(), com.developer.manali.galleryapp.AlbumDetailActivity::class.java).apply {
                            putExtra("bucket_id", album.bucketId)
                            putExtra("bucket_name", album.bucketName)
                            putExtra("item_count", album.itemCount)
                        }
                        startActivity(intent)
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
                }
            },
            onAlbumLongClick = { album ->
                val activity = activity as? com.developer.manali.galleryapp.MainActivity
                if (activity != null) {
                    if (!activity.isSelectionMode) {
                        activity.enterAlbumSelectionMode(album)
                    } else {
                        if (::albumAdapter.isInitialized) {
                            activity.onAlbumSelectionUpdated(albumAdapter.getSelectedItems().size, albumAdapter.getSelectedItems())
                        }
                    }
                }
            }
        )
        albumAdapter.setListView(isList)

        binding.rvAlbums.setHasFixedSize(true)
        binding.rvAlbums.setItemViewCacheSize(25)
        binding.rvAlbums.layoutManager = GridLayoutManager(requireContext(), span)
        binding.rvAlbums.adapter = albumAdapter

        com.developer.manali.galleryapp.util.PinchZoomGridHelper(
            context = requireContext(),
            minSpan = 2,
            maxSpan = 4,
            getSpanCount = { if (lastAppliedSpan > 0) lastAppliedSpan else span },
            onSpanCountChanged = { newSpan ->
                updateGridColumns(newSpan)
            }
        ).attachToRecyclerView(binding.rvAlbums)
    }

    private var lastAppliedSpan: Int = -1
    private var lastAppliedIsList: Boolean? = null

    fun applyViewType(viewType: String? = null) {
        val context = context ?: return
        val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(context)
        val effectiveType = viewType ?: prefs.albumsViewType
        val isList = effectiveType == com.developer.manali.galleryapp.data.AppPreferences.VIEW_TYPE_LIST
        val span = if (isList) 1 else prefs.albumsGridColumns
        if (_binding != null && ::albumAdapter.isInitialized) {
            val savedState = binding.rvAlbums.layoutManager?.onSaveInstanceState()
            lastAppliedSpan = span
            lastAppliedIsList = isList

            albumAdapter.setListView(isList)
            binding.rvAlbums.layoutManager = GridLayoutManager(context, span)
            if (savedState != null) {
                binding.rvAlbums.layoutManager?.onRestoreInstanceState(savedState)
            }
            albumAdapter.notifyDataSetChanged()
        }
    }

    fun updateGridColumns(newSpanCount: Int) {
        val span = newSpanCount.coerceIn(2, 5)
        context?.let {
            val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(it)
            prefs.albumsGridColumns = span
            prefs.albumsViewType = com.developer.manali.galleryapp.data.AppPreferences.VIEW_TYPE_GRID
        }
        if (_binding != null && ::albumAdapter.isInitialized) {
            lastAppliedSpan = span
            lastAppliedIsList = false
            albumAdapter.setListView(false)

            val transition = androidx.transition.TransitionSet().apply {
                ordering = androidx.transition.TransitionSet.ORDERING_TOGETHER
                addTransition(androidx.transition.ChangeBounds())
                duration = 250
            }
            androidx.transition.TransitionManager.beginDelayedTransition(binding.rvAlbums, transition)

            val existingManager = binding.rvAlbums.layoutManager as? GridLayoutManager
            if (existingManager != null) {
                existingManager.spanCount = span
            } else {
                binding.rvAlbums.layoutManager = GridLayoutManager(requireContext(), span)
                albumAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshAlbums.setColorSchemeResources(
            R.color.lumina_primary
        )
        binding.swipeRefreshAlbums.setOnRefreshListener {
            loadAlbums()
        }
    }

    private fun setupSearch() {
        binding.etSearchAlbums.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                binding.btnClearAlbumSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterAlbums(currentSearchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearAlbumSearch.setOnClickListener {
            binding.etSearchAlbums.setText("")
        }
    }

    private fun filterAlbums(query: String) {
        val filteredList = if (query.isEmpty()) {
            allAlbums
        } else {
            allAlbums.filter { it.bucketName.contains(query, ignoreCase = true) }
        }

        if (_binding != null) {
            if (filteredList.isEmpty()) {
                binding.layoutEmptyAlbums.visibility = View.VISIBLE
                binding.rvAlbums.visibility = View.GONE
            } else {
                binding.layoutEmptyAlbums.visibility = View.GONE
                binding.rvAlbums.visibility = View.VISIBLE
                albumAdapter.submitList(ArrayList(filteredList))
            }
        }
    }


    fun isAlbumPresent(name: String): Boolean {
        val trimmed = name.trim()
        return allAlbums.any { it.bucketName.equals(trimmed, ignoreCase = true) }
    }

    fun showCreateAlbumDialog() {
        val dialog = Dialog(requireContext())
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
                val created = createNewAlbum(name)
                if (created) {
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.please_enter_album_name), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
        etName.requestFocus()
    }


    fun createNewAlbum(name: String, providedContext: android.content.Context? = null): Boolean {
        val safeContext = providedContext ?: context ?: return false
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false

        if (isAlbumPresent(trimmed) || com.developer.manali.galleryapp.data.MediaRepository.albumExists(safeContext, trimmed)) {
            Toast.makeText(safeContext, safeContext.getString(R.string.album_already_exists), Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val newDir = File(baseDir, trimmed)
            if (!newDir.exists()) {
                newDir.mkdirs()
            }
            com.developer.manali.galleryapp.data.AppPreferences.getInstance(safeContext).addCreatedAlbum(trimmed)
            com.developer.manali.galleryapp.data.MediaRepository.clearCache()
        } catch (_: Exception) {}

        val newAlbum = AlbumItem(
            bucketId = trimmed,
            bucketName = trimmed,
            coverUri = null,
            itemCount = 0
        )

        allAlbums.add(0, newAlbum)

        filterAlbums(currentSearchQuery)
        if (_binding != null) {
            binding.rvAlbums.smoothScrollToPosition(0)
        }

        safeContext.sendBroadcast(android.content.Intent("com.developer.manali.galleryapp.ALBUMS_UPDATED"))
        Toast.makeText(safeContext, safeContext.getString(R.string.album_created, trimmed), Toast.LENGTH_SHORT).show()
        return true
    }

    fun applySort(sortKey: String? = null) {
        val context = context ?: return
        val prefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(context)
        val effectiveSort = sortKey ?: prefs.albumsSortBy

        val sortedList = when (effectiveSort) {
            com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_ASC ->
                allAlbums.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.bucketName })
            com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_DESC ->
                allAlbums.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.bucketName })
            com.developer.manali.galleryapp.data.AppPreferences.SORT_OLDEST ->
                allAlbums.sortedWith(compareBy<AlbumItem> { it.itemCount }.thenBy { it.bucketName })
            else ->
                allAlbums.sortedWith(compareByDescending<AlbumItem> { it.itemCount }.thenBy { it.bucketName })
        }

        allAlbums.clear()
        allAlbums.addAll(sortedList)
        if (_binding != null) {
            filterAlbums(currentSearchQuery)
        }
    }

    fun refreshData() {
        if (_binding != null) {
            loadAlbums()
        }
    }

    private fun loadAlbums() {
        if (!isAdded) return
        val safeContext = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding == null) return@launch
            if (albumAdapter.itemCount == 0 && !mediaRepository.hasCachedAlbums()) {
                binding.progressAlbums.visibility = View.VISIBLE
            }
            val sortedAlbums = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val rawAlbums = mediaRepository.getAlbums(safeContext)
                val appPrefs = com.developer.manali.galleryapp.data.AppPreferences.getInstance(safeContext)
                val lockedIds = appPrefs.getLockedAlbumIds()
                val filteredRawAlbums = rawAlbums.filter { !lockedIds.contains(it.bucketId) }

                val sortBy = appPrefs.albumsSortBy
                when (sortBy) {
                    com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_ASC ->
                        filteredRawAlbums.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.bucketName })
                    com.developer.manali.galleryapp.data.AppPreferences.SORT_NAME_DESC ->
                        filteredRawAlbums.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.bucketName })
                    com.developer.manali.galleryapp.data.AppPreferences.SORT_OLDEST ->
                        filteredRawAlbums.sortedWith(compareBy<AlbumItem> { it.itemCount }.thenBy { it.bucketName })
                    else ->
                        filteredRawAlbums.sortedWith(compareByDescending<AlbumItem> { it.itemCount }.thenBy { it.bucketName })
                }
            }

            allAlbums.clear()
            allAlbums.addAll(sortedAlbums)

            if (_binding != null) {
                binding.progressAlbums.visibility = View.GONE
                binding.swipeRefreshAlbums.isRefreshing = false
                filterAlbums(currentSearchQuery)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun enterSelectionMode(initialItem: AlbumItem? = null) {
        if (::albumAdapter.isInitialized) {
            albumAdapter.enterSelectionMode(initialItem)
            val activity = activity as? com.developer.manali.galleryapp.MainActivity
            activity?.onAlbumSelectionUpdated(albumAdapter.getSelectedItems().size, albumAdapter.getSelectedItems())
        }
    }

    fun exitSelectionMode() {
        if (::albumAdapter.isInitialized) {
            albumAdapter.exitSelectionMode()
        }
    }

    fun removeAlbums(albumsToRemove: List<AlbumItem>) {
        val ids = albumsToRemove.map { it.bucketId }.toSet()
        val names = albumsToRemove.map { it.bucketName.lowercase() }.toSet()
        allAlbums.removeAll { ids.contains(it.bucketId) || names.contains(it.bucketName.lowercase()) }
        if (::albumAdapter.isInitialized) {
            albumAdapter.removeAlbums(albumsToRemove)
        }
        filterAlbums(currentSearchQuery)
    }

    fun getSelectedItems(): List<AlbumItem> {
        return if (::albumAdapter.isInitialized) albumAdapter.getSelectedItems() else emptyList()
    }

    fun selectAll() {
        if (::albumAdapter.isInitialized) {
            albumAdapter.selectAll()
            val activity = activity as? com.developer.manali.galleryapp.MainActivity
            activity?.onAlbumSelectionUpdated(albumAdapter.getSelectedItems().size, albumAdapter.getSelectedItems())
        }
    }

    fun deselectAll() {
        if (::albumAdapter.isInitialized) {
            albumAdapter.deselectAll()
            val activity = activity as? com.developer.manali.galleryapp.MainActivity
            activity?.onAlbumSelectionUpdated(albumAdapter.getSelectedItems().size, albumAdapter.getSelectedItems())
        }
    }

    fun isSelectionMode(): Boolean {
        return if (::albumAdapter.isInitialized) albumAdapter.isSelectionMode else false
    }
    
    fun getAllMediaItems(): List<AlbumItem> {
        return allAlbums
    }

    companion object {
        fun newInstance() = AlbumsFragment()
    }
}
