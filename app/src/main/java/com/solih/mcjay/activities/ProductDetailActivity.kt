package com.solih.mcjay.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.solih.mcjay.R
import com.solih.mcjay.adapters.ImageSliderAdapter
import com.solih.mcjay.adapters.ReviewsAdapter
import com.solih.mcjay.adapters.SimilarProductsAdapter
import com.solih.mcjay.databinding.ActivityProductDetailBinding
import com.solih.mcjay.fragments.ReviewDialogFragment
import com.solih.mcjay.models.CartItem
import com.solih.mcjay.models.Product
import com.solih.mcjay.models.Review
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var product: Product
    private var quantity = 1
    private var isFavorite = false
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var reviewsAdapter: ReviewsAdapter
    private lateinit var similarProductsAdapter: SimilarProductsAdapter
    private val reviewsList = mutableListOf<Review>()
    private val similarProductsList = mutableListOf<Product>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val productIdStr = intent.getStringExtra("product_id") ?: run {
            Toast.makeText(this, "Product ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        scope.launch {
            try {
                product = fetchProduct(productIdStr)
                withContext(Dispatchers.Main) {
                    setupViews()
                    setupRecyclerViews()
                    loadProductDetails()
                    loadReviews()
                    loadSimilarProducts()
                    checkFavoriteStatus()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProductDetailActivity, "Error loading product: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("ProductDetail", "Error fetching product: ${e.message}")
                    finish()
                }
            }
        }
    }

    private suspend fun fetchProduct(productIdStr: String): Product {
        return withContext(Dispatchers.IO) {
            val products = supabase.postgrest.from("products")
                .select {
                    filter {
                        eq("product_id", productIdStr)
                    }
                }
                .decodeList<Product>()

            if (products.isEmpty()) {
                throw Exception("Product not found with ID: $productIdStr")
            }

            products.first()
        }
    }

    private fun setupViews() {
        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Setup quantity controls
        binding.quantityText.text = quantity.toString()

        binding.minusButton.setOnClickListener {
            if (quantity > 1) {
                quantity--
                binding.quantityText.text = quantity.toString()
                updateAddToCartButtonState()
            }
        }

        binding.plusButton.setOnClickListener {
            if (quantity < product.stock_quantity) {
                quantity++
                binding.quantityText.text = quantity.toString()
                updateAddToCartButtonState()
            } else {
                Toast.makeText(this, "Only ${product.stock_quantity} available", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup buttons
        binding.addToCartButton.setOnClickListener {
            addToCart()
        }

        binding.favoriteButton.setOnClickListener {
            toggleFavorite()
        }

        binding.writeReviewButton.setOnClickListener {
            showReviewDialog()
        }

        // Setup image slider dots
        setupImageSlider()

        // Initial button state
        updateAddToCartButtonState()
    }

    private fun updateAddToCartButtonState() {
        val isOutOfStock = product.stock_quantity == 0
        val exceedsStock = quantity > product.stock_quantity

        if (isOutOfStock) {
            binding.addToCartButton.text = "Out of Stock"
            binding.addToCartButton.isEnabled = false
            binding.addToCartButton.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))
        } else if (exceedsStock) {
            binding.addToCartButton.text = "Exceeds Stock"
            binding.addToCartButton.isEnabled = false
            binding.addToCartButton.setBackgroundColor(ContextCompat.getColor(this, R.color.error_color))
        } else {
            binding.addToCartButton.text = "Add to Cart"
            binding.addToCartButton.isEnabled = true
            binding.addToCartButton.setBackgroundColor(ContextCompat.getColor(this, R.color.error_color))
        }
    }

    private fun setupRecyclerViews() {
        // Reviews RecyclerView
        reviewsAdapter = ReviewsAdapter(reviewsList)
        binding.reviewsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ProductDetailActivity)
            adapter = reviewsAdapter
        }

        // Similar Products RecyclerView
        similarProductsAdapter = SimilarProductsAdapter(similarProductsList) { similarProduct ->
            val intent = Intent(this@ProductDetailActivity, ProductDetailActivity::class.java)
            intent.putExtra("product_id", similarProduct.product_id)
            startActivity(intent)
        }

        binding.similarProductsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ProductDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = similarProductsAdapter
        }
    }

    private fun setupImageSlider() {
        val images = product.getImageUrls()
        val imageSliderAdapter = ImageSliderAdapter(images) { imageUrl ->
            showFullScreenImage(imageUrl)
        }

        binding.imageSliderRecyclerView.apply {
            adapter = imageSliderAdapter
            layoutManager = LinearLayoutManager(this@ProductDetailActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        // Add PagerSnapHelper for smooth scrolling
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.imageSliderRecyclerView)

        // Setup dots indicator
        setupDotsIndicator(images.size)
    }

    private fun setupDotsIndicator(count: Int) {
        binding.dotsIndicator.removeAllViews()

        for (i in 0 until count) {
            val dot = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@ProductDetailActivity, R.drawable.dot_inactive))
                val params = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.dot_size),
                    resources.getDimensionPixelSize(R.dimen.dot_size)
                )
                params.setMargins(8, 0, 8, 0)
                layoutParams = params
            }
            binding.dotsIndicator.addView(dot)
        }

        // Update dots when page changes
        binding.imageSliderRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateDotsIndicator()
            }
        })

        updateDotsIndicator()
    }

    private fun updateDotsIndicator() {
        val layoutManager = binding.imageSliderRecyclerView.layoutManager as LinearLayoutManager
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

        for (i in 0 until binding.dotsIndicator.childCount) {
            val dot = binding.dotsIndicator.getChildAt(i) as ImageView
            if (i == firstVisibleItemPosition) {
                dot.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.dot_active))
            } else {
                dot.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.dot_inactive))
            }
        }
    }

    private fun loadProductDetails() {
        // Load main product image
        val firstImage = product.getFirstImageUrl()
        Glide.with(this)
            .load(firstImage)
            .placeholder(R.drawable.placeholder_image)
            .into(binding.mainProductImage)

        // Set product details
        binding.productName.text = product.name
        binding.productDescription.text = product.description ?: "No description available"
        binding.productCategory.text = "Category: ${product.category}"

        // Update stock display
        if (product.stock_quantity == 0) {
            binding.productStock.text = "Out of Stock"
            binding.productStock.setTextColor(ContextCompat.getColor(this, R.color.error_color))
        } else if (product.stock_quantity < 5) {
            binding.productStock.text = "Only ${product.stock_quantity} left in stock!"
            binding.productStock.setTextColor(ContextCompat.getColor(this, R.color.error_color))
        } else {
            binding.productStock.text = "In Stock (${product.stock_quantity} available)"
            binding.productStock.setTextColor(ContextCompat.getColor(this, R.color.green_500))
        }

        // Handle pricing
        if (product.hasDiscount() && product.discount_price != null) {
            binding.productPrice.text = "$${String.format("%.2f", product.price)}"
            binding.productPrice.paintFlags = android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            binding.discountPrice.text = "$${String.format("%.2f", product.discount_price)}"
            binding.discountPrice.visibility = View.VISIBLE
            binding.discountBadge.text = "${product.getDiscountPercentage()}% OFF"
            binding.discountBadge.visibility = View.VISIBLE
        } else {
            binding.productPrice.text = "$${String.format("%.2f", product.price)}"
            binding.discountPrice.visibility = View.GONE
            binding.discountBadge.visibility = View.GONE
        }

        // Set rating
        binding.ratingBar.rating = product.ratings.toFloat()
        binding.ratingText.text = "(${product.reviews_count} reviews)"
    }

    private fun loadReviews() {
        scope.launch {
            try {
                Log.d("ProductDetail", "=== START LOAD REVIEWS ===")
                Log.d("ProductDetail", "Product string ID: ${product.product_id}")

                // Get reviews for this specific product
                val reviews = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("reviews")
                        .select {
                            filter { eq("product_id", product.product_id) }
                            order(column = "created_at", order = Order.DESCENDING)
                        }
                        .decodeList<Review>()
                }

                Log.d("ProductDetail", "Loaded ${reviews.size} reviews for product ${product.product_id}")

                withContext(Dispatchers.Main) {
                    reviewsList.clear()
                    reviewsList.addAll(reviews)
                    reviewsAdapter.notifyDataSetChanged()

                    Log.d("ProductDetail", "Reviews list size: ${reviewsList.size}")
                    Log.d("ProductDetail", "Adapter item count: ${reviewsAdapter.itemCount}")

                    // ALWAYS show the reviews section, but control what's inside
                    binding.reviewsSection.visibility = View.VISIBLE

                    if (reviewsList.isEmpty()) {
                        // Show "no reviews" message but keep section visible
                        binding.noReviewsText.visibility = View.VISIBLE
                        binding.reviewsRecyclerView.visibility = View.GONE
                        binding.noReviewsText.text = "No reviews yet. Be the first to review!"
                        Log.d("ProductDetail", "No reviews found - showing empty state")
                    } else {
                        // Show the reviews list
                        binding.noReviewsText.visibility = View.GONE
                        binding.reviewsRecyclerView.visibility = View.VISIBLE
                        Log.d("ProductDetail", "Reviews found - showing ${reviewsList.size} reviews")
                    }

                    // Force UI update
                    binding.reviewsRecyclerView.invalidate()
                    binding.reviewsSection.requestLayout()
                }

                Log.d("ProductDetail", "=== END LOAD REVIEWS ===")

            } catch (e: Exception) {
                Log.e("ProductDetail", "Error loading reviews: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // Show error but keep section visible
                    binding.reviewsSection.visibility = View.VISIBLE
                    binding.noReviewsText.visibility = View.VISIBLE
                    binding.reviewsRecyclerView.visibility = View.GONE
                    binding.noReviewsText.text = "Error loading reviews: ${e.message}"
                }
            }
        }
    }

    private fun loadSimilarProducts() {
        scope.launch {
            try {
                val similarProducts = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("products")
                        .select {
                            filter {
                                eq("category", product.category)
                                neq("product_id", product.product_id)
                                gt("stock_quantity", 0) // Only show in-stock similar products
                            }
                            limit(10)
                        }
                        .decodeList<Product>()
                }

                similarProductsList.clear()
                similarProductsList.addAll(similarProducts)
                similarProductsAdapter.notifyDataSetChanged()

                binding.similarProductsSection.visibility = if (similarProductsList.isEmpty()) View.GONE else View.VISIBLE

            } catch (e: Exception) {
                Log.e("ProductDetail", "Error loading similar products: ${e.message}")
                binding.similarProductsSection.visibility = View.GONE
            }
        }
    }

    private fun checkFavoriteStatus() {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

                // CHANGED: Use string product_id for favorites
                val favorites = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("favorites")
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("product_id", product.product_id) // CHANGED: Use string product_id
                            }
                        }
                        .decodeList<Map<String, Any>>()
                }

                isFavorite = favorites.isNotEmpty()
                updateFavoriteButton()

            } catch (e: Exception) {
                Log.e("ProductDetail", "Error checking favorite status: ${e.message}")
            }
        }
    }

    private fun toggleFavorite() {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: run {
                    Toast.makeText(this@ProductDetailActivity, "Please login to manage favorites", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (isFavorite) {
                    // Remove from favorites - CHANGED: Use string product_id
                    withContext(Dispatchers.IO) {
                        supabase.postgrest.from("favorites").delete {
                            filter {
                                eq("user_id", userId)
                                eq("product_id", product.product_id) // CHANGED: Use string product_id
                            }
                        }
                    }
                    isFavorite = false
                    Toast.makeText(this@ProductDetailActivity, "Removed from favorites", Toast.LENGTH_SHORT).show()
                } else {
                    // Add to favorites - CHANGED: Use string product_id
                    withContext(Dispatchers.IO) {
                        supabase.postgrest.from("favorites").insert(
                            mapOf(
                                "user_id" to userId,
                                "product_id" to product.product_id // CHANGED: Use string product_id
                            )
                        )
                    }
                    isFavorite = true
                    Toast.makeText(this@ProductDetailActivity, "Added to favorites", Toast.LENGTH_SHORT).show()
                }

                updateFavoriteButton()

            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "Error updating favorite", Toast.LENGTH_SHORT).show()
                Log.e("ProductDetail", "Error toggling favorite: ${e.message}")
            }
        }
    }

    private fun updateFavoriteButton() {
        if (isFavorite) {
            binding.favoriteButton.setImageResource(R.drawable.ic_favorite_filled)
            binding.favoriteButton.setColorFilter(ContextCompat.getColor(this, R.color.error_color))
        } else {
            binding.favoriteButton.setImageResource(R.drawable.ic_favorite_outline)
            binding.favoriteButton.setColorFilter(ContextCompat.getColor(this, R.color.gray))
        }
    }

    private fun addToCart() {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: run {
                    Toast.makeText(this@ProductDetailActivity, "Please login to add to cart", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d("CartDebug", "Starting addToCart - User: $userId, Product ID: ${product.id}, Quantity: $quantity")

                // Check stock first
                if (quantity > product.stock_quantity) {
                    Toast.makeText(this@ProductDetailActivity, "Only ${product.stock_quantity} available in stock", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Use product.id (integer) for cart operations
                val existingCartItems = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("cart").select {
                        filter {
                            eq("user_id", userId)
                            eq("product_id", product.id!!) // Use integer product.id
                        }
                    }.decodeList<CartItem>()
                }

                Log.d("CartDebug", "Found ${existingCartItems.size} existing cart items")

                if (existingCartItems.isNotEmpty()) {
                    val existingItem = existingCartItems[0]
                    val newQuantity = existingItem.quantity + quantity

                    if (newQuantity > product.stock_quantity) {
                        Toast.makeText(this@ProductDetailActivity, "Cannot add more than available stock. You already have ${existingItem.quantity} in cart", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    withContext(Dispatchers.IO) {
                        supabase.postgrest.from("cart").update(
                            mapOf(
                                "quantity" to newQuantity
                            )
                        ) {
                            filter { eq("id", existingItem.id!!) }
                        }
                    }
                    Toast.makeText(this@ProductDetailActivity, "Updated quantity to $newQuantity", Toast.LENGTH_SHORT).show()
                    Log.d("CartDebug", "Updated cart item quantity to $newQuantity")
                } else {
                    if (quantity > product.stock_quantity) {
                        Toast.makeText(this@ProductDetailActivity, "Only ${product.stock_quantity} available in stock", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // Create a CartItem object instead of using mapOf()
                    val cartItem = CartItem(
                        user_id = userId,
                        product_id = product.id!!,
                        quantity = quantity,
                        price = (product.discount_price ?: product.price).toDouble()
                    )

                    withContext(Dispatchers.IO) {
                        supabase.postgrest.from("cart").insert(cartItem)
                    }
                    Toast.makeText(this@ProductDetailActivity, "Added $quantity ${product.name} to cart", Toast.LENGTH_LONG).show()
                    Log.d("CartDebug", "Added new item to cart successfully")
                }

            } catch (e: Exception) {
                Log.e("CartDebug", "Error adding to cart: ${e.message}", e)
                Toast.makeText(this@ProductDetailActivity, "Error adding to cart: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showReviewDialog() {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            Toast.makeText(this, "Please login to write a review", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if user has purchased this product
        scope.launch {
            try {
                val hasPurchased = withContext(Dispatchers.IO) {
                    val orders = supabase.postgrest.from("order_items")
                        .select {
                            filter {
                                eq("product_id", product.id!!)
                                eq("orders.user_id", userId)
                                eq("orders.status", "delivered")
                            }
                        }
                        .decodeList<Map<String, Any>>()

                    orders.isNotEmpty()
                }

                withContext(Dispatchers.Main) {
                    if (hasPurchased) {
                        val reviewDialog = ReviewDialogFragment.newInstance(product.product_id)
                        reviewDialog.setReviewListener(object : ReviewDialogFragment.ReviewSubmitListener {
                            override fun onReviewSubmitted(rating: Int, reviewText: String, imageUri: Uri?) {
                                submitReview(rating, reviewText, imageUri)
                            }
                        })
                        reviewDialog.show(supportFragmentManager, "ReviewDialog")
                    } else {
                        Toast.makeText(this@ProductDetailActivity, "You need to purchase this product before reviewing", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ProductDetail", "Error checking purchase status: ${e.message}")
                    // Fallback - allow review without purchase check
                    val reviewDialog = ReviewDialogFragment.newInstance(product.product_id)
                    reviewDialog.setReviewListener(object : ReviewDialogFragment.ReviewSubmitListener {
                        override fun onReviewSubmitted(rating: Int, reviewText: String, imageUri: Uri?) {
                            submitReview(rating, reviewText, imageUri)
                        }
                    })
                    reviewDialog.show(supportFragmentManager, "ReviewDialog")
                }
            }
        }
    }

    private fun submitReview(rating: Int, reviewText: String, imageUri: Uri?) {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not logged in")

                val productId = product.product_id // This should be String

                // Upload image first if exists and get URL
                val imageUrl = if (imageUri != null) {
                    uploadReviewImage(imageUri, userId)
                } else {
                    null
                }

                val reviewData = buildMap<String, Any> {
                    put("user_id", userId)
                    put("product_id", productId) // This should be String
                    put("rating", rating)
                    if (reviewText.isNotEmpty()) {
                        put("review_text", reviewText)
                    }
                    imageUrl?.let { put("review_image_url", it) }
                    put("updated_at", "now()")
                }

                // Check if user already has a review for this product
                val existingReview = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("reviews")
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("product_id", productId) // This should be String
                            }
                        }
                        .decodeList<Review>()
                        .firstOrNull()
                }

                if (existingReview != null) {
                    // Update existing review
                    withContext(Dispatchers.IO) {
                        supabase.postgrest.from("reviews")
                            .update(reviewData) {
                                filter {
                                    eq("id", existingReview.id!!)
                                }
                            }
                    }
                    Toast.makeText(this@ProductDetailActivity, "Review updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    // Create new review
                    withContext(Dispatchers.IO) {
                        supabase.postgrest.from("reviews")
                            .insert(reviewData)
                    }
                    Toast.makeText(this@ProductDetailActivity, "Review submitted successfully!", Toast.LENGTH_SHORT).show()
                }

                // Reload reviews
                loadReviews()

            } catch (e: Exception) {
                Log.e("ProductDetail", "Error submitting review: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProductDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun uploadReviewImage(imageUri: Uri, userId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Generate unique file name
                val fileName = "review_${userId}_${System.currentTimeMillis()}.jpg"

                // Read the image file
                val inputStream = contentResolver.openInputStream(imageUri)
                val bytes = inputStream?.use { it.readBytes() }
                    ?: throw Exception("Could not read image file")

                // Upload to Supabase storage
                supabase.storage.from("review-images").upload(fileName, bytes) {
                    upsert = false
                }

                // Get public URL
                val publicUrl = supabase.storage.from("review-images").publicUrl(fileName)

                publicUrl
            } catch (e: Exception) {
                Log.e("Review", "Error uploading review image: ${e.message}", e)
                throw e
            }
        }
    }

    private fun showFullScreenImage(imageUrl: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_fullscreen_image, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val imageView: ImageView = dialogView.findViewById(R.id.fullscreen_image_view)
        val closeButton: ImageButton = dialogView.findViewById(R.id.close_button)

        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(imageView)

        // Set click listeners
        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        imageView.setOnClickListener {
            dialog.dismiss()
        }

        // Also dismiss when clicking outside the image
        dialogView.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}