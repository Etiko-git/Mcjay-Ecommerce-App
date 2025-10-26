package com.solih.mcjay.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.solih.mcjay.R
import com.solih.mcjay.adapters.FavoritesAdapter
import com.solih.mcjay.databinding.FragmentFavoritesBinding
import com.solih.mcjay.models.Favorite
import com.solih.mcjay.models.Product
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

class FavoriteFragment : Fragment() {

    private lateinit var binding: FragmentFavoritesBinding
    private lateinit var favoritesAdapter: FavoritesAdapter
    private val favoriteProducts = mutableListOf<Product>()
    private var sortOption = "name_asc" // Default sort option
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadFavorites()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        favoritesAdapter = FavoritesAdapter { product, action ->
            when (action) {
                "add_to_cart" -> addToCart(product)
                "remove_favorite" -> removeFromFavorites(product)
                "view_detail" -> navigateToProductDetail(product)
            }
        }

        binding.favoritesRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = favoritesAdapter
        }
    }

    private fun setupClickListeners() {
        binding.browseProductsButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.sortButton.setOnClickListener {
            showSortOptions()
        }

        binding.searchFavoritesField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterFavorites(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadFavorites() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.favoritesRecyclerView.visibility = View.GONE

        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: run {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showEmptyState("Please login to view favorites")
                        Log.w("FavoriteFragment", "No authenticated user found")
                    }
                    return@launch
                }
                Log.d("FavoriteFragment", "Loading favorites for userId: $userId")

                // Fetch favorites with error handling
                val favorites = try {
                    withContext(Dispatchers.IO) {
                        val result = supabase.postgrest["favorites"].select {
                            filter { eq("user_id", userId) }
                        }.decodeList<Favorite>()
                        Log.d("FavoriteFragment", "Fetched ${result.size} favorites")
                        result.forEach { fav ->
                            Log.d("FavoriteFragment", "Favorite: id=${fav.id}, user_id=${fav.user_id}, product_id=${fav.product_id}, created_at=${fav.created_at}")
                        }
                        result
                    }
                } catch (e: Exception) {
                    Log.e("FavoriteFragment", "Error fetching favorites: ${e.message}", e)
                    emptyList<Favorite>()
                }

                // Extract product IDs
                val productIds = favorites
                    .mapNotNull { it.product_id.toIntOrNull() }
                    .filter { it != 0 }
                Log.d("FavoriteFragment", "Extracted productIds: $productIds")

                if (productIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showEmptyState("No favorites yet")
                        Log.w("FavoriteFragment", "No valid product IDs found in favorites")
                    }
                    return@launch
                }

                // Fetch products
                val products = try {
                    withContext(Dispatchers.IO) {
                        val result = mutableListOf<Product>()
                        productIds.forEach { productId ->
                            val productList = supabase.postgrest["products"].select {
                                filter { eq("id", productId) }
                            }.decodeList<Product>()
                            result.addAll(productList)
                            Log.d("FavoriteFragment", "Fetched products for id $productId: ${productList.size}")
                        }
                        Log.d("FavoriteFragment", "Total products fetched: ${result.size}")
                        result.forEach { prod ->
                            Log.d("FavoriteFragment", "Product: id=${prod.id}, name=${prod.name}, product_id=${prod.product_id}")
                        }
                        result
                    }
                } catch (e: Exception) {
                    Log.e("FavoriteFragment", "Error fetching products: ${e.message}", e)
                    emptyList<Product>()
                }

                favoriteProducts.clear()
                favoriteProducts.addAll(products)

                // Apply sorting
                sortFavorites()

                withContext(Dispatchers.Main) {
                    favoritesAdapter.updateList(favoriteProducts)
                    binding.progressBar.visibility = View.GONE

                    if (favoriteProducts.isEmpty()) {
                        showEmptyState("No favorites found")
                        Log.w("FavoriteFragment", "No products found for the favorite IDs")
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.favoritesRecyclerView.visibility = View.VISIBLE
                        Log.d("FavoriteFragment", "Displayed ${favoriteProducts.size} favorites")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showEmptyState("Error loading favorites")
                    Toast.makeText(requireContext(), "Error loading favorites: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("FavoriteFragment", "Error loading favorites: ${e.stackTraceToString()}", e)
                }
            }
        }
    }

    private fun removeFromFavorites(product: Product) {
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
                        Toast.makeText(requireContext(), "Invalid product ID", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Delete favorite entry
                withContext(Dispatchers.IO) {
                    supabase.postgrest["favorites"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("product_id", productId)
                        }
                    }
                }

                // Update UI (FavoritesAdapter handles local removal)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Removed from favorites", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error updating favorite: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("FavoriteFragment", "Error updating favorite: ${e.stackTraceToString()}", e)
                }
            }
        }
    }

    private fun sortFavorites() {
        favoriteProducts.sortWith(
            when (sortOption) {
                "name_asc" -> compareBy { it.name }
                "name_desc" -> compareByDescending { it.name }
                "price_asc" -> compareBy { it.price }
                "price_desc" -> compareByDescending { it.price }
                else -> compareBy { it.name }
            }
        )
    }

    private fun showEmptyState(message: String) {
        binding.emptyStateText.text = message
        binding.emptyState.visibility = View.VISIBLE
        binding.favoritesRecyclerView.visibility = View.GONE
    }

    private fun addToCart(product: Product) {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Please login to add to cart", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val productId = product.id ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error: Product ID is missing", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Check if product already exists in cart
                val existingCartItems = withContext(Dispatchers.IO) {
                    supabase.postgrest["cart"].select {
                        filter {
                            eq("user_id", userId)
                            eq("product_id", productId)
                        }
                    }.decodeList<Map<String, Any>>()
                }

                if (existingCartItems.isNotEmpty()) {
                    val item = existingCartItems[0]
                    val currentQuantity = (item["quantity"] as? Number)?.toInt() ?: 0
                    val cartItemId = item["id"]?.toString() ?: ""

                    withContext(Dispatchers.IO) {
                        supabase.postgrest["cart"].update(mapOf("quantity" to (currentQuantity + 1))) {
                            filter { eq("id", cartItemId) }
                        }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        supabase.postgrest["cart"].insert(
                            mapOf(
                                "user_id" to userId,
                                "product_id" to productId,
                                "quantity" to 1,
                                "price" to (product.discount_price ?: product.price)
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Added to cart", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error adding to cart: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("FavoriteFragment", "Error adding to cart: ${e.stackTraceToString()}", e)
                }
            }
        }
    }

    private fun filterFavorites(query: String) {
        val filtered = if (query.isEmpty()) {
            favoriteProducts
        } else {
            favoriteProducts.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description?.contains(query, ignoreCase = true) == true ||
                        it.category.contains(query, ignoreCase = true)
            }
        }

        favoritesAdapter.updateList(filtered)

        if (filtered.isEmpty()) {
            showEmptyState("No favorites match your search")
        } else {
            binding.emptyState.visibility = View.GONE
            binding.favoritesRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showSortOptions() {
        val sortOptions = arrayOf(
            "Name (A-Z)" to "name_asc",
            "Name (Z-A)" to "name_desc",
            "Price (Low to High)" to "price_asc",
            "Price (High to Low)" to "price_desc"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Sort Favorites")
            .setItems(sortOptions.map { it.first }.toTypedArray()) { _, which ->
                sortOption = sortOptions[which].second
                sortFavorites()
                favoritesAdapter.updateList(favoriteProducts)
                Toast.makeText(requireContext(), "Sorted by ${sortOptions[which].first}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToProductDetail(product: Product) {
        Toast.makeText(requireContext(), "View details for ${product.name}", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance() = FavoriteFragment()
    }
}