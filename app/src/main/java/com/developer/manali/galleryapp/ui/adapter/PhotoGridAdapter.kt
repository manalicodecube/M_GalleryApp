package com.developer.manali.galleryapp.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.data.PhotoListItem
import com.developer.manali.galleryapp.databinding.ItemDateHeaderBinding
import com.developer.manali.galleryapp.databinding.ItemPhotoGridBinding
import com.developer.manali.galleryapp.databinding.ItemPhotoListBinding

class PhotoGridAdapter(
    private val onItemClick: (MediaItem) -> Unit,
    private val onSelectClick: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM_GRID = 1
        const val TYPE_ITEM_LIST = 2
    }

    private val items = mutableListOf<PhotoListItem>()
    private var isListView: Boolean = false
    var isSelectionMode: Boolean = false
    val selectedIds = mutableSetOf<Long>()
    private var onSelectionChanged: ((Int, List<MediaItem>) -> Unit)? = null
    private var onItemLongClick: ((MediaItem) -> Unit)? = null

    fun setOnSelectionChangedListener(listener: (Int, List<MediaItem>) -> Unit) {
        this.onSelectionChanged = listener
    }

    fun setOnItemLongClickListener(listener: (MediaItem) -> Unit) {
        this.onItemLongClick = listener
    }

    fun getAllMediaItems(): List<MediaItem> {
        return items.filterIsInstance<PhotoListItem.Media>().map { it.item }
    }

    fun removeItems(itemsToRemove: List<MediaItem>) {
        val removeIds = itemsToRemove.map { it.id }.toSet()
        val filtered = items.filter { item ->
            when (item) {
                is PhotoListItem.Media -> !removeIds.contains(item.item.id)
                else -> true
            }
        }
        val cleaned = mutableListOf<PhotoListItem>()
        var currentHeader: PhotoListItem.Header? = null
        var headerHasMedia = false

        for (item in filtered) {
            if (item is PhotoListItem.Header) {
                if (currentHeader != null && headerHasMedia) {
                    cleaned.add(currentHeader)
                }
                currentHeader = item
                headerHasMedia = false
            } else {
                headerHasMedia = true
                if (currentHeader != null) {
                    cleaned.add(currentHeader)
                    currentHeader = null
                }
                cleaned.add(item)
            }
        }
        submitList(cleaned)
    }

    fun getSelectedItems(): List<MediaItem> {
        return getAllMediaItems().filter { selectedIds.contains(it.id) }
    }

    fun enterSelectionMode(initialItem: MediaItem? = null) {
        isSelectionMode = true
        if (initialItem != null) {
            selectedIds.add(initialItem.id)
        }
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun toggleSelection(item: MediaItem) {
        if (selectedIds.contains(item.id)) {
            selectedIds.remove(item.id)
        } else {
            selectedIds.add(item.id)
        }
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun selectAll() {
        val all = getAllMediaItems()
        selectedIds.clear()
        selectedIds.addAll(all.map { it.id })
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun deselectAll() {
        selectedIds.clear()
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun selectHeaderGroup(headerTitle: String) {
        var matching = false
        val groupItems = mutableListOf<MediaItem>()
        for (i in items) {
            if (i is PhotoListItem.Header) {
                matching = (i.title == headerTitle)
            } else if (i is PhotoListItem.Media && matching) {
                groupItems.add(i.item)
            }
        }
        if (groupItems.isNotEmpty()) {
            val allAlreadySelected = groupItems.all { selectedIds.contains(it.id) }
            if (allAlreadySelected) {
                groupItems.forEach { selectedIds.remove(it.id) }
            } else {
                groupItems.forEach { selectedIds.add(it.id) }
            }
            if (!isSelectionMode) isSelectionMode = true
            notifyDataSetChanged()
            notifySelectionUpdate()
        }
    }

    private fun notifySelectionUpdate() {
        val selected = getSelectedItems()
        onSelectionChanged?.invoke(selected.size, selected)
    }

    fun submitList(newItems: List<PhotoListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setListView(isList: Boolean) {
        if (isListView != isList) {
            isListView = isList
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PhotoListItem.Header -> TYPE_HEADER
            is PhotoListItem.Media -> if (isListView) TYPE_ITEM_LIST else TYPE_ITEM_GRID
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemDateHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            TYPE_ITEM_LIST -> {
                val binding = ItemPhotoListBinding.inflate(inflater, parent, false)
                PhotoListViewHolder(binding)
            }
            else -> {
                val binding = ItemPhotoGridBinding.inflate(inflater, parent, false)
                PhotoGridViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PhotoListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is PhotoListItem.Media -> {
                if (holder is PhotoListViewHolder) {
                    holder.bind(item.item)
                } else if (holder is PhotoGridViewHolder) {
                    holder.bind(item.item)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: PhotoListItem.Header) {
            binding.tvDateHeader.text = header.title

        }
    }

    inner class PhotoGridViewHolder(private val binding: ItemPhotoGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(mediaItem: MediaItem) {
            val isSelected = selectedIds.contains(mediaItem.id)

            val isFav = com.developer.manali.galleryapp.data.AppPreferences.getInstance(binding.root.context).isFavorite(mediaItem.id)
            binding.ivFavoriteBadge.visibility = if (isFav) android.view.View.VISIBLE else android.view.View.GONE

            if (mediaItem.isVideo) {
                binding.tvVideoDuration.visibility = android.view.View.VISIBLE
                binding.tvVideoDuration.text = MediaRepository.formatDuration(mediaItem.duration)
            } else {
                binding.tvVideoDuration.visibility = android.view.View.GONE
            }

            if (isSelectionMode) {
                binding.ivSelectCheck.visibility = android.view.View.VISIBLE
                binding.ivSelectCheck.setImageResource(
                    if (isSelected) com.developer.manali.galleryapp.R.drawable.ic_select_checked
                    else com.developer.manali.galleryapp.R.drawable.ic_select_unchecked
                )
            } else {
                binding.ivSelectCheck.visibility = android.view.View.GONE
            }

            Glide.with(binding.ivPhoto.context)
                .load(mediaItem.uri)
                .override(300, 300)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .into(binding.ivPhoto)

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(mediaItem)
                } else {
                    onItemClick(mediaItem)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode(mediaItem)
                    onItemLongClick?.invoke(mediaItem)
                    true
                } else {
                    toggleSelection(mediaItem)
                    true
                }
            }
        }
    }

    inner class PhotoListViewHolder(private val binding: ItemPhotoListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(mediaItem: MediaItem) {
            val isSelected = selectedIds.contains(mediaItem.id)

            val isFav = com.developer.manali.galleryapp.data.AppPreferences.getInstance(binding.root.context).isFavorite(mediaItem.id)
            binding.ivFavoriteBadge.visibility = if (isFav) android.view.View.VISIBLE else android.view.View.GONE

            binding.tvPhotoName.text = mediaItem.displayName
            val sizeStr = MediaRepository.formatFileSize(mediaItem.size)
            val durStr = if (mediaItem.isVideo) MediaRepository.formatDuration(mediaItem.duration) else ""
            val dateStr = MediaRepository.formatExactDate(mediaItem.dateAdded)
            val subDetails = if (durStr.isNotEmpty() && durStr != "0:00") "$sizeStr • $durStr" else sizeStr
            binding.tvPhotoDetails.text = if (dateStr.isNotEmpty()) "$subDetails • $dateStr" else subDetails

            if (isSelectionMode) {
                binding.ivChevron.visibility = android.view.View.GONE
                binding.ivSelectCheck.visibility = android.view.View.VISIBLE
                if (isSelected) {
                    binding.ivSelectCheck.setImageResource(com.developer.manali.galleryapp.R.drawable.ic_select_checked)
                    binding.ivSelectCheck.imageTintList = null
                } else {
                    val isNight = com.developer.manali.galleryapp.PreferencesUtility.getInstance(binding.root.context).isNightMode()
                    binding.ivSelectCheck.setImageResource(
                        if (!isNight) com.developer.manali.galleryapp.R.drawable.ic_select_unchecked_black
                        else com.developer.manali.galleryapp.R.drawable.ic_select_unchecked
                    )
                    binding.ivSelectCheck.imageTintList = null
                }
            } else {
                binding.ivChevron.visibility = android.view.View.VISIBLE
                binding.ivSelectCheck.visibility = android.view.View.GONE
            }

            Glide.with(binding.ivPhoto.context)
                .load(mediaItem.uri)
                .override(300, 300)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .into(binding.ivPhoto)

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(mediaItem)
                } else {
                    onItemClick(mediaItem)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode(mediaItem)
                    onItemLongClick?.invoke(mediaItem)
                    true
                } else {
                    toggleSelection(mediaItem)
                    true
                }
            }
        }
    }
}
