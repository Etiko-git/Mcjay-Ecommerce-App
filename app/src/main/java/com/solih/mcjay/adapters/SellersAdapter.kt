package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.models.Seller

class SellersAdapter(
    private val sellers: List<Seller>,
    private val onDeleteClick: (Seller) -> Unit
) : RecyclerView.Adapter<SellersAdapter.SellerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SellerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_seller_admin, parent, false)
        return SellerViewHolder(view)
    }

    override fun onBindViewHolder(holder: SellerViewHolder, position: Int) {
        holder.bind(sellers[position])
    }

    override fun getItemCount(): Int = sellers.size

    inner class SellerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProfile: ImageView = itemView.findViewById(R.id.ivProfile)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        private val tvStore: TextView = itemView.findViewById(R.id.tvStore)
        //private val tvEarnings: TextView = itemView.findViewById(R.id.tvEarnings)
        private val tvVerified: TextView = itemView.findViewById(R.id.tvVerified)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(seller: Seller) {
            // Load profile image
            if (!seller.profile_image.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(seller.profile_image)
                    .placeholder(R.drawable.ic_profile)
                    .into(ivProfile)
            } else {
                ivProfile.setImageResource(R.drawable.ic_profile)
            }

            tvName.text = seller.full_name
            tvEmail.text = seller.email
            tvStore.text = seller.store_name ?: "No Store Name"
            //tvEarnings.text = "Earnings: $${seller.total_earnings}"
            tvVerified.text = if (seller.is_verified) "Verified" else "Not Verified"
            tvVerified.setTextColor(
                if (seller.is_verified) itemView.context.getColor(R.color.green_500)
                else itemView.context.getColor(R.color.red_500)
            )

            btnDelete.setOnClickListener {
                onDeleteClick(seller)
            }
        }
    }
}