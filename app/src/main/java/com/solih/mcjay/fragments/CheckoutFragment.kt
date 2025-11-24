package com.solih.mcjay.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.R
import com.solih.mcjay.adapters.CheckoutAdapter
import com.solih.mcjay.databinding.FragmentCheckoutBinding
import com.solih.mcjay.models.CartItem
import com.solih.mcjay.models.Order
import com.solih.mcjay.models.Seller
import com.solih.mcjay.models.OrderItem
import com.solih.mcjay.models.Product
import com.solih.mcjay.models.User
import com.solih.mcjay.models.Payment
import com.solih.mcjay.models.CompanyBalance
import com.solih.mcjay.models.SellerBalance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.solih.mcjay.activities.OrderSummaryActivity
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import io.github.jan.supabase.postgrest.rpc
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay

class CheckoutFragment : Fragment() {
    private lateinit var binding: FragmentCheckoutBinding
    private lateinit var checkoutAdapter: CheckoutAdapter
    private val cartItems = mutableListOf<CartItem>()
    private val productsMap = mutableMapOf<Int, Product>()
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentUser: User? = null
    // Stripe PaymentSheet variables
    private lateinit var paymentSheet: PaymentSheet
    private var paymentIntentClientSecret: String? = null
    private var pendingOrderId: Int = -1
    private var pendingOrderNumber: String = ""
    private var pendingTotalAmount: Double = 0.0
    private var pendingPaymentMethod: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCheckoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize Stripe
        PaymentConfiguration.init(
            requireContext(),
            "pk_test_51SOeDH3JwzRXuXAp59A8I9nxzHqUlJPm5NnikXzVAqMcF2Hr87wPoHFFIIjIcLt8ke4i2W6Y3BANwxcKCo6L8y5u00yTh185Ua"
        )
        // Initialize PaymentSheet
        paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)
        setupRecyclerView()
        setupClickListeners()
        setupPaymentMethodSelection()
        loadUserData()
        loadCartItems()
    }

    private fun setupRecyclerView() {
        checkoutAdapter = CheckoutAdapter(cartItems, productsMap)
        binding.checkoutRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = checkoutAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnChangeAddress.setOnClickListener {
            Toast.makeText(requireContext(), "Change Address", Toast.LENGTH_SHORT).show()
        }
        binding.btnPlaceOrder.setOnClickListener {
            placeOrder()
        }
    }

    private fun loadUserData() {
        scope.launch {
            try {
                val authUser = supabase.auth.currentUserOrNull()
                if (authUser == null) {
                    Toast.makeText(requireContext(), "Please login to checkout", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    return@launch
                }
                currentUser = withContext(Dispatchers.IO) {
                    supabase.postgrest["users"]
                        .select {
                            filter { eq("id", authUser.id) }
                        }
                        .decodeSingle<User>()
                }
                currentUser?.let { user ->
                    val address = user.address ?: "No address set"
                    binding.tvShippingAddress.text = address
                }
            } catch (e: Exception) {
                Log.e("CheckoutFragment", "Error loading user data", e)
            }
        }
    }

    private fun loadCartItems() {
        binding.progressBar.visibility = View.VISIBLE
        scope.launch {
            try {
                val authUser = supabase.auth.currentUserOrNull()
                if (authUser == null) return@launch
                // Fetch cart items
                val cartItemsList = withContext(Dispatchers.IO) {
                    supabase.postgrest["cart"]
                        .select {
                            filter { eq("user_id", authUser.id) }
                        }
                        .decodeList<CartItem>()
                }
                if (cartItemsList.isEmpty()) {
                    Toast.makeText(requireContext(), "Your cart is empty", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    return@launch
                }
                // Fetch product details
                val productIds = cartItemsList.map { it.product_id }.distinct()
                productsMap.clear()
                for (productId in productIds) {
                    try {
                        val productList = withContext(Dispatchers.IO) {
                            supabase.postgrest["products"]
                                .select {
                                    filter { eq("id", productId) }
                                }
                                .decodeList<Product>()
                        }
                        if (productList.isNotEmpty()) {
                            productsMap[productId] = productList[0]
                        }
                    } catch (e: Exception) {
                        Log.e("CheckoutFragment", "Error fetching product $productId", e)
                    }
                }
                // Update UI
                cartItems.clear()
                cartItems.addAll(cartItemsList)
                checkoutAdapter.notifyDataSetChanged()
                calculateTotals()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading cart items", Toast.LENGTH_SHORT).show()
                Log.e("CheckoutFragment", "Error loading cart items", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun calculateTotals() {
        var subtotal = 0.0
        cartItems.forEach { cartItem ->
            val product = productsMap[cartItem.product_id]
            val unitPrice = product?.discount_price ?: product?.price ?: 0.0
            subtotal += unitPrice * cartItem.quantity
        }
        val shipping = if (subtotal > 50.0) 0.0 else 5.0
        val tax = subtotal * 0.08
        val total = subtotal + shipping + tax
        binding.tvSubtotal.text = "₹${String.format("%.2f", subtotal)}"
        binding.tvShipping.text = "₹${String.format("%.2f", shipping)}"
        binding.tvTax.text = "₹${String.format("%.2f", tax)}"
        binding.tvTotal.text = "₹${String.format("%.2f", total)}"
    }

    private fun placeOrder() {
        if (cartItems.isEmpty()) {
            Toast.makeText(requireContext(), "Your cart is empty", Toast.LENGTH_SHORT).show()
            return
        }
        // Validate address
        if (currentUser?.address.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please set your shipping address", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        binding.btnPlaceOrder.isEnabled = false
        scope.launch {
            try {
                val authUser = supabase.auth.currentUserOrNull()
                if (authUser == null) return@launch
                // Calculate final totals
                val subtotal = cartItems.sumOf { cartItem ->
                    val product = productsMap[cartItem.product_id]
                    val unitPrice = product?.discount_price ?: product?.price ?: 0.0
                    unitPrice * cartItem.quantity
                }
                val shipping = if (subtotal > 50.0) 0.0 else 5.0
                val tax = subtotal * 0.08
                val total = subtotal + shipping + tax
                // Get selected payment method
                val paymentMethod = getSelectedPaymentMethod()
                pendingPaymentMethod = paymentMethod
                // Create order and get both order_id and order_number
                val (orderId, orderNumber) = createOrderBasic(
                    userId = authUser.id,
                    total = total,
                    paymentMethod = paymentMethod,
                    address = currentUser?.address ?: ""
                )
                pendingOrderId = orderId
                pendingOrderNumber = orderNumber
                pendingTotalAmount = total
                // Handle payment based on method
                when (paymentMethod) {
                    "Card" -> initiateStripePayment(total)
                    "UPI" -> simulateUPIPayment(orderId, orderNumber)
                    "Cash on Delivery" -> {
                        createPaymentRecord(orderId, orderNumber, total, paymentMethod, "pending")
                        processOrderItems()
                        clearUserCart()
                        completeOrder(orderId, orderNumber, "Order placed successfully! Payment will be collected on delivery.")
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnPlaceOrder.isEnabled = true
                Toast.makeText(requireContext(), "Error placing order: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CheckoutFragment", "Error placing order", e)
            }
        }
    }

    private fun initiateStripePayment(totalAmount: Double) {
        scope.launch {
            try {
                // Convert to cents for Stripe
                val amountInCents = (totalAmount * 100).toInt()
                paymentIntentClientSecret = createPaymentIntent(amountInCents)
                if (paymentIntentClientSecret != null) {
                    presentPaymentSheet()
                } else {
                    handlePaymentFailure("Failed to initialize payment")
                }
            } catch (e: Exception) {
                handlePaymentFailure("Payment initialization error: ${e.message}")
                Log.e("CheckoutFragment", "Stripe payment initiation error", e)
            }
        }
    }

    private suspend fun createPaymentIntent(amount: Int): String? = withContext(Dispatchers.IO) {
        try {
            val backendUrl = "https://mcj-stripe-backend.onrender.com/create-payment-intent.php"
            val url = URL(backendUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            val postData = JSONObject().apply {
                put("amount", amount)
            }.toString()
            Log.d("StripeDebug", "➡ Sending JSON: $postData")
            connection.outputStream.use { os ->
                os.write(postData.toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("StripeDebug", "✅ Response code: $responseCode")
            Log.d("StripeDebug", "✅ Response body: $responseBody")
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val json = JSONObject(responseBody)
                json.optString("client_secret", null) ?: json.optString("clientSecret", null)
            } else {
                Log.e("StripeDebug", "❌ Error from server: $responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e("StripeDebug", "❌ Exception: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchEphemeralKey(customerId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://mcj-stripe-backend.onrender.com/create-ephemeral-key.php")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            val body = JSONObject().apply {
                put("customer_id", customerId)
            }.toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            json.optString("ephemeralKey", null)
        } catch (e: Exception) {
            Log.e("StripeDebug", "Error fetching ephemeral key", e)
            null
        }
    }

    private fun presentPaymentSheet() {
        CoroutineScope(Dispatchers.Main).launch {
            val customerId = "cus_TLOTiuEU23pQrq"
            val ephemeralKey = fetchEphemeralKey(customerId)
            if (ephemeralKey == null) {
                handlePaymentFailure("Failed to fetch ephemeral key")
                return@launch
            }
            val configuration = PaymentSheet.Configuration(
                merchantDisplayName = "McJay Store",
                customer = PaymentSheet.CustomerConfiguration(
                    id = customerId,
                    ephemeralKeySecret = ephemeralKey
                )
            )
            paymentIntentClientSecret?.let { clientSecret ->
                paymentSheet.presentWithPaymentIntent(
                    paymentIntentClientSecret = clientSecret,
                    configuration = configuration
                )
            } ?: handlePaymentFailure("Payment not ready")
        }
    }

    private fun onPaymentSheetResult(paymentResult: PaymentSheetResult) {
        when (paymentResult) {
            is PaymentSheetResult.Completed -> {
                Log.d("PaymentDebug", "🎉 Stripe payment completed successfully")
                scope.launch {
                    updateOrderStatus(pendingOrderId, "Paid", "Confirmed")
                    createPaymentRecord(pendingOrderId, pendingOrderNumber, pendingTotalAmount, pendingPaymentMethod, "completed")
                    completeStripeOrder()
                }
            }
            is PaymentSheetResult.Canceled -> {
                Log.d("PaymentDebug", "❌ Stripe payment canceled")
                handlePaymentFailure("Payment was canceled")
            }
            is PaymentSheetResult.Failed -> {
                Log.d("PaymentDebug", "❌ Stripe payment failed: ${paymentResult.error.message}")
                handlePaymentFailure("Payment failed: ${paymentResult.error.message}")
            }
        }
    }

    private fun simulateUPIPayment(orderId: Int, orderNumber: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnPlaceOrder.text = "Processing UPI..."
        scope.launch {
            try {
                Log.d("PaymentDebug", "🔄 Starting UPI payment simulation")
                withContext(Dispatchers.IO) {
                    Thread.sleep(2000)
                }
                val isSuccess = (1..100).random() > 10
                Log.d("PaymentDebug", "🎲 UPI payment simulation result: $isSuccess")
                if (isSuccess) {
                    updateOrderStatus(orderId, "Paid", "Confirmed")
                    createPaymentRecord(orderId, orderNumber, pendingTotalAmount, pendingPaymentMethod, "completed")
                    processOrderItems()
                    clearUserCart()
                    completeOrder(orderId, orderNumber, "✅ UPI payment successful! Order confirmed.")
                } else {
                    createPaymentRecord(orderId, orderNumber, pendingTotalAmount, pendingPaymentMethod, "failed")
                    handlePaymentFailure("UPI payment failed. Please try again or use another method.")
                }
            } catch (e: Exception) {
                Log.e("PaymentDebug", "❌ UPI payment simulation error", e)
                handlePaymentFailure("UPI processing error: ${e.message}")
            }
        }
    }

    private suspend fun processOrderItems() {
        try {
            Log.d("CheckoutFragment", "Starting processOrderItems with ${cartItems.size} cart items")
            for (cartItem in cartItems) {
                val product = productsMap[cartItem.product_id]
                Log.d("CheckoutFragment", "Processing cart item: product_id=${cartItem.product_id}, product=$product")
                if (product == null || product.id == null) {
                    Log.e("CheckoutFragment", "❌ Product or product ID not found for cart item: ${cartItem.product_id}")
                    continue
                }
                val unitPrice = product.discount_price ?: product.price ?: 0.0
                val actualProductId = if (!product.product_id.isNullOrEmpty()) {
                    product.product_id
                } else {
                    product.id.toString()
                }
                Log.d("CheckoutFragment", "Using product_id: $actualProductId for order item")
                createOrderItemBasic(
                    orderId = pendingOrderId,
                    orderNumber = pendingOrderNumber,
                    productId = actualProductId,
                    sellerId = product.seller_id ?: supabase.auth.currentUserOrNull()?.id ?: "",
                    quantity = cartItem.quantity,
                    price = unitPrice,
                    subtotal = unitPrice * cartItem.quantity
                )
                // Update stock quantity
                updateProductStock(product.id, cartItem.quantity)
            }
            Log.d("CheckoutFragment", "✅ All order items processed and stocks updated")
        } catch (e: Exception) {
            Log.e("CheckoutFragment", "Error processing order items", e)
            throw e
        }
    }

    private suspend fun completeStripeOrder() {
        try {
            processOrderItems()
            // Clear cart after successful order
            clearUserCart()
            completeOrder(pendingOrderId, pendingOrderNumber, "✅ Card payment successful! Order confirmed.")
        } catch (e: Exception) {
            Log.e("CheckoutFragment", "Error completing Stripe order", e)
            handlePaymentFailure("Error completing order: ${e.message}")
        }
    }

    private suspend fun updateProductStock(productId: Int, quantityToReduce: Int) {
        withContext(Dispatchers.IO) {
            try {
                val currentProduct = supabase.postgrest.from("products")
                    .select {
                        filter { eq("id", productId) }
                    }
                    .decodeSingle<Product>()
                val newStock = (currentProduct.stock_quantity - quantityToReduce).coerceAtLeast(0)
                supabase.postgrest.from("products")
                    .update({
                        set("stock_quantity", newStock)
                    }) {
                        filter { eq("id", productId) }
                    }
                Log.d("CheckoutFragment", "✅ Updated stock for product $productId to $newStock")
            } catch (e: Exception) {
                Log.e("CheckoutFragment", "❌ Error updating stock for product $productId", e)
                throw e
            }
        }
    }

    private suspend fun clearUserCart() {
        val authUser = supabase.auth.currentUserOrNull()
        if (authUser != null) {
            try {
                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("cart")
                        .delete {
                            filter { eq("user_id", authUser.id) }
                        }
                }
                Log.d("CheckoutFragment", "✅ Cart cleared successfully")
            } catch (e: Exception) {
                Log.e("CheckoutFragment", "❌ Error clearing cart", e)
            }
        }
    }

    private fun generateOrderNumber(): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val random = UUID.randomUUID().toString().substring(0, 8).uppercase()
        return "ORD${timestamp}${random}"
    }

    private fun generatePaymentTrackingId(): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val random = UUID.randomUUID().toString().substring(0, 6).uppercase()
        return "PAY-$timestamp-$random"
    }

    private suspend fun createOrderBasic(
        userId: String,
        total: Double,
        paymentMethod: String,
        address: String
    ): Pair<Int, String> {
        return withContext(Dispatchers.IO) {
            try {
                val paymentMethodEnum = when (paymentMethod) {
                    "Card" -> "Card"
                    "Cash on Delivery" -> "Cash on Delivery"
                    "UPI" -> "UPI"
                    else -> "Card"
                }
                val paymentStatus = when (paymentMethodEnum) {
                    "Cash on Delivery" -> "Pending"
                    else -> "Pending"
                }
                val orderNumber = generateOrderNumber()
                val order = Order(
                    order_number = orderNumber,
                    user_id = userId,
                    total_amount = total,
                    payment_method = paymentMethodEnum,
                    payment_status = paymentStatus,
                    order_status = "Pending",
                    shipping_address = address
                )
                val result = supabase.postgrest.from("orders")
                    .insert(order) {
                        select()
                    }
                    .decodeSingle<Order>()
                val orderId = result.order_id ?: throw Exception("No order ID returned")
                Pair(orderId, orderNumber)
            } catch (e: Exception) {
                Log.e("CheckoutFragment", "Error creating order", e)
                throw e
            }
        }
    }

    private suspend fun createOrderItemBasic(
        orderId: Int,
        orderNumber: String,
        productId: String,
        sellerId: String,
        quantity: Int,
        price: Double,
        subtotal: Double
    ) {
        withContext(Dispatchers.IO) {
            try {
                val orderItem = OrderItem(
                    order_id = orderId,
                    order_number = orderNumber,
                    product_id = productId,
                    seller_id = sellerId,
                    quantity = quantity,
                    price = price,
                    subtotal = subtotal,
                    item_status = "Pending"
                )
                supabase.postgrest.from("order_items")
                    .insert(orderItem)
            } catch (e: Exception) {
                Log.e("CheckoutFragment", "Error creating order item", e)
                throw e
            }
        }
    }

    // UPDATED: Multiple seller payment processing functions
    private suspend fun createPaymentRecord(
        orderId: Int,
        orderNumber: String,
        totalAmount: Double, // This is the TOTAL order amount (63934.92)
        paymentMethod: String,
        status: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val authUser = supabase.auth.currentUserOrNull()
                if (authUser == null) {
                    Log.e("PaymentDebug", "❌ User not authenticated")
                    return@withContext
                }
                Log.d("PaymentDebug", "🔍 Starting payment record creation")
                Log.d("PaymentDebug", "Order ID: $orderId, Order Number: $orderNumber")
                Log.d("PaymentDebug", "Total Order Amount: $totalAmount, Method: $paymentMethod, Status: $status")
                // Debug: Log all products to see what seller_id they have
                Log.d("PaymentDebug", "🔍 Debugging products in cart:")
                cartItems.forEachIndexed { index, cartItem ->
                    val product = productsMap[cartItem.product_id]
                    Log.d("PaymentDebug", " Product $index: ID=${product?.id}, Name=${product?.name}, SellerID=${product?.seller_id}, SellerName=${product?.seller_name}")
                }
                // Group cart items by seller to handle multiple sellers
                val sellerGroups = groupCartItemsBySeller()
                if (sellerGroups.isEmpty()) {
                    // If still no sellers found, use a fallback approach
                    Log.w("PaymentDebug", "⚠️ No sellers found, using fallback approach")
                    processFallbackPayment(
                        orderId = orderId,
                        orderNumber = orderNumber,
                        amount = totalAmount,
                        paymentMethod = paymentMethod,
                        status = status,
                        authUserId = authUser.id
                    )
                    return@withContext
                }
                Log.d("PaymentDebug", "👥 Found ${sellerGroups.size} seller(s) in cart")
                // Calculate total subtotal for proportion calculation
                val totalSubtotal = cartItems.sumOf { cartItem ->
                    val product = productsMap[cartItem.product_id]
                    val unitPrice = product?.discount_price ?: product?.price ?: 0.0
                    unitPrice * cartItem.quantity
                }
                Log.d("PaymentDebug", "💰 Total subtotal for order: $totalSubtotal")
                Log.d("PaymentDebug", "💰 Total order amount: $totalAmount")
                // Process payment for each seller group with proper amount distribution
                for ((sellerId, sellerCartItems) in sellerGroups) {
                    try {
                        Log.d("PaymentDebug", "💰 Processing payment for seller: $sellerId")
                        // Calculate this seller's subtotal
                        val sellerSubtotal = sellerCartItems.sumOf { cartItem ->
                            val product = productsMap[cartItem.product_id]
                            val unitPrice = product?.discount_price ?: product?.price ?: 0.0
                            unitPrice * cartItem.quantity
                        }
                        // Calculate this seller's proportion of the total
                        val sellerProportion = sellerSubtotal / totalSubtotal
                        val sellerShare = totalAmount * sellerProportion
                        Log.d("PaymentDebug", "💰 Seller $sellerId subtotal: $sellerSubtotal")
                        Log.d("PaymentDebug", "💰 Seller proportion: ${"%.2f".format(sellerProportion * 100)}%")
                        Log.d("PaymentDebug", "💰 Seller share: $sellerShare")
                        processSellerPayment(
                            sellerId = sellerId,
                            sellerCartItems = sellerCartItems,
                            orderId = orderId,
                            orderNumber = orderNumber,
                            paymentMethod = paymentMethod,
                            status = status,
                            authUserId = authUser.id,
                            sellerShare = sellerShare, // Seller's share of the total
                            totalOrderAmount = totalAmount // Total amount user paid
                        )
                    } catch (e: Exception) {
                        Log.e("PaymentDebug", "❌ Failed to process payment for seller $sellerId: ${e.message}", e)
                        // Continue to next seller instead of failing the entire process
                    }
                }
                Log.d("PaymentDebug", "✅ All seller payments processed successfully")
            } catch (e: Exception) {
                Log.e("PaymentDebug", "❌ Error creating payment record", e)
                Log.e("PaymentDebug", "❌ Error details: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun processFallbackPayment(
        orderId: Int,
        orderNumber: String,
        amount: Double,
        paymentMethod: String,
        status: String,
        authUserId: String
    ) {
        try {
            Log.d("PaymentDebug", "🔄 Using fallback payment processing")
            val defaultSellerId = getDefaultSellerId()
            if (defaultSellerId == null) {
                throw Exception("Could not determine seller for payment")
            }
            Log.d("PaymentDebug", "💰 Using default seller: $defaultSellerId")
            // Get seller's commission rate
            val seller = getSellerProfile(defaultSellerId)
            val commissionRate = seller?.commission_rate ?: 15.0
            // Calculate amounts based on commission rate
            val companyAmount = amount * (commissionRate / 100)
            val sellerNetAmount = amount - companyAmount
            Log.d("PaymentDebug", "💰 Commission Rate: $commissionRate%")
            Log.d("PaymentDebug", "💰 Amount breakdown - Total: $amount, Seller: $sellerNetAmount, Company: $companyAmount")
            // Map payment method to match database ENUM
            val dbPaymentMethod = when (paymentMethod) {
                "Card" -> "card"
                "UPI" -> "wallet"
                "Cash on Delivery" -> "bank_transfer"
                else -> "card"
            }
            val paymentTrackingId = generatePaymentTrackingId()
            Log.d("PaymentDebug", "✅ Generated tracking ID: $paymentTrackingId")
            val paidAt = if (status == "completed") {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            } else {
                null
            }
            // Create payment record - FIXED: amount should be total order amount
            val payment = Payment(
                seller_id = defaultSellerId,
                user_id = authUserId,
                order_id = orderId,
                order_number = orderNumber,
                payment_trackingid = paymentTrackingId,
                amount = amount, // Total order amount
                seller_amount = sellerNetAmount,
                company_amount = companyAmount,
                payment_method = dbPaymentMethod,
                payment_status = status,
                paid_at = paidAt
            )
            Log.d("PaymentDebug", "📦 Payment object created: $payment")
            // Insert into payments table
            withContext(Dispatchers.IO) {
                supabase.postgrest.from("payments")
                    .insert(payment)
            }
            Log.d("PaymentDebug", "✅ Payment record successfully inserted: $paymentTrackingId")
            // UPDATE BALANCES if payment is completed
            if (status == "completed") {
                Log.d("PaymentDebug", "🔄 Starting balance update for seller: $defaultSellerId")
                updateBalancesForSeller(defaultSellerId, sellerNetAmount, companyAmount)
            }
        } catch (e: Exception) {
            Log.e("PaymentDebug", "❌ Error in fallback payment processing", e)
            throw e
        }
    }

    private suspend fun groupCartItemsBySeller(): Map<String, List<CartItem>> {
        val sellerGroups = mutableMapOf<String, MutableList<CartItem>>()
        for (cartItem in cartItems) {
            val product = productsMap[cartItem.product_id]
            if (product != null) {
                // Try to get seller_id from different possible sources
                val sellerId = when {
                    !product.seller_id.isNullOrEmpty() -> product.seller_id
                    !product.seller_name.isNullOrEmpty() -> {
                        // If we have seller_name but no seller_id, try to find or create seller
                        getOrCreateSellerByName(product.seller_name)
                    }
                    else -> {
                        // Fallback: Use a default seller or the current user as seller
                        Log.w("PaymentDebug", "⚠️ No seller info for product ${product.id}, using default seller")
                        getDefaultSellerId()
                    }
                }
                if (!sellerId.isNullOrEmpty()) {
                    if (!sellerGroups.containsKey(sellerId)) {
                        sellerGroups[sellerId] = mutableListOf()
                    }
                    sellerGroups[sellerId]?.add(cartItem)
                    Log.d("PaymentDebug", "📦 Added item to seller $sellerId: ${product.name}")
                } else {
                    Log.e("PaymentDebug", "❌ Could not determine seller for product: ${product.id}")
                }
            } else {
                Log.e("PaymentDebug", "❌ Product not found for cart item: ${cartItem.product_id}")
            }
        }
        return sellerGroups
    }

    private suspend fun getOrCreateSellerByName(sellerName: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                // First try to find existing seller by name
                val existingSeller = supabase.postgrest.from("sellers")
                    .select {
                        filter { eq("full_name", sellerName) }
                    }
                    .decodeSingleOrNull<Seller>()
                if (existingSeller != null) {
                    existingSeller.id
                } else {
                    // Create a new seller record
                    val authUser = supabase.auth.currentUserOrNull()
                    if (authUser != null) {
                        val newSeller = Seller(
                            id = authUser.id, // Use user ID as seller ID
                            full_name = sellerName,
                            email = authUser.email ?: "",
                            mobile_number = "",
                            user_type = "seller",
                            created_at = System.currentTimeMillis(),
                            is_verified = false,
                            commission_rate = 15.0,
                            seller_balance = 0.0,
                            total_earnings = 0.0
                        )
                        supabase.postgrest.from("sellers")
                            .insert(newSeller)
                        authUser.id
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PaymentDebug", "❌ Error finding/creating seller by name: $sellerName", e)
            getDefaultSellerId()
        }
    }

    private suspend fun getDefaultSellerId(): String? {
        return try {
            withContext(Dispatchers.IO) {
                // Try to get the first seller from the database
                val defaultSeller = supabase.postgrest.from("sellers")
                    .select {
                        limit(1)
                    }
                    .decodeSingleOrNull<Seller>()
                defaultSeller?.id ?: run {
                    // If no sellers exist, use current user as seller
                    val authUser = supabase.auth.currentUserOrNull()
                    if (authUser != null) {
                        // Create a seller record for the current user
                        val newSeller = Seller(
                            id = authUser.id,
                            full_name = authUser.email ?: "Default Seller",
                            email = authUser.email ?: "",
                            mobile_number = "",
                            user_type = "seller",
                            created_at = System.currentTimeMillis(),
                            is_verified = false,
                            commission_rate = 15.0,
                            seller_balance = 0.0,
                            total_earnings = 0.0
                        )
                        supabase.postgrest.from("sellers")
                            .insert(newSeller)
                        authUser.id
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PaymentDebug", "❌ Error getting default seller", e)
            null
        }
    }

    // UPDATED: processSellerPayment with proper amount handling
    private suspend fun processSellerPayment(
        sellerId: String,
        sellerCartItems: List<CartItem>,
        orderId: Int,
        orderNumber: String,
        paymentMethod: String,
        status: String,
        authUserId: String,
        sellerShare: Double, // Seller's share of the total amount
        totalOrderAmount: Double // Total amount user paid
    ) {
        try {
            Log.d("PaymentDebug", "🔍 Processing payment for seller: $sellerId")
            Log.d("PaymentDebug", "📦 Seller has ${sellerCartItems.size} items in cart")
            Log.d("PaymentDebug", "💰 Seller share: $sellerShare")
            Log.d("PaymentDebug", "💰 Total order amount: $totalOrderAmount")
            // Get seller's commission rate
            val seller = getSellerProfile(sellerId)
            val commissionRate = seller?.commission_rate ?: 15.0
            // Calculate amounts based on commission rate
            val companyAmount = sellerShare * (commissionRate / 100)
            val sellerNetAmount = sellerShare - companyAmount
            Log.d("PaymentDebug", "💰 Commission Rate: $commissionRate%")
            Log.d("PaymentDebug", "💰 Amount breakdown - Seller Share: $sellerShare, Seller Net: $sellerNetAmount, Company: $companyAmount")
            // Map payment method to match database ENUM
            val dbPaymentMethod = when (paymentMethod) {
                "Card" -> "card"
                "UPI" -> "wallet"
                "Cash on Delivery" -> "bank_transfer"
                else -> "card"
            }
            val paymentTrackingId = "${generatePaymentTrackingId()}-$sellerId"
            Log.d("PaymentDebug", "✅ Generated tracking ID: $paymentTrackingId")
            val paidAt = if (status == "completed") {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            } else {
                null
            }
            // Create payment record for this seller - FIXED: amount should be sellerShare to satisfy check_amount_totals
            val payment = Payment(
                seller_id = sellerId,
                user_id = authUserId,
                order_id = orderId,
                order_number = orderNumber,
                payment_trackingid = paymentTrackingId,
                amount = sellerShare, // FIXED: Use sellerShare instead of totalOrderAmount
                seller_amount = sellerNetAmount,
                company_amount = companyAmount,
                payment_method = dbPaymentMethod,
                payment_status = status,
                paid_at = paidAt
            )
            Log.d("PaymentDebug", "📦 Payment object created for seller $sellerId: $payment")
            // Insert into payments table
            withContext(Dispatchers.IO) {
                supabase.postgrest.from("payments")
                    .insert(payment)
            }
            Log.d("PaymentDebug", "✅ Payment record successfully inserted for seller $sellerId: $paymentTrackingId")
            // UPDATE BALANCES if payment is completed
            if (status == "completed") {
                Log.d("PaymentDebug", "🔄 Starting balance update for seller: $sellerId")
                updateBalancesForSeller(sellerId, sellerNetAmount, companyAmount)
            }
        } catch (e: Exception) {
            Log.e("PaymentDebug", "❌ Error processing payment for seller $sellerId", e)
            throw e
        }
    }

    // UPDATED: Fixed balance update functions
    private suspend fun updateBalancesForSeller(sellerId: String, sellerAmount: Double, companyAmount: Double) {
        try {
            Log.d("PaymentDebug", "🔄 Updating balances for seller: $sellerId")
            Log.d("PaymentDebug", "Seller Amount: $sellerAmount, Company Amount: $companyAmount")
            // Update seller balance using direct database operations
            updateSellerBalanceDirect(sellerId, sellerAmount)
            // Update company balance using direct database operations
            updateCompanyBalanceDirect(companyAmount)
            Log.d("PaymentDebug", "✅ Balances updated successfully for seller: $sellerId")
        } catch (e: Exception) {
            Log.e("PaymentDebug", "❌ Error updating balances for seller $sellerId", e)
            throw e
        }
    }

    private suspend fun updateSellerBalanceDirect(sellerId: String, amountToAdd: Double) {
        try {
            Log.d("PaymentDebug", "🔄 Updating seller balance directly for: $sellerId")
            Log.d("PaymentDebug", "Amount to add to seller: $amountToAdd")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            withContext(Dispatchers.IO) {
                // Try to get current seller balance
                val currentBalanceResult = supabase.postgrest.from("seller_balance")
                    .select {
                        filter { eq("seller_id", sellerId) }
                    }
                    .decodeSingleOrNull<SellerBalance>()
                if (currentBalanceResult == null) {
                    // Insert new record
                    val newSellerBalance = SellerBalance(
                        seller_id = sellerId,
                        balance = amountToAdd,
                        total_earnings = amountToAdd,
                        updated_at = timestamp
                    )
                    supabase.postgrest.from("seller_balance")
                        .insert(newSellerBalance)
                    Log.d("PaymentDebug", "✅ New seller balance record created for: $sellerId")
                } else {
                    // Update existing record using set builder to avoid serialization issues
                    val newBalance = currentBalanceResult.balance + amountToAdd
                    val newTotalEarnings = currentBalanceResult.total_earnings + amountToAdd
                    supabase.postgrest.from("seller_balance")
                        .update({
                            set("balance", newBalance)
                            set("total_earnings", newTotalEarnings)
                            set("updated_at", timestamp)
                        }) {
                            filter { eq("seller_id", sellerId) }
                        }
                    Log.d("PaymentDebug", "✅ Seller balance updated for: $sellerId - New Balance: $newBalance")
                }
            }
        } catch (e: Exception) {
            Log.e("PaymentDebug", "❌ Error updating seller balance for $sellerId", e)
            throw e
        }
    }

    private suspend fun updateCompanyBalanceDirect(amountToAdd: Double) {
        try {
            Log.d("PaymentDebug", "🔄 Updating company balance directly")
            Log.d("PaymentDebug", "Amount to add to company: $amountToAdd")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            withContext(Dispatchers.IO) {
                // Try to get current company balance
                val currentBalanceResult = supabase.postgrest.from("company_balance")
                    .select {
                        filter { eq("id", 1) }
                    }
                    .decodeSingleOrNull<CompanyBalance>()
                if (currentBalanceResult == null) {
                    // Insert new record
                    val newCompanyBalance = CompanyBalance(
                        id = 1,
                        company_balance = amountToAdd,
                        total_commission_earned = amountToAdd,
                        updated_at = timestamp
                    )
                    supabase.postgrest.from("company_balance")
                        .insert(newCompanyBalance)
                    Log.d("PaymentDebug", "✅ New company balance record created")
                } else {
                    // Update existing record using set builder to avoid serialization issues
                    val newBalance = currentBalanceResult.company_balance + amountToAdd
                    val newTotalCommission = currentBalanceResult.total_commission_earned + amountToAdd
                    supabase.postgrest.from("company_balance")
                        .update({
                            set("company_balance", newBalance)
                            set("total_commission_earned", newTotalCommission)
                            set("updated_at", timestamp)
                        }) {
                            filter { eq("id", 1) }
                        }
                    Log.d("PaymentDebug", "✅ Company balance updated - New Balance: $newBalance")
                }
            }
        } catch (e: Exception) {
            Log.e("PaymentDebug", "❌ Error updating company balance", e)
            throw e
        }
    }

    private suspend fun getSellerProfile(sellerId: String): Seller? {
        return try {
            withContext(Dispatchers.IO) {
                supabase.postgrest.from("sellers")
                    .select {
                        filter { eq("id", sellerId) }
                    }
                    .decodeSingleOrNull<Seller>()
            }
        } catch (e: Exception) {
            Log.e("PaymentDebug", "Error fetching seller profile, using default commission rate", e)
            null
        }
    }

    private fun getSelectedPaymentMethod(): String {
        return when {
            binding.radioCard.isChecked -> "Card"
            binding.radioUPI.isChecked -> "UPI"
            binding.radioCOD.isChecked -> "Cash on Delivery"
            else -> "Card"
        }
    }

    private fun setupPaymentMethodSelection() {
        val cardCardView = getCardView(0)
        val upiCardView = getCardView(1)
        val codCardView = getCardView(2)
        val radioCard = binding.radioCard
        val radioUPI = binding.radioUPI
        val radioCOD = binding.radioCOD
        selectPaymentMethod(radioCard, cardCardView)
        cardCardView?.setOnClickListener { selectPaymentMethod(radioCard, cardCardView) }
        upiCardView?.setOnClickListener { selectPaymentMethod(radioUPI, upiCardView) }
        codCardView?.setOnClickListener { selectPaymentMethod(radioCOD, codCardView) }
        radioCard.setOnClickListener { selectPaymentMethod(radioCard, cardCardView) }
        radioUPI.setOnClickListener { selectPaymentMethod(radioUPI, upiCardView) }
        radioCOD.setOnClickListener { selectPaymentMethod(radioCOD, codCardView) }
    }

    private fun getCardView(index: Int): MaterialCardView? {
        return if (binding.paymentMethodGroup.childCount > index) {
            binding.paymentMethodGroup.getChildAt(index) as? MaterialCardView
        } else {
            null
        }
    }

    private fun resetAllPaymentMethods() {
        for (i in 0 until binding.paymentMethodGroup.childCount) {
            val cardView = getCardView(i)
            cardView?.let {
                it.strokeColor = ContextCompat.getColor(requireContext(), R.color.gray_300)
                it.strokeWidth = 1
                it.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
        }
        binding.radioCard.isChecked = false
        binding.radioUPI.isChecked = false
        binding.radioCOD.isChecked = false
    }

    private fun selectPaymentMethod(radioButton: RadioButton, cardView: MaterialCardView?) {
        resetAllPaymentMethods()
        radioButton.isChecked = true
        cardView?.let {
            it.strokeColor = ContextCompat.getColor(requireContext(), R.color.purple_700)
            it.strokeWidth = 2
            it.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.purple_50))
        }
    }

    private suspend fun updateOrderStatus(orderId: Int, paymentStatus: String, orderStatus: String) {
        withContext(Dispatchers.IO) {
            supabase.postgrest.from("orders")
                .update(
                    mapOf(
                        "payment_status" to paymentStatus,
                        "order_status" to orderStatus
                    )
                ) {
                    filter { eq("order_id", orderId) }
                }
        }
    }

    private fun handlePaymentFailure(errorMessage: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnPlaceOrder.isEnabled = true
        binding.btnPlaceOrder.text = "Place Order"
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        if (pendingOrderId != -1) {
            scope.launch {
                updateOrderStatus(pendingOrderId, "Failed", "Cancelled")
                createPaymentRecord(pendingOrderId, pendingOrderNumber, pendingTotalAmount, pendingPaymentMethod, "failed")
            }
        }
    }

    private fun completeOrder(orderId: Int, orderNumber: String, message: String) {
        Log.d("CheckoutFragment", "completeOrder called - Order ID: $orderId, Order Number: $orderNumber")
        binding.progressBar.visibility = View.GONE
        binding.btnPlaceOrder.isEnabled = true
        binding.btnPlaceOrder.text = "Place Order"
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                Log.d("CheckoutFragment", "Attempting to start OrderSummaryActivity")
                val intent = Intent(requireContext(), OrderSummaryActivity::class.java).apply {
                    putExtra("order_id", orderId)
                    putExtra("order_number", orderNumber)
                }
                Log.d("CheckoutFragment", "Starting activity with order ID: $orderId")
                startActivity(intent)
                Log.d("CheckoutFragment", "Activity started successfully")
            } catch (e: Exception) {
                Log.e("CheckoutFragment", "Error starting OrderSummaryActivity: ${e.message}", e)
                Toast.makeText(requireContext(), "Error showing order summary: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, 1000)
    }

    companion object {
        fun newInstance() = CheckoutFragment()
    }
}