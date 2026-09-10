package com.developer.manali.galleryapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.developer.manali.galleryapp.data.AlbumItem
import com.developer.manali.galleryapp.databinding.ItemDialogAlbumMoveBinding

class MoveAlbumAdapter(
    private val albums: List<AlbumItem>,
    private val onAlbumSelected: (AlbumItem) -> Unit
) : RecyclerView.Adapter<MoveAlbumAdapter.MoveAlbumViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoveAlbumViewHolder {
        val binding = ItemDialogAlbumMoveBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MoveAlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MoveAlbumViewHolder, position: Int) {
        holder.bind(albums[position])
    }

    override fun getItemCount(): Int = albums.size

    inner class MoveAlbumViewHolder(private val binding: ItemDialogAlbumMoveBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(album: AlbumItem) {
            binding.tvMoveAlbumName.text = album.bucketName
            binding.tvMoveAlbumCount.text = "${album.itemCount} items"

            if (album.coverUri != null) {
                binding.ivMoveAlbumCover.visibility = View.VISIBLE
                binding.ivMoveFolderIcon.visibility = View.GONE
                Glide.with(binding.ivMoveAlbumCover.context)
                    .load(album.coverUri)
                    .centerCrop()
                    .into(binding.ivMoveAlbumCover)
            } else {
                binding.ivMoveAlbumCover.visibility = View.VISIBLE
                binding.ivMoveFolderIcon.visibility = View.VISIBLE
                binding.ivMoveAlbumCover.setImageDrawable(null)
            }

            binding.root.setOnClickListener {
                onAlbumSelected(album)
            }
        }
    }
}
