package com.developer.manali.galleryapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.developer.manali.galleryapp.data.MediaItem
import com.developer.manali.galleryapp.databinding.ItemMediaPagerBinding

class MediaPagerAdapter(
    private val onVideoPlayClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaPagerAdapter.MediaPagerViewHolder>() {

    private val mediaList = mutableListOf<MediaItem>()

    fun submitList(newMediaList: List<MediaItem>) {
        mediaList.clear()
        mediaList.addAll(newMediaList)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): MediaItem? {
        return if (position in 0 until mediaList.size) mediaList[position] else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaPagerViewHolder {
        val binding = ItemMediaPagerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaPagerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaPagerViewHolder, position: Int) {
        holder.bind(mediaList[position])
    }

    override fun onViewRecycled(holder: MediaPagerViewHolder) {
        super.onViewRecycled(holder)
        holder.resetZoom()
    }

    override fun getItemCount(): Int = mediaList.size

    inner class MediaPagerViewHolder(private val binding: ItemMediaPagerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun resetZoom() {
            binding.ivPagerMedia.setScale(1f)
        }
        fun bind(item: MediaItem) {
            binding.ivPagerMedia.setScale(1f)

            val glideRequest = Glide.with(binding.ivPagerMedia.context)
                .load(item.uri)
                .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
                .fitCenter()
                .diskCacheStrategy(DiskCacheStrategy.ALL)

            if (item.isVideo) {
                glideRequest.frame(1_000_000L).into(binding.ivPagerMedia)
                binding.ivPagerPlayVideo.visibility = View.VISIBLE
                binding.ivPagerPlayVideo.setOnClickListener {
                    onVideoPlayClick(item)
                }
                binding.ivPagerMedia.setOnClickListener {
                    onVideoPlayClick(item)
                }
            } else {
                glideRequest.into(binding.ivPagerMedia)
                binding.ivPagerPlayVideo.visibility = View.GONE
                binding.ivPagerMedia.setOnClickListener(null)
            }
        }
    }
}
