package com.solih.mcjay.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.solih.mcjay.databinding.ActivityEditProductBinding
import com.solih.mcjay.models.Product
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class EditProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProductBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    private val imageUris = mutableListOf<Uri>()
    private lateinit var imagePreviewAdapter: ImagePreviewAdapter
    private var existingImageUrls = mutableListOf<String>()
    private var productId: String = ""

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
                            if (imageUris.size < 5) {
                                imageUris.add(uri)
                            } else {
                                Toast.makeText(this, "Maximum 5 images allowed", Toast.LENGTH_SHORT).show()
                                break
                            }
                        }
                    }
                } else {
                    // Single image selected
                    intent.data?.let { uri ->
                        if (imageUris.size < 5) {
                            imageUris.add(uri)
                        } else {
                            Toast.makeText(this, "Maximum 5 images allowed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                updateImagePreview()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get product data from intent
        getProductDataFromIntent()
        setupCategoryDropdown()
        setupImagePreview()
        setupListeners()
        populateFormWithProductData()
    }

    private fun getProductDataFromIntent() {
        productId = intent.getStringExtra("product_id") ?: ""
        Log.d("EditProduct", "Editing product with ID: $productId")
    }

    private fun setupCategoryDropdown() {
        val adapter = ArrayAdapter(this, R.layout.dropdown_menu_item, categories)
        (binding.categoryInputLayout.editText as? MaterialAutoCompleteTextView)?.setAdapter(adapter)
    }

    private fun setupImagePreview() {
        imagePreviewAdapter = ImagePreviewAdapter(imageUris) { position ->
            // Remove image callback
            imageUris.removeAt(position)
            updateImagePreview()
        }

        binding.rvImagePreview.apply {
            layoutManager = GridLayoutManager(this@EditProductActivity, 3)
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
                updateProduct()
            }
        }
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
        if (imageUris.isNotEmpty() || existingImageUrls.isNotEmpty()) {
            binding.rvImagePreview.visibility = RecyclerView.VISIBLE
            imagePreviewAdapter.notifyDataSetChanged()
        } else {
            binding.rvImagePreview.visibility = RecyclerView.GONE
        }
    }

    private fun populateFormWithProductData() {
        binding.btnSubmit.text = "Update Product"
        binding.btnSubmit.setIconResource(R.drawable.ic_save)

        // Set values from intent extras
        binding.etProductName.setText(intent.getStringExtra("product_name") ?: "")
        binding.etDescription.setText(intent.getStringExtra("product_description") ?: "")
        binding.autoCompleteCategory.setText(intent.getStringExtra("product_category") ?: "")
        binding.etType.setText(intent.getStringExtra("product_type") ?: "")
        binding.etBrand.setText(intent.getStringExtra("product_brand") ?: "")

        val price = intent.getDoubleExtra("product_price", 0.0)
        binding.etPrice.setText(price.toString())

        val discountPrice = intent.getDoubleExtra("product_discount_price", 0.0)
        if (discountPrice > 0) {
            binding.etDiscountPrice.setText(discountPrice.toString())
        }

        val stockQuantity = intent.getIntExtra("product_stock_quantity", 0)
        binding.etStockQuantity.setText(stockQuantity.toString())

        binding.etSku.setText(intent.getStringExtra("product_sku") ?: "")

        val isActive = intent.getBooleanExtra("product_is_active", true)
        binding.switchActive.isChecked = isActive

        // Update UI title
//        val titleView = findViewById<TextView>(R.id.title)
//        titleView?.text = "Edit Product"

        // Update seller info
        binding.tvSellerInfo.text = "Editing product: ${intent.getStringExtra("product_name")}"

        // Load existing images if any
        loadExistingImages()
    }

    private fun loadExistingImages() {
        // In a real app, you would fetch the product with images from the database
        // For now, we'll use the data from intent or fetch from database
        scope.launch {
            try {
                val product = withContext(Dispatchers.IO) {
                    supabase.postgrest["products"]
                        .select {
                            filter { eq("product_id", productId) }
                        }
                        .decodeSingle<Product>()
                }

                // Extract image URLs from product
                product.images?.let { imagesJson ->
                    val urls = product.getImageUrls()
                    existingImageUrls.clear()
                    existingImageUrls.addAll(urls)
                    updateImagePreview()
                }

            } catch (e: Exception) {
                Log.e("EditProduct", "Error loading product images: ${e.message}", e)
            }
        }
    }

    private fun validateInputs(): Boolean {
        val name = binding.etProductName.text.toString().trim()
        val category = binding.autoCompleteCategory.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        val stockQuantity = binding.etStockQuantity.text.toString().trim()

        // Clear previous errors
        binding.nameInputLayout.error = null
        binding.categoryInputLayout.error = null
        binding.priceInputLayout.error = null
        binding.stockInputLayout.error = null

        var isValid = true

        // Name validation
        if (name.isEmpty()) {
            binding.nameInputLayout.error = "Product name is required"
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
                }
            } catch (e: NumberFormatException) {
                binding.stockInputLayout.error = "Invalid stock quantity"
                isValid = false
            }
        }

        // Discount price validation (if provided)
        val discountPrice = binding.etDiscountPrice.text.toString().trim()
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
                }
            } catch (e: NumberFormatException) {
                binding.discountPriceInputLayout.error = "Invalid discount price format"
                isValid = false
            }
        }

        return isValid
    }

    private fun updateProduct() {
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
                Log.d("EditProduct", "Updating product: $name")

                // Step 1: Upload new images to Supabase Storage if any
                val newImageUrls = if (imageUris.isNotEmpty()) {
                    uploadImagesToStorage(productId)
                } else {
                    emptyList()
                }

                // Step 2: Combine existing and new image URLs
                val allImageUrls = if (newImageUrls.isNotEmpty()) {
                    existingImageUrls + newImageUrls
                } else {
                    existingImageUrls
                }

                // Step 3: Prepare images JSON
                val imagesJson = if (allImageUrls.isNotEmpty()) {
                    buildJsonArray {
                        allImageUrls.forEach { url ->
                            add(url)
                        }
                    }
                } else {
                    null
                }

                // Step 4: Update product in database
                val updatedProduct = mapOf(
                    "name" to name,
                    "description" to description.takeIf { it.isNotEmpty() },
                    "category" to category,
                    "type" to type.takeIf { it.isNotEmpty() },
                    "brand" to brand.takeIf { it.isNotEmpty() },
                    "price" to price,
                    "discount_price" to discountPrice,
                    "stock_quantity" to stockQuantity,
                    "sku" to sku.takeIf { it.isNotEmpty() },
                    "images" to imagesJson,
                    "is_active" to isActive
                )

                // Step 5: Update product in database
                withContext(Dispatchers.IO) {
                    supabase.postgrest["products"]
                        .update(updatedProduct) {
                            filter { eq("product_id", productId) }
                        }
                }

                Log.d("EditProduct", "Product updated successfully")

                runOnUiThread {
                    setLoadingState(false)
                    Toast.makeText(
                        this@EditProductActivity,
                        "Product updated successfully!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Set result and finish
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                Log.e("EditProduct", "Error updating product: ${e.message}", e)
                runOnUiThread {
                    setLoadingState(false)
                    handleUpdateProductError(e)
                }
            }
        }
    }

    private suspend fun uploadImagesToStorage(productId: String): List<String> {
        val uploadedUrls = mutableListOf<String>()

        for ((index, uri) in imageUris.withIndex()) {
            try {
                Log.d("EditProduct", "Uploading image ${index + 1}/${imageUris.size}")

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

                    Log.d("EditProduct", "Image uploaded successfully: $publicUrl")
                } else {
                    throw Exception("Could not read image file")
                }

            } catch (e: Exception) {
                Log.e("EditProduct", "Error uploading image $index: ${e.message}", e)
                throw Exception("Failed to upload image ${index + 1}: ${e.message}")
            }
        }

        return uploadedUrls
    }

    private fun getFileExtensionFromUri(uri: Uri): String {
        return contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
    }

    private fun handleUpdateProductError(e: Exception) {
        val errorMessage = when {
            e.message?.contains("network", ignoreCase = true) == true ->
                "Network error. Please check your internet connection."
            e.message?.contains("foreign key", ignoreCase = true) == true ->
                "Database constraint error. Please check your input data."
            e.message?.contains("RLS", ignoreCase = true) == true ->
                "Permission denied. Please check your database policies."
            e.message?.contains("storage", ignoreCase = true) == true ->
                "Storage error: ${e.message}"
            else -> "Failed to update product: ${e.message ?: "Unknown error"}"
        }

        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnSubmit.isEnabled = !isLoading
        binding.btnSubmit.text = if (isLoading) "Updating Product..." else "Update Product"
        binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnAddImages.isEnabled = !isLoading

        // Disable all input fields during loading
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

    private fun editProduct(product: Product) {
        val intent = Intent(this, EditProductActivity::class.java).apply {
            putExtra("product_id", product.product_id)
            putExtra("product_name", product.name)
            putExtra("product_description", product.description ?: "")
            putExtra("product_category", product.category)
            putExtra("product_type", product.type ?: "")
            putExtra("product_brand", product.brand ?: "")
            putExtra("product_price", product.price)
            putExtra("product_discount_price", product.discount_price ?: 0.0)
            putExtra("product_stock_quantity", product.stock_quantity)
            putExtra("product_sku", product.sku ?: "")
            putExtra("product_is_active", product.is_active)
        }
        startActivity(intent)
    }
}