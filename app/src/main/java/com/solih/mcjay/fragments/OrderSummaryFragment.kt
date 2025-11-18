package com.solih.mcjay.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.solih.mcjay.R
import com.solih.mcjay.databinding.FragmentOrderSummaryBinding
import com.solih.mcjay.models.Order
import com.solih.mcjay.models.OrderItem
import com.solih.mcjay.models.Product
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderSummaryFragment : Fragment() {

    private lateinit var binding: FragmentOrderSummaryBinding
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var orderItemsAdapter: OrderItemsAdapter

    private var orderId: Int = -1
    private var orderNumber: String = ""
    private var orderItems = mutableListOf<OrderItem>()
    private val productsMap = mutableMapOf<String, Product>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOrderSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get order details from arguments
        arguments?.let {
            orderId = it.getInt("order_id", -1)
            orderNumber = it.getString("order_number", "")
        }

        if (orderId == -1 || orderNumber.isEmpty()) {
            Toast.makeText(requireContext(), "Order details not found", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setupRecyclerView()
        setupClickListeners()
        loadOrderDetails()
    }

    private fun setupRecyclerView() {
        orderItemsAdapter = OrderItemsAdapter(orderItems, productsMap)
        binding.rvOrderItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = orderItemsAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBackToHome.setOnClickListener {
            // Safe navigation to home
            try {
                // Try to navigate to home fragment
                findNavController().navigate(R.id.homeFragment)
            } catch (e: Exception) {
                // If navigation fails, pop back to root
                findNavController().popBackStack(R.id.nav_graph, false)
            }
        }

        binding.btnTrackOrder.setOnClickListener {
            Toast.makeText(requireContext(), "Tracking feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadOrderDetails() {
        scope.launch {
            try {
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

                // Load product details for order items
                val productIds = items.map { it.product_id }.distinct()
                for (productId in productIds) {
                    try {
                        val product = withContext(Dispatchers.IO) {
                            com.solih.mcjay.SupabaseClientInstance.client.postgrest["products"]
                                .select {
                                    filter { eq("product_id", productId) }
                                }
                                .decodeList<Product>()
                        }
                        if (product.isNotEmpty()) {
                            productsMap[productId] = product[0]
                        }
                    } catch (e: Exception) {
                        Log.e("OrderSummary", "Error loading product $productId", e)
                    }
                }

                // Update UI with order data
                updateUI(order, items)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading order details", Toast.LENGTH_SHORT).show()
                Log.e("OrderSummary", "Error loading order details", e)
            }
        }
    }

    private fun updateUI(order: Order, items: List<OrderItem>) {
        // Update header
        binding.tvOrderNumber.text = "Order #${order.order_number}"
        binding.tvOrderDate.text = formatDate(order.created_at)

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
        binding.tvSubtotal.text = "₹${String.format("%.2f", subtotal)}"
        binding.tvShipping.text = "₹${String.format("%.2f", shipping)}"
        binding.tvTax.text = "₹${String.format("%.2f", tax)}"
        binding.tvTotal.text = "₹${String.format("%.2f", total)}"

        // Update shipping and payment
        binding.tvShippingAddress.text = order.shipping_address
        binding.tvPaymentMethod.text = order.payment_method

        // Update order status
        binding.tvOrderStatus.text = order.order_status
        binding.tvDeliveryEstimate.text = getDeliveryEstimate(order.order_status)
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

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val productName: TextView = itemView.findViewById(R.id.tvProductName)
            val productPrice: TextView = itemView.findViewById(R.id.tvPrice)
            val quantity: TextView = itemView.findViewById(R.id.tvQuantity)
            val subtotal: TextView = itemView.findViewById(R.id.tvSubtotal)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_order_product, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val orderItem = orderItems[position]
            val product = productsMap[orderItem.product_id]

            holder.productName.text = product?.name ?: "Product"
            holder.productPrice.text = "₹${String.format("%.2f", orderItem.price)}"
            holder.quantity.text = "Qty: ${orderItem.quantity}"
            holder.subtotal.text = "₹${String.format("%.2f", orderItem.subtotal)}"
        }

        override fun getItemCount(): Int = orderItems.size
    }
}