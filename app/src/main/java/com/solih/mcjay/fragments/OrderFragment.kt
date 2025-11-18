package com.solih.mcjay.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.activities.OrderSummaryActivity
import com.solih.mcjay.adapters.OrderAdapter
import com.solih.mcjay.models.Order
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderFragment : Fragment() {

    private lateinit var rvOrders: RecyclerView
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var orderAdapter: OrderAdapter
    private var allOrders = mutableListOf<Order>()
    private var filteredOrders = mutableListOf<Order>()
    private var currentFilter = "all"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadOrders()
    }

    private fun initViews(view: View) {
        rvOrders = view.findViewById(R.id.rvOrders)
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        emptyState = view.findViewById(R.id.emptyState)
        progressBar = view.findViewById(R.id.progressBar)

        // Setup swipe refresh
        swipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorPrimaryDark,
            R.color.colorAccent
        )
    }

    private fun setupRecyclerView() {
        orderAdapter = OrderAdapter(filteredOrders) { order ->
            navigateToOrderSummary(order)
        }
        rvOrders.layoutManager = LinearLayoutManager(requireContext())
        rvOrders.adapter = orderAdapter
    }

    private fun setupClickListeners() {
        // Swipe to refresh
        swipeRefreshLayout.setOnRefreshListener {
            loadOrders()
        }

        // Filter chips
        chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)
            when (chip.id) {
                R.id.chipAll -> currentFilter = "all"
                R.id.chipPending -> currentFilter = "Pending"
                R.id.chipConfirmed -> currentFilter = "Confirmed"
                R.id.chipProcessing -> currentFilter = "Processing"
                R.id.chipShipped -> currentFilter = "Shipped"
                R.id.chipDelivered -> currentFilter = "Delivered"
            }
            applyFilter()
        }
    }

    private fun loadOrders() {
        val currentUser = SupabaseClientInstance.client.auth.currentUserOrNull()
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        scope.launch {
            try {
                val orders = withContext(Dispatchers.IO) {
                    SupabaseClientInstance.client.postgrest["orders"]
                        .select {
                            filter {
                                eq("user_id", currentUser.id)
                            }
                            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        }
                        .decodeList<Order>()
                }

                allOrders.clear()
                allOrders.addAll(orders)
                applyFilter()

                Log.d("OrderFragment", "Loaded ${orders.size} orders")

            } catch (e: Exception) {
                Log.e("OrderFragment", "Error loading orders: ${e.message}", e)
                Toast.makeText(requireContext(), "Error loading orders", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                updateEmptyState()
            }
        }
    }

    private fun applyFilter() {
        filteredOrders.clear()

        if (currentFilter == "all") {
            filteredOrders.addAll(allOrders)
        } else {
            filteredOrders.addAll(allOrders.filter { it.order_status == currentFilter })
        }

        orderAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (filteredOrders.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            rvOrders.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            rvOrders.visibility = View.VISIBLE
        }
    }

    private fun navigateToOrderSummary(order: Order) {
        val intent = Intent(requireContext(), OrderSummaryActivity::class.java).apply {
            putExtra("order_id", order.order_id ?: -1)
            putExtra("order_number", order.order_number ?: "")
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Refresh orders when fragment becomes visible
        loadOrders()
    }
}