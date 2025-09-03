package com.solih.mcjay.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.solih.mcjay.R

class OnboardingAdapter(private val context: Context) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    private val images = listOf(
        R.drawable.slide1,
        R.drawable.slide2,
        R.drawable.slide3
    )

    private val titles = listOf(
        "Latest Products",
        "Best Deals",
        "Fast Delivery"
    )

    private val descriptions = listOf(
        "Discover our latest collection of products",
        "Get the best deals on your favorite items",
        "Fast and reliable delivery to your doorstep"
    )

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.imageView)
        val title: TextView = itemView.findViewById(R.id.titleTextView)
        val description: TextView = itemView.findViewById(R.id.descriptionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_onboarding, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.image.setImageResource(images[position])
        holder.title.text = titles[position]
        holder.description.text = descriptions[position]
    }

    override fun getItemCount(): Int {
        return images.size
    }
}