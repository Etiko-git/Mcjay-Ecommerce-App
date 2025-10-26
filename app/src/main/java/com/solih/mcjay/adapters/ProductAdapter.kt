package com.solih.mcjay.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView
import com.solih.mcjay.R
import com.solih.mcjay.models.Product

class ProductAdapter(
    private var products: List<Product>,
    private var favoriteSet: MutableSet<Int>, // Changed to MutableSet and Int
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
        //val categoryBadge: TextView = itemView.findViewById(R.id.categoryBadge)
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

            // Load image with Glide - IMPROVED for storage URLs
            val imageUrl = product.getFirstImageUrl()
            Log.d("ProductAdapter", "Loading image for product ${product.name}: $imageUrl")

            Glide.with(context)
                .load(imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.default_product_background)
                .thumbnail(0.1f) // Load thumbnail first for better performance
                .into(holder.imageView)

            // Set product name
            holder.nameTextView.text = product.name

            // Set category badge
//            holder.categoryBadge.text = product.category
//            holder.categoryBadge.visibility = View.VISIBLE

            // Handle pricing with proper formatting
            if (product.hasDiscount() && product.discount_price != null) {
                // Show original price with strike-through and discounted price
                holder.priceTextView.apply {
                    text = context.getString(R.string.price_format, product.price)
                    paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    setTextColor(ContextCompat.getColor(context, R.color.gray))
                    visibility = View.VISIBLE
                }
                holder.discountPriceTextView.text = context.getString(R.string.price_format, product.discount_price)
                holder.discountPriceTextView.setTextColor(ContextCompat.getColor(context, R.color.error_color))
                holder.discountPriceTextView.visibility = View.VISIBLE

                // Show discount badge
                holder.discountBadge.text = context.getString(R.string.discount_percent, product.getDiscountPercentage())
                holder.discountBadge.visibility = View.VISIBLE
            } else {
                // Show only regular price
                holder.priceTextView.text = context.getString(R.string.price_format, product.price)
                holder.priceTextView.paintFlags = 0 // Remove strike-through
                holder.priceTextView.setTextColor(ContextCompat.getColor(context, R.color.black))
                holder.priceTextView.visibility = View.VISIBLE
                holder.discountPriceTextView.visibility = View.GONE
                holder.discountBadge.visibility = View.GONE
            }

            // Set rating with proper formatting
            val ratingText = if (product.reviews_count > 0) {
                "⭐ ${String.format("%.1f", product.ratings)} (${product.reviews_count})"
            } else {
                "⭐ ${String.format("%.1f", product.ratings)}"
            }
            holder.ratingTextView.text = ratingText

            // Handle favorite state - FIXED: Using product.id directly as Int
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

            // Handle out of stock status
            val isOutOfStock = product.stock_quantity <= 0 || !product.is_active
            if (isOutOfStock) {
                holder.outOfStockOverlay.visibility = View.VISIBLE
                holder.outOfStockText.visibility = View.VISIBLE
                holder.cardView.alpha = 0.7f
            } else {
                holder.outOfStockOverlay.visibility = View.GONE
                holder.outOfStockText.visibility = View.GONE
                holder.cardView.alpha = 1.0f
            }

            // Set click listeners with improved animations
            holder.cardView.setOnClickListener {
                if (!isOutOfStock) {
                    val scaleAnimation = AnimationUtils.loadAnimation(context, R.anim.scale_down_up)
                    holder.cardView.startAnimation(scaleAnimation)
                    onProductClick(product)
                } else {
                    Toast.makeText(context, "This product is currently out of stock", Toast.LENGTH_SHORT).show()
                }
            }

            holder.favoriteIcon.setOnClickListener {
                val bounceAnimation = AnimationUtils.loadAnimation(context, R.anim.bounce)
                holder.favoriteIcon.startAnimation(bounceAnimation)

                // Get current favorite state for this product
                val currentFavoriteState = product.id?.let { favoriteSet.contains(it) } ?: false
                onFavoriteClick(product, !currentFavoriteState)
            }

            // Add entrance animation
            setAnimation(holder.itemView, position)

        } catch (e: Exception) {
            Log.e("ProductAdapter", "Error binding view holder at position $position: ${e.message}", e)
            // Set default image on error
            holder.imageView.setImageResource(R.drawable.default_product_background)
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
        val oldSize = products.size
        products = newProducts
        if (oldSize == newProducts.size) {
            notifyItemRangeChanged(0, newProducts.size)
        } else {
            notifyDataSetChanged()
        }
        lastPosition = -1 // Reset animation position
    }

    // NEW: Method to update favorite set
    fun updateFavoriteSet(newFavoriteSet: Set<Int>) {
        favoriteSet.clear()
        favoriteSet.addAll(newFavoriteSet)
        notifyDataSetChanged()
    }

    // NEW: Method to get product position by product ID
    fun getProductPosition(product: Product): Int {
        return products.indexOfFirst { it.id == product.id }
    }

    // NEW: Method to update single product favorite status
    fun updateProductFavoriteStatus(productId: Int, isFavorite: Boolean) {
        if (isFavorite) {
            favoriteSet.add(productId)
        } else {
            favoriteSet.remove(productId)
        }

        val position = products.indexOfFirst { it.id == productId }
        if (position != -1) {
            notifyItemChanged(position)
        }
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
                        product.brand?.contains(query, true) == true ||
                        product.type?.contains(query, true) == true
            }
        }
    }

    fun filterByCategory(category: String?): List<Product> {
        return if (category.isNullOrEmpty() || category == "All") {
            products
        } else {
            products.filter { it.category.equals(category, true) }
        }
    }

    fun sortProducts(sortType: String) {
        val sortedList = when (sortType) {
            "price_low_high" -> products.sortedBy { it.price }
            "price_high_low" -> products.sortedByDescending { it.price }
            "name_asc" -> products.sortedBy { it.name }
            "name_desc" -> products.sortedByDescending { it.name }
            "rating" -> products.sortedByDescending { it.ratings }
            "newest" -> products.sortedByDescending { it.created_at }
            else -> products
        }
        updateProducts(sortedList)
    }

    fun getProductById(productId: Int): Product? {
        return products.find { it.id == productId }
    }

    fun updateProductStock(productId: Int, newStock: Int) {
        val updatedProducts = products.map { product ->
            if (product.id == productId) {
                product.copy(stock_quantity = newStock)
            } else {
                product
            }
        }
        updateProducts(updatedProducts)
    }
}