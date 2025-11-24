package com.solih.mcjay.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.adapters.ProductsAdapter
import com.solih.mcjay.databinding.ActivityManageProductsBinding
import com.solih.mcjay.models.Product
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.util.Locale

class ManageProductsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageProductsBinding
    private val supabase = SupabaseClientInstance.client
    private lateinit var adapter: ProductsAdapter
    private val productsList = mutableListOf<Product>()
    private val filteredList = mutableListOf<Product>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()
        loadProducts()
    }

    private fun setupRecyclerView() {
        adapter = ProductsAdapter(filteredList) { product ->
            showDeleteConfirmation(product)
        }
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            filterProducts(binding.etSearch.text.toString())
            true
        }

        binding.etSearch.setOnKeyListener { _, _, _ ->
            filterProducts(binding.etSearch.text.toString())
            false
        }
    }

    private fun loadProducts() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvEmpty.visibility = android.view.View.GONE

        lifecycleScope.launch {
            try {
                val products = supabase.postgrest["products"]
                    .select()
                    .decodeList<Product>()

                productsList.clear()
                productsList.addAll(products)
                filteredList.clear()
                filteredList.addAll(products)

                runOnUiThread {
                    binding.tvTotalProducts.text = "${products.size} Products"
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                    binding.progressBar.visibility = android.view.View.GONE
                }

            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@ManageProductsActivity, "Error loading products: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filterProducts(query: String) {
        filteredList.clear()
        if (query.isEmpty()) {
            filteredList.addAll(productsList)
        } else {
            val lowerQuery = query.lowercase(Locale.getDefault())
            filteredList.addAll(productsList.filter {
                it.seller_name?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true ||
                        it.name.lowercase(Locale.getDefault()).contains(lowerQuery)
            })
        }
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        binding.tvEmpty.visibility = if (filteredList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showDeleteConfirmation(product: Product) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Product")
            .setMessage("Are you sure you want to delete '${product.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteProduct(product)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteProduct(product: Product) {
        lifecycleScope.launch {
            try {
                supabase.postgrest["products"]
                    .delete {
                        filter {
                            eq("id", product.id ?: 0)
                        }
                    }

                runOnUiThread {
                    productsList.remove(product)
                    filterProducts(binding.etSearch.text.toString())
                    Toast.makeText(this@ManageProductsActivity, "Product deleted successfully", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ManageProductsActivity, "Error deleting product: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}