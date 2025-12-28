package com.solih.mcjay.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.adapters.AllOrdersAdapter
import com.solih.mcjay.adapters.OrderItemsAdapter
import com.solih.mcjay.databinding.ActivityManageOrdersBinding
import com.solih.mcjay.models.Order
import com.solih.mcjay.models.OrderItem
import com.solih.mcjay.models.Product
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ManageOrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageOrdersBinding
    private val supabase = SupabaseClientInstance.client
    private lateinit var allOrdersAdapter: AllOrdersAdapter
    private lateinit var orderItemsAdapter: OrderItemsAdapter

    private val allOrdersList = mutableListOf<Order>()
    private val orderItemsList = mutableListOf<OrderItem>()

    private var currentOrder: Order? = null
    private var fromDate: String? = null
    private var toDate: String? = null
    private var sortBy: String = "created_at_desc"
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        setupListeners()
        setupDatePickers()

        // Load all orders initially
        loadAllOrders()
    }

    private fun setupRecyclerViews() {
        // Setup All Orders RecyclerView
        allOrdersAdapter = AllOrdersAdapter(allOrdersList) { order ->
            showOrderDetails(order)
        }
        binding.rvAllOrders.layoutManager = LinearLayoutManager(this)
        binding.rvAllOrders.adapter = allOrdersAdapter

        // Setup Order Items RecyclerView
        orderItemsAdapter = OrderItemsAdapter(orderItemsList)
        binding.rvOrderItems.layoutManager = LinearLayoutManager(this)
        binding.rvOrderItems.adapter = orderItemsAdapter
    }

    private fun setupListeners() {
        // Back button
        binding.ivBack.setOnClickListener {
            onBackPressed()
        }

        // Search functionality
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

        // Real-time search
        binding.etOrderNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                loadAllOrders()
            }
        })

        // Filter button
        binding.btnFilter.setOnClickListener {
            toggleFilterContainer()
        }

        // Sort button
        binding.btnSort.setOnClickListener {
            showSortOptionsDialog()
        }

        // Apply filter button
        binding.btnApplyFilter.setOnClickListener {
            applyDateFilter()
        }

        // Clear filter button
        binding.btnClearFilter.setOnClickListener {
            clearDateFilter()
        }

        // Close details button
        binding.btnCloseDetails.setOnClickListener {
            hideOrderDetails()
        }
    }

    private fun setupDatePickers() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        binding.etFromDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val selectedDate = Calendar.getInstance()
                    selectedDate.set(year, month, day)
                    binding.etFromDate.setText(dateFormat.format(selectedDate.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.etToDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val selectedDate = Calendar.getInstance()
                    selectedDate.set(year, month, day)
                    binding.etToDate.setText(dateFormat.format(selectedDate.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun toggleFilterContainer() {
        val isVisible = binding.filterContainer.visibility == android.view.View.VISIBLE
        binding.filterContainer.visibility = if (isVisible) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun showSortOptionsDialog() {
        val sortOptions = listOf(
            "Newest First" to "created_at_desc",
            "Oldest First" to "created_at_asc",
            "Total Amount (High to Low)" to "amount_desc",
            "Total Amount (Low to High)" to "amount_asc",
            "Order Number (A-Z)" to "order_number_asc",
            "Order Number (Z-A)" to "order_number_desc"
        )

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_sort_options)

        val listView = dialog.findViewById<android.widget.ListView>(R.id.listViewSortOptions)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            sortOptions.map { it.first })

        listView?.adapter = adapter
        listView?.setOnItemClickListener { _, _, position, _ ->
            sortBy = sortOptions[position].second
            loadAllOrders()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadAllOrders() {
        setLoadingState(true)
        clearError()

        lifecycleScope.launch {
            try {
                Log.d("ManageOrders", "Loading all orders...")

                // Simple query - just get all orders
                val orders = supabase.postgrest["orders"]
                    .select()
                    .decodeList<Order>()

                runOnUiThread {
                    allOrdersList.clear()
                    allOrdersList.addAll(orders)
                    allOrdersAdapter.notifyDataSetChanged()

                    if (orders.isEmpty()) {
                        showError("No orders found")
                    }

                    setLoadingState(false)
                }

            } catch (e: Exception) {
                Log.e("ManageOrders", "Error loading orders: ${e.message}", e)
                runOnUiThread {
                    showError("Error loading orders: ${e.message}")
                    setLoadingState(false)
                }
            }
        }
    }

    private fun searchOrder() {
        val orderNumber = binding.etOrderNumber.text.toString().trim()

        if (orderNumber.isEmpty()) {
            showError("Please enter an order number")
            return
        }

        setLoadingState(true)
        clearError()

        lifecycleScope.launch {
            try {
                Log.d("ManageOrders", "Searching for order: $orderNumber")

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
                showOrderDetails(order)

            } catch (e: Exception) {
                Log.e("ManageOrders", "Error searching order: ${e.message}", e)
                runOnUiThread {
                    showError("Error searching order: ${e.message}")
                    setLoadingState(false)
                }
            }
        }
    }

    private fun showOrderDetails(order: Order) {
        currentOrder = order

        lifecycleScope.launch {
            try {
                setLoadingState(true)

                // Fetch order items
                val orderItems = supabase.postgrest["order_items"]
                    .select {
                        filter {
                            eq("order_number", order.order_number ?: "")
                        }
                    }
                    .decodeList<OrderItem>()

                // Fetch product details
                val orderItemsWithProductDetails = fetchProductDetails(orderItems)

                runOnUiThread {
                    displayOrderDetails(order, orderItemsWithProductDetails)
                    setLoadingState(false)
                }

            } catch (e: Exception) {
                Log.e("ManageOrders", "Error loading order details: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@ManageOrdersActivity,
                        "Error loading order details", Toast.LENGTH_SHORT).show()
                    setLoadingState(false)
                }
            }
        }
    }

    private suspend fun fetchProductDetails(orderItems: List<OrderItem>): List<OrderItem> {
        val orderItemsWithDetails = mutableListOf<OrderItem>()

        for (orderItem in orderItems) {
            try {
                val products = supabase.postgrest["products"]
                    .select {
                        filter {
                            eq("product_id", orderItem.product_id)
                        }
                    }
                    .decodeList<Product>()

                if (products.isNotEmpty()) {
                    val product = products.first()
                    val enrichedOrderItem = orderItem.copy(
                        product_name = product.name,
                        product_image_url = product.getFirstImageUrl()
                    )
                    orderItemsWithDetails.add(enrichedOrderItem)
                } else {
                    orderItemsWithDetails.add(orderItem.copy(
                        product_name = "Product Not Found",
                        product_image_url = null
                    ))
                }
            } catch (e: Exception) {
                Log.e("ManageOrders", "Error fetching product details: ${e.message}")
                orderItemsWithDetails.add(orderItem)
            }
        }

        return orderItemsWithDetails
    }

    private fun displayOrderDetails(order: Order, orderItems: List<OrderItem>) {
        // Update order header information
        binding.tvOrderNumber.text = order.order_number ?: "N/A"
        binding.tvOrderDate.text = formatDate(order.created_at ?: "")
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
        orderItemsAdapter.notifyDataSetChanged()

        // Show order details section
        binding.rvAllOrders.visibility = android.view.View.GONE
        binding.scrollViewOrderDetails.visibility = android.view.View.VISIBLE
        binding.tvError.visibility = android.view.View.GONE
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
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

    private fun applyDateFilter() {
        fromDate = binding.etFromDate.text.toString().trim()
        toDate = binding.etToDate.text.toString().trim()

        // Validate dates
        if (!fromDate.isNullOrEmpty() && !toDate.isNullOrEmpty()) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            try {
                val from = dateFormat.parse(fromDate)
                val to = dateFormat.parse(toDate)

                if (from != null && to != null && from.after(to)) {
                    showError("From date cannot be after To date")
                    return
                }
            } catch (e: Exception) {
                showError("Invalid date format")
                return
            }
        }

        binding.filterContainer.visibility = android.view.View.GONE
        loadAllOrders()
    }

    private fun clearDateFilter() {
        binding.etFromDate.text?.clear()
        binding.etToDate.text?.clear()
        fromDate = null
        toDate = null
        binding.filterContainer.visibility = android.view.View.GONE
        loadAllOrders()
    }

    private fun hideOrderDetails() {
        binding.scrollViewOrderDetails.visibility = android.view.View.GONE
        binding.rvAllOrders.visibility = android.view.View.VISIBLE
        orderItemsList.clear()
        orderItemsAdapter.notifyDataSetChanged()
        currentOrder = null
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnSearch.isEnabled = !isLoading
        binding.btnFilter.isEnabled = !isLoading
        binding.btnSort.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = android.view.View.VISIBLE
    }

    private fun clearError() {
        binding.tvError.visibility = android.view.View.GONE
    }
}