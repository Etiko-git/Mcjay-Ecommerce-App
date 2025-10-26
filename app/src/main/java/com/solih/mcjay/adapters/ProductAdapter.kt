package com.solih.mcjay.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView
import com.solih.mcjay.R
import com.solih.mcjay.models.Product

class ProductAdapter(
    private var products: List<Product>,
    private val favoriteSet: Set<Int>,
    private val onProductClick: (Product) -> Unit,
    private val onFavoriteClick: (Product, Boolean) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private var lastPosition = -1

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.productCard)
        val imageView: ImageView = itemView.findViewById(R.id.productImage)
        val nameTextView: TextView = itemView.findViewById(R.id.productName)
        val priceTextView: TextView = itemView.findViewById(R.id.productPrice)
        val discountPriceTextView: TextView = itemView.findViewById(R.id.discountPrice)
        val discountBadge: TextView = itemView.findViewById(R.id.discountBadge)
        val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon)
        val ratingTextView: TextView = itemView.findViewById(R.id.ratingText)
        val outOfStockOverlay: View = itemView.findViewById(R.id.outOfStockOverlay)
        val outOfStockText: TextView = itemView.findViewById(R.id.outOfStockText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        try {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_product, parent, false)
            return ProductViewHolder(view)
        } catch (e: Exception) {
            Log.e("ProductAdapter", "Error creating view holder: ${e.message}", e)
            throw e
        }
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        try {
            val product = products[position]
            val context = holder.itemView.context

            // Load image with Glide
            Glide.with(context)
                .load(product.getFirstImageUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.default_product_background)
                .into(holder.imageView)

            // Set product name
            holder.nameTextView.text = product.name

            // Handle pricing
            if (product.hasDiscount()) {
                holder.priceTextView.apply {
                    text = "$${String.format("%.2f", product.price)}"
                    paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    visibility = View.VISIBLE
                }
                holder.discountPriceTextView.text = "$${String.format("%.2f", product.discount_price)}"
                holder.discountPriceTextView.visibility = View.VISIBLE
                holder.discountBadge.text = "${product.getDiscountPercentage()}% OFF"
                holder.discountBadge.visibility = View.VISIBLE
            } else {
                holder.priceTextView.text = "$${String.format("%.2f", product.price)}"
                holder.priceTextView.paintFlags = 0 // Remove strike-through
                holder.priceTextView.visibility = View.VISIBLE
                holder.discountPriceTextView.visibility = View.GONE
                holder.discountBadge.visibility = View.GONE
            }

            // Set rating
            holder.ratingTextView.text = "⭐ ${product.ratings} (${product.reviews_count})"

            // Handle favorite state - FIXED: Now id is Int? so no need for toIntOrNull()
            val isFavorite = product.id?.let { favoriteSet.contains(it) } ?: false

            holder.favoriteIcon.setImageResource(
                if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
            )

            // Set favorite icon color
            val favoriteColor = if (isFavorite) {
                ContextCompat.getColor(context, R.color.error_color)
            } else {
                ContextCompat.getColor(context, R.color.gray)
            }
            holder.favoriteIcon.setColorFilter(favoriteColor)

            // Handle out of stock
            if (product.stock_quantity <= 0 || !product.is_active) {
                holder.outOfStockOverlay.visibility = View.VISIBLE
                holder.outOfStockText.visibility = View.VISIBLE
            } else {
                holder.outOfStockOverlay.visibility = View.GONE
                holder.outOfStockText.visibility = View.GONE
            }

            // Set click listeners
            holder.cardView.setOnClickListener {
                val scaleAnimation = AnimationUtils.loadAnimation(context, R.anim.scale_down_up)
                holder.cardView.startAnimation(scaleAnimation)
                onProductClick(product)
            }

            holder.favoriteIcon.setOnClickListener {
                val bounceAnimation = AnimationUtils.loadAnimation(context, R.anim.bounce)
                holder.favoriteIcon.startAnimation(bounceAnimation)
                onFavoriteClick(product, !isFavorite)
            }

            // Add entrance animation
            setAnimation(holder.itemView, position)
        } catch (e: Exception) {
            Log.e("ProductAdapter", "Error binding view holder at position $position: ${e.message}", e)
            throw e
        }
    }
    
    private fun setAnimation(view: View, position: Int) {
        if (position > lastPosition) {
            val animation = AnimationUtils.loadAnimation(view.context, R.anim.slide_in_bottom)
            view.startAnimation(animation)
            lastPosition = position
        }
    }

    override fun onViewDetachedFromWindow(holder: ProductViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.clearAnimation()
    }

    override fun getItemCount(): Int = products.size

    fun updateProducts(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }

    fun getProducts(): List<Product> {
        return products
    }

    fun filterProducts(query: String?): List<Product> {
        return if (query.isNullOrEmpty()) {
            products
        } else {
            products.filter { product ->
                product.name.contains(query, true) ||
                        product.description?.contains(query, true) == true ||
                        product.category.contains(query, true) ||
                        product.brand?.contains(query, true) == true
            }
        }
    }
}