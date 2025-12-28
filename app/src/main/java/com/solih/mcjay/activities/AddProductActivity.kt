package com.solih.mcjay.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.adapters.ImagePreviewAdapter
import com.solih.mcjay.databinding.ActivityAddProductBinding
import com.solih.mcjay.models.Product
import com.solih.mcjay.models.Seller
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    private val imageUris = mutableListOf<Uri>()
    private lateinit var imagePreviewAdapter: ImagePreviewAdapter

    // Seller information
    private var currentSellerId: String? = null
    private var currentSellerName: String? = null

    // Auto-generated product ID
    private var generatedProductId: String = ""

    // Categories list
    private val categories = arrayOf(
        "Bags",
        "Shoes",
        "Electronics",
        "Clothing",
        "Home & Garden",
        "Jewelry",
        "Beauty & Health",
        "Sports & Outdoors",
        "Toys & Games",
        "Books & Stationery",
        "Automotive",
        "Food & Beverages"
    )

    // Validation limits
    private companion object {
        const val MAX_NAME_LENGTH = 200
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_TYPE_LENGTH = 100
        const val MAX_BRAND_LENGTH = 100
        const val MAX_SKU_LENGTH = 50
        const val MAX_PRICE = 1000000.0
        const val MAX_DISCOUNT_PRICE = 1000000.0
        const val MAX_STOCK_QUANTITY = 100
        const val MAX_IMAGES = 3
    }

    // Contract for image selection
    private val imagePickerResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                if (intent.clipData != null) {
                    // Multiple images selected
                    val clipData = intent.clipData
                    for (i in 0 until (clipData?.itemCount ?: 0)) {
                        clipData?.getItemAt(i)?.uri?.let { uri ->
                            if (imageUris.size < MAX_IMAGES) {
                                imageUris.add(uri)
                            } else {
                                Toast.makeText(this, "Maximum $MAX_IMAGES images allowed", Toast.LENGTH_SHORT).show()
                                break
                            }
                        }
                    }
                } else {
                    // Single image selected
                    intent.data?.let { uri ->
                        if (imageUris.size < MAX_IMAGES) {
                            imageUris.add(uri)
                        } else {
                            Toast.makeText(this, "Maximum $MAX_IMAGES images allowed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                updateImagePreview()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Generate product ID first
        generateProductId()
        // Load seller information
        loadSellerInfo()
        setupCategoryDropdown()
        setupImagePreview()
        setupListeners()
        setupTextWatchers()
    }

    private fun generateProductId() {
        // Generate a unique product ID using timestamp and random alphanumeric characters
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val randomChars = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        generatedProductId = "PROD-${timestamp}${randomChars}"

        Log.d("AddProduct", "Generated Product ID: $generatedProductId")
    }

    private fun loadSellerInfo() {
        scope.launch {
            try {
                // Get current authenticated user
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser != null) {
                    currentSellerId = currentUser.id
                    Log.d("AddProduct", "Current user ID: $currentSellerId")

                    // Fetch seller profile to get full name
                    val sellerProfile = getSellerProfile(currentUser.id)
                    currentSellerName = sellerProfile?.full_name ?: "Unknown Seller"

                    Log.d("AddProduct", "Seller name: $currentSellerName")

                    runOnUiThread {
                        // Update UI to show who is adding the product
                        binding.tvSellerInfo.text = "Adding product as: $currentSellerName"
                    }
                } else {
                    Log.e("AddProduct", "No user logged in")
                    runOnUiThread {
                        Toast.makeText(this@AddProductActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("AddProduct", "Error loading seller info: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@AddProductActivity, "Error loading seller information", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getSellerProfile(userId: String): Seller? {
        return try {
            supabase.postgrest["sellers"]
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<Seller>()
        } catch (e: Exception) {
            Log.e("AddProduct", "Error fetching seller profile: ${e.message}", e)
            null
        }
    }

    private fun setupCategoryDropdown() {
        val adapter = ArrayAdapter(this, R.layout.dropdown_menu_item, categories)
        (binding.categoryInputLayout.editText as? MaterialAutoCompleteTextView)?.setAdapter(adapter)

        // Set dropdown properties
        binding.autoCompleteCategory.setOnItemClickListener { parent, view, position, id ->
            val selectedCategory = parent.getItemAtPosition(position) as String
            Log.d("AddProduct", "Selected category: $selectedCategory")
        }
    }

    private fun setupImagePreview() {
        imagePreviewAdapter = ImagePreviewAdapter(imageUris) { position ->
            // Remove image callback
            imageUris.removeAt(position)
            updateImagePreview()
        }

        binding.rvImagePreview.apply {
            layoutManager = GridLayoutManager(this@AddProductActivity, 3)
            adapter = imagePreviewAdapter
        }
    }

    private fun setupListeners() {
        // Back button listener
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnAddImages.setOnClickListener {
            openImagePicker()
        }

        binding.btnSubmit.setOnClickListener {
            if (validateInputs()) {
                addProduct()
            }
        }
    }

    private fun setupTextWatchers() {
        // Name text watcher
        binding.etProductName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val name = s.toString().trim()
                if (name.length > MAX_NAME_LENGTH) {
                    binding.nameInputLayout.error = "Name cannot exceed $MAX_NAME_LENGTH characters"
                } else {
                    binding.nameInputLayout.error = null
                }
            }
        })

        // Description text watcher
        binding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val description = s.toString().trim()
                if (description.length > MAX_DESCRIPTION_LENGTH) {
                    binding.descriptionInputLayout.error = "Description cannot exceed $MAX_DESCRIPTION_LENGTH characters"
                } else {
                    binding.descriptionInputLayout.error = null
                }
            }
        })

        // Type text watcher
        binding.etType.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val type = s.toString().trim()
                if (type.length > MAX_TYPE_LENGTH) {
                    binding.typeInputLayout.error = "Type cannot exceed $MAX_TYPE_LENGTH characters"
                } else {
                    binding.typeInputLayout.error = null
                }
            }
        })

        // Brand text watcher
        binding.etBrand.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val brand = s.toString().trim()
                if (brand.length > MAX_BRAND_LENGTH) {
                    binding.brandInputLayout.error = "Brand cannot exceed $MAX_BRAND_LENGTH characters"
                } else {
                    binding.brandInputLayout.error = null
                }
            }
        })

        // SKU text watcher
        binding.etSku.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val sku = s.toString().trim()
                if (sku.length > MAX_SKU_LENGTH) {
                    binding.skuInputLayout.error = "SKU cannot exceed $MAX_SKU_LENGTH characters"
                } else {
                    binding.skuInputLayout.error = null
                }
            }
        })

        // Price text watcher
        binding.etPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val priceText = s.toString().trim()
                if (priceText.isNotEmpty()) {
                    try {
                        val priceValue = priceText.toDouble()
                        if (priceValue > MAX_PRICE) {
                            binding.priceInputLayout.error = "Price cannot exceed ₹$MAX_PRICE"
                        } else {
                            binding.priceInputLayout.error = null
                        }
                    } catch (e: NumberFormatException) {
                        binding.priceInputLayout.error = "Invalid price format"
                    }
                }
            }
        })

        // Stock quantity text watcher
        binding.etStockQuantity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val stockText = s.toString().trim()
                if (stockText.isNotEmpty()) {
                    try {
                        val stockValue = stockText.toInt()
                        if (stockValue > MAX_STOCK_QUANTITY) {
                            binding.stockInputLayout.error = "Stock quantity cannot exceed $MAX_STOCK_QUANTITY"
                        } else {
                            binding.stockInputLayout.error = null
                        }
                    } catch (e: NumberFormatException) {
                        binding.stockInputLayout.error = "Invalid stock quantity"
                    }
                }
            }
        })

        // Discount price text watcher
        binding.etDiscountPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val discountText = s.toString().trim()
                val priceText = binding.etPrice.text.toString().trim()

                if (discountText.isNotEmpty() && priceText.isNotEmpty()) {
                    try {
                        val discountValue = discountText.toDouble()
                        val priceValue = priceText.toDouble()

                        if (discountValue > MAX_DISCOUNT_PRICE) {
                            binding.discountPriceInputLayout.error = "Discount price cannot exceed ₹$MAX_DISCOUNT_PRICE"
                        } else if (discountValue >= priceValue) {
                            binding.discountPriceInputLayout.error = "Discount price must be less than regular price"
                        } else {
                            binding.discountPriceInputLayout.error = null
                        }
                    } catch (e: NumberFormatException) {
                        binding.discountPriceInputLayout.error = "Invalid discount price format"
                    }
                } else if (discountText.isNotEmpty()) {
                    try {
                        val discountValue = discountText.toDouble()
                        if (discountValue > MAX_DISCOUNT_PRICE) {
                            binding.discountPriceInputLayout.error = "Discount price cannot exceed ₹$MAX_DISCOUNT_PRICE"
                        } else {
                            binding.discountPriceInputLayout.error = null
                        }
                    } catch (e: NumberFormatException) {
                        binding.discountPriceInputLayout.error = "Invalid discount price format"
                    }
                } else {
                    binding.discountPriceInputLayout.error = null
                }
            }
        })
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        imagePickerResult.launch(intent)
    }

    private fun updateImagePreview() {
        if (imageUris.isNotEmpty()) {
            binding.rvImagePreview.visibility = RecyclerView.VISIBLE
            imagePreviewAdapter.notifyDataSetChanged()
        } else {
            binding.rvImagePreview.visibility = RecyclerView.GONE
        }
    }

    private fun validateInputs(): Boolean {
        val name = binding.etProductName.text.toString().trim()
        val category = binding.autoCompleteCategory.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        val stockQuantity = binding.etStockQuantity.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val type = binding.etType.text.toString().trim()
        val brand = binding.etBrand.text.toString().trim()
        val sku = binding.etSku.text.toString().trim()
        val discountPrice = binding.etDiscountPrice.text.toString().trim()

        // Clear previous errors
        binding.nameInputLayout.error = null
        binding.categoryInputLayout.error = null
        binding.priceInputLayout.error = null
        binding.stockInputLayout.error = null
        binding.descriptionInputLayout.error = null
        binding.typeInputLayout.error = null
        binding.brandInputLayout.error = null
        binding.skuInputLayout.error = null
        binding.discountPriceInputLayout.error = null

        var isValid = true

        // Name validation
        if (name.isEmpty()) {
            binding.nameInputLayout.error = "Product name is required"
            isValid = false
        } else if (name.length > MAX_NAME_LENGTH) {
            binding.nameInputLayout.error = "Name cannot exceed $MAX_NAME_LENGTH characters"
            isValid = false
        }

        // Category validation
        if (category.isEmpty()) {
            binding.categoryInputLayout.error = "Please select a category"
            isValid = false
        }

        // Price validation
        if (price.isEmpty()) {
            binding.priceInputLayout.error = "Price is required"
            isValid = false
        } else {
            try {
                val priceValue = price.toDouble()
                if (priceValue <= 0) {
                    binding.priceInputLayout.error = "Price must be greater than 0"
                    isValid = false
                } else if (priceValue > MAX_PRICE) {
                    binding.priceInputLayout.error = "Price cannot exceed ₹$MAX_PRICE"
                    isValid = false
                }
            } catch (e: NumberFormatException) {
                binding.priceInputLayout.error = "Invalid price format"
                isValid = false
            }
        }

        // Stock quantity validation
        if (stockQuantity.isEmpty()) {
            binding.stockInputLayout.error = "Stock quantity is required"
            isValid = false
        } else {
            try {
                val stockValue = stockQuantity.toInt()
                if (stockValue < 0) {
                    binding.stockInputLayout.error = "Stock quantity cannot be negative"
                    isValid = false
                } else if (stockValue > MAX_STOCK_QUANTITY) {
                    binding.stockInputLayout.error = "Stock quantity cannot exceed $MAX_STOCK_QUANTITY"
                    isValid = false
                }
            } catch (e: NumberFormatException) {
                binding.stockInputLayout.error = "Invalid stock quantity"
                isValid = false
            }
        }

        // Description validation
        if (description.length > MAX_DESCRIPTION_LENGTH) {
            binding.descriptionInputLayout.error = "Description cannot exceed $MAX_DESCRIPTION_LENGTH characters"
            isValid = false
        }

        // Type validation
        if (type.length > MAX_TYPE_LENGTH) {
            binding.typeInputLayout.error = "Type cannot exceed $MAX_TYPE_LENGTH characters"
            isValid = false
        }

        // Brand validation
        if (brand.length > MAX_BRAND_LENGTH) {
            binding.brandInputLayout.error = "Brand cannot exceed $MAX_BRAND_LENGTH characters"
            isValid = false
        }

        // SKU validation
        if (sku.length > MAX_SKU_LENGTH) {
            binding.skuInputLayout.error = "SKU cannot exceed $MAX_SKU_LENGTH characters"
            isValid = false
        }

        // Discount price validation (if provided)
        if (discountPrice.isNotEmpty()) {
            try {
                val discountValue = discountPrice.toDouble()
                val priceValue = price.toDouble()

                if (discountValue <= 0) {
                    binding.discountPriceInputLayout.error = "Discount price must be greater than 0"
                    isValid = false
                } else if (discountValue >= priceValue) {
                    binding.discountPriceInputLayout.error = "Discount price must be less than regular price"
                    isValid = false
                } else if (discountValue > MAX_DISCOUNT_PRICE) {
                    binding.discountPriceInputLayout.error = "Discount price cannot exceed ₹$MAX_DISCOUNT_PRICE"
                    isValid = false
                }
            } catch (e: NumberFormatException) {
                binding.discountPriceInputLayout.error = "Invalid discount price format"
                isValid = false
            }
        }

        // Image validation
        if (imageUris.size > MAX_IMAGES) {
            Toast.makeText(this, "Maximum $MAX_IMAGES images allowed", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        // Check if seller info is available
        if (currentSellerId == null || currentSellerName == null) {
            Toast.makeText(this, "Seller information not available. Please login again.", Toast.LENGTH_LONG).show()
            isValid = false
        }

        // Check if product ID is generated
        if (generatedProductId.isEmpty()) {
            Toast.makeText(this, "Product ID generation failed. Please try again.", Toast.LENGTH_LONG).show()
            isValid = false
        }

        return isValid
    }

    private fun addProduct() {
        val name = binding.etProductName.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val category = binding.autoCompleteCategory.text.toString().trim()
        val type = binding.etType.text.toString().trim()
        val brand = binding.etBrand.text.toString().trim()
        val price = binding.etPrice.text.toString().toDouble()
        val discountPrice = binding.etDiscountPrice.text.toString().trim().takeIf { it.isNotEmpty() }?.toDouble()
        val stockQuantity = binding.etStockQuantity.text.toString().toInt()
        val sku = binding.etSku.text.toString().trim()
        val isActive = binding.switchActive.isChecked

        setLoadingState(true)

        scope.launch {
            try {
                Log.d("AddProduct", "Starting product creation for: $name by seller: $currentSellerName")

                // Step 1: Upload images to Supabase Storage
                val imageUrls = if (imageUris.isNotEmpty()) {
                    uploadImagesToStorage(generatedProductId)
                } else {
                    emptyList()
                }

                // Step 2: Prepare images JSON with the uploaded URLs
                val imagesJson = buildJsonArray {
                    imageUrls.forEach { url ->
                        add(url)
                    }
                }

                // Step 3: Create product with image URLs and seller information
                val newProduct = Product(
                    product_id = generatedProductId, // Use auto-generated ID
                    name = name,
                    description = description.takeIf { it.isNotEmpty() },
                    category = category,
                    type = type.takeIf { it.isNotEmpty() },
                    brand = brand.takeIf { it.isNotEmpty() },
                    price = price,
                    discount_price = discountPrice,
                    stock_quantity = stockQuantity,
                    sku = sku.takeIf { it.isNotEmpty() },
                    images = if (imageUrls.isNotEmpty()) imagesJson else null,
                    ratings = 0f,
                    reviews_count = 0,
                    is_active = isActive,
                    seller_id = currentSellerId, // Add seller ID
                    seller_name = currentSellerName // Add seller name
                )

                // Step 4: Insert product into database
                Log.d("AddProduct", "Inserting product into database with seller info")
                val result = supabase.postgrest["products"].insert(newProduct)

                Log.d("AddProduct", "Product added successfully: $result")

                runOnUiThread {
                    setLoadingState(false)
                    Toast.makeText(
                        this@AddProductActivity,
                        "Product added successfully!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Clear form and go back
                    clearForm()
                    finish() // Go back to previous activity
                }

            } catch (e: Exception) {
                Log.e("AddProduct", "Error adding product: ${e.message}", e)
                runOnUiThread {
                    setLoadingState(false)
                    handleAddProductError(e)
                }
            }
        }
    }

    private suspend fun uploadImagesToStorage(productId: String): List<String> {
        val uploadedUrls = mutableListOf<String>()

        for ((index, uri) in imageUris.withIndex()) {
            try {
                Log.d("AddProduct", "Uploading image ${index + 1}/${imageUris.size}")

                // Generate unique filename for each image
                val fileExtension = getFileExtensionFromUri(uri)
                val fileName = "${productId}_${UUID.randomUUID()}.$fileExtension"

                // Get file content from URI
                val inputStream = contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes()
                inputStream?.close()

                if (fileBytes != null) {
                    // Upload to Supabase Storage using ByteArray
                    val result = supabase.storage["product-images"].upload(
                        path = fileName,
                        data = fileBytes
                    )

                    // Get public URL for the uploaded image
                    val publicUrl = supabase.storage["product-images"].publicUrl(fileName)
                    uploadedUrls.add(publicUrl)

                    Log.d("AddProduct", "Image uploaded successfully: $publicUrl")
                } else {
                    throw Exception("Could not read image file")
                }

            } catch (e: Exception) {
                Log.e("AddProduct", "Error uploading image $index: ${e.message}", e)
                throw Exception("Failed to upload image ${index + 1}: ${e.message}")
            }
        }

        return uploadedUrls
    }

    private fun getFileExtensionFromUri(uri: Uri): String {
        return contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
    }

    private fun handleAddProductError(e: Exception) {
        val errorMessage = when {
            e.message?.contains("duplicate key", ignoreCase = true) == true -> {
                // Regenerate product ID if duplicate
                generateProductId()
                "Product ID conflict. New ID generated. Please try again."
            }
            e.message?.contains("network", ignoreCase = true) == true ->
                "Network error. Please check your internet connection."
            e.message?.contains("foreign key", ignoreCase = true) == true ->
                "Database constraint error. Please check your input data."
            e.message?.contains("RLS", ignoreCase = true) == true ->
                "Permission denied. Please check your database policies."
            e.message?.contains("storage", ignoreCase = true) == true ->
                "Storage error: ${e.message}"
            e.message?.contains("bucket", ignoreCase = true) == true ->
                "Storage bucket not found. Please create 'product-images' bucket in Supabase."
            else -> "Failed to add product: ${e.message ?: "Unknown error"}"
        }

        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun clearForm() {
        // Don't clear product ID field as it's auto-generated and read-only
        binding.etProductName.text?.clear()
        binding.etDescription.text?.clear()
        binding.autoCompleteCategory.text?.clear()
        binding.etType.text?.clear()
        binding.etBrand.text?.clear()
        binding.etPrice.text?.clear()
        binding.etDiscountPrice.text?.clear()
        binding.etStockQuantity.setText("0")
        binding.etSku.text?.clear()
        imageUris.clear()
        updateImagePreview()
        binding.switchActive.isChecked = true

        // Generate new product ID for next product
        generateProductId()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnSubmit.isEnabled = !isLoading
        binding.btnSubmit.text = if (isLoading) "Adding Product..." else "Add Product"
        binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnAddImages.isEnabled = !isLoading

        // Disable all input fields during loading (except product ID which is already disabled)
        binding.etProductName.isEnabled = !isLoading
        binding.etDescription.isEnabled = !isLoading
        binding.autoCompleteCategory.isEnabled = !isLoading
        binding.etType.isEnabled = !isLoading
        binding.etBrand.isEnabled = !isLoading
        binding.etPrice.isEnabled = !isLoading
        binding.etDiscountPrice.isEnabled = !isLoading
        binding.etStockQuantity.isEnabled = !isLoading
        binding.etSku.isEnabled = !isLoading
        binding.switchActive.isEnabled = !isLoading
    }
}