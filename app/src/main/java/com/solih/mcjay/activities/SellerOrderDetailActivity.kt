package com.solih.mcjay.activities

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

class SellerOrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerOrderDetailBinding
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var orderItemsAdapter: SellerOrderItemsAdapter

    private var orderId: Int = -1
    private var orderNumber: String = ""
    private val orderItems = mutableListOf<OrderItem>()
    private lateinit var currentOrder: Order
    private var customer: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get order data from intent
        orderId = intent.getIntExtra("order_id", -1)
        orderNumber = intent.getStringExtra("order_number") ?: ""

        if (orderId == -1) {
            Toast.makeText(this, "Invalid order", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupRetryButton()
        loadOrderDetails()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Order #$orderNumber"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        orderItemsAdapter = SellerOrderItemsAdapter(orderItems)
        binding.rvOrderItems.apply {
            layoutManager = LinearLayoutManager(this@SellerOrderDetailActivity)
            adapter = orderItemsAdapter
        }
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            loadOrderDetails()
        }
    }

    private fun loadOrderDetails() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.content.visibility = android.view.View.GONE
        binding.errorState.visibility = android.view.View.GONE

        scope.launch {
            try {
                Log.d("OrderDetail", "Loading order details for orderId: $orderId")

                // Load order details
                val order = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("orders")
                        .select {
                            filter {
                                eq("order_id", orderId)
                            }
                        }
                        .decodeSingle<Order>()
                }

                Log.d("OrderDetail", "Order loaded: ${order.order_number}")
                currentOrder = order

                // Load customer information from users table - FIXED: Use decodeList and handle empty case
                Log.d("OrderDetail", "Loading customer info for user: ${order.user_id}")
                val customers = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("users")
                        .select {
                            filter {
                                eq("id", order.user_id)
                            }
                        }
                        .decodeList<User>()
                }

                if (customers.isNotEmpty()) {
                    customer = customers.first()
                    Log.d("OrderDetail", "Customer loaded: ${customer?.name}")
                } else {
                    Log.w("OrderDetail", "No customer found for user ID: ${order.user_id}")
                    // Create a placeholder customer
                    customer = User(
                        id = order.user_id,
                        name = "Unknown Customer",
                        email = "No email",
                        username = "Not provided"
                    )
                }

                // Load order items for this specific order and seller
                val currentUser = supabase.auth.currentUserOrNull()
                val sellerId = currentUser?.id ?: ""
                Log.d("OrderDetail", "Loading order items for seller: $sellerId")

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

                Log.d("OrderDetail", "Found ${items.size} order items")

                // Fetch product details including images for each order item
                val itemsWithProductDetails = mutableListOf<OrderItem>()
                for (orderItem in items) {
                    try {
                        Log.d("OrderDetail", "Fetching product for product_id: ${orderItem.product_id}")
                        val products = withContext(Dispatchers.IO) {
                            supabase.postgrest.from("products")
                                .select {
                                    filter {
                                        eq("product_id", orderItem.product_id.toString())
                                    }
                                }
                                .decodeList<com.solih.mcjay.models.Product>()
                        }

                        val product = if (products.isNotEmpty()) products.first() else null

                        if (product == null) {
                            Log.w("OrderDetail", "Product not found for product_id: ${orderItem.product_id}")
                        } else {
                            Log.d("OrderDetail", "Product found: ${product.name}")
                        }

                        val updatedItem = orderItem.copy(
                            product_name = product?.name ?: "Unknown Product",
                            product_image_url = product?.getFirstImageUrl() ?: ""
                        )
                        itemsWithProductDetails.add(updatedItem)

                    } catch (e: Exception) {
                        Log.e("OrderDetail", "Error fetching product for item ${orderItem.product_id}: ${e.message}")
                        // Add the item without product details
                        itemsWithProductDetails.add(orderItem.copy(
                            product_name = "Unknown Product",
                            product_image_url = ""
                        ))
                    }
                }

                orderItems.clear()
                orderItems.addAll(itemsWithProductDetails)
                orderItemsAdapter.notifyDataSetChanged()

                Log.d("OrderDetail", "Updating UI with ${itemsWithProductDetails.size} items")
                updateUI(order, customer, itemsWithProductDetails)

            } catch (e: Exception) {
                Log.e("OrderDetail", "Error loading order details: ${e.message}", e)
                showErrorState("Error loading order details: ${e.message}")
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun showErrorState(errorMessage: String) {
        binding.content.visibility = android.view.View.GONE
        binding.errorState.visibility = android.view.View.VISIBLE
        binding.tvEmptyState.text = errorMessage
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun updateUI(order: Order, customer: User?, items: List<OrderItem>) {
        // Update order information
        binding.tvOrderNumber.text = order.order_number ?: "N/A"
        binding.tvOrderDate.text = formatDate(order.created_at ?: "")
        binding.tvOrderStatus.text = order.order_status
        binding.tvShippingAddress.text = order.shipping_address ?: "No shipping address"

        // Update customer information from users table - handle null customer
        binding.tvCustomerName.text = customer?.name ?: "Unknown Customer"
        binding.tvCustomerEmail.text = customer?.email ?: "No email"
        binding.tvCustomerPhone.text = customer?.mobile ?: "Not provided"

        // Update order summary
        val subtotal = items.sumOf { it.subtotal }
        val shippingCost =  0.0
        val taxAmount =  0.0
        val total = order.total_amount

        binding.tvSubtotal.text = "$${String.format("%.2f", subtotal)}"
        binding.tvShippingCost.text = "$${String.format("%.2f", shippingCost)}"
        binding.tvTaxAmount.text = "$${String.format("%.2f", taxAmount)}"
        binding.tvTotalAmount.text = "$${String.format("%.2f", total)}"

        // Update status badge color based on order status
        updateStatusBadge(order.order_status)

        // Setup status change buttons if needed
        setupStatusActions(order.order_status)

        // Show the content and hide error state
        binding.content.visibility = android.view.View.VISIBLE
        binding.errorState.visibility = android.view.View.GONE
    }

    private fun formatDate(dateString: String): String {
        if (dateString.isBlank()) return "N/A"

        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            "Invalid date"
        }
    }

    private fun updateStatusBadge(status: String) {
        val colorRes = when (status.lowercase()) {
            "pending" -> R.color.orange
            "confirmed" -> R.color.blue
            "processing" -> R.color.purple_700
            "shipped" -> R.color.info_blue
            "delivered" -> R.color.success_green
            "cancelled" -> R.color.error_red
            else -> R.color.gray_600
        }

        val color = ContextCompat.getColor(this, colorRes)
        binding.tvOrderStatus.setBackgroundColor(color)

        // Add padding and corner radius for better appearance
        binding.tvOrderStatus.setPadding(16, 8, 16, 8)
    }

    private fun setupStatusActions(currentStatus: String) {
        binding.statusActions.removeAllViews()

        val nextStatus = when (currentStatus.lowercase()) {
            "pending" -> "Confirm Order"
            "confirmed" -> "Start Processing"
            "processing" -> "Mark as Shipped"
            "shipped" -> "Mark as Delivered"
            else -> null
        }

        if (nextStatus != null) {
            val button = android.widget.Button(this).apply {
                text = nextStatus
                setBackgroundColor(ContextCompat.getColor(context, R.color.purple_700))
                setTextColor(ContextCompat.getColor(context, R.color.white))
                setPadding(32, 16, 32, 16)
                textSize = 14f

                setOnClickListener {
                    updateOrderStatus(getNextStatus(currentStatus))
                }
            }
            binding.statusActions.addView(button)
        }

        // Add cancel button for pending and confirmed orders
        if (currentStatus.lowercase() in listOf("pending", "confirmed")) {
            val cancelButton = android.widget.Button(this).apply {
                text = "Cancel Order"
                setBackgroundColor(ContextCompat.getColor(context, R.color.error_red))
                setTextColor(ContextCompat.getColor(context, R.color.white))
                setPadding(32, 16, 32, 16)
                textSize = 14f

                setOnClickListener {
                    showCancelConfirmation()
                }
            }
            binding.statusActions.addView(cancelButton)
        }

        // Add some spacing between buttons if both exist
        if (binding.statusActions.childCount > 1) {
            for (i in 0 until binding.statusActions.childCount - 1) {
                val view = binding.statusActions.getChildAt(i)
                val layoutParams = view.layoutParams as android.widget.LinearLayout.LayoutParams
                layoutParams.bottomMargin = 8
                view.layoutParams = layoutParams
            }
        }
    }

    private fun getNextStatus(currentStatus: String): String {
        return when (currentStatus.lowercase()) {
            "pending" -> "Confirmed"
            "confirmed" -> "Processing"
            "processing" -> "Shipped"
            "shipped" -> "Delivered"
            else -> currentStatus
        }
    }

    private fun updateOrderStatus(newStatus: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("orders")
                        .update(
                            mapOf(
                                "order_status" to newStatus,
                                "updated_at" to Instant.now().toString()
                            )
                        ) {
                            filter {
                                eq("order_id", orderId)
                            }
                        }
                }

                // Update local order and UI
                currentOrder = currentOrder.copy(order_status = newStatus)
                updateUI(currentOrder, customer, orderItems)
                Toast.makeText(
                    this@SellerOrderDetailActivity,
                    "Order status updated to $newStatus",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Log.e("OrderDetail", "Error updating order status: ${e.message}", e)
                Toast.makeText(
                    this@SellerOrderDetailActivity,
                    "Error updating order status: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showCancelConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Order")
            .setMessage("Are you sure you want to cancel this order? This action cannot be undone.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                updateOrderStatus("Cancelled")
            }
            .setNegativeButton("No", null)
            .show()
    }
}