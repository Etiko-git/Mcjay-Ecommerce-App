package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.solih.mcjay.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private var delay = 3000L // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupListeners()
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(this)
        binding.viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.indicator, binding.viewPager) { tab, position -> }.attach()

        // Auto-scroll
        handler = Handler(Looper.getMainLooper())
        runnable = Runnable {
            val currentItem = binding.viewPager.currentItem
            val totalItems = adapter.itemCount
            binding.viewPager.currentItem = if (currentItem == totalItems - 1) 0 else currentItem + 1
        }
    }

    private fun setupListeners() {
        binding.createAccountButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.loginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.signInAsSellerButton.setOnClickListener {
            startActivity(Intent(this, SellerRegistrationActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(runnable, delay)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable)
    }
}