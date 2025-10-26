package com.solih.mcjay.fragments

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.solih.mcjay.R
import com.solih.mcjay.SharedPrefManager
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.adapters.ProductAdapter
import com.solih.mcjay.databinding.FragmentHomeBinding
import com.solih.mcjay.models.Favorite
import com.solih.mcjay.models.Product
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var productAdapter: ProductAdapter
    // Change this line in your class properties
    private var favoriteSet = mutableSetOf<Int>() // Back to Int for product IDs
    private var isHeaderVisible = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserGreeting()
        setupCategories()
        setupProducts()
        setupSearch()
        setupScrollBehavior()
        loadProductsAndFavorites()
    }

    private fun setupUserGreeting() {
        val sharedPrefManager = SharedPrefManager.getInstance(requireContext())

        // Detailed debugging
        Log.d("HomeFragment Debug", "=== HOME FRAGMENT DEBUG ===")
        Log.d("HomeFragment Debug", "isLoggedIn: ${sharedPrefManager.isLoggedIn()}")
        Log.d("HomeFragment Debug", "authType: ${sharedPrefManager.getAuthType()}")
        Log.d("HomeFragment Debug", "userName: '${sharedPrefManager.getUserName()}'")
        Log.d("HomeFragment Debug", "authToken: ${sharedPrefManager.getAuthToken()}")
        Log.d("HomeFragment Debug", "=== END DEBUG ===")

        if (sharedPrefManager.isLoggedIn() && sharedPrefManager.getAuthType() == "myid") {
            val userName = sharedPrefManager.getUserName()

            Log.d("HomeFragment", "MyID User detected, userName: '$userName'")

            if (userName != "Guest" && userName != "MyID User") {
                binding.welcomeText.text = "Hello, $userName! 👋"
                Log.d("HomeFragment", "Using actual MyID username: $userName")
            } else {
                binding.welcomeText.text = "Hello, Welcome Back! 👋"
                Log.d("HomeFragment", "Using fallback - userName is: '$userName'")
            }
        } else {
            // Fallback to Supabase user
            Log.d("HomeFragment", "Not MyID user or not logged in, checking Supabase")
            val user = supabase.auth.currentUserOrNull()
            val username = user?.email?.substringBefore("@") ?: "Guest"
            binding.welcomeText.text = "Hello, $username! 👋"
            Log.d("HomeFragment", "Using Supabase username: $username")
        }
    }
    private fun setupCategories() {
        val categories = listOf("All", "Bags", "Shoes", "Electronics", "Clothing", "Home", "Jewelry", "Beauty", "Sports")

        binding.categoriesContainer.removeAllViews()

        categories.forEachIndexed { index, category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = category == "All"
                chipBackgroundColor = resources.getColorStateList(com.solih.mcjay.R.color.chip_background_color, null)
                setTextColor(resources.getColorStateList(com.solih.mcjay.R.color.black, null))
                setOnClickListener {
                    filterProducts(category)
                    val scaleAnimation = AnimationUtils.loadAnimation(requireContext(), com.solih.mcjay.R.anim.scale_up)
                    startAnimation(scaleAnimation)
                }

                val slideInAnimation = AnimationUtils.loadAnimation(requireContext(), com.solih.mcjay.R.anim.slide_in_bottom)
                slideInAnimation.startOffset = (index * 100).toLong()
                startAnimation(slideInAnimation)
            }
            binding.categoriesContainer.addView(chip)
        }
    }

    private fun setupProducts() {
        productAdapter = ProductAdapter(emptyList(), favoriteSet,
            onProductClick = { product ->
                showProductDetails(product)
            },
            onFavoriteClick = { product, isFavorite ->
                toggleFavorite(product, isFavorite)
            }
        )

        binding.productsRecycler.apply {
            adapter = productAdapter
            layoutManager = GridLayoutManager(requireContext(), 2)
            itemAnimator = DefaultItemAnimator()
        }

        showShimmerEffect(true)
    }

    private fun setupScrollBehavior() {
        binding.productsRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var scrolledDistance = 0
            private val scrollThreshold = 100

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 0) {
                    scrolledDistance += dy
                    if (scrolledDistance > scrollThreshold && isHeaderVisible) {
                        hideHeader()
                        isHeaderVisible = false
                        scrolledDistance = 0
                    }
                } else if (dy < 0) {
                    scrolledDistance += dy
                    if (scrolledDistance < -scrollThreshold && !isHeaderVisible) {
                        showHeader()
                        isHeaderVisible = true
                        scrolledDistance = 0
                    }
                }

                if (!recyclerView.canScrollVertically(-1)) {
                    scrolledDistance = 0
                    if (!isHeaderVisible) {
                        showHeader()
                        isHeaderVisible = true
                    }
                }
            }
        })
    }

    private fun hideHeader() {
        val initialHeight = binding.headerContainer.height
        val anim = ValueAnimator.ofInt(initialHeight, 0)
        anim.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            val layoutParams = binding.headerContainer.layoutParams
            layoutParams.height = value
            binding.headerContainer.layoutParams = layoutParams
            binding.headerContainer.requestLayout()
        }
        anim.duration = 300
        anim.start()
    }

    private fun showHeader() {
        val finalHeight = resources.getDimensionPixelSize(com.solih.mcjay.R.dimen.header_height)
        val anim = ValueAnimator.ofInt(0, finalHeight)
        anim.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            val layoutParams = binding.headerContainer.layoutParams
            layoutParams.height = value
            binding.headerContainer.layoutParams = layoutParams
            binding.headerContainer.requestLayout()
        }
        anim.duration = 300
        anim.start()
    }

    private fun loadProductsAndFavorites(category: String = "All") {
        scope.launch {
            try {
                val sharedPrefManager = SharedPrefManager.getInstance(requireContext())

                // Check if user is logged in via MyID
                if (sharedPrefManager.isLoggedIn() && sharedPrefManager.getAuthType() == "myid") {
                    // MyID user - load products without user-specific data
                    loadProductsForMyIDUser(category)
                } else {
                    // Supabase user - load products with user data
                    loadProductsForSupabaseUser(category)
                }

            } catch (e: Exception) {
                Log.e("HomeFragment", "Error in loadProductsAndFavorites: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showShimmerEffect(false)
                    Toast.makeText(requireContext(), "Error loading products", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun loadProductsForMyIDUser(category: String) {
        val products = withContext(Dispatchers.IO) {
            try {
                val result = if (category == "All") {
                    supabase.postgrest["products"]
                        .select()
                        .decodeList<Product>()
                } else {
                    supabase.postgrest["products"]
                        .select {
                            filter { eq("category", category) }
                        }
                        .decodeList<Product>()
                }
                Log.d("HomeFragment", "Successfully fetched ${result.size} products for MyID user")
                result
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching products for MyID user: ${e.message}", e)
                emptyList<Product>()
            }
        }

        withContext(Dispatchers.Main) {
            productAdapter.updateProducts(products)
            showShimmerEffect(false)

            if (products.isEmpty()) {
                Toast.makeText(requireContext(), "No products found", Toast.LENGTH_SHORT).show()
            } else {
                binding.productsRecycler.visibility = View.VISIBLE
                val slideInAnimation = AnimationUtils.loadAnimation(requireContext(), com.solih.mcjay.R.anim.slide_in_bottom)
                binding.productsRecycler.startAnimation(slideInAnimation)
            }
        }
    }

    private suspend fun loadProductsForSupabaseUser(category: String) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return

        val products = withContext(Dispatchers.IO) {
            try {
                val result = if (category == "All") {
                    supabase.postgrest["products"]
                        .select()
                        .decodeList<Product>()
                } else {
                    supabase.postgrest["products"]
                        .select {
                            filter { eq("category", category) }
                        }
                        .decodeList<Product>()
                }
                Log.d("HomeFragment", "Successfully fetched ${result.size} products")
                result
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error fetching products: ${e.message}", e)
                emptyList<Product>()
            }
        }

        val favorites = try {
            withContext(Dispatchers.IO) {
                supabase.postgrest["favorites"]
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<Favorite>()
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error fetching favorites: ${e.message}")
            emptyList<Favorite>()
        }

        favoriteSet.clear()
        favorites.forEach { favorite ->
            favoriteSet.add(favorite.product_id) // Directly using the Int product_id
        }

        withContext(Dispatchers.Main) {
            productAdapter.updateProducts(products)
            showShimmerEffect(false)

            if (products.isEmpty()) {
                Toast.makeText(requireContext(), "No products found", Toast.LENGTH_SHORT).show()
            } else {
                binding.productsRecycler.visibility = View.VISIBLE
                val slideInAnimation = AnimationUtils.loadAnimation(requireContext(), com.solih.mcjay.R.anim.slide_in_bottom)
                binding.productsRecycler.startAnimation(slideInAnimation)
            }
        }
    }

    private fun showShimmerEffect(show: Boolean) {
        if (show) {
            binding.shimmerLayout.startShimmer()
            binding.shimmerLayout.visibility = View.VISIBLE
            binding.productsRecycler.visibility = View.GONE
        } else {
            binding.shimmerLayout.stopShimmer()
            binding.shimmerLayout.visibility = View.GONE
            binding.productsRecycler.visibility = View.VISIBLE
        }
    }

    private fun toggleFavorite(product: Product, isFavorite: Boolean) {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Please login to manage favorites", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val productId = product.id ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Invalid product", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (isFavorite) {
                    // Add to favorites
                    withContext(Dispatchers.IO) {
                        supabase.postgrest["favorites"].insert(
                            Favorite(
                                user_id = userId,
                                product_id = productId
                            )
                        )
                    }
                    favoriteSet.add(productId)
                    Log.d("Favorite", "Added product $productId to favorites")
                } else {
                    // Remove from favorites
                    withContext(Dispatchers.IO) {
                        supabase.postgrest["favorites"].delete {
                            filter {
                                eq("user_id", userId)
                                eq("product_id", productId)
                            }
                        }
                    }
                    favoriteSet.remove(productId)
                    Log.d("Favorite", "Removed product $productId from favorites")
                }

                withContext(Dispatchers.Main) {
                    productAdapter.notifyItemChanged(productAdapter.getProductPosition(product))
                    Toast.makeText(
                        requireContext(),
                        if (isFavorite) "Added to favorites" else "Removed from favorites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error updating favorite: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error updating favorite: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun showProductDetails(product: Product) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_product_details, null)

        val productImage: ImageView = dialogView.findViewById(R.id.dialog_product_image)
        val productName: TextView = dialogView.findViewById(R.id.dialog_product_name)
        val productPrice: TextView = dialogView.findViewById(R.id.dialog_product_price)
        val productDescription: TextView = dialogView.findViewById(R.id.dialog_product_description)
        val productCategory: TextView = dialogView.findViewById(R.id.dialog_product_category)
        val productStock: TextView = dialogView.findViewById(R.id.dialog_product_stock)
        val quantityText: TextView = dialogView.findViewById(R.id.dialog_quantity_text)
        val minusButton: ImageButton = dialogView.findViewById(R.id.dialog_quantity_minus)
        val plusButton: ImageButton = dialogView.findViewById(R.id.dialog_quantity_plus)
        val addToCartButton: Button = dialogView.findViewById(R.id.dialog_add_to_cart)

        // Set product data
        Glide.with(requireContext())
            .load(product.getFirstImageUrl())
            .placeholder(R.drawable.placeholder_image)
            .into(productImage)

        productName.text = product.name
        productDescription.text = product.description ?: "No description available"
        productCategory.text = "Category: ${product.category}"
        productStock.text = "Stock: ${product.stock_quantity} available"

        // Handle pricing
        if (product.hasDiscount()) {
            productPrice.text = "$${product.discount_price} (Save ${product.getDiscountPercentage()}%)"
        } else {
            productPrice.text = "$${product.price}"
        }

        // Quantity management
        var quantity = 1
        quantityText.text = quantity.toString()

        minusButton.setOnClickListener {
            if (quantity > 1) {
                quantity--
                quantityText.text = quantity.toString()
            }
        }

        plusButton.setOnClickListener {
            if (quantity < product.stock_quantity) {
                quantity++
                quantityText.text = quantity.toString()
            } else {
                Toast.makeText(requireContext(), "Only ${product.stock_quantity} available", Toast.LENGTH_SHORT).show()
            }
        }

        // Add to cart functionality - UPDATED to use Supabase cart table
        addToCartButton.setOnClickListener {
            addToCartToDatabase(product, quantity)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    private fun addToCartToDatabase(product: Product, quantity: Int) {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Please login to add items to cart", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val productId = product.id ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Invalid product", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (quantity > product.stock_quantity) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Only ${product.stock_quantity} items available", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d("CartDebug", "Adding to cart: user_id=$userId, product_id=$productId, quantity=$quantity")

                // Use the proper insert syntax for Supabase Kotlin SDK
                withContext(Dispatchers.IO) {
                    try {
                        // Option 1: Simple insert without expecting a response
                        supabase.postgrest["cart"].insert(
                            mapOf(
                                "user_id" to userId,
                                "product_id" to productId,
                                "quantity" to quantity
                            )
                        )

                        // If you need the inserted data back, use this approach instead:
                        // val inserted = supabase.postgrest.from("cart").insert(
                        //     mapOf(
                        //         "user_id" to userId,
                        //         "product_id" to productId,
                        //         "quantity" to quantity
                        //     )
                        // ).select().decodeSingle<Map<String, Any>>()

                        Log.d("CartDebug", "Insert completed successfully")
                    } catch (e: Exception) {
                        Log.e("CartDebug", "Insert error: ${e.message}", e)
                        throw e
                    }
                }

                withContext(Dispatchers.Main) {
                    val scaleAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.bounce)
                    binding.root.startAnimation(scaleAnimation)
                    Toast.makeText(
                        requireContext(),
                        "Added $quantity ${product.name} to cart",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("HomeFragment", "Error adding to cart: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    when {
                        e.message?.contains("duplicate", ignoreCase = true) == true -> {
                            Toast.makeText(requireContext(), "Item already in cart", Toast.LENGTH_SHORT).show()
                        }
                        e.message?.contains("foreign key", ignoreCase = true) == true -> {
                            Toast.makeText(requireContext(), "Invalid product", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(requireContext(), "Failed to add to cart: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun filterProducts(category: String) {
        showShimmerEffect(true)
        loadProductsAndFavorites(category)
    }

    private fun setupSearch() {
        binding.searchField.setOnEditorActionListener { _, _, _ ->
            val query = binding.searchField.text.toString().trim()
            if (query.isNotEmpty()) {
                searchProducts(query)
            }
            true
        }
    }

    private fun searchProducts(query: String) {
        scope.launch {
            try {
                val products = withContext(Dispatchers.IO) {
                    try {
                        supabase.postgrest["products"]
                            .select {
                                filter { ilike("name", "%$query%") }
                            }
                            .decodeList<Product>()
                    } catch (e: Exception) {
                        emptyList<Product>()
                    }
                }

                withContext(Dispatchers.Main) {
                    productAdapter.updateProducts(products)
                    if (products.isEmpty()) {
                        Toast.makeText(requireContext(), "No products match your search", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Search error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}