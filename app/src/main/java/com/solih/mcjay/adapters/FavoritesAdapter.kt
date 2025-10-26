package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.models.Product
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import java.text.NumberFormat
import java.util.Locale

class FavoritesAdapter(
    private val onItemAction: (Product, String) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {

    private var products: MutableList<Product> = mutableListOf()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.productCard)
        val imageView: ShapeableImageView = itemView.findViewById(R.id.productImage)
        val nameTextView: TextView = itemView.findViewById(R.id.productName)
        val priceTextView: TextView = itemView.findViewById(R.id.discountPrice)
        val originalPriceTextView: TextView = itemView.findViewById(R.id.productPrice)
        val addToCartButton: ImageButton = itemView.findViewById(R.id.addToCartButton)
        val removeFavoriteButton: ImageButton = itemView.findViewById(R.id.favoriteIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = products[position]

        // Load product image
        Glide.with(holder.itemView.context)
            .load(product.getFirstImageUrl())
            .placeholder(R.drawable.placeholder_image)
            .into(holder.imageView)

        holder.nameTextView.text = product.name

        // Format prices
        val currentPrice = product.discount_price ?: product.price
        holder.priceTextView.text = currencyFormat.format(currentPrice)

        // Show original price with strike-through if discounted
        if (product.hasDiscount()) {
            holder.originalPriceTextView.text = currencyFormat.format(product.price)
            holder.originalPriceTextView.paintFlags = android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            holder.originalPriceTextView.visibility = View.VISIBLE
        } else {
            holder.originalPriceTextView.visibility = View.GONE
        }

        // Add to cart
        holder.addToCartButton.setOnClickListener {
            onItemAction(product, "add_to_cart")
        }

        // Remove from favorites (updates list immediately)
        holder.removeFavoriteButton.setImageResource(R.drawable.ic_favorite_filled)
        holder.removeFavoriteButton.setOnClickListener {
            removeItemAt(position) // update locally
            onItemAction(product, "remove_favorite") // notify fragment to sync with DB
        }

        // Navigate to product detail
        holder.cardView.setOnClickListener {
            onItemAction(product, "view_detail")
        }
    }

    override fun getItemCount() = products.size

    fun updateList(newList: List<Product>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = products.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                products[oldItemPosition].id == newList[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                products[oldItemPosition] == newList[newItemPosition]
        })
        products = newList.toMutableList()
        diffResult.dispatchUpdatesTo(this)
    }

    /** ✅ Removes one item locally without full reload */
    fun removeItemAt(position: Int) {
        if (position in products.indices) {
            val newList = products.toMutableList()
            newList.removeAt(position)
            updateList(newList)
        }
    }

    /** ✅ Adds a product to the list locally (e.g., after re-favoriting) */
    fun addItem(product: Product) {
        val newList = products.toMutableList()
        newList.add(0, product) // add at top
        updateList(newList)
    }
}
