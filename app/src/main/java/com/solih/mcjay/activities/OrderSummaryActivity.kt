package com.solih.mcjay.activities

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.solih.mcjay.R
import com.solih.mcjay.models.Order
import com.solih.mcjay.models.OrderItem
import com.solih.mcjay.models.Product
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class OrderSummaryActivity : AppCompatActivity() {

    private lateinit var rvOrderItems: RecyclerView
    private lateinit var tvOrderNumber: TextView
    private lateinit var tvOrderDate: TextView
    private lateinit var tvSubtotal: TextView
    private lateinit var tvShipping: TextView
    private lateinit var tvTax: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvShippingAddress: TextView
    private lateinit var tvPaymentMethod: TextView
    private lateinit var tvOrderStatus: TextView
    private lateinit var tvDeliveryEstimate: TextView
    private lateinit var btnBackToHome: Button
    private lateinit var btnTrackOrder: Button
    private lateinit var progressBar: ProgressBar

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var orderItemsAdapter: OrderItemsAdapter

    private var orderId: Int = -1
    private var orderNumber: String = ""
    private var orderItems = mutableListOf<OrderItem>()
    private val productsMap = mutableMapOf<String, Product>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_summary)

        Log.d("OrderSummaryActivity", "Activity created successfully")

        // Initialize views
        initializeViews()

        // Get order details from intent
        orderId = intent.getIntExtra("order_id", -1)
        orderNumber = intent.getStringExtra("order_number") ?: ""

        Log.d("OrderSummaryActivity", "Received - Order ID: $orderId, Order Number: $orderNumber")

        if (orderId == -1 || orderNumber.isEmpty()) {
            Log.e("OrderSummaryActivity", "Invalid order details received")
            Toast.makeText(this, "Order details not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("OrderSummaryActivity", "Setting up UI for order")
        setupRecyclerView()
        setupClickListeners()
        loadOrderDetails()
    }

    private fun initializeViews() {
        rvOrderItems = findViewById(R.id.rvOrderItems)
        tvOrderNumber = findViewById(R.id.tvOrderNumber)
        tvOrderDate = findViewById(R.id.tvOrderDate)
        tvSubtotal = findViewById(R.id.tvSubtotal)
        tvShipping = findViewById(R.id.tvShipping)
        tvTax = findViewById(R.id.tvTax)
        tvTotal = findViewById(R.id.tvTotal)
        tvShippingAddress = findViewById(R.id.tvShippingAddress)
        tvPaymentMethod = findViewById(R.id.tvPaymentMethod)
        tvOrderStatus = findViewById(R.id.tvOrderStatus)
        tvDeliveryEstimate = findViewById(R.id.tvDeliveryEstimate)
        btnBackToHome = findViewById(R.id.btnBackToHome)
        btnTrackOrder = findViewById(R.id.btnTrackOrder)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        orderItemsAdapter = OrderItemsAdapter(orderItems, productsMap)
        rvOrderItems.layoutManager = LinearLayoutManager(this)
        rvOrderItems.adapter = orderItemsAdapter
    }

    private fun setupClickListeners() {
        btnBackToHome.setOnClickListener {
            // Finish this activity and go back
            Toast.makeText(this, "Home feature coming soon!", Toast.LENGTH_SHORT).show()

        }

        btnTrackOrder.setOnClickListener {
            Toast.makeText(this, "Tracking feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadOrderDetails() {
        scope.launch {
            try {
                progressBar.visibility = android.view.View.VISIBLE

                // Load order details
                val order = withContext(Dispatchers.IO) {
                    com.solih.mcjay.SupabaseClientInstance.client.postgrest["orders"]
                        .select {
                            filter { eq("order_id", orderId) }
                        }
                        .decodeSingle<Order>()
                }

                // Load order items
                val items = withContext(Dispatchers.IO) {
                    com.solih.mcjay.SupabaseClientInstance.client.postgrest["order_items"]
                        .select {
                            filter { eq("order_id", orderId) }
                        }
                        .decodeList<OrderItem>()
                }

                Log.d("OrderSummary", "Loaded ${items.size} order items")

                // Load product details for order items
                val productIds = items.map { it.product_id }.distinct()
                for (productId in productIds) {
                    try {
                        val product = withContext(Dispatchers.IO) {
                            com.solih.mcjay.SupabaseClientInstance.client.postgrest["products"]
                                .select {
                                    // Try both product_id and id fields
                                    filter {
                                        eq("product_id", productId)
                                    }
                                }
                                .decodeList<Product>()
                        }
                        if (product.isNotEmpty()) {
                            productsMap[productId] = product[0]
                        } else {
                            Log.w("OrderSummary", "Product not found: $productId")
                        }
                    } catch (e: Exception) {
                        Log.e("OrderSummary", "Error loading product $productId", e)
                    }
                }

                // Update UI with order data
                updateUI(order, items)

            } catch (e: Exception) {
                Log.e("OrderSummary", "Error loading order details", e)
                Toast.makeText(this@OrderSummaryActivity, "Error loading order details", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun updateUI(order: Order, items: List<OrderItem>) {
        try {
            // Update header
            tvOrderNumber.text = "Order #${order.order_number}"
            tvOrderDate.text = formatDate(order.created_at)

            // Update order items
            orderItems.clear()
            orderItems.addAll(items)
            orderItemsAdapter.notifyDataSetChanged()

            // Calculate totals
            val subtotal = items.sumOf { it.subtotal }
            val shipping = if (subtotal > 50.0) 0.0 else 5.0
            val tax = subtotal * 0.08
            val total = subtotal + shipping + tax

            // Update totals
            tvSubtotal.text = "$${String.format("%.2f", subtotal)}"
            tvShipping.text = "$${String.format("%.2f", shipping)}"
            tvTax.text = "$${String.format("%.2f", tax)}"
            tvTotal.text = "$${String.format("%.2f", total)}"

            // Update shipping and payment
            tvShippingAddress.text = order.shipping_address
            tvPaymentMethod.text = order.payment_method

            // Update order status
            tvOrderStatus.text = order.order_status
            tvDeliveryEstimate.text = getDeliveryEstimate(order.order_status)

        } catch (e: Exception) {
            Log.e("OrderSummary", "Error updating UI", e)
        }
    }

    private fun formatDate(dateString: String?): String {
        return if (!dateString.isNullOrEmpty()) {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                "Just now"
            }
        } else {
            "Just now"
        }
    }

    private fun getDeliveryEstimate(orderStatus: String): String {
        return when (orderStatus) {
            "Confirmed" -> "Estimated delivery: 3-5 business days"
            "Processing" -> "Estimated delivery: 2-4 business days"
            "Shipped" -> "Estimated delivery: 1-2 business days"
            "Delivered" -> "Delivered successfully"
            else -> "Processing your order"
        }
    }

    // Order Items Adapter
    private class OrderItemsAdapter(
        private val orderItems: List<OrderItem>,
        private val productsMap: Map<String, Product>
    ) : RecyclerView.Adapter<OrderItemsAdapter.ViewHolder>() {

        class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val productName: TextView = itemView.findViewById(R.id.tvProductName)
            val productPrice: TextView = itemView.findViewById(R.id.tvPrice)
            val quantity: TextView = itemView.findViewById(R.id.tvQuantity)
            val subtotal: TextView = itemView.findViewById(R.id.tvSubtotal)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_order_product, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            try {
                val orderItem = orderItems[position]
                val product = productsMap[orderItem.product_id]

                holder.productName.text = product?.name ?: "Product"
                holder.productPrice.text = "$${String.format("%.2f", orderItem.price)}"
                holder.quantity.text = "Qty: ${orderItem.quantity}"
                holder.subtotal.text = "$${String.format("%.2f", orderItem.subtotal)}"
            } catch (e: Exception) {
                Log.e("OrderItemsAdapter", "Error binding view holder", e)
            }
        }

        override fun getItemCount(): Int = orderItems.size
    }
}