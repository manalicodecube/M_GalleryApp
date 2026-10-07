package com.imagefolders.photoorganizer.mediagallery.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.imagefolders.photoorganizer.mediagallery.ui.fragment.AlbumsFragment
import com.imagefolders.photoorganizer.mediagallery.ui.fragment.PhotosFragment
import com.imagefolders.photoorganizer.mediagallery.ui.fragment.VideosFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    val photosFragment = PhotosFragment.newInstance()
    val albumsFragment = AlbumsFragment.newInstance()
    val videosFragment = VideosFragment.newInstance()

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> albumsFragment
            1 -> photosFragment
            2 -> videosFragment
            else -> albumsFragment
        }
    }
}
