package com.solih.mcjay.adapters
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView
import com.solih.mcjay.R
import com.solih.mcjay.models.Product

class ProductAdapter(
    private var products: List<Product>,
    private val favoriteSet: Set<String>,
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
        val outOfStockText: TextView = itemView.findViewById(R.id.outOfStockText) // Add this line
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
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
                text = "$${product.price}"
                paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            holder.discountPriceTextView.text = "$${product.discount_price}"
            holder.discountBadge.text = "${product.getDiscountPercentage()}% OFF"
            holder.discountBadge.visibility = View.VISIBLE
        } else {
            holder.priceTextView.text = "$${product.price}"
            holder.discountPriceTextView.text = ""
            holder.discountBadge.visibility = View.GONE
        }

        // Set rating
        holder.ratingTextView.text = "⭐ ${product.ratings} (${product.reviews_count})"

        // Handle favorite state
        val isFavorite = favoriteSet.contains(product.product_id)
        holder.favoriteIcon.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
        )

        // Handle out of stock
        if (product.stock_quantity <= 0) {
            holder.outOfStockOverlay.visibility = View.VISIBLE
            holder.outOfStockText.visibility = View.VISIBLE
            holder.cardView.alpha = 0.7f
        } else {
            holder.outOfStockOverlay.visibility = View.GONE
            holder.outOfStockText.visibility = View.GONE
            holder.cardView.alpha = 1f
        }

        // Set click listeners
        holder.cardView.setOnClickListener {
            val scaleAnimation = AnimationUtils.loadAnimation(context, R.anim.scale_down_up)
            holder.cardView.startAnimation(scaleAnimation)
            onProductClick(product)
        }

        holder.imageView.setOnClickListener {
            val fadeAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in_out)
            holder.imageView.startAnimation(fadeAnimation)
            onProductClick(product)
        }

        holder.favoriteIcon.setOnClickListener {
            val bounceAnimation = AnimationUtils.loadAnimation(context, R.anim.bounce)
            holder.favoriteIcon.startAnimation(bounceAnimation)
            onFavoriteClick(product, !isFavorite)
        }

        // Add entrance animation
        setAnimation(holder.itemView, position)
    }

    private fun setAnimation(view: View, position: Int) {
        if (position > lastPosition) {
            val animation = AnimationUtils.loadAnimation(view.context, R.anim.slide_in_bottom)
            view.startAnimation(animation)
            lastPosition = position
        }
    }

    override fun getItemCount(): Int = products.size

    fun updateProducts(newProducts: List<Product>, newFavorites: Set<String>) {
        products = newProducts
        notifyDataSetChanged()
    }

    fun getProducts(): List<Product> {
        return products
    }
}