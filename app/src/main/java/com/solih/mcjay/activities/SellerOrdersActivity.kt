package com.solih.mcjay.activities

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
    private var currentDateFilter = "All"
    private var currentSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFilterSpinners()
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

    private fun setupFilterSpinners() {
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

        // Date filter
        val dateOptions = arrayOf("All Time", "Today", "Last 7 Days", "Last 30 Days", "This Month")
        val dateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dateOptions)
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDateFilter.adapter = dateAdapter

        binding.spinnerDateFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentDateFilter = when (position) {
                    0 -> "All"
                    1 -> "Today"
                    2 -> "Last7Days"
                    3 -> "Last30Days"
                    4 -> "ThisMonth"
                    else -> "All"
                }
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
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
                ordersAdapter.updateCustomerMap(customerMap) // Add this line

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
            val dateMatch = when (currentDateFilter) {
                "Today" -> isToday(order.created_at)
                "Last7Days" -> isWithinLastDays(order.created_at, 7)
                "Last30Days" -> isWithinLastDays(order.created_at, 30)
                "ThisMonth" -> isThisMonth(order.created_at)
                else -> true // "All"
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

        return false
    }

    private fun isToday(dateString: String?): Boolean {
        if (dateString.isNullOrEmpty()) return false
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val orderDate = format.parse(dateString.substring(0, 10))
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            orderDate == today
        } catch (e: Exception) {
            false
        }
    }

    private fun isWithinLastDays(dateString: String?, days: Int): Boolean {
        if (dateString.isNullOrEmpty()) return false
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val orderDate = format.parse(dateString.substring(0, 10))
            val cutoffDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -days)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            orderDate >= cutoffDate
        } catch (e: Exception) {
            false
        }
    }

    private fun isThisMonth(dateString: String?): Boolean {
        if (dateString.isNullOrEmpty()) return false
        return try {
            val format = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val orderMonth = format.parse(dateString.substring(0, 7))
            val currentMonth = format.parse(
                SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            )
            orderMonth == currentMonth
        } catch (e: Exception) {
            false
        }
    }

    private fun updateStats(ordersList: List<Order>) {
        val totalOrders = ordersList.size
        val pendingOrders = ordersList.count { it.order_status == "Pending" }
        val totalRevenue = ordersList.sumOf { order ->
            orderItemsMap[order.order_id]?.sumOf { it.subtotal } ?: 0.0
        }

        binding.tvTotalOrders.text = totalOrders.toString()
        binding.tvPendingOrders.text = pendingOrders.toString()
//        binding.tvTotalRevenue.text = "$${String.format("%.2f", totalRevenue)}"
    }

    private fun showEmptyState() {
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = when {
            currentSearchQuery.isNotEmpty() -> "No orders found for \"$currentSearchQuery\""
            currentFilter != "All" || currentDateFilter != "All" -> "No orders match your filters"
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