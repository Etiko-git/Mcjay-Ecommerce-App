package com.solih.mcjay.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.adapters.SellersAdapter
import com.solih.mcjay.databinding.ActivityManageSellersBinding
import com.solih.mcjay.models.Seller
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class ManageSellersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageSellersBinding
    private val supabase = SupabaseClientInstance.client
    private lateinit var adapter: SellersAdapter
    private val sellersList = mutableListOf<Seller>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageSellersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadSellers()
    }

    private fun setupRecyclerView() {
        adapter = SellersAdapter(sellersList) { seller ->
            showDeleteConfirmation(seller)
        }
        binding.rvSellers.layoutManager = LinearLayoutManager(this)
        binding.rvSellers.adapter = adapter
    }

    private fun loadSellers() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvEmpty.visibility = android.view.View.GONE

        lifecycleScope.launch {
            try {
                val sellers = supabase.postgrest["sellers"]
                    .select()
                    .decodeList<Seller>()

                sellersList.clear()
                sellersList.addAll(sellers)

                runOnUiThread {
                    binding.tvTotalSellers.text = "${sellers.size} Sellers"
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                    binding.progressBar.visibility = android.view.View.GONE
                }

            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@ManageSellersActivity, "Error loading sellers: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateEmptyState() {
        binding.tvEmpty.visibility = if (sellersList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showDeleteConfirmation(seller: Seller) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Seller")
            .setMessage("Are you sure you want to delete seller '${seller.full_name}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSeller(seller)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSeller(seller: Seller) {
        lifecycleScope.launch {
            try {
                supabase.postgrest["sellers"]
                    .delete {
                        filter {
                            eq("id", seller.id)
                        }
                    }

                runOnUiThread {
                    sellersList.remove(seller)
                    adapter.notifyDataSetChanged()
                    binding.tvTotalSellers.text = "${sellersList.size} Sellers"
                    updateEmptyState()
                    Toast.makeText(this@ManageSellersActivity, "Seller deleted successfully", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ManageSellersActivity, "Error deleting seller: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}