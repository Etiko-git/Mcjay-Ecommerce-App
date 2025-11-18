package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.adapters.SellerProductsAdapter
import com.solih.mcjay.databinding.ActivitySellerProductsBinding
import com.solih.mcjay.databinding.DialogFilterProductsBinding
import com.solih.mcjay.databinding.DialogSortProductsBinding
import com.solih.mcjay.models.Product
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SellerProductsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerProductsBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var productsAdapter: SellerProductsAdapter
    private var allProducts: List<Product> = emptyList()
    private var filteredProducts: List<Product> = emptyList()

    // Filter states
    private var currentSearchQuery: String = ""
    private var currentCategory: String = ""
    private var currentType: String = ""
    private var currentBrand: String = ""
    private var currentStockFilter: String = "all"
    private var currentSortBy: String = "newest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        loadProducts()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        productsAdapter = SellerProductsAdapter(
            products = emptyList(),
            onProductClick = { product: Product ->
                showProductDetails(product)
            },
            onEditClick = { product: Product ->
                editProduct(product)
            },
            onDeleteClick = { product: Product ->
                showDeleteConfirmation(product)
            },
            onToggleStatus = { product: Product, isActive: Boolean ->
                toggleProductStatus(product, isActive)
            }
        )

        binding.rvProducts.apply {
            layoutManager = GridLayoutManager(this@SellerProductsActivity, 2)
            adapter = productsAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        // Search functionality
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                currentSearchQuery = binding.etSearch.text.toString().trim()
                applyFilters()
                true
            } else {
                false
            }
        }

        // Text watcher for search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentSearchQuery = s.toString().trim()
                applyFilters()
            }
        })

        // Filter button
        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }

        // Sort button
        binding.btnSort.setOnClickListener {
            showSortDialog()
        }

        // Add product FAB
        binding.fabAddProduct.setOnClickListener {
            startActivity(Intent(this, AddProductActivity::class.java))
        }

        // Add first product button
        binding.btnAddFirstProduct.setOnClickListener {
            startActivity(Intent(this, AddProductActivity::class.java))
        }

        // Swipe to refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadProducts()
        }
    }

    private fun loadProducts() {
        setLoadingState(true)
        binding.emptyState.visibility = View.GONE

        scope.launch {
            try {
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser == null) {
                    runOnUiThread {
                        Toast.makeText(this@SellerProductsActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                Log.d("SellerProducts", "Loading products for seller: ${currentUser.id}")

                // Fetch products for current seller
                val products = supabase.postgrest["products"]
                    .select {
                        filter { eq("seller_id", currentUser.id) }
                    }
                    .decodeList<Product>()

                Log.d("SellerProducts", "Loaded ${products.size} products")

                allProducts = products
                applyFilters()

                runOnUiThread {
                    updateStats(products)
                    binding.swipeRefreshLayout.isRefreshing = false
                }

            } catch (e: Exception) {
                Log.e("SellerProducts", "Error loading products: ${e.message}", e)
                runOnUiThread {
                    binding.swipeRefreshLayout.isRefreshing = false
                    setLoadingState(false)
                    Toast.makeText(this@SellerProductsActivity, "Error loading products: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyFilters() {
        filteredProducts = allProducts.filter { product ->
            // Search filter
            val matchesSearch = currentSearchQuery.isEmpty() ||
                    product.name.contains(currentSearchQuery, true) ||
                    product.brand?.contains(currentSearchQuery, true) == true ||
                    product.sku?.contains(currentSearchQuery, true) == true ||
                    product.category.contains(currentSearchQuery, true)

            // Category filter
            val matchesCategory = currentCategory.isEmpty() || product.category.equals(currentCategory, true)

            // Type filter
            val matchesType = currentType.isEmpty() || product.type?.equals(currentType, true) == true

            // Brand filter
            val matchesBrand = currentBrand.isEmpty() || product.brand?.equals(currentBrand, true) == true

            // Stock filter
            val matchesStock = when (currentStockFilter) {
                "in_stock" -> product.stock_quantity > 0
                "out_of_stock" -> product.stock_quantity <= 0
                "low_stock" -> product.stock_quantity in 1..10
                else -> true // "all"
            }

            matchesSearch && matchesCategory && matchesType && matchesBrand && matchesStock
        }

        // Apply sorting
        filteredProducts = when (currentSortBy) {
            "name_asc" -> filteredProducts.sortedBy { it.name }
            "name_desc" -> filteredProducts.sortedByDescending { it.name }
            "price_low_high" -> filteredProducts.sortedBy { it.price }
            "price_high_low" -> filteredProducts.sortedByDescending { it.price }
            "stock_low_high" -> filteredProducts.sortedBy { it.stock_quantity }
            "stock_high_low" -> filteredProducts.sortedByDescending { it.stock_quantity }
            "newest" -> filteredProducts.sortedByDescending { it.created_at }
            "oldest" -> filteredProducts.sortedBy { it.created_at }
            else -> filteredProducts
        }

        updateUI()
        updateFilterChips()
    }

    private fun updateUI() {
        productsAdapter.updateProducts(filteredProducts)

        if (filteredProducts.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvProducts.visibility = View.GONE
            if (allProducts.isEmpty()) {
                binding.btnAddFirstProduct.visibility = View.VISIBLE
                // Update text views in empty state
                binding.tvEmptyTitle.text = "No Products Yet"
                binding.tvEmptySubtitle.text = "Start by adding your first product to showcase in your store"
            } else {
                binding.btnAddFirstProduct.visibility = View.GONE
                // Update text views in empty state
                binding.tvEmptyTitle.text = "No Products Found"
                binding.tvEmptySubtitle.text = "No products match your current search and filter criteria"
            }
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvProducts.visibility = View.VISIBLE
        }

        setLoadingState(false)
    }

    private fun updateStats(products: List<Product>) {
        val totalProducts = products.size
        val activeProducts = products.count { it.is_active }

        binding.tvTotalProducts.text = totalProducts.toString()
        binding.tvActiveProducts.text = activeProducts.toString()
    }

    private fun updateFilterChips() {
        binding.chipGroupFilters.removeAllViews()

        val activeFilters = mutableListOf<String>()

        if (currentSearchQuery.isNotEmpty()) {
            activeFilters.add("Search: $currentSearchQuery")
        }
        if (currentCategory.isNotEmpty()) {
            activeFilters.add("Category: $currentCategory")
        }
        if (currentType.isNotEmpty()) {
            activeFilters.add("Type: $currentType")
        }
        if (currentBrand.isNotEmpty()) {
            activeFilters.add("Brand: $currentBrand")
        }
        if (currentStockFilter != "all") {
            activeFilters.add("Stock: ${currentStockFilter.replace("_", " ").replaceFirstChar { it.uppercase() }}")
        }

        activeFilters.forEach { filterText ->
            val chip = Chip(this).apply {
                text = filterText
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    clearFilter(filterText)
                }
            }
            binding.chipGroupFilters.addView(chip)
        }

        binding.chipGroupFilters.visibility = if (activeFilters.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun clearFilter(filterText: String) {
        when {
            filterText.startsWith("Search:") -> {
                currentSearchQuery = ""
                binding.etSearch.text?.clear()
            }
            filterText.startsWith("Category:") -> currentCategory = ""
            filterText.startsWith("Type:") -> currentType = ""
            filterText.startsWith("Brand:") -> currentBrand = ""
            filterText.startsWith("Stock:") -> currentStockFilter = "all"
        }
        applyFilters()
    }

    private fun showFilterDialog() {
        val dialogBinding = DialogFilterProductsBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Filter Products")
            .setView(dialogBinding.root)
            .setPositiveButton("Apply", null)
            .setNegativeButton("Clear All") { _, _ ->
                clearAllFilters()
            }
            .setNeutralButton("Cancel", null)
            .create()

        // Set current values
        val categories = allProducts.map { it.category }.distinct().sorted()
        val types = allProducts.mapNotNull { it.type }.distinct().sorted()
        val brands = allProducts.mapNotNull { it.brand }.distinct().sorted()

        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("All Categories") + categories)
        dialogBinding.spCategory.setAdapter(categoryAdapter)
        dialogBinding.spCategory.setText(currentCategory, false)

        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, types)
        dialogBinding.spType.setAdapter(typeAdapter)
        dialogBinding.spType.setText(currentType, false)

        val brandAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, brands)
        dialogBinding.spBrand.setAdapter(brandAdapter)
        dialogBinding.spBrand.setText(currentBrand, false)

        // Stock filter radio group
        when (currentStockFilter) {
            "in_stock" -> dialogBinding.radioStockInStock.isChecked = true
            "out_of_stock" -> dialogBinding.radioStockOutOfStock.isChecked = true
            "low_stock" -> dialogBinding.radioStockLowStock.isChecked = true
            else -> dialogBinding.radioStockAll.isChecked = true
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                currentCategory = dialogBinding.spCategory.text.toString().trim()
                currentType = dialogBinding.spType.text.toString().trim()
                currentBrand = dialogBinding.spBrand.text.toString().trim()

                currentStockFilter = when (dialogBinding.radioGroupStock.checkedRadioButtonId) {
                    R.id.radioStockInStock -> "in_stock"
                    R.id.radioStockOutOfStock -> "out_of_stock"
                    R.id.radioStockLowStock -> "low_stock"
                    else -> "all"
                }

                applyFilters()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showSortDialog() {
        val dialogBinding = DialogSortProductsBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Sort Products")
            .setView(dialogBinding.root)
            .setPositiveButton("Apply", null)
            .setNegativeButton("Cancel", null)
            .create()

        // Set current sort option
        when (currentSortBy) {
            "name_asc" -> dialogBinding.radioNameAsc.isChecked = true
            "name_desc" -> dialogBinding.radioNameDesc.isChecked = true
            "price_low_high" -> dialogBinding.radioPriceLowHigh.isChecked = true
            "price_high_low" -> dialogBinding.radioPriceHighLow.isChecked = true
            "stock_low_high" -> dialogBinding.radioStockLowHigh.isChecked = true
            "stock_high_low" -> dialogBinding.radioStockHighLow.isChecked = true
            "newest" -> dialogBinding.radioNewest.isChecked = true
            "oldest" -> dialogBinding.radioOldest.isChecked = true
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                currentSortBy = when (dialogBinding.radioGroupSort.checkedRadioButtonId) {
                    R.id.radioNameAsc -> "name_asc"
                    R.id.radioNameDesc -> "name_desc"
                    R.id.radioPriceLowHigh -> "price_low_high"
                    R.id.radioPriceHighLow -> "price_high_low"
                    R.id.radioStockLowHigh -> "stock_low_high"
                    R.id.radioStockHighLow -> "stock_high_low"
                    R.id.radioNewest -> "newest"
                    R.id.radioOldest -> "oldest"
                    else -> "newest"
                }

                applyFilters()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun clearAllFilters() {
        currentSearchQuery = ""
        currentCategory = ""
        currentType = ""
        currentBrand = ""
        currentStockFilter = "all"

        binding.etSearch.text?.clear()
        applyFilters()
    }

    private fun showProductDetails(product: Product) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(product.name)
            .setMessage(
                """
                Product ID: ${product.product_id}
                Category: ${product.category}
                Brand: ${product.brand ?: "N/A"}
                Type: ${product.type ?: "N/A"}
                Price: ₹${String.format("%.2f", product.price)}
                ${if (product.hasDiscount()) "Discount Price: ₹${String.format("%.2f", product.discount_price!!)}" else ""}
                Stock: ${product.stock_quantity}
                SKU: ${product.sku ?: "N/A"}
                Status: ${if (product.is_active) "Active" else "Inactive"}
                ${product.description?.let { "\nDescription: $it" } ?: ""}
                """.trimIndent()
            )
            .setPositiveButton("Close", null)
            .setNeutralButton("Edit") { _, _ ->
                editProduct(product)
            }
            .create()

        dialog.show()
    }

    private fun editProduct(product: Product) {
        Toast.makeText(this, "Edit product: ${product.name}", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirmation(product: Product) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Product")
            .setMessage("Are you sure you want to delete \"${product.name}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteProduct(product)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun deleteProduct(product: Product) {
        setLoadingState(true)

        scope.launch {
            try {
                supabase.postgrest["products"]
                    .delete {
                        filter { eq("product_id", product.product_id) }
                    }

                runOnUiThread {
                    Toast.makeText(this@SellerProductsActivity, "Product deleted successfully", Toast.LENGTH_SHORT).show()
                    loadProducts()
                }

            } catch (e: Exception) {
                Log.e("SellerProducts", "Error deleting product: ${e.message}", e)
                runOnUiThread {
                    setLoadingState(false)
                    Toast.makeText(this@SellerProductsActivity, "Error deleting product: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun toggleProductStatus(product: Product, isActive: Boolean) {
        scope.launch {
            try {
                supabase.postgrest["products"]
                    .update({
                        set("is_active", isActive)
                        set("updated_at", System.currentTimeMillis().toString())
                    }) {
                        filter { eq("product_id", product.product_id) }
                    }

                runOnUiThread {
                    Toast.makeText(
                        this@SellerProductsActivity,
                        "Product ${if (isActive) "activated" else "deactivated"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadProducts()
                }

            } catch (e: Exception) {
                Log.e("SellerProducts", "Error updating product status: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@SellerProductsActivity, "Error updating product status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.swipeRefreshLayout.isEnabled = !isLoading
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.seller_products_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_refresh -> {
                loadProducts()
                true
            }
            R.id.menu_clear_filters -> {
                clearAllFilters()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }
}