package com.solih.mcjay.activities

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.adapters.OrderItemsAdapter
import com.solih.mcjay.databinding.ActivityManageOrdersBinding
import com.solih.mcjay.models.Order
import com.solih.mcjay.models.OrderItem
import com.solih.mcjay.models.Product
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class ManageOrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageOrdersBinding
    private val supabase = SupabaseClientInstance.client
    private lateinit var adapter: OrderItemsAdapter
    private val orderItemsList = mutableListOf<OrderItem>()
    private var currentOrder: Order? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()

        // Initialize with empty state
        hideOrderDetails()
    }

    private fun setupRecyclerView() {
        adapter = OrderItemsAdapter(orderItemsList)
        binding.rvOrderItems.layoutManager = LinearLayoutManager(this)
        binding.rvOrderItems.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener {
            searchOrder()
        }

        binding.etOrderNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchOrder()
                true
            } else {
                false
            }
        }

        // Handle back button in header
        findViewById<android.widget.ImageView>(R.id.ivBack)?.setOnClickListener {
            onBackPressed()
        }
    }

    private fun searchOrder() {
        val orderNumber = binding.etOrderNumber.text.toString().trim()

        if (orderNumber.isEmpty()) {
            showError("Please enter an order number")
            return
        }

        hideOrderDetails()
        setLoadingState(true)
        clearError()

        lifecycleScope.launch {
            try {
                Log.d("ManageOrders", "Searching for order: $orderNumber")

                // Search for order by order_number
                val orders = supabase.postgrest["orders"]
                    .select {
                        filter {
                            eq("order_number", orderNumber)
                        }
                    }
                    .decodeList<Order>()

                if (orders.isEmpty()) {
                    runOnUiThread {
                        showError("Order not found with number: $orderNumber")
                        setLoadingState(false)
                    }
                    return@launch
                }

                val order = orders.first()
                currentOrder = order

                // Fetch order items for this order
                val orderItems = supabase.postgrest["order_items"]
                    .select {
                        filter {
                            eq("order_number", orderNumber)
                        }
                    }
                    .decodeList<OrderItem>()

                Log.d("ManageOrders", "Found ${orderItems.size} items for order $orderNumber")

                // Fetch product details for each order item
                val orderItemsWithProductDetails = fetchProductDetails(orderItems)

                runOnUiThread {
                    displayOrderDetails(order, orderItemsWithProductDetails)
                    setLoadingState(false)
                }

            } catch (e: Exception) {
                Log.e("ManageOrders", "Error searching order: ${e.message}", e)
                runOnUiThread {
                    showError("Error searching order: ${e.message}")
                    setLoadingState(false)
                }
            }
        }
    }

    private suspend fun fetchProductDetails(orderItems: List<OrderItem>): List<OrderItem> {
        val orderItemsWithDetails = mutableListOf<OrderItem>()

        for (orderItem in orderItems) {
            try {
                // Fetch product details using product_id
                val products = supabase.postgrest["products"]
                    .select {
                        filter {
                            eq("product_id", orderItem.product_id)
                        }
                    }
                    .decodeList<Product>()

                if (products.isNotEmpty()) {
                    val product = products.first()
                    // Create a new OrderItem with product details
                    val enrichedOrderItem = orderItem.copy(
                        product_name = product.name,
                        product_image_url = product.getFirstImageUrl()
                    )
                    orderItemsWithDetails.add(enrichedOrderItem)
                } else {
                    // If product not found, use basic info
                    orderItemsWithDetails.add(orderItem.copy(
                        product_name = "Product Not Found",
                        product_image_url = null
                    ))
                }
            } catch (e: Exception) {
                Log.e("ManageOrders", "Error fetching product details: ${e.message}")
                // Add order item without product details
                orderItemsWithDetails.add(orderItem)
            }
        }

        return orderItemsWithDetails
    }

    private fun displayOrderDetails(order: Order, orderItems: List<OrderItem>) {
        // Update order header information
        binding.tvOrderNumber.text = order.order_number ?: "N/A"
        binding.tvTotalAmount.text = "$${order.total_amount}"
        binding.tvPaymentMethod.text = order.payment_method
        binding.tvPaymentStatus.text = order.payment_status
        binding.tvOrderStatus.text = order.order_status

        // Update status background colors
        updateStatusBackground(binding.tvPaymentStatus, order.payment_status)
        updateStatusBackground(binding.tvOrderStatus, order.order_status)

        // Update order items
        orderItemsList.clear()
        orderItemsList.addAll(orderItems)
        adapter.notifyDataSetChanged()

        // Show order details section
        binding.scrollViewOrderDetails.visibility = android.view.View.VISIBLE
        binding.tvEmpty.visibility = android.view.View.GONE
        binding.tvError.visibility = android.view.View.GONE
    }

    private fun updateStatusBackground(textView: android.widget.TextView, status: String) {
        val backgroundRes = when (status.lowercase()) {
            "completed", "paid", "delivered" -> R.drawable.bg_status_completed
            "pending", "processing" -> R.drawable.bg_status_pending
            "cancelled", "failed", "refunded" -> R.drawable.bg_status_cancelled
            "shipped" -> R.drawable.bg_status_shipped
            else -> R.drawable.bg_status_pending
        }

        textView.setBackgroundResource(backgroundRes)
    }

    private fun hideOrderDetails() {
        binding.scrollViewOrderDetails.visibility = android.view.View.GONE
        binding.tvEmpty.visibility = android.view.View.VISIBLE
        binding.tvError.visibility = android.view.View.GONE
        orderItemsList.clear()
        adapter.notifyDataSetChanged()
        currentOrder = null
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnSearch.isEnabled = !isLoading
        binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        binding.etOrderNumber.isEnabled = !isLoading

        if (isLoading) {
            binding.btnSearch.text = "Searching..."
        } else {
            binding.btnSearch.text = "Search"
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = android.view.View.VISIBLE
        binding.scrollViewOrderDetails.visibility = android.view.View.GONE
        binding.tvEmpty.visibility = android.view.View.GONE
    }

    private fun clearError() {
        binding.tvError.visibility = android.view.View.GONE
    }
}