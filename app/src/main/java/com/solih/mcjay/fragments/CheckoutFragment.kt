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

class CheckoutFragment : Fragment() {

    private lateinit var binding: FragmentCheckoutBinding
    private lateinit var checkoutAdapter: CheckoutAdapter
    private val cartItems = mutableListOf<CartItem>()
    private val productsMap = mutableMapOf<Int, Product>()
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCheckoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

                // Create order using the most basic approach
                val orderId = createOrderBasic(
                    userId = authUser.id,
                    total = total,
                    paymentMethod = paymentMethod,
                    address = currentUser?.address ?: ""
                )

                // Create order items
                for (cartItem in cartItems) {
                    val product = productsMap[cartItem.product_id]
                    val unitPrice = product?.discount_price ?: product?.price ?: 0.0

                    createOrderItemBasic(
                        orderId = orderId,
                        productId = cartItem.product_id,
                        sellerId = product?.seller_id ?: authUser.id,
                        quantity = cartItem.quantity,
                        price = unitPrice,
                        subtotal = unitPrice * cartItem.quantity
                    )
                }

                // Clear cart after successful order
                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("cart")
                        .delete {
                            filter { eq("user_id", authUser.id) }
                        }
                }

                // Handle payment based on method
                handlePaymentProcessing(paymentMethod, orderId)

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnPlaceOrder.isEnabled = true
                Toast.makeText(requireContext(), "Error placing order: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CheckoutFragment", "Error placing order", e)
            }
        }
    }

    private suspend fun createOrderBasic(
        userId: String,
        total: Double,
        paymentMethod: String,
        address: String
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val paymentStatus = if (paymentMethod == "Cash on Delivery") "Pending" else "Paid"

                val order = mapOf<String, Any>(
                    "user_id" to userId,
                    "total_amount" to total,
                    "payment_method" to paymentMethod,
                    "payment_status" to paymentStatus,
                    "order_status" to "Pending",
                    "shipping_address" to address
                )

                val result = supabase.postgrest.from("orders")
                    .insert(order) {
                        select()
                    }
                    .decodeSingle<Map<String, Any>>()

                (result["order_id"] as? Number)?.toInt() ?: throw Exception("No order ID returned")
            } catch (e: Exception) {
                Log.e("CheckoutFragment", "Error creating order", e)
                throw e
            }
        }
    }

    private suspend fun createOrderItemBasic(
        orderId: Int,
        productId: Int,
        sellerId: String,
        quantity: Int,
        price: Double,
        subtotal: Double
    ) {
        withContext(Dispatchers.IO) {
            try {
                val orderItem = mapOf<String, Any>(
                    "order_id" to orderId,
                    "product_id" to productId,
                    "seller_id" to sellerId,
                    "quantity" to quantity,
                    "price" to price,
                    "subtotal" to subtotal,
                    "item_status" to "Pending"
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

    private fun handlePaymentProcessing(paymentMethod: String, orderId: Int) {
        when (paymentMethod) {
            "Card" -> simulateCardPayment(orderId)
            "UPI" -> simulateUPIPayment(orderId)
            "Cash on Delivery" -> completeOrder(orderId, "Order placed successfully! Payment will be collected on delivery.")
        }
    }

    private fun setupPaymentMethodSelection() {
        // Get card views and radio buttons
        val cardCardView = getCardView(0)
        val upiCardView = getCardView(1)
        val codCardView = getCardView(2)

        val radioCard = binding.radioCard
        val radioUPI = binding.radioUPI
        val radioCOD = binding.radioCOD

        // Set initial selection
        selectPaymentMethod(radioCard, cardCardView)

        // Set click listeners for cards
        cardCardView?.setOnClickListener { selectPaymentMethod(radioCard, cardCardView) }
        upiCardView?.setOnClickListener { selectPaymentMethod(radioUPI, upiCardView) }
        codCardView?.setOnClickListener { selectPaymentMethod(radioCOD, codCardView) }

        // Set click listeners for radio buttons
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

    private fun simulateCardPayment(orderId: Int) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnPlaceOrder.text = "Processing Payment..."

        scope.launch {
            try {
                // Simulate API call delay
                withContext(Dispatchers.IO) {
                    Thread.sleep(3000)
                }

                // Simulate payment with 80% success rate
                val isSuccess = (1..100).random() > 20

                if (isSuccess) {
                    updateOrderStatus(orderId, "Paid", "Confirmed")
                    completeOrder(orderId, "✅ Card payment successful! Order confirmed.")
                } else {
                    handlePaymentFailure("Card payment failed. Please try another payment method.")
                }

            } catch (e: Exception) {
                handlePaymentFailure("Payment processing error: ${e.message}")
                Log.e("CheckoutFragment", "Card payment error", e)
            }
        }
    }

    private fun simulateUPIPayment(orderId: Int) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnPlaceOrder.text = "Processing UPI..."

        scope.launch {
            try {
                // Simulate UPI processing delay
                withContext(Dispatchers.IO) {
                    Thread.sleep(2000)
                }

                // Simulate payment with 90% success rate
                val isSuccess = (1..100).random() > 10

                if (isSuccess) {
                    updateOrderStatus(orderId, "Paid", "Confirmed")
                    completeOrder(orderId, "✅ UPI payment successful! Order confirmed.")
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
    }

    private fun completeOrder(orderId: Int, message: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnPlaceOrder.text = "Place Order"
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

        // Navigate back after a short delay to show the success message
        scope.launch {
            withContext(Dispatchers.IO) {
                Thread.sleep(2000) // Show success message for 2 seconds
            }
            findNavController().popBackStack()
        }
    }

    companion object {
        fun newInstance() = CheckoutFragment()
    }
//
//    private data class OrderData(
//        val user_id: String,
//        val total_amount: Double,
//        val payment_method: String,
//        val payment_status: String,
//        val order_status: String,
//        val shipping_address: String
//    )
//
//    private data class OrderItemData(
//        val order_id: Int,
//        val product_id: Int,
//        val seller_id: String,
//        val quantity: Int,
//        val price: Double,
//        val subtotal: Double,
//        val item_status: String
//    )

}