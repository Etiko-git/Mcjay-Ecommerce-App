package com.solih.mcjay.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.R
import com.solih.mcjay.adapters.TopProductsAdapter
import com.solih.mcjay.databinding.ActivitySalesReportsBinding
import com.solih.mcjay.models.TopProduct
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
    private lateinit var topProductsAdapter: TopProductsAdapter

    private var sellerId: String = ""
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        getCurrentSeller()
        setupRecyclerView()
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
            // Set default dates (last 30 days vs previous 30 days)
            setDefaultDates()
        } else {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupRecyclerView() {
        topProductsAdapter = TopProductsAdapter(mutableListOf())
        binding.rvTopProducts.apply {
            layoutManager = LinearLayoutManager(this@SalesReportsActivity)
            adapter = topProductsAdapter
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

        // Generate initial report
        generateReport()
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
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setupClickListeners() {
        binding.btnGenerateReport.setOnClickListener {
            generateReport()
        }
    }

    private fun generateReport() {
        val startDate = binding.etStartDate.text.toString()
        val endDate = binding.etEndDate.text.toString()

        if (startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(this, "Please select both dates", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = android.view.View.VISIBLE

        scope.launch {
            try {
                // Calculate period 2 (previous period of same duration)
                val period1Start = startDate
                val period1End = endDate

                val period2Start = calculatePreviousPeriodStart(startDate, endDate)
                val period2End = startDate

                // Load data for both periods
                val period1Data = loadSalesData(period1Start, period1End)
                val period2Data = loadSalesData(period2Start, period2End)

                // Update UI
                updateComparisonUI(period1Data, period2Data, period1Start, period1End, period2Start, period2End)

                // Load top products
                loadTopProducts(period1Start, period1End)

            } catch (e: Exception) {
                Log.e("SalesReports", "Error generating report: ${e.message}", e)
                Toast.makeText(this@SalesReportsActivity, "Error generating report: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun calculatePreviousPeriodStart(startDate: String, endDate: String): String {
        val start = dateFormat.parse(startDate)!!
        val end = dateFormat.parse(endDate)!!

        val duration = end.time - start.time
        val previousStart = Date(start.time - duration)

        return dateFormat.format(previousStart)
    }

    private suspend fun loadSalesData(startDate: String, endDate: String): SalesData {
        return withContext(Dispatchers.IO) {
            val response = supabase.postgrest.from("order_items")
                .select {
                    // Use Columns.list for column selection
                    Columns.list("subtotal", "created_at", "seller_id", "item_status")
                    filter {
                        eq("seller_id", sellerId)
                        eq("item_status", "Delivered")
                        gte("created_at", "$startDate 00:00:00")
                        lte("created_at", "$endDate 23:59:59")
                    }
                }
                .decodeList<Map<String, Any>>()

            val orderCount = response.size
            val totalSales = response.sumOf { it["subtotal"] as? Double ?: 0.0 }

            SalesData(totalSales, orderCount)
        }
    }

    private fun updateComparisonUI(period1: SalesData, period2: SalesData,
                                   period1Start: String, period1End: String,
                                   period2Start: String, period2End: String) {
        // Update period labels
        binding.tvPeriod1Label.text = "Period 1\n$period1Start to $period1End"
        binding.tvPeriod2Label.text = "Period 2\n$period2Start to $period2End"

        // Update sales and orders
        binding.tvPeriod1Sales.text = "$${String.format("%.2f", period1.totalSales)}"
        binding.tvPeriod1Orders.text = "${period1.orderCount} orders"

        binding.tvPeriod2Sales.text = "$${String.format("%.2f", period2.totalSales)}"
        binding.tvPeriod2Orders.text = "${period2.orderCount} orders"

        // Calculate and show growth
        if (period2.totalSales > 0) {
            val growthPercentage = ((period1.totalSales - period2.totalSales) / period2.totalSales) * 100
            binding.tvGrowthPercentage.text = "${String.format("%.1f", growthPercentage)}% growth"
            binding.growthIndicator.visibility = android.view.View.VISIBLE

            // Set color based on growth
            val colorRes = if (growthPercentage >= 0) R.color.green_600 else R.color.red_600
            binding.growthIndicator.setBackgroundColor(getColor(colorRes))
            binding.tvGrowthPercentage.setTextColor(getColor(colorRes))
        } else {
            binding.tvGrowthPercentage.text = "No previous data"
            binding.growthIndicator.visibility = android.view.View.GONE
        }
    }

    private suspend fun loadTopProducts(startDate: String, endDate: String) {
        try {
            val topProducts = withContext(Dispatchers.IO) {
                supabase.postgrest.from("order_items")
                    .select(
                        // Use Columns for raw SQL selection
                        Columns.raw("""
                        product_id,
                        products(name),
                        sum(quantity) as total_quantity,
                        sum(subtotal) as total_sales
                    """.trimIndent())
                    ) {
                        filter {
                            eq("seller_id", sellerId)
                            eq("item_status", "Delivered")
                            gte("created_at", "$startDate 00:00:00")
                            lte("created_at", "$endDate 23:59:59")
                        }
                        // Use Order for ordering (column as String)
                        order("total_sales", Order.DESCENDING)
                        limit(5)
                    }
                    .decodeList<Map<String, Any>>()
            }
            val products = topProducts.mapNotNull { product ->
                try {
                    TopProduct(
                        productId = product["product_id"] as? String ?: "",
                        productName = (product["products"] as? Map<String, Any>)?.get("name") as? String ?: "Unknown Product",
                        totalQuantity = (product["total_quantity"] as? Number)?.toInt() ?: 0,
                        totalSales = (product["total_sales"] as? Number)?.toDouble() ?: 0.0
                    )
                } catch (e: Exception) {
                    Log.e("SalesReports", "Error parsing product: ${e.message}")
                    null
                }
            }
            topProductsAdapter.updateProducts(products)
        } catch (e: Exception) {
            Log.e("SalesReports", "Error loading top products: ${e.message}", e)
            Toast.makeText(this@SalesReportsActivity, "Error loading top products", Toast.LENGTH_SHORT).show()
        }
    }

    data class SalesData(val totalSales: Double, val orderCount: Int)
}