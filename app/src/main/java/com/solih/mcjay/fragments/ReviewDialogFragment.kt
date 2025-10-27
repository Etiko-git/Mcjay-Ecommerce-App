package com.solih.mcjay.fragments

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.solih.mcjay.databinding.DialogReviewBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviewDialogFragment : DialogFragment() {

    interface ReviewSubmitListener {
        fun onReviewSubmitted(rating: Int, reviewText: String, imageUri: Uri?)
    }

    private var reviewListener: ReviewSubmitListener? = null
    private lateinit var binding: DialogReviewBinding
    private var selectedImageUri: Uri? = null
    private var productId: String? = null
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    // Modern way to handle activity results
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            displaySelectedImage()
        }
    }

    fun setProductId(productId: String) {
        this.productId = productId
    }

    fun setReviewListener(listener: ReviewSubmitListener) {
        this.reviewListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRatingBar()
        setupImageUpload()
        setupButtons()
        loadExistingReview()
    }

    private fun setupRatingBar() {
        binding.reviewRatingBar.setOnRatingBarChangeListener { ratingBar, rating, fromUser ->
            if (fromUser) {
                Log.d("ReviewDialog", "Rating changed to: $rating")
            }
        }
    }

    private fun setupImageUpload() {
        binding.uploadImageButton.setOnClickListener {
            openImagePicker()
        }

        binding.removeImageButton.setOnClickListener {
            removeSelectedImage()
        }
    }

    private fun openImagePicker() {
        try {
            pickImageLauncher.launch("image/*")
        } catch (e: Exception) {
            Log.e("ReviewDialog", "Error opening image picker: ${e.message}")
            Toast.makeText(requireContext(), "Error opening image gallery", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeSelectedImage() {
        selectedImageUri = null
        binding.reviewImagePreview.visibility = View.GONE
        binding.removeImageButton.visibility = View.GONE
        binding.uploadImageButton.text = "Choose Image"
        binding.reviewImagePreview.setImageURI(null)
    }

    private fun displaySelectedImage() {
        selectedImageUri?.let { uri ->
            try {
                binding.reviewImagePreview.setImageURI(uri)
                binding.reviewImagePreview.visibility = View.VISIBLE
                binding.removeImageButton.visibility = View.VISIBLE
                binding.uploadImageButton.text = "Change Image"
            } catch (e: Exception) {
                Log.e("ReviewDialog", "Error displaying image: ${e.message}")
                Toast.makeText(requireContext(), "Error loading image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        binding.cancelReviewButton.setOnClickListener {
            dismiss()
        }

        binding.submitReviewButton.setOnClickListener {
            submitReview()
        }
    }

    private fun loadExistingReview() {
        scope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                val currentProductId = productId ?: return@launch

                Log.d("ReviewDialog", "Loading existing review for user: $userId, product: $currentProductId")

                val existingReview = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("reviews")
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("product_id", currentProductId) // This should match your database type
                            }
                        }
                        .decodeList<com.solih.mcjay.models.Review>()
                        .firstOrNull()
                }

                if (existingReview != null) {
                    Log.d("ReviewDialog", "Found existing review: ${existingReview.id}")
                    withContext(Dispatchers.Main) {
                        // Populate the form with existing review data
                        binding.reviewRatingBar.rating = existingReview.rating.toFloat()
                        binding.reviewEditText.setText(existingReview.review_text ?: "")
                        binding.submitReviewButton.text = "Update Review"

                        // Load existing image if available
                        existingReview.review_image_url?.let { imageUrl ->
                            if (imageUrl.isNotEmpty()) {
                                Glide.with(requireContext())
                                    .load(imageUrl)
                                    .placeholder(com.solih.mcjay.R.drawable.placeholder_image)
                                    .into(binding.reviewImagePreview)
                                binding.reviewImagePreview.visibility = View.VISIBLE
                                binding.removeImageButton.visibility = View.VISIBLE
                                binding.uploadImageButton.text = "Change Image"
                            }
                        }
                    }
                } else {
                    Log.d("ReviewDialog", "No existing review found")
                }

            } catch (e: Exception) {
                Log.e("ReviewDialog", "Error loading existing review: ${e.message}", e)
                // Don't show error to user - just proceed with empty form
            }
        }
    }

    private fun submitReview() {
        val rating = binding.reviewRatingBar.rating.toInt()
        val reviewText = binding.reviewEditText.text.toString().trim()

        if (rating == 0) {
            Toast.makeText(requireContext(), "Please select a rating", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("ReviewDialog", "Submitting review - Rating: $rating, Text: $reviewText, Has Image: ${selectedImageUri != null}")

        reviewListener?.onReviewSubmitted(rating, reviewText, selectedImageUri)
        dismiss()
    }

    companion object {
        fun newInstance(productId: String): ReviewDialogFragment {
            val fragment = ReviewDialogFragment()
            fragment.setProductId(productId)
            return fragment
        }
    }
}