package com.solih.mcjay.activities

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.solih.mcjay.R
import com.solih.mcjay.databinding.ActivitySalesAnalysisBinding
import com.solih.mcjay.models.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SalesAnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesAnalysisBinding
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var sellerId: String
    private lateinit var startDate: String
    private lateinit var endDate: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from intent
        sellerId = intent.getStringExtra("sellerId") ?: ""
        startDate = intent.getStringExtra("startDate") ?: ""
        endDate = intent.getStringExtra("endDate") ?: ""

        if (sellerId.isEmpty()) {
            Toast.makeText(this, "Invalid seller ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("SalesAnalysis", "Starting analysis for seller: $sellerId, dates: $startDate to $endDate")
        setupToolbar()
        loadAnalysisData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sales Analysis - $startDate to $endDate"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadAnalysisData() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        scope.launch {
            try {
                // Load sales data for chart
                val dailySales = loadDailySalesData()
                val topProducts = loadTopProducts()
                val totalRevenue = dailySales.sumOf { it.sales }

                Log.d("SalesAnalysis", "Analysis data loaded - Daily sales: ${dailySales.size}, Top products: ${topProducts.size}, Total revenue: $totalRevenue")

                // Update UI
                updateRevenueUI(totalRevenue)
                setupLineChart(dailySales)
                updateTopProducts(topProducts)

                if (dailySales.isEmpty()) {
                    Toast.makeText(this@SalesAnalysisActivity, "No sales data found for the selected period", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("SalesAnalysis", "Error loading analysis data: ${e.message}", e)
                Toast.makeText(this@SalesAnalysisActivity, "Error loading analysis: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private suspend fun loadDailySalesData(): List<DailySales> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SalesAnalysis", "Loading daily sales data from $startDate to $endDate for seller: $sellerId")

                // First get all products for this seller
                val sellerProducts = supabase.postgrest.from("products")
                    .select {
                        Columns.list("product_id")
                        filter {
                            eq("seller_id", sellerId)
                        }
                    }
                    .decodeList<ProductResponse>()

                val productIds = sellerProducts.mapNotNull { it.product_id }

                if (productIds.isEmpty()) {
                    Log.d("SalesAnalysis", "No products found for this seller")
                    return@withContext emptyList()
                }

                // Get all order items and filter manually by seller's product IDs
                val allOrderItems = supabase.postgrest.from("order_items")
                    .select {
                        Columns.list("created_at", "quantity", "price", "item_status_type", "product_id")
                        filter {
                            gte("created_at", "$startDate 00:00:00")
                            lte("created_at", "$endDate 23:59:59")
                        }
                    }
                    .decodeList<OrderItemActual>()

                Log.d("SalesAnalysis", "All order items: ${allOrderItems.size}")

                // Filter by seller's product IDs manually
                val sellerOrderItems = allOrderItems.filter { item ->
                    item.product_id in productIds
                }

                Log.d("SalesAnalysis", "Seller order items: ${sellerOrderItems.size}")

                // Group by date manually
                val groupedByDate = sellerOrderItems.groupBy { item ->
                    val createdAt = item.created_at ?: ""
                    if (createdAt.length >= 10) createdAt.substring(0, 10) else "unknown"
                }

                Log.d("SalesAnalysis", "Grouped by date: ${groupedByDate.keys}")

                // Calculate daily sales using price * quantity
                val dailySales = groupedByDate.mapNotNull { (date, items) ->
                    try {
                        val totalSales = items.sumOf { item ->
                            val quantity = item.quantity ?: 0
                            val price = item.price ?: 0.0
                            quantity * price
                        }
                        DailySales(date = date, sales = totalSales)
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.date }

                dailySales
            } catch (e: Exception) {
                Log.e("SalesAnalysis", "Error loading daily sales: ${e.message}", e)
                emptyList()
            }
        }
    }

    private suspend fun loadTopProducts(): List<TopProduct> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SalesAnalysis", "Loading top products from $startDate to $endDate for seller: $sellerId")

                // First get all products for this seller with names
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
                    Log.d("SalesAnalysis", "No products found for this seller")
                    return@withContext emptyList()
                }

                // Get all order items and filter manually by seller's product IDs
                val allOrderItems = supabase.postgrest.from("order_items")
                    .select {
                        Columns.list("product_id", "quantity", "price", "item_status_type")
                        filter {
                            gte("created_at", "$startDate 00:00:00")
                            lte("created_at", "$endDate 23:59:59")
                        }
                    }
                    .decodeList<OrderItemActual>()

                Log.d("SalesAnalysis", "All order items: ${allOrderItems.size}")

                // Filter by seller's product IDs manually
                val sellerOrderItems = allOrderItems.filter { item ->
                    item.product_id in productIds
                }

                Log.d("SalesAnalysis", "Seller order items: ${sellerOrderItems.size}")

                // Calculate product totals manually using price * quantity
                val productTotals = sellerOrderItems.groupBy { it.product_id ?: "unknown" }
                    .mapNotNull { (productId, items) ->
                        try {
                            val totalQuantity = items.sumOf { it.quantity ?: 0 }
                            val totalSales = items.sumOf { item ->
                                val quantity = item.quantity ?: 0
                                val price = item.price ?: 0.0
                                quantity * price
                            }
                            TopProduct(
                                productId = productId,
                                productName = productNames[productId] ?: "Unknown Product",
                                totalQuantity = totalQuantity,
                                totalSales = totalSales
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    .sortedByDescending { it.totalSales }
                    .take(3)

                productTotals
            } catch (e: Exception) {
                Log.e("SalesAnalysis", "Error loading top products: ${e.message}", e)
                emptyList()
            }
        }
    }

    private fun updateRevenueUI(totalRevenue: Double) {
        binding.tvTotalRevenue.text = "₹${String.format("%.2f", totalRevenue)}"
        //binding.tvPeriod.text = "Period: $startDate to $endDate"
        Log.d("SalesAnalysis", "Revenue UI updated: $totalRevenue")
    }

    private fun setupLineChart(dailySales: List<DailySales>) {
        if (dailySales.isEmpty()) {
            binding.lineChart.visibility = android.view.View.GONE
            binding.tvNoData.visibility = android.view.View.VISIBLE
            Log.d("SalesAnalysis", "No data for chart, showing empty state")
            return
        }

        binding.lineChart.visibility = android.view.View.VISIBLE
        binding.tvNoData.visibility = android.view.View.GONE

        val entries = ArrayList<Entry>()
        val dates = ArrayList<String>()

        dailySales.forEachIndexed { index, dailySale ->
            entries.add(Entry(index.toFloat(), dailySale.sales.toFloat()))

            val displayDate = try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dailySale.date)
                SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
            } catch (e: Exception) {
                dailySale.date
            }
            dates.add(displayDate)
        }

        val dataSet = LineDataSet(entries, "Daily Sales Amount (₹)")
        dataSet.color = Color.parseColor("#6200EE")
        dataSet.valueTextColor = Color.BLACK
        dataSet.lineWidth = 2f
        dataSet.setCircleColor(Color.parseColor("#6200EE"))
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextSize = 10f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        // Set value formatter to show currency
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "₹${String.format("%.2f", value)}"
            }
        }

        val lineData = LineData(dataSet)
        binding.lineChart.data = lineData

        // Customize chart appearance
        binding.lineChart.description.isEnabled = true
        binding.lineChart.description.text = "Daily Sales Trend"
        binding.lineChart.description.textSize = 12f
        binding.lineChart.legend.isEnabled = true
        binding.lineChart.setTouchEnabled(true)
        binding.lineChart.setPinchZoom(true)
        binding.lineChart.setDrawGridBackground(false)

        // Customize X axis
        val xAxis = binding.lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return dates.getOrNull(value.toInt()) ?: ""
            }
        }
        xAxis.setLabelCount(dates.size.coerceAtMost(6), true)
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = -45f
        xAxis.setDrawGridLines(false)
        xAxis.textSize = 10f
        xAxis.axisLineWidth = 1f

        // Customize Y axis (left)
        val yAxis = binding.lineChart.axisLeft
        yAxis.setDrawGridLines(true)
        yAxis.axisMinimum = 0f
        yAxis.textSize = 10f
        yAxis.axisLineWidth = 1f
        yAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "₹${String.format("%.0f", value)}"
            }
        }
        yAxis.labelCount = 6

        // Customize right Y axis
        val rightYAxis = binding.lineChart.axisRight
        rightYAxis.isEnabled = true
        rightYAxis.setDrawGridLines(false)
        rightYAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "₹${String.format("%.0f", value)}"
            }
        }
        rightYAxis.labelCount = 6

        // Add animation
        binding.lineChart.animateXY(1000, 1000)

        // Refresh chart
        binding.lineChart.invalidate()

        Log.d("SalesAnalysis", "Chart setup completed with ${entries.size} data points")
    }

    private fun updateTopProducts(topProducts: List<TopProduct>) {
        if (topProducts.isEmpty()) {
            binding.tvProduct1.text = "No products sold in this period"
            binding.tvProduct1.visibility = android.view.View.VISIBLE
            binding.tvProduct2.visibility = android.view.View.GONE
            binding.tvProduct3.visibility = android.view.View.GONE
            return
        }

        if (topProducts.isNotEmpty()) {
            binding.tvProduct1.text = "1. ${topProducts[0].productName} - ₹${String.format("%.2f", topProducts[0].totalSales)} (${topProducts[0].totalQuantity} sold)"
            binding.tvProduct1.visibility = android.view.View.VISIBLE
        }
        if (topProducts.size > 1) {
            binding.tvProduct2.text = "2. ${topProducts[1].productName} - ₹${String.format("%.2f", topProducts[1].totalSales)} (${topProducts[1].totalQuantity} sold)"
            binding.tvProduct2.visibility = android.view.View.VISIBLE
        }
        if (topProducts.size > 2) {
            binding.tvProduct3.text = "3. ${topProducts[2].productName} - ₹${String.format("%.2f", topProducts[2].totalSales)} (${topProducts[2].totalQuantity} sold)"
            binding.tvProduct3.visibility = android.view.View.VISIBLE
        }
    }
}