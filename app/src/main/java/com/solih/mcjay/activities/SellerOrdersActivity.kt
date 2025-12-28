package com.solih.mcjay.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.R
import com.solih.mcjay.adapters.SellerOrdersAdapter
import com.solih.mcjay.databinding.ActivitySellerOrdersBinding
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
import java.util.*

class SellerOrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerOrdersBinding
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var ordersAdapter: SellerOrdersAdapter

    private var sellerId: String = ""
    private val allOrders = mutableListOf<Order>()
    private val displayedOrders = mutableListOf<Order>()
    private val orderItemsMap = mutableMapOf<Int, List<OrderItem>>()
    private val customerMap = mutableMapOf<String, User>()
    private var currentFilter = "All"
    private var selectedDate: String? = null
    private var currentSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFilterSpinner()
        setupDatePicker()
        setupSearch()
        setupClickListeners()
        getCurrentSeller()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Orders"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        ordersAdapter = SellerOrdersAdapter(displayedOrders, orderItemsMap, customerMap) { order ->
            showOrderDetails(order)
        }
        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(this@SellerOrdersActivity)
            adapter = ordersAdapter
        }
    }

    private fun setupFilterSpinner() {
        // Status filter
        val statusOptions = arrayOf("All Orders", "Pending", "Confirmed", "Processing", "Shipped", "Delivered", "Cancelled")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusOptions)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilter.adapter = statusAdapter

        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = when (position) {
                    0 -> "All"
                    1 -> "Pending"
                    2 -> "Confirmed"
                    3 -> "Processing"
                    4 -> "Shipped"
                    5 -> "Delivered"
                    6 -> "Cancelled"
                    else -> "All"
                }
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDatePicker() {
        binding.dateFilterLayout.setOnClickListener {
            showDatePickerDialog()
        }

        binding.ivClearDate.setOnClickListener {
            clearDateFilter()
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                // Note: Month is 0-based in DatePickerDialog
                val actualMonth = selectedMonth + 1

                // Format the date as yyyy-MM-dd
                selectedDate = String.format("%04d-%02d-%02d", selectedYear, actualMonth, selectedDay)

                // Update UI
                binding.tvDateFilter.text = selectedDate
                binding.ivClearDate.visibility = View.VISIBLE

                // Apply filters
                applyFilters()
            },
            year,
            month,
            day
        )

        datePickerDialog.show()
    }

    private fun clearDateFilter() {
        selectedDate = null
        binding.tvDateFilter.text = "Select Date"
        binding.ivClearDate.visibility = View.GONE
        applyFilters()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.ivClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s.toString().trim()
                applyFilters()
            }
        })

        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    private fun setupClickListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            loadOrders()
        }

        binding.fabRefresh.setOnClickListener {
            loadOrders()
        }
    }

    private fun getCurrentSeller() {
        val currentUser = supabase.auth.currentUserOrNull()
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        sellerId = currentUser.id
        loadOrders()
    }

    private fun loadOrders() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = true

        scope.launch {
            try {
                // First, get order items for this seller
                val orderItems = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("order_items")
                        .select {
                            filter {
                                eq("seller_id", sellerId)
                            }
                        }
                        .decodeList<OrderItem>()
                }

                Log.d("SellerOrders", "Found ${orderItems.size} order items for seller")

                if (orderItems.isEmpty()) {
                    showEmptyState()
                    return@launch
                }

                // Get unique order IDs from order items
                val orderIds = orderItems.map { it.order_id }.distinct()
                Log.d("SellerOrders", "Unique order IDs: $orderIds")

                // Fetch all orders
                val allOrdersList = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("orders")
                        .select()
                        .decodeList<Order>()
                }

                // Sort orders by created_at descending manually
                val sortedOrders = allOrdersList.sortedByDescending { it.created_at }

                // Filter orders based on order IDs
                val filteredOrders = sortedOrders.filter { it.order_id in orderIds }

                // Get unique user IDs from orders
                val userIds = filteredOrders.map { it.user_id }.distinct()

                // Fetch customer information
                customerMap.clear()
                if (userIds.isNotEmpty()) {
                    val customers = withContext(Dispatchers.IO) {
                        supabase.postgrest.from("users")
                            .select {
                                filter {
                                    isIn("id", userIds)
                                }
                            }
                            .decodeList<User>()
                    }
                    customers.forEach { user ->
                        customerMap[user.id] = user
                    }
                }

                Log.d("SellerOrders", "Found ${filteredOrders.size} orders and ${customerMap.size} customers")

                // Update data
                allOrders.clear()
                allOrders.addAll(filteredOrders)

                // Group order items by order_id
                orderItemsMap.clear()
                orderItems.groupBy { it.order_id }.forEach { (orderId, items) ->
                    orderItemsMap[orderId] = items
                }

                // Update adapter with customer data
                ordersAdapter.updateCustomerMap(customerMap)

                // Apply initial filters
                applyFilters()

            } catch (e: Exception) {
                Log.e("SellerOrders", "Error loading orders: ${e.message}", e)
                Toast.makeText(
                    this@SellerOrdersActivity,
                    "Error loading orders: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                showEmptyState()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun applyFilters() {
        val filteredOrders = allOrders.filter { order ->
            // Status filter
            val statusMatch = currentFilter == "All" || order.order_status == currentFilter

            // Date filter
            val dateMatch = if (selectedDate != null) {
                isOnDate(order.created_at, selectedDate!!)
            } else {
                true
            }

            // Search filter
            val searchMatch = currentSearchQuery.isEmpty() || matchesSearch(order, currentSearchQuery)

            statusMatch && dateMatch && searchMatch
        }

        // Update displayed orders
        displayedOrders.clear()
        displayedOrders.addAll(filteredOrders)
        ordersAdapter.notifyDataSetChanged()

        if (displayedOrders.isEmpty()) {
            showEmptyState()
        } else {
            binding.tvEmpty.visibility = View.GONE
        }

        updateStats(displayedOrders)
    }

    private fun matchesSearch(order: Order, query: String): Boolean {
        val lowerQuery = query.lowercase(Locale.getDefault())

        // Search in order number
        if (order.order_number?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true) {
            return true
        }

        // Search in customer name
        val customer = customerMap[order.user_id]
        if (customer?.name?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true) {
            return true
        }

        // Search in customer email
        if (customer?.email?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true) {
            return true
        }

        // Search in customer phone number
        if (customer?.mobile?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true) {
            return true
        }

        return false
    }

    private fun isOnDate(dateString: String?, targetDate: String): Boolean {
        if (dateString.isNullOrEmpty()) return false
        return try {
            // Extract just the date part (yyyy-MM-dd) from the timestamp
            val orderDate = dateString.substring(0, 10)
            orderDate == targetDate
        } catch (e: Exception) {
            false
        }
    }

    private fun updateStats(ordersList: List<Order>) {
        val totalOrders = ordersList.size
        val pendingOrders = ordersList.count { it.order_status == "Pending" }

        binding.tvTotalOrders.text = totalOrders.toString()
        binding.tvPendingOrders.text = pendingOrders.toString()
    }

    private fun showEmptyState() {
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = when {
            currentSearchQuery.isNotEmpty() -> "No orders found for \"$currentSearchQuery\""
            currentFilter != "All" -> "No orders with status \"$currentFilter\""
            selectedDate != null -> "No orders on $selectedDate"
            else -> "No orders found"
        }
    }

    private fun showOrderDetails(order: Order) {
        Log.d("SellerOrders", "Opening order details for order: ${order.order_id}")
        val intent = Intent(this, SellerOrderDetailActivity::class.java).apply {
            putExtra("order_id", order.order_id)
            putExtra("order_number", order.order_number)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadOrders()
    }
}