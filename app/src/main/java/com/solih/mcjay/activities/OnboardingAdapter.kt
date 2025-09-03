package com.solih.mcjay.activities

import com.solih.mcjay.R
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter(private val context: Context) :
    RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    // Sample data for your onboarding slides
    private val slides = listOf(
        Slide(R.drawable.slide1, "Bags", "Style meets function—carry confidence with every step."),
        Slide(R.drawable.slide2, "Jewelry", "Shine with elegance. Sparkle with confidence."),
        Slide(R.drawable.slide3, "clothing", "For every moment, there's an outfit.")
    )

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.imageView)
        val title: TextView = itemView.findViewById(R.id.titleTextView)
        val description: TextView = itemView.findViewById(R.id.descriptionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_onboarding, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val slide = slides[position]
        holder.image.setImageResource(slide.imageRes)
        holder.title.text = slide.title
        holder.description.text = slide.description
    }

    override fun getItemCount(): Int = slides.size

    // Simple data class for slides
    data class Slide(
        val imageRes: Int,
        val title: String,
        val description: String
    )
}