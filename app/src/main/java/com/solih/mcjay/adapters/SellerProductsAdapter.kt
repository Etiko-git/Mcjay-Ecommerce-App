package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.solih.mcjay.R
import com.solih.mcjay.models.Product

class SellerProductsAdapter(
    private var products: List<Product>,
    private val onProductClick: (Product) -> Unit,
    private val onEditClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit,
    private val onToggleStatus: (Product, Boolean) -> Unit
) : RecyclerView.Adapter<SellerProductsAdapter.ProductViewHolder>() {

    private var lastPosition = -1

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.productCard)
        val imageView: ImageView = itemView.findViewById(R.id.productImage)
        val nameTextView: TextView = itemView.findViewById(R.id.productName)
        val priceTextView: TextView = itemView.findViewById(R.id.productPrice)
        val stockTextView: TextView = itemView.findViewById(R.id.productStock)
        val categoryTextView: TextView = itemView.findViewById(R.id.productCategory)
        val statusSwitch: SwitchMaterial = itemView.findViewById(R.id.switchStatus)
        val btnEdit: MaterialButton = itemView.findViewById(R.id.btnEdit)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
        val outOfStockOverlay: View = itemView.findViewById(R.id.outOfStockOverlay)
        val outOfStockText: TextView = itemView.findViewById(R.id.outOfStockText)
        val discountBadge: TextView = itemView.findViewById(R.id.discountBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_seller_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        val context = holder.itemView.context

        // Load image
        Glide.with(context)
            .load(product.getFirstImageUrl())
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .into(holder.imageView)

        // Set product details
        holder.nameTextView.text = product.name
        holder.priceTextView.text = context.getString(R.string.price_format, product.price)
        holder.stockTextView.text = "Stock: ${product.stock_quantity}"
        holder.categoryTextView.text = product.category

        // Handle discount badge
        if (product.hasDiscount()) {
            holder.discountBadge.text = "${product.getDiscountPercentage()}% OFF"
            holder.discountBadge.visibility = View.VISIBLE
        } else {
            holder.discountBadge.visibility = View.GONE
        }

        // Handle out of stock
        if (product.stock_quantity <= 0) {
            holder.outOfStockOverlay.visibility = View.VISIBLE
            holder.outOfStockText.visibility = View.VISIBLE
            holder.stockTextView.setTextColor(ContextCompat.getColor(context, R.color.error_color))
        } else {
            holder.outOfStockOverlay.visibility = View.GONE
            holder.outOfStockText.visibility = View.GONE
            holder.stockTextView.setTextColor(ContextCompat.getColor(context, R.color.gray))
        }

        // Set status switch
        holder.statusSwitch.isChecked = product.is_active
        holder.statusSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggleStatus(product, isChecked)
        }

        // Set click listeners with animations
        holder.cardView.setOnClickListener {
            val scaleAnimation = AnimationUtils.loadAnimation(context, R.anim.scale_down_up)
            holder.cardView.startAnimation(scaleAnimation)
            onProductClick(product)
        }

        holder.btnEdit.setOnClickListener {
            val bounceAnimation = AnimationUtils.loadAnimation(context, R.anim.bounce)
            holder.btnEdit.startAnimation(bounceAnimation)
            onEditClick(product)
        }

        holder.btnDelete.setOnClickListener {
            val shakeAnimation = AnimationUtils.loadAnimation(context, R.anim.shake)
            holder.btnDelete.startAnimation(shakeAnimation)
            onDeleteClick(product)
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

    override fun onViewDetachedFromWindow(holder: ProductViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.clearAnimation()
    }

    override fun getItemCount(): Int = products.size

    fun updateProducts(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
        lastPosition = -1
    }
}