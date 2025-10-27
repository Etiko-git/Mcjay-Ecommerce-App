package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]

        holder.userName.text = review.user_name ?: "Anonymous User"
        holder.ratingBar.rating = review.rating.toFloat()
        holder.reviewText.text = review.review_text ?: "No review text provided"

        // Format date
        review.created_at?.let { dateString ->
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                holder.reviewDate.text = outputFormat.format(date!!)
            } catch (e: Exception) {
                holder.reviewDate.text = dateString
            }
        } ?: run {
            holder.reviewDate.text = "Unknown date"
        }
    }

    override fun getItemCount(): Int = reviews.size
}