package com.solih.mcjay.fragments

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.solih.mcjay.R
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
    private var favoriteSet = mutableSetOf<String>() // Stores product IDs that are favorites
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
        setupScrollBehavior() // Add scroll behavior
        loadProductsAndFavorites()
    }

    private fun setupUserGreeting() {
        val user = supabase.auth.currentUserOrNull()
        // Extract username from email (part before @) or use a default
        val username = user?.email?.substringBefore("@") ?: "Guest"
        binding.welcomeText.text = "Hello, $username! 👋"
    }

    private fun setupCategories() {
        val categories = listOf("All", "Bags", "Shoes", "Electronics", "Clothing", "Home", "Jewelry", "Beauty", "Sports")

        binding.categoriesContainer.removeAllViews() // Clear existing chips

        categories.forEachIndexed { index, category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = category == "All"
                chipBackgroundColor = resources.getColorStateList(com.solih.mcjay.R.color.chip_background_color, null)
                setTextColor(resources.getColorStateList(com.solih.mcjay.R.color.black, null))
                setOnClickListener {
                    filterProducts(category)
                    // Add chip selection animation
                    val scaleAnimation = AnimationUtils.loadAnimation(requireContext(), com.solih.mcjay.R.anim.scale_up)
                    startAnimation(scaleAnimation)
                }

                // Staggered entrance animation for chips
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
                // Handle product click - navigate to product detail
                navigateToProductDetail(product)
            },
            onFavoriteClick = { product, isFavorite ->
                // Handle favorite toggle
                toggleFavorite(product, isFavorite)
            }
        )

        binding.productsRecycler.apply {
            adapter = productAdapter
            layoutManager = GridLayoutManager(requireContext(), 2)
            itemAnimator = DefaultItemAnimator()
        }

        // Add shimmer effect while loading
        showShimmerEffect(true)
    }

    private fun setupScrollBehavior() {
        binding.productsRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var scrolledDistance = 0
            private val scrollThreshold = 100 // Adjust this value for sensitivity

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 0) { // Scrolling down
                    scrolledDistance += dy
                    if (scrolledDistance > scrollThreshold && isHeaderVisible) {
                        hideHeader()
                        isHeaderVisible = false
                        scrolledDistance = 0
                    }
                } else if (dy < 0) { // Scrolling up
                    scrolledDistance += dy
                    if (scrolledDistance < -scrollThreshold && !isHeaderVisible) {
                        showHeader()
                        isHeaderVisible = true
                        scrolledDistance = 0
                    }
                }

                // Reset scrolledDistance when at top
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
        // Collapse the height smoothly
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
        // Expand the height smoothly
        val finalHeight = resources.getDimensionPixelSize(R.dimen.header_height) // Set a fixed height or measure
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

    /**
     * Loads products and favorites in parallel so the hearts display correctly
     */
    private fun loadProductsAndFavorites(category: String = "All") {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

                val productsDeferred = withContext(Dispatchers.IO) {
                    if (category == "All") {
                        supabase.postgrest["products"]
                            .select()
                            .decodeList<Product>()
                    } else {
                        supabase.postgrest["products"]
                            .select {
                                filter {
                                    eq("category", category)
                                }
                            }
                            .decodeList<Product>()
                    }
                }

                val favoritesDeferred = withContext(Dispatchers.IO) {
                    supabase.postgrest["favorites"]
                        .select {
                            filter { eq("user_id", userId) }
                        }
                        .decodeList<Favorite>()
                }

                // Extract favorite product IDs
                favoriteSet.clear()
                favoriteSet.addAll(favoritesDeferred.map { it.product_id })

                productAdapter.updateProducts(productsDeferred, favoriteSet)

                // Hide shimmer after loading
                showShimmerEffect(false)

                // Show success animation
                if (productsDeferred.isNotEmpty()) {
                    val slideInAnimation = AnimationUtils.loadAnimation(requireContext(), com.solih.mcjay.R.anim.slide_in_bottom)
                    binding.productsRecycler.startAnimation(slideInAnimation)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showShimmerEffect(false)
                Toast.makeText(requireContext(), "Error loading products: ${e.message}", Toast.LENGTH_SHORT).show()
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
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

                if (isFavorite) {
                    supabase.postgrest["favorites"].insert(
                        mapOf(
                            "user_id" to userId,
                            "product_id" to product.product_id
                        )
                    )
                    favoriteSet.add(product.product_id)
                } else {
                    supabase.postgrest["favorites"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("product_id", product.product_id)
                        }
                    }
                    favoriteSet.remove(product.product_id)
                }

                val currentProducts = productAdapter.getProducts()
                productAdapter.updateProducts(currentProducts, favoriteSet)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error updating favorite: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToProductDetail(product: Product) {
        // Implement navigation to product detail fragment/activity
        Toast.makeText(requireContext(), "Clicked: ${product.name}", Toast.LENGTH_SHORT).show()
    }

    private fun filterProducts(category: String) {
        showShimmerEffect(true)
        loadProductsAndFavorites(category)
    }

    private fun setupSearch() {
        binding.searchField.setOnEditorActionListener { _, _, _ ->
            val query = binding.searchField.text.toString().trim()
            if (query.isNotEmpty()) {
                // Add search animation
                val shakeAnimation = AnimationUtils.loadAnimation(requireContext(), com.solih.mcjay.R.anim.shake)
                binding.searchField.startAnimation(shakeAnimation)
                searchProducts(query)
            }
            true
        }

        // Add focus change animation
        binding.searchField.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val scaleAnimation = AnimationUtils.loadAnimation(requireContext(), com.solih.mcjay.R.anim.scale_up)
                binding.searchField.startAnimation(scaleAnimation)
            }
        }
    }

    private fun searchProducts(query: String) {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

                val products = withContext(Dispatchers.IO) {
                    supabase.postgrest["products"]
                        .select {
                            filter {
                                ilike("name", "%$query%")
                            }
                        }
                        .decodeList<Product>()
                }

                val favorites = withContext(Dispatchers.IO) {
                    supabase.postgrest["favorites"]
                        .select {
                            filter { eq("user_id", userId) }
                        }
                        .decodeList<Map<String, Any>>()
                }

                favoriteSet.clear()
                favoriteSet.addAll(favorites.map { it["product_id"] as String })

                productAdapter.updateProducts(products, favoriteSet)

                // Add search results animation
                val fadeInAnimation = AnimationUtils.loadAnimation(requireContext(), com.solih.mcjay.R.anim.fade_in)
                binding.productsRecycler.startAnimation(fadeInAnimation)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}