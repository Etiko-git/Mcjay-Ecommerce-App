package com.solih.mcjay.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.R
import com.solih.mcjay.adapters.SellerOrderItemsAdapter
import com.solih.mcjay.databinding.ActivitySellerOrderDetailBinding
import com.solih.mcjay.models.Order
import com.solih.mcjay.models.OrderItem
import com.solih.mcjay.models.User
import com.solih.mcjay.models.Product // Add this import
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SellerOrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerOrderDetailBinding
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var orderItemsAdapter: SellerOrderItemsAdapter

    private var orderId: Int = 0
    private var orderNumber: String = ""
    private lateinit var currentOrder: Order
    private val orderItems = mutableListOf<OrderItem>()
    private lateinit var customer: User
    private val productMap = mutableMapOf<String, Product>() // Map to store product details

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        getIntentData()
        setupRecyclerView()
        setupClickListeners()
        loadOrderDetails()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Order Details"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun getIntentData() {
        orderId = intent.getIntExtra("order_id", 0)
        orderNumber = intent.getStringExtra("order_number") ?: ""

        if (orderId == 0) {
            Toast.makeText(this, "Invalid order", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupRecyclerView() {
        orderItemsAdapter = SellerOrderItemsAdapter(orderItems, productMap)
        binding.rvOrderItems.apply {
            layoutManager = LinearLayoutManager(this@SellerOrderDetailActivity)
            adapter = orderItemsAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnRetry.setOnClickListener {
            loadOrderDetails()
        }
    }

    private fun loadOrderDetails() {
        showLoadingState()

        scope.launch {
            try {
                // Fetch order details
                val orders = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("orders")
                        .select {
                            filter {
                                eq("order_id", orderId)
                            }
                        }
                        .decodeList<Order>()
                }

                if (orders.isEmpty()) {
                    showErrorState("Order not found")
                    return@launch
                }

                currentOrder = orders.first()

                // Fetch order items for this order and seller
                val currentUser = supabase.auth.currentUserOrNull()
                val sellerId = currentUser?.id ?: ""

                val items = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("order_items")
                        .select {
                            filter {
                                eq("order_id", orderId)
                                eq("seller_id", sellerId)
                            }
                        }
                        .decodeList<OrderItem>()
                }

                orderItems.clear()
                orderItems.addAll(items)

                // Fetch product details for all order items
                val productIds = items.map { it.product_id }.distinct()
                if (productIds.isNotEmpty()) {
                    val products = withContext(Dispatchers.IO) {
                        supabase.postgrest.from("products")
                            .select {
                                filter {
                                    isIn("product_id", productIds)
                                }
                            }
                            .decodeList<Product>()
                    }

                    productMap.clear()
                    products.forEach { product ->
                        productMap[product.product_id] = product
                    }
                }

                // Fetch customer information
                val customers = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("users")
                        .select {
                            filter {
                                eq("id", currentOrder.user_id)
                            }
                        }
                        .decodeList<User>()
                }

                if (customers.isNotEmpty()) {
                    customer = customers.first()
                } else {
                    showErrorState("Customer information not found")
                    return@launch
                }

                updateUI()
                showContentState()

            } catch (e: Exception) {
                showErrorState("Error loading order details: ${e.message}")
            }
        }
    }

    private fun updateUI() {
        // Order summary
        binding.tvOrderNumber.text = currentOrder.order_number ?: "N/A"
        binding.tvOrderDate.text = formatDate(currentOrder.created_at)
        binding.tvOrderStatus.text = currentOrder.order_status
        updateStatusColor(currentOrder.order_status)

        // Customer information
        binding.tvCustomerName.text = customer.name ?: "Unknown Customer"
        binding.tvCustomerEmail.text = customer.email ?: "No email"
        binding.tvCustomerPhone.text = customer.mobile ?: "No phone number"
        binding.tvShippingAddress.text = currentOrder.shipping_address

        // Order items
        orderItemsAdapter.notifyDataSetChanged()

        // Calculate totals
        updateTotals()

        // Setup status actions
        setupStatusActions()
    }

    private fun updateStatusColor(status: String) {
        val statusColor = when (status.lowercase(Locale.getDefault())) {
            "pending" -> R.color.orange
            "confirmed" -> R.color.blue
            "processing" -> R.color.purple
            "shipped" -> R.color.teal
            "delivered" -> R.color.green
            "cancelled" -> R.color.red
            else -> R.color.gray
        }

        binding.tvOrderStatus.setBackgroundColor(ContextCompat.getColor(this, statusColor))
    }

    private fun updateTotals() {
        val subtotal = orderItems.sumOf { it.subtotal }
        val shippingCost = 0.0 // You might want to fetch this from your order data
        val taxAmount = 0.0 // You might want to fetch this from your order data
        val total = subtotal + shippingCost + taxAmount

        binding.tvSubtotal.text = "$${String.format("%.2f", subtotal)}"
        binding.tvShippingCost.text = "$${String.format("%.2f", shippingCost)}"
        binding.tvTaxAmount.text = "$${String.format("%.2f", taxAmount)}"
        binding.tvTotalAmount.text = "$${String.format("%.2f", total)}"
    }

    private fun setupStatusActions() {
        binding.statusActions.removeAllViews()

        val currentStatus = currentOrder.order_status.lowercase(Locale.getDefault())

        when (currentStatus) {
            "pending" -> {
                addStatusButton("Confirm Order", "confirmed") {
                    updateOrderStatus("Confirmed")
                }
                addStatusButton("Cancel Order", "cancelled") {
                    updateOrderStatus("Cancelled")
                }
            }
            "confirmed" -> {
                addStatusButton("Start Processing", "processing") {
                    updateOrderStatus("Processing")
                }
            }
            "processing" -> {
                addStatusButton("Mark as Shipped", "shipped") {
                    updateOrderStatus("Shipped")
                }
            }
            "shipped" -> {
                addStatusButton("Mark as Delivered", "delivered") {
                    updateOrderStatus("Delivered")
                }
            }
        }
    }

    private fun addStatusButton(text: String, status: String, onClick: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            setBackgroundColor(ContextCompat.getColor(this@SellerOrderDetailActivity, R.color.purple_700))
            setTextColor(ContextCompat.getColor(this@SellerOrderDetailActivity, android.R.color.white))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setOnClickListener { onClick() }
        }
        binding.statusActions.addView(button)
    }

    private fun updateOrderStatus(newStatus: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("orders")
                        .update({
                            set("order_status", newStatus)
                            set("updated_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                        }) {
                            filter {
                                eq("order_id", orderId)
                            }
                        }
                }

                // Also update order items status
                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("order_items")
                        .update({
                            set("item_status", newStatus)
                            set("updated_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                        }) {
                            filter {
                                eq("order_id", orderId)
                                eq("seller_id", supabase.auth.currentUserOrNull()?.id ?: "")
                            }
                        }
                }

                currentOrder = currentOrder.copy(order_status = newStatus)
                updateUI()
                Toast.makeText(this@SellerOrderDetailActivity, "Order status updated to $newStatus", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(this@SellerOrderDetailActivity, "Error updating status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatDate(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "Unknown date"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun showLoadingState() {
        binding.progressBar.visibility = View.VISIBLE
        binding.content.visibility = View.GONE
        binding.errorState.visibility = View.GONE
    }

    private fun showContentState() {
        binding.progressBar.visibility = View.GONE
        binding.content.visibility = View.VISIBLE
        binding.errorState.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.content.visibility = View.GONE
        binding.errorState.visibility = View.VISIBLE
        binding.tvEmptyState.text = message
    }
}