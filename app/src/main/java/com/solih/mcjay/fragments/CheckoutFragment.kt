package com.solih.mcjay.fragments

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
import com.solih.mcjay.models.OrderItem
import com.solih.mcjay.models.Product
import com.solih.mcjay.models.User
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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
    private var pendingOrderNumber: String = "" // New variable for order number
    private var pendingTotalAmount: Double = 0.0

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
            // Navigate to address management
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
                    // Display shipping address
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

        val shipping = if (subtotal > 50.0) 0.0 else 5.0 // Free shipping over $50
        val tax = subtotal * 0.08 // 8% tax
        val total = subtotal + shipping + tax

        binding.tvSubtotal.text = "$${String.format("%.2f", subtotal)}"
        binding.tvShipping.text = "$${String.format("%.2f", shipping)}"
        binding.tvTax.text = "$${String.format("%.2f", tax)}"
        binding.tvTotal.text = "$${String.format("%.2f", total)}"
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

                // Create order and get both order_id and order_number
                val (orderId, orderNumber) = createOrderBasic(
                    userId = authUser.id,
                    total = total,
                    paymentMethod = paymentMethod,
                    address = currentUser?.address ?: ""
                )

                pendingOrderId = orderId
                pendingOrderNumber = orderNumber // Store order number
                pendingTotalAmount = total

                // Handle payment based on method
                when (paymentMethod) {
                    "Card" -> initiateStripePayment(total)
                    "UPI" -> simulateUPIPayment(orderId, orderNumber)
                    "Cash on Delivery" -> completeOrder(orderId, orderNumber, "Order placed successfully! Payment will be collected on delivery.")
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
                    // Present Stripe PaymentSheet
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
            // ⚠️ Replace with your actual server address (ngrok or LAN)
            val backendUrl = "http://10.0.2.2/mcj/create-payment-intent.php"

            val url = URL(backendUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            // Create valid JSON body
            val postData = JSONObject().apply {
                // Stripe expects amount in cents (e.g., $10 = 1000)
                put("amount", amount)
            }.toString()

            Log.d("StripeDebug", "➡ Sending JSON: $postData")

            // Send request
            connection.outputStream.use { os ->
                os.write(postData.toByteArray(Charsets.UTF_8))
            }

            // Read response
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

    suspend fun fetchEphemeralKey(customerId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://10.0.2.2/mcj/create-ephemeral-key.php")
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
            val customerId = "cus_TLOTiuEU23pQrq" // from your Stripe dashboard or created earlier
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
                // Payment successful - update order status
                scope.launch {
                    updateOrderStatus(pendingOrderId, "Paid", "Confirmed")
                    completeStripeOrder()
                }
            }
            is PaymentSheetResult.Canceled -> {
                handlePaymentFailure("Payment was canceled")
            }
            is PaymentSheetResult.Failed -> {
                handlePaymentFailure("Payment failed: ${paymentResult.error.message}")
            }
        }
    }

    private suspend fun completeStripeOrder() {
        try {
            Log.d("CheckoutFragment", "Starting completeStripeOrder with ${cartItems.size} cart items")

            // Create order items for the pending order
            for (cartItem in cartItems) {
                val product = productsMap[cartItem.product_id]
                Log.d("CheckoutFragment", "Processing cart item: product_id=${cartItem.product_id}, product=$product")

                if (product == null) {
                    Log.e("CheckoutFragment", "❌ Product not found for cart item: ${cartItem.product_id}")
                    continue
                }

                // Debug product fields
                Log.d("CheckoutFragment", "Product details: id=${product.id}, product_id=${product.product_id}, name=${product.name}")

                val unitPrice = product.discount_price ?: product.price ?: 0.0

                // Determine the correct product_id to use
                val actualProductId = if (!product.product_id.isNullOrEmpty()) {
                    product.product_id
                } else {
                    product.id?.toString() ?: cartItem.product_id.toString()
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
            }

            // Clear cart after successful order
            val authUser = supabase.auth.currentUserOrNull()
            if (authUser != null) {
                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("cart")
                        .delete {
                            filter { eq("user_id", authUser.id) }
                        }
                }
            }

            completeOrder(pendingOrderId, pendingOrderNumber, "✅ Card payment successful! Order confirmed.")

        } catch (e: Exception) {
            Log.e("CheckoutFragment", "Error completing Stripe order", e)
            handlePaymentFailure("Error completing order: ${e.message}")
        }
    }

    private fun generateOrderNumber(): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val random = UUID.randomUUID().toString().substring(0, 8).uppercase()
        return "ORD-$timestamp-$random"
    }

    private suspend fun createOrderBasic(
        userId: String,
        total: Double,
        paymentMethod: String,
        address: String
    ): Pair<Int, String> { // Return both order_id and order_number
        return withContext(Dispatchers.IO) {
            try {
                // Map payment method to ENUM values
                val paymentMethodEnum = when (paymentMethod) {
                    "Card" -> "Card"
                    "Cash on Delivery" -> "Cash on Delivery"
                    "UPI" -> "UPI"
                    else -> "Card" // default
                }

                val paymentStatus = when (paymentMethodEnum) {
                    "Cash on Delivery" -> "Pending"
                    "Card" -> "Pending" // Will be updated after Stripe payment
                    "UPI" -> "Pending"
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
        orderNumber: String, // New parameter
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
                    order_number = orderNumber, // Include order number
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

    private fun getSelectedPaymentMethod(): String {
        return when {
            binding.radioCard.isChecked -> "Card"
            binding.radioUPI.isChecked -> "UPI"
            binding.radioCOD.isChecked -> "Cash on Delivery"
            else -> "Card" // default
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

    private fun simulateUPIPayment(orderId: Int, orderNumber: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnPlaceOrder.text = "Processing UPI..."

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Thread.sleep(2000)
                }

                val isSuccess = (1..100).random() > 10

                if (isSuccess) {
                    updateOrderStatus(orderId, "Paid", "Confirmed")
                    completeOrder(orderId, orderNumber, "✅ UPI payment successful! Order confirmed.")
                } else {
                    handlePaymentFailure("UPI payment failed. Please try again or use another method.")
                }

            } catch (e: Exception) {
                handlePaymentFailure("UPI processing error: ${e.message}")
                Log.e("CheckoutFragment", "UPI payment error", e)
            }
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

        // Update order status to failed for card payments
        if (pendingOrderId != -1) {
            scope.launch {
                updateOrderStatus(pendingOrderId, "Failed", "Cancelled")
            }
        }
    }

    private fun completeOrder(orderId: Int, orderNumber: String, message: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnPlaceOrder.isEnabled = true
        binding.btnPlaceOrder.text = "Place Order"

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

        // Simple approach - navigate immediately without delay
        try {
            val bundle = Bundle().apply {
                putInt("order_id", orderId)
                putString("order_number", orderNumber)
            }
            findNavController().navigate(R.id.orderSummaryFragment, bundle)
        } catch (e: Exception) {
            Log.e("CheckoutFragment", "Navigation failed: ${e.message}", e)
            Toast.makeText(requireContext(), "Order placed! Navigation failed.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun newInstance() = CheckoutFragment()
    }
}