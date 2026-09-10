package com.developer.manali.galleryapp


import android.annotation.SuppressLint
import android.app.Activity
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class LanguageSelectAdapter(
    private val activity: Activity,
    private var arrLanguageList: List<LanguagesModel>,
    val sparseArray: SparseArray<LanguagesModel>
) : RecyclerView.Adapter<LanguageSelectAdapter.MyViewHolder>() {

    private val resDraw = arrayOf(
        R.drawable.icn_lang_english,
        R.drawable.icn_lang_spanish,
        R.drawable.ic_india,
        R.drawable.icn_lang_french,
        R.drawable.icn_lang_portuguese,
        R.drawable.icn_lang_italian,
        R.drawable.icn_lang_german,
        R.drawable.ic_urdu,
        R.drawable.ic_korean,
        R.drawable.ic_persian,
        R.drawable.ic_malaysian,
        R.drawable.ic_japanese,
        R.drawable.icn_lang_russian
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(activity).inflate(R.layout.raw_language_item, parent, false)
        return MyViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val arrItem = arrLanguageList[position]

        if (position < resDraw.size) {
            try {
                Glide.with(activity)
                    .load(resDraw[position])
                    .into(holder.imgFlag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        holder.txtLanguageName.text = arrItem.name

        val isSelected = sparseArray.get(position) == arrItem

        holder.loutMain.setOnClickListener {
            sparseArray.clear()
            sparseArray.put(position, arrItem)
            notifyDataSetChanged()
        }

        holder.imgSelected.setImageDrawable(
            ContextCompat.getDrawable(
                activity,
                if (isSelected) R.drawable.icn_selected else R.drawable.icn_unselected
            )
        )
    }

    override fun getItemCount(): Int {
        return arrLanguageList.size
    }

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val loutMain: LinearLayout = itemView.findViewById(R.id.loutMain)
        val txtLanguageName: TextView = itemView.findViewById(R.id.txtLanguageName)
        val imgFlag: ImageView = itemView.findViewById(R.id.imgFlag)
        val imgSelected: ImageView = itemView.findViewById(R.id.imgSelected)
    }
}
