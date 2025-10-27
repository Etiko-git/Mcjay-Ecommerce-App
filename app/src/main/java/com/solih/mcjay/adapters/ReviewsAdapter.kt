package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.models.Review
import java.text.SimpleDateFormat
import java.util.*

class ReviewsAdapter(private val reviews: List<Review>) : RecyclerView.Adapter<ReviewsAdapter.ReviewViewHolder>() {

    inner class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.review_user_name)
        val ratingBar: RatingBar = itemView.findViewById(R.id.review_rating_bar)
        val reviewText: TextView = itemView.findViewById(R.id.review_text)
        val reviewDate: TextView = itemView.findViewById(R.id.review_date)
        val reviewImage: androidx.appcompat.widget.AppCompatImageView = itemView.findViewById(R.id.review_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]

        // Set user name
        holder.userName.text = review.user_name ?: "Anonymous User"

        // Set rating
        holder.ratingBar.rating = review.rating.toFloat()

        // Set review text - handle empty or null text
        val reviewText = review.review_text
        if (reviewText.isNullOrEmpty()) {
            holder.reviewText.visibility = View.GONE
        } else {
            holder.reviewText.visibility = View.VISIBLE
            holder.reviewText.text = reviewText
        }

        // Format date with better error handling
        review.created_at?.let { dateString ->
            try {
                // Try multiple date formats to handle different Supabase timestamp formats
                val date = try {
                    // Format 1: Standard ISO format
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault()).parse(dateString)
                } catch (e: Exception) {
                    try {
                        // Format 2: Without microseconds
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(dateString)
                    } catch (e: Exception) {
                        // Format 3: Just try to parse as is
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString.substring(0, 10))
                    }
                }

                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                holder.reviewDate.text = outputFormat.format(date!!)
            } catch (e: Exception) {
                // If parsing fails, show the raw date string or a portion of it
                holder.reviewDate.text = if (dateString.length >= 10) {
                    dateString.substring(0, 10) // Show just YYYY-MM-DD
                } else {
                    dateString
                }
            }
        } ?: run {
            holder.reviewDate.text = "Unknown date"
        }

        // Handle review image
        review.review_image_url?.let { imageUrl ->
            if (imageUrl.isNotEmpty()) {
                holder.reviewImage.visibility = View.VISIBLE
                Glide.with(holder.itemView.context)
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(holder.reviewImage)

                // Optional: Add click listener to view image in full screen
                holder.reviewImage.setOnClickListener {
                    // You can implement full-screen image view here if needed
                }
            } else {
                holder.reviewImage.visibility = View.GONE
            }
        } ?: run {
            holder.reviewImage.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = reviews.size

    // Helper method to update data
    fun updateData(newReviews: List<Review>) {
        (this as? RecyclerView.Adapter<ReviewViewHolder>)?.notifyDataSetChanged()
    }
}