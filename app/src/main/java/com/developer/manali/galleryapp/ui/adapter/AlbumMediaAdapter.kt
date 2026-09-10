package com.developer.manali.galleryapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.databinding.ItemAlbumMediaBinding
import com.developer.manali.galleryapp.databinding.ItemAlbumMediaListBinding

class AlbumMediaAdapter(
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    private val items = mutableListOf<MediaItem>()
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

    fun getAllMediaItems(): List<MediaItem> = items.toList()

    fun getSelectedItems(): List<MediaItem> = items.filter { selectedIds.contains(it.id) }

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
        selectedIds.clear()
        selectedIds.addAll(items.map { it.id })
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun deselectAll() {
        selectedIds.clear()
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    private fun notifySelectionUpdate() {
        val selected = getSelectedItems()
        onSelectionChanged?.invoke(selected.size, selected)
    }

    fun submitList(newItems: List<MediaItem>) {
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
        return if (isListView) VIEW_TYPE_LIST else VIEW_TYPE_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_LIST) {
            val binding = ItemAlbumMediaListBinding.inflate(inflater, parent, false)
            AlbumMediaListViewHolder(binding)
        } else {
            val binding = ItemAlbumMediaBinding.inflate(inflater, parent, false)
            AlbumMediaGridViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is AlbumMediaListViewHolder) {
            holder.bind(item)
        } else if (holder is AlbumMediaGridViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class AlbumMediaGridViewHolder(private val binding: ItemAlbumMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mediaItem: MediaItem) {
            val isSelected = selectedIds.contains(mediaItem.id)

            val isFav = com.developer.manali.galleryapp.data.AppPreferences.getInstance(binding.root.context).isFavorite(mediaItem.id)
            binding.ivFavoriteBadge.visibility = if (isFav) View.VISIBLE else View.GONE

            if (isSelectionMode) {
                binding.ivSelectCheck.visibility = View.VISIBLE
                binding.ivSelectCheck.setImageResource(
                    if (isSelected) com.developer.manali.galleryapp.R.drawable.ic_select_checked
                    else com.developer.manali.galleryapp.R.drawable.ic_select_unchecked
                )
            } else {
                binding.ivSelectCheck.visibility = View.GONE
            }

            val glideRequest = Glide.with(binding.ivAlbumMedia.context)
                .load(mediaItem.uri)
                .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()

            if (mediaItem.isVideo) {
                glideRequest.frame(1_000_000L).into(binding.ivAlbumMedia)
                binding.ivAlbumMediaPlay.visibility = View.VISIBLE
                binding.tvAlbumMediaDuration.visibility = View.VISIBLE
                binding.tvAlbumMediaDuration.text = MediaRepository.formatDuration(mediaItem.duration)
            } else {
                glideRequest.into(binding.ivAlbumMedia)
                binding.ivAlbumMediaPlay.visibility = View.GONE
                binding.tvAlbumMediaDuration.visibility = View.GONE
            }

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

    inner class AlbumMediaListViewHolder(private val binding: ItemAlbumMediaListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mediaItem: MediaItem) {
            val isSelected = selectedIds.contains(mediaItem.id)

            val isFav = com.developer.manali.galleryapp.data.AppPreferences.getInstance(binding.root.context).isFavorite(mediaItem.id)
            binding.ivFavoriteBadge.visibility = if (isFav) View.VISIBLE else View.GONE

            binding.tvAlbumMediaName.text = mediaItem.displayName
            val sizeStr = MediaRepository.formatFileSize(mediaItem.size)
            val dateStr = MediaRepository.formatExactDate(mediaItem.dateAdded)

            if (isSelectionMode) {
                binding.ivChevron.visibility = View.GONE
                binding.ivSelectCheck.visibility = View.VISIBLE
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
                binding.ivChevron.visibility = View.GONE
                binding.ivSelectCheck.visibility = View.GONE
            }

            val glideRequest = Glide.with(binding.ivAlbumMedia.context)
                .load(mediaItem.uri)
                .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()

            if (mediaItem.isVideo) {
                val durStr = MediaRepository.formatDuration(mediaItem.duration)
                binding.tvAlbumMediaDetails.text = if (dateStr.isNotEmpty()) "$durStr • $sizeStr • $dateStr" else "$durStr • $sizeStr"
                glideRequest.frame(1_000_000L).into(binding.ivAlbumMedia)
                binding.ivAlbumMediaPlay.visibility = View.VISIBLE
                binding.tvAlbumMediaDuration.visibility = View.VISIBLE
                binding.tvAlbumMediaDuration.text = durStr
            } else {
                binding.tvAlbumMediaDetails.text = if (dateStr.isNotEmpty()) "$sizeStr • $dateStr" else sizeStr
                glideRequest.into(binding.ivAlbumMedia)
                binding.ivAlbumMediaPlay.visibility = View.GONE
                binding.tvAlbumMediaDuration.visibility = View.GONE
            }

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
