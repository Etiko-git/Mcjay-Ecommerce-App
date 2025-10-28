package com.solih.mcjay.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.R
import com.solih.mcjay.adapters.CartAdapter
import com.solih.mcjay.databinding.FragmentCartBinding
import com.solih.mcjay.models.CartItem
import com.solih.mcjay.models.Product
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CartFragment : Fragment() {

    private lateinit var binding: FragmentCartBinding
    private lateinit var cartAdapter: CartAdapter
    private val cartItems = mutableListOf<CartItem>()
    private val productsMap = mutableMapOf<Int, Product>() // Changed to Map<Int, Product>
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadCartItems()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        cartAdapter = CartAdapter(cartItems, productsMap) { cartItem, action ->
            when (action) {
                "increase" -> updateQuantity(cartItem, cartItem.quantity + 1)
                "decrease" -> updateQuantity(cartItem, cartItem.quantity - 1)
                "remove" -> removeFromCart(cartItem)
            }
        }

        binding.cartRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = cartAdapter
        }
    }

    private fun setupClickListeners() {
        binding.checkoutButton.setOnClickListener {
            proceedToCheckout()
        }

        binding.continueShoppingButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadCartItems() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.cartRecyclerView.visibility = View.GONE

        scope.launch {
            try {
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser == null) {
                    showEmptyState("Please login to view your cart")
                    return@launch
                }

                // Fetch cart items for current user
                val cartItemsList = withContext(Dispatchers.IO) {
                    supabase.postgrest["cart"]
                        .select {
                            filter { eq("user_id", currentUser.id) }
                        }
                        .decodeList<CartItem>()
                }

                if (cartItemsList.isEmpty()) {
                    showEmptyState("Your cart is empty")
                    return@launch
                }

                // Get product IDs from cart items (these are now integers)
                val productIds = cartItemsList.map { it.product_id }.distinct()

                Log.d("CartFragment", "Loading products for IDs: $productIds")

                // Fetch product details for cart items using integer product ID
                val products = mutableListOf<Product>()
                for (productId in productIds) {
                    try {
                        val productList = withContext(Dispatchers.IO) {
                            supabase.postgrest["products"]
                                .select {
                                    filter { eq("id", productId) } // Use integer ID
                                }
                                .decodeList<Product>()
                        }
                        if (productList.isNotEmpty()) {
                            products.add(productList[0])
                            Log.d("CartFragment", "Loaded product: ${productList[0].name} (ID: ${productList[0].id})")
                        } else {
                            Log.w("CartFragment", "No product found for ID: $productId")
                        }
                    } catch (e: Exception) {
                        Log.e("CartFragment", "Error fetching product $productId: ${e.message}")
                    }
                }

                // Create products map with integer keys
                productsMap.clear()
                products.forEach { product ->
                    product.id?.let { productId ->
                        productsMap[productId] = product // Use integer product.id as key
                    }
                }

                Log.d("CartFragment", "Products map size: ${productsMap.size}")

                // Update cart items
                cartItems.clear()
                cartItems.addAll(cartItemsList)

                cartAdapter.notifyDataSetChanged()
                updateTotalPrice()
                binding.progressBar.visibility = View.GONE

                if (cartItems.isEmpty()) {
                    showEmptyState("Your cart is empty")
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.cartRecyclerView.visibility = View.VISIBLE
                    Log.d("CartFragment", "Displaying ${cartItems.size} cart items")
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                showEmptyState("Error loading cart")
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CartFragment", "Error loading cart items", e)
            }
        }
    }

    private fun updateQuantity(cartItem: CartItem, newQuantity: Int) {
        if (newQuantity < 1) {
            removeFromCart(cartItem)
            return
        }

        // Check stock availability - now using integer key
        val product = productsMap[cartItem.product_id]
        if (product != null && newQuantity > product.stock_quantity) {
            Toast.makeText(requireContext(), "Only ${product.stock_quantity} items available", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    supabase.postgrest["cart"]
                        .update({
                            set("quantity", newQuantity)
                        }) {
                            filter {
                                eq("id", cartItem.id ?: 0)
                                eq("user_id", supabase.auth.currentUserOrNull()?.id ?: "")
                            }
                        }
                }

                // Update local cart item
                val index = cartItems.indexOfFirst { it.id == cartItem.id }
                if (index != -1) {
                    cartItems[index] = cartItem.copy(quantity = newQuantity)
                    cartAdapter.notifyItemChanged(index)
                    updateTotalPrice()
                    Toast.makeText(requireContext(), "Quantity updated", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error updating quantity: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CartFragment", "Error updating quantity", e)
            }
        }
    }

    private fun removeFromCart(cartItem: CartItem) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    supabase.postgrest["cart"]
                        .delete {
                            filter {
                                eq("id", cartItem.id ?: 0)
                                eq("user_id", supabase.auth.currentUserOrNull()?.id ?: "")
                            }
                        }
                }

                // Remove from local list
                val index = cartItems.indexOfFirst { it.id == cartItem.id }
                if (index != -1) {
                    cartItems.removeAt(index)
                    cartAdapter.notifyItemRemoved(index)
                    updateTotalPrice()

                    if (cartItems.isEmpty()) {
                        showEmptyState("Your cart is empty")
                    }

                    Toast.makeText(requireContext(), "Removed from cart", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error removing item: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CartFragment", "Error removing item", e)
            }
        }
    }

    private fun updateTotalPrice() {
        var total = 0.0

        cartItems.forEach { cartItem ->
            val product = productsMap[cartItem.product_id] // Integer lookup
            val unitPrice = product?.discount_price ?: product?.price ?: 0.0
            total += unitPrice * cartItem.quantity
        }

        binding.totalPrice.text = "$${String.format("%.2f", total)}"
        binding.checkoutButton.isEnabled = cartItems.isNotEmpty()

        Log.d("CartFragment", "Total price updated: $$total")
    }

    private fun showEmptyState(message: String) {
        binding.emptyStateText.text = message
        binding.emptyState.visibility = View.VISIBLE
        binding.cartRecyclerView.visibility = View.GONE
        binding.checkoutButton.isEnabled = false
        binding.progressBar.visibility = View.GONE
    }

    private fun proceedToCheckout() {
        if (cartItems.isEmpty()) {
            Toast.makeText(requireContext(), "Your cart is empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if all items are in stock
        val outOfStockItems = cartItems.filter { cartItem ->
            val product = productsMap[cartItem.product_id] // Integer lookup
            product == null || cartItem.quantity > product.stock_quantity
        }

        if (outOfStockItems.isNotEmpty()) {
            Toast.makeText(requireContext(), "Some items are out of stock", Toast.LENGTH_SHORT).show()
            return
        }

        // Navigate to checkout screen
        Toast.makeText(requireContext(), "Proceeding to checkout", Toast.LENGTH_SHORT).show()
        // You can add navigation to checkout fragment here
    }

    companion object {
        fun newInstance() = CartFragment()
    }
}