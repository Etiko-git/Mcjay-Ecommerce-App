package com.solih.mcjay.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.databinding.ActivitySalesReportsBinding
import com.solih.mcjay.models.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SalesReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesReportsBinding
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    private var sellerId: String = ""
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Store current report data for PDF export
    private var currentSalesData: SalesData? = null
    private var currentOrderItems: List<OrderItemDetail> = emptyList()
    private var currentStartDate: String = ""
    private var currentEndDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        getCurrentSeller()
        setupDatePickers()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sales Reports"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun getCurrentSeller() {
        val currentUser = supabase.auth.currentUserOrNull()
        if (currentUser != null) {
            sellerId = currentUser.id
            Log.d("SalesReports", "Current seller ID: $sellerId")
            // Set default dates (last 30 days)
            setDefaultDates()
        } else {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setDefaultDates() {
        val calendar = Calendar.getInstance()

        // End date (today)
        val endDate = calendar.time
        binding.etEndDate.setText(dateFormat.format(endDate))

        // Start date (30 days ago)
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startDate = calendar.time
        binding.etStartDate.setText(dateFormat.format(startDate))

        // Clear previous data when setting default dates
        clearPreviousData()
    }

    private fun setupDatePickers() {
        binding.etStartDate.setOnClickListener {
            showDatePicker(true)
        }

        binding.etEndDate.setOnClickListener {
            showDatePicker(false)
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                val dateString = dateFormat.format(selectedDate.time)
                if (isStartDate) {
                    binding.etStartDate.setText(dateString)
                } else {
                    binding.etEndDate.setText(dateString)
                }

                // Clear previous data when date changes
                clearPreviousData()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun clearPreviousData() {
        // Clear previous data when dates change
        currentSalesData = null
        currentOrderItems = emptyList()
        binding.salesSummaryCard.visibility = android.view.View.GONE
        binding.exportButtons.visibility = android.view.View.GONE

        // Reset UI to default state
        binding.tvTotalSales.text = "₹0.00"
        binding.tvTotalOrders.text = "0 orders"
        binding.tvTotalItems.text = "0 items"
    }

    private fun setupClickListeners() {
        binding.btnGenerateReport.setOnClickListener {
            generateReport()
        }

        binding.btnDownloadPdf.setOnClickListener {
            downloadPdfReport()
        }

        binding.btnShowAnalysis.setOnClickListener {
            showDetailedAnalysis()
        }
    }

    private fun generateReport() {
        val startDate = binding.etStartDate.text.toString()
        val endDate = binding.etEndDate.text.toString()

        if (startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(this, "Please select both dates", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate date range
        if (startDate > endDate) {
            Toast.makeText(this, "Start date cannot be after end date", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("SalesReports", "Generating report for dates: $startDate to $endDate for seller: $sellerId")
        binding.progressBar.visibility = android.view.View.VISIBLE

        scope.launch {
            try {
                // Load sales data and order items
                val salesData = loadSalesData(startDate, endDate)
                val orderItems = loadOrderItems(startDate, endDate)

                Log.d("SalesReports", "Sales data loaded: $salesData")
                Log.d("SalesReports", "Order items loaded: ${orderItems.size} items")

                // Update UI
                updateSalesUI(salesData)

                // Store data for PDF export
                currentSalesData = salesData
                currentOrderItems = orderItems
                currentStartDate = startDate
                currentEndDate = endDate

                // Show export buttons
                binding.exportButtons.visibility = android.view.View.VISIBLE

                if (salesData.orderCount == 0) {
                    Toast.makeText(this@SalesReportsActivity, "No sales data found for the selected period", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SalesReportsActivity, "Report generated successfully!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("SalesReports", "Error generating report: ${e.message}", e)
                Toast.makeText(this@SalesReportsActivity, "Error generating report: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private suspend fun loadSalesData(startDate: String, endDate: String): SalesData {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SalesReports", "Loading sales data from $startDate to $endDate for seller: $sellerId")

                // First get all products for this seller
                val sellerProducts = supabase.postgrest.from("products")
                    .select {
                        Columns.list("product_id")
                        filter {
                            eq("seller_id", sellerId)
                        }
                    }
                    .decodeList<ProductResponse>()

                Log.d("SalesReports", "Seller products: ${sellerProducts.size} products")
                val productIds = sellerProducts.mapNotNull { it.product_id }

                if (productIds.isEmpty()) {
                    Log.d("SalesReports", "No products found for this seller")
                    return@withContext SalesData(0.0, 0, 0)
                }

                Log.d("SalesReports", "Product IDs for seller: $productIds")

                // FIXED: Use proper UTC timezone in date filtering
                val allOrderItems = supabase.postgrest.from("order_items")
                    .select {
                        Columns.list("quantity", "price", "item_status_type", "created_at", "product_id")
                        filter {
                            // Add UTC timezone (+00) to match your created_at format
                            gte("created_at", "$startDate 00:00:00+00")
                            lte("created_at", "$endDate 23:59:59+00")
                        }
                    }
                    .decodeList<OrderItemActual>()

                Log.d("SalesReports", "All order items: ${allOrderItems.size}")

                // Filter by seller's product IDs manually
                val sellerOrderItems = allOrderItems.filter { item ->
                    item.product_id in productIds
                }

                Log.d("SalesReports", "Seller order items: ${sellerOrderItems.size}")

                // Calculate using price instead of subtotal
                val orderCount = sellerOrderItems.size
                val totalSales = sellerOrderItems.sumOf { item ->
                    val quantity = item.quantity ?: 0
                    val price = item.price ?: 0.0
                    quantity * price
                }
                val totalItems = sellerOrderItems.sumOf { it.quantity ?: 0 }

                Log.d("SalesReports", "Calculated - Orders: $orderCount, Sales: $totalSales, Items: $totalItems")

                SalesData(totalSales, orderCount, totalItems)
            } catch (e: Exception) {
                Log.e("SalesReports", "Error loading sales data: ${e.message}", e)
                SalesData(0.0, 0, 0)
            }
        }
    }

    private suspend fun loadOrderItems(startDate: String, endDate: String): List<OrderItemDetail> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SalesReports", "Loading order items from $startDate to $endDate for seller: $sellerId")

                // First get all products for this seller
                val sellerProducts = supabase.postgrest.from("products")
                    .select {
                        Columns.list("product_id", "name")
                        filter {
                            eq("seller_id", sellerId)
                        }
                    }
                    .decodeList<ProductResponse>()

                val productIds = sellerProducts.mapNotNull { it.product_id }
                val productNames = sellerProducts.associate { it.product_id to it.name }.filterValues { it != null }

                if (productIds.isEmpty()) {
                    Log.d("SalesReports", "No products found for this seller")
                    return@withContext emptyList()
                }

                // FIXED: Use proper UTC timezone in date filtering
                val allOrderItems = supabase.postgrest.from("order_items")
                    .select {
                        Columns.list("created_at", "quantity", "price", "product_id", "order_id", "item_status_type")
                        filter {
                            // Add UTC timezone (+00) to match your created_at format
                            gte("created_at", "$startDate 00:00:00+00")
                            lte("created_at", "$endDate 23:59:59+00")
                        }
                        order("created_at", Order.ASCENDING)
                    }
                    .decodeList<OrderItemActual>()

                Log.d("SalesReports", "All order items: ${allOrderItems.size}")

                // Filter by seller's product IDs manually
                val sellerOrderItems = allOrderItems.filter { item ->
                    item.product_id in productIds
                }

                Log.d("SalesReports", "Seller order items: ${sellerOrderItems.size}")

                val orderItems = sellerOrderItems.mapNotNull { item ->
                    try {
                        val date = item.created_at ?: ""
                        val quantity = item.quantity ?: 0
                        val productId = item.product_id ?: ""
                        val productName = productNames[productId] ?: "Unknown Product"
                        val price = item.price ?: 0.0
                        val amount = quantity * price

                        OrderItemDetail(
                            date = date,
                            quantity = quantity,
                            productName = productName,
                            amount = amount
                        )
                    } catch (e: Exception) {
                        Log.e("SalesReports", "Error parsing order item: ${e.message}")
                        null
                    }
                }

                Log.d("SalesReports", "Final order items: ${orderItems.size}")
                orderItems
            } catch (e: Exception) {
                Log.e("SalesReports", "Error loading order items: ${e.message}", e)
                emptyList()
            }
        }
    }

    private fun updateSalesUI(salesData: SalesData) {
        binding.tvTotalSales.text = "$${String.format("%.2f", salesData.totalSales)}"
        binding.tvTotalOrders.text = "${salesData.orderCount} orders"
        binding.tvTotalItems.text = "${salesData.totalItems} items"

        // Show sales summary card
        binding.salesSummaryCard.visibility = android.view.View.VISIBLE
        Log.d("SalesReports", "UI updated with sales data: $salesData")
    }

    private fun downloadPdfReport() {
        val salesData = currentSalesData
        val orderItems = currentOrderItems

        if (salesData == null || orderItems.isEmpty()) {
            Toast.makeText(this, "Please generate a report first", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "PDF export feature will be implemented soon", Toast.LENGTH_SHORT).show()
        showExportPreview(salesData, orderItems)
    }

    private fun showExportPreview(salesData: SalesData, orderItems: List<OrderItemDetail>) {
        val preview = StringBuilder()
        preview.append("Sales Report - Seller: $sellerId\n")
        preview.append("Period: $currentStartDate to $currentEndDate\n\n")
        preview.append("Summary:\n")
        preview.append("Total Sales: $${String.format("%.2f", salesData.totalSales)}\n")
        preview.append("Total Orders: ${salesData.orderCount}\n")
        preview.append("Total Items: ${salesData.totalItems}\n\n")
        preview.append("Detailed Transactions:\n")
        preview.append("Date | Items | Product | Amount\n")
        preview.append("--------------------------------\n")

        orderItems.forEach { item ->
            val displayDate = try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(item.date.substring(0, 10))
                displayDateFormat.format(date)
            } catch (e: Exception) {
                item.date.substring(0, 10)
            }
            preview.append("$displayDate | ${item.quantity} | ${item.productName} | $${String.format("%.2f", item.amount)}\n")
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Report Preview (PDF Export)")
            .setMessage(preview.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDetailedAnalysis() {
        val salesData = currentSalesData
        if (salesData == null) {
            Toast.makeText(this, "Please generate a report first", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, SalesAnalysisActivity::class.java).apply {
            putExtra("startDate", currentStartDate)
            putExtra("endDate", currentEndDate)
            putExtra("sellerId", sellerId)
        }
        startActivity(intent)
    }
}