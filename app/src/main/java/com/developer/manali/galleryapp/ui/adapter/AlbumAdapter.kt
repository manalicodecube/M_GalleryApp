package com.developer.manali.galleryapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.developer.manali.galleryapp.data.AlbumItem
import com.developer.manali.galleryapp.data.MediaRepository
import com.developer.manali.galleryapp.databinding.ItemAlbumCardBinding
import com.developer.manali.galleryapp.databinding.ItemAlbumListBinding
import java.text.NumberFormat
import java.util.Locale

class AlbumAdapter(
    private val onAlbumClick: (AlbumItem) -> Unit,
    private val onAlbumLongClick: ((AlbumItem) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    private val albums = mutableListOf<AlbumItem>()
    private var isListView: Boolean = false

    var isSelectionMode: Boolean = false
        private set

    private val selectedAlbums = HashSet<String>()

    fun submitList(newAlbums: List<AlbumItem>) {
        albums.clear()
        albums.addAll(newAlbums)
        notifyDataSetChanged()
    }

    fun setListView(isList: Boolean) {
        if (isListView != isList) {
            isListView = isList
            notifyDataSetChanged()
        }
    }

    fun enterSelectionMode(initialItem: AlbumItem?) {
        isSelectionMode = true
        selectedAlbums.clear()
        if (initialItem != null) {
            selectedAlbums.add(initialItem.bucketId)
        }
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedAlbums.clear()
        notifyDataSetChanged()
    }

    fun toggleSelection(item: AlbumItem): Boolean {
        val id = item.bucketId
        val wasSelected = selectedAlbums.contains(id)
        if (wasSelected) {
            selectedAlbums.remove(id)
        } else {
            selectedAlbums.add(id)
        }
        notifyItemChanged(albums.indexOf(item))
        return !wasSelected
    }

    fun selectAll() {
        selectedAlbums.clear()
        selectedAlbums.addAll(albums.map { it.bucketId })
        notifyDataSetChanged()
    }

    fun deselectAll() {
        selectedAlbums.clear()
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<AlbumItem> {
        return albums.filter { selectedAlbums.contains(it.bucketId) }
    }

    fun getAllAlbums(): List<AlbumItem> {
        return albums.toList()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isListView) VIEW_TYPE_LIST else VIEW_TYPE_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_LIST) {
            val binding = ItemAlbumListBinding.inflate(inflater, parent, false)
            AlbumListViewHolder(binding)
        } else {
            val binding = ItemAlbumCardBinding.inflate(inflater, parent, false)
            AlbumGridViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val album = albums[position]
        if (holder is AlbumListViewHolder) {
            holder.bind(album)
        } else if (holder is AlbumGridViewHolder) {
            holder.bind(album)
        }
    }

    override fun getItemCount(): Int = albums.size

    inner class AlbumGridViewHolder(private val binding: ItemAlbumCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(album: AlbumItem) {
            binding.tvAlbumName.text = album.bucketName

            if (album.isHidden) {
                binding.layoutHiddenContainer.visibility = View.VISIBLE
                binding.ivAlbumCover.visibility = View.INVISIBLE
                binding.ivFavoriteBadge.visibility = View.GONE
                binding.tvAlbumCount.text = "Locked"
            } else {
                binding.layoutHiddenContainer.visibility = View.GONE
                binding.ivAlbumCover.visibility = View.VISIBLE

                val formattedCount = NumberFormat.getNumberInstance(Locale.US).format(album.itemCount)
                val countStr = if (album.itemCount == 1) "1 Item" else "$formattedCount Items"
                val sizeStr = MediaRepository.formatFileSize(album.totalSizeBytes)
                binding.tvAlbumCount.text = "$countStr • $sizeStr"

                if (album.isFavorites) {
                    binding.ivFavoriteBadge.visibility = View.VISIBLE
                } else {
                    binding.ivFavoriteBadge.visibility = View.GONE
                }

                if (album.coverUri != null) {
                    Glide.with(binding.ivAlbumCover.context)
                        .load(album.coverUri)
                        .override(300, 300)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .centerCrop()
                        .into(binding.ivAlbumCover)
                } else {
                    binding.ivAlbumCover.setImageResource(android.R.color.darker_gray)
                }
            }

            val isSelected = selectedAlbums.contains(album.bucketId)
            binding.vSelectionOverlay.visibility = if (isSelectionMode && isSelected) View.VISIBLE else View.GONE
            if (isSelectionMode) {
                binding.ivSelectIndicator.visibility = View.VISIBLE
                if (isSelected) {
                    binding.ivSelectIndicator.setImageResource(com.developer.manali.galleryapp.R.drawable.ic_select_checked)
                    binding.ivSelectIndicator.imageTintList = null
                } else {
                    binding.ivSelectIndicator.setImageResource(com.developer.manali.galleryapp.R.drawable.ic_select_unchecked)
                    binding.ivSelectIndicator.imageTintList = null
                }
            } else {
                binding.ivSelectIndicator.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(album)
                    onAlbumLongClick?.invoke(album)
                } else {
                    onAlbumClick(album)
                }
            }
            
            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    toggleSelection(album)
                    onAlbumLongClick?.invoke(album)
                    true
                } else {
                    toggleSelection(album)
                    onAlbumLongClick?.invoke(album)
                    true
                }
            }
        }
    }

    inner class AlbumListViewHolder(private val binding: ItemAlbumListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(album: AlbumItem) {
            binding.tvAlbumName.text = album.bucketName

            if (album.isHidden) {
                binding.layoutHiddenContainer.visibility = View.VISIBLE
                binding.ivAlbumCover.visibility = View.INVISIBLE
                binding.ivFavoriteBadge.visibility = View.GONE
                binding.tvAlbumCount.text = "Locked"
            } else {
                binding.layoutHiddenContainer.visibility = View.GONE
                binding.ivAlbumCover.visibility = View.VISIBLE

                val formattedCount = NumberFormat.getNumberInstance(Locale.US).format(album.itemCount)
                val countStr = if (album.itemCount == 1) "1 Item" else "$formattedCount Items"
                val sizeStr = MediaRepository.formatFileSize(album.totalSizeBytes)
                binding.tvAlbumCount.text = "$countStr • $sizeStr"

                if (album.isFavorites) {
                    binding.ivFavoriteBadge.visibility = View.VISIBLE
                } else {
                    binding.ivFavoriteBadge.visibility = View.GONE
                }

                if (album.coverUri != null) {
                    Glide.with(binding.ivAlbumCover.context)
                        .load(album.coverUri)
                        .override(300, 300)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .centerCrop()
                        .into(binding.ivAlbumCover)
                } else {
                    binding.ivAlbumCover.setImageResource(android.R.color.darker_gray)
                }
            }

            val isSelected = selectedAlbums.contains(album.bucketId)
            binding.vSelectionOverlay.visibility = if (isSelectionMode && isSelected) View.VISIBLE else View.GONE
            if (isSelectionMode) {
                binding.ivSelectIndicator.visibility = View.VISIBLE
                if (isSelected) {
                    binding.ivSelectIndicator.setImageResource(com.developer.manali.galleryapp.R.drawable.ic_select_checked)
                    binding.ivSelectIndicator.imageTintList = null
                } else {
                    val isNight = com.developer.manali.galleryapp.PreferencesUtility.getInstance(binding.root.context).isNightMode()
                    binding.ivSelectIndicator.setImageResource(
                        if (!isNight) com.developer.manali.galleryapp.R.drawable.ic_select_unchecked_black
                        else com.developer.manali.galleryapp.R.drawable.ic_select_unchecked
                    )
                    binding.ivSelectIndicator.imageTintList = null
                }
            } else {
                binding.ivSelectIndicator.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(album)
                    onAlbumLongClick?.invoke(album)
                } else {
                    onAlbumClick(album)
                }
            }
            
            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    toggleSelection(album)
                    onAlbumLongClick?.invoke(album)
                    true
                } else {
                    toggleSelection(album)
                    onAlbumLongClick?.invoke(album)
                    true
                }
            }
        }
    }
}
