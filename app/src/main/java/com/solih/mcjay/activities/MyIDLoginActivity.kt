package com.solih.mcjay.activities

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.solih.mcjay.SharedPrefManager
import com.solih.mcjay.databinding.ActivityMyidLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MyIDLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyidLoginBinding
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentOrderId: String? = null

    // OkHttp client (single instance)
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    companion object {
        private const val LOCATION_REQUEST_CODE = 1
        const val API_BASE_URL = "https://proj-ei-d-backend.vercel.app/api"
        const val CALLBACK_URL = "mcjay://auth-success"
        const val CLIENT_APP = "Mcjay"
        const val POLLING_MAX_ATTEMPTS = 60
        const val POLLING_INITIAL_INTERVAL = 3000L
        const val POLLING_MAX_INTERVAL = 10000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyidLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST_CODE)
        }

        setupListeners()
        handleCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallback(intent)
    }

    private fun handleCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            Log.d("MyID Callback", "Received callback URI: $uri")
            if (uri.toString().startsWith("https://nigeriaims.unaux.com") || uri.toString().startsWith("mcjay://")) {
                val success = uri.getQueryParameter("success") == "true"
                val token = uri.getQueryParameter("token")
                val orderId = uri.getQueryParameter("orderId")

                Log.d("MyID", "Callback params - success: $success, token: $token, orderId: $orderId")

                if (success && token != null) {
                    Log.d("MyID", "Authentication successful, token: $token")
                    navigateToHome()
                } else {
                    Toast.makeText(this, "Authentication failed in MyID app", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    resetUI()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location permission denied. Using default location.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        binding.startMyIDButton.setOnClickListener {
            loginWithMyIDOnSameDevice()
        }

        binding.differentDeviceButton.setOnClickListener {
            generateQRCodeForAnotherDevice()
        }

        binding.supportLink.setOnClickListener {
            showTestQRCode()
        }

        // Add manual status check
        binding.manualCheckButton.setOnClickListener {
            currentOrderId?.let { orderId ->
                checkStatusManually(orderId)
            } ?: run {
                Toast.makeText(this, "No active authentication session", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkStatusManually(orderId: String) {
        scope.launch {
            showLoading(true)
            binding.statusText.text = "Manually checking status..."

            try {
                val status = checkAuthenticationStatus(orderId, 0)
                Log.d("MyID Manual", "Manual status check: $status")

                when (status) {
                    "Completed" -> {
                        // Get user name before navigating
                        val userName = getUserNameFromBackend(orderId)
                        navigateToHome(userName)
                    }
                    "Scanned" -> {
                        showPinVerificationUI()
                        binding.statusText.text = "Status: Scanned - Enter PIN in MyID App"
                    }
                    else -> {
                        binding.statusText.text = "Status: $status - Still waiting..."
                        Toast.makeText(this@MyIDLoginActivity, "Status: $status", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                binding.statusText.text = "Manual check failed"
                Toast.makeText(this@MyIDLoginActivity, "Check failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showTestQRCode() {
        binding.initialOptionsContainer.visibility = View.GONE
        binding.qrCodeContainer.visibility = View.VISIBLE
        binding.pinVerificationContainer.visibility = View.GONE
        binding.successContainer.visibility = View.GONE
        binding.statusText.visibility = View.VISIBLE

        scope.launch(Dispatchers.IO) {
            val testData = "myapp://test?app=Mcjay&timestamp=${System.currentTimeMillis()}"
            val testBitmap = createQRBitmap(testData, 300, 300)

            withContext(Dispatchers.Main) {
                if (testBitmap != null) {
                    binding.qrCodeImage.setImageBitmap(testBitmap)
                    binding.qrExpiryText.text = "Test QR Code"
                    binding.statusText.text = "This is a test QR code for support"
                } else {
                    binding.statusText.text = "Failed to generate test QR"
                }
            }
        }
    }

    private fun loginWithMyIDOnSameDevice() {
        showLoading(true)

        scope.launch {
            try {
                val authData = initiateAuthentication("authenticate")
                if (authData != null) {
                    val orderId = extractOrderId(authData)
                    if (orderId != null) {
                        currentOrderId = orderId
                        startPollingAuthenticationStatus(orderId)

                        val encodedCallback = Uri.encode(CALLBACK_URL)
                        val encodedClientApp = Uri.encode(CLIENT_APP)
                        val deepLinkToken = extractDeepLinkToken(authData)

                        val deepLinkUrl = "myapp://identify?callback_url=$encodedCallback&clientApp=$encodedClientApp&token=$deepLinkToken"
                        Log.d("MyID", "Deep Link URL: $deepLinkUrl")

                        openDeepLink(deepLinkUrl)
                        showPinVerificationUI()
                    } else {
                        throw Exception("OrderID not found in response")
                    }
                } else {
                    throw Exception("Auth initiation returned null")
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MyIDLoginActivity, "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MyID", "Authentication error: ${e.message}", e)
            }
        }
    }

    private fun generateQRCodeForAnotherDevice() {
        showLoading(true)

        scope.launch {
            try {
                val authData = initiateAuthentication("authenticate")
                if (authData != null) {
                    Log.d("MyID API", "Full API Response: ${authData.toString(2)}")

                    val orderId = extractOrderId(authData)
                    if (orderId != null) {
                        currentOrderId = orderId
                        startPollingAuthenticationStatus(orderId)
                        showQRCodeUI()

                        val qrToken = extractQrCodeToken(authData)
                        val qrSecret = extractQrCodeSecret(authData)

                        Log.d("MyID QR Setup", "OrderID: $orderId")
                        Log.d("MyID QR Setup", "QR Token: $qrToken")
                        Log.d("MyID QR Setup", "QR Secret: $qrSecret")

                        if (!qrToken.isNullOrEmpty() && !qrSecret.isNullOrEmpty()) {
                            generateMovingQRCode(qrToken, qrSecret)
                        } else {
                            withContext(Dispatchers.Main) {
                                binding.qrCodeImage.setImageBitmap(null)
                                binding.qrExpiryText.text = ""
                                binding.statusText.text = "QR code data missing from API response"
                                Toast.makeText(
                                    this@MyIDLoginActivity,
                                    "API did not return QR code data",
                                    Toast.LENGTH_LONG
                                ).show()
                                showLoading(false)
                            }
                        }
                    } else {
                        throw Exception("OrderID not found in response")
                    }
                } else {
                    throw Exception("Auth initiation returned null")
                }
            } catch (e: Exception) {
                showLoading(false)
                binding.statusText.text = "QR code generation failed"
                Toast.makeText(
                    this@MyIDLoginActivity,
                    "QR Code generation failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("MyID", "QR generation error: ${e.message}", e)
            }
        }
    }

    private fun extractOrderId(authData: JSONObject): String? {
        return try {
            when {
                authData.has("orderID") -> authData.getString("orderID")
                authData.has("orderId") -> authData.getString("orderId")
                authData.has("data") -> {
                    val data = authData.getJSONObject("data")
                    when {
                        data.has("orderID") -> data.getString("orderID")
                        data.has("orderId") -> data.getString("orderId")
                        else -> null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("MyID", "Error extracting orderId: ${e.message}")
            null
        }
    }

    private fun extractDeepLinkToken(authData: JSONObject): String {
        return try {
            when {
                authData.has("deepLinkToken") -> authData.getString("deepLinkToken")
                authData.has("data") -> authData.getJSONObject("data").getString("deepLinkToken")
                else -> "dl_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 12)}"
            }
        } catch (e: Exception) {
            Log.e("MyID", "Error extracting deepLinkToken: ${e.message}")
            "dl_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 12)}"
        }
    }

    private fun extractQrCodeToken(authData: JSONObject): String? {
        return try {
            when {
                authData.has("qrCodeToken") -> authData.getString("qrCodeToken")
                authData.has("qr_token") -> authData.getString("qr_token")
                authData.has("qrToken") -> authData.getString("qrToken")
                authData.has("data") -> {
                    val data = authData.getJSONObject("data")
                    when {
                        data.has("qrCodeToken") -> data.getString("qrCodeToken")
                        data.has("qr_token") -> data.getString("qr_token")
                        data.has("qrToken") -> data.getString("qrToken")
                        else -> null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("MyID", "Error extracting QR token: ${e.message}")
            null
        }
    }

    private fun extractQrCodeSecret(authData: JSONObject): String? {
        return try {
            when {
                authData.has("qrCodeSecret") -> authData.getString("qrCodeSecret")
                authData.has("qr_secret") -> authData.getString("qr_secret")
                authData.has("qrSecret") -> authData.getString("qrSecret")
                authData.has("data") -> {
                    val data = authData.getJSONObject("data")
                    when {
                        data.has("qrCodeSecret") -> data.getString("qrCodeSecret")
                        data.has("qr_secret") -> data.getString("qr_secret")
                        data.has("qrSecret") -> data.getString("qrSecret")
                        else -> null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("MyID", "Error extracting QR secret: ${e.message}")
            null
        }
    }

    private suspend fun initiateAuthentication(type: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val deviceInfo = getDeviceInfo()

                val requestBody = JSONObject().apply {
                    put("ipAddress", deviceInfo.getString("ipAddress"))
                    put("deviceInfo", deviceInfo.getJSONObject("deviceInfo"))
                    put("location", deviceInfo.getJSONObject("location"))
                    put("callBackUrl", CALLBACK_URL)
                    put("clientApp", CLIENT_APP)
                }

                Log.d("MyID", "Auth Request Body: ${requestBody.toString()}")

                val url = "$API_BASE_URL/$type"
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestBody.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    val code = resp.code
                    val respBody = resp.body?.string() ?: ""
                    Log.d("MyID", "Auth API Response code: $code body: $respBody")

                    if (code in 200..299 && respBody.isNotBlank()) {
                        try {
                            JSONObject(respBody)
                        } catch (je: Exception) {
                            Log.e("MyID", "Failed to parse JSON response: ${je.message}", je)
                            null
                        }
                    } else {
                        Log.e("MyID", "Auth API returned non-success: $code")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("MyID", "Network error in initiateAuthentication: ${e.message}", e)
                null
            }
        }
    }

    private suspend fun checkAuthenticationStatus(orderId: String, attempt: Int): String {
        return withContext(Dispatchers.IO) {
            try {
                val userInfo = getDeviceInfo()

                val requestBody = JSONObject().apply {
                    put("orderId", orderId)
                    put("deviceInfo", userInfo.getJSONObject("deviceInfo"))
                    put("ipAddress", userInfo.getString("ipAddress"))
                }

                Log.d("MyID Polling", "Status Request: ${requestBody.toString()}")

                val url = "$API_BASE_URL/auth/status"
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestBody.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    val code = resp.code
                    val bodyText = resp.body?.string() ?: ""
                    Log.d("MyID Polling", "Status API code: $code")
                    Log.d("MyID Polling", "Status API response: $bodyText")

                    if (code in 200..299 && bodyText.isNotBlank()) {
                        val json = JSONObject(bodyText)
                        val status = when {
                            json.has("data") -> {
                                val data = json.getJSONObject("data")
                                when {
                                    data.has("status") -> data.getString("status")
                                    data.has("authStatus") -> data.getString("authStatus")
                                    else -> null
                                }
                            }
                            json.has("status") -> json.getString("status")
                            else -> null
                        }

                        Log.d("MyID Polling", "Parsed status: $status")

                        when (status?.lowercase()) {
                            "scanned" -> "Scanned"
                            "completed", "success" -> "Completed"
                            "failed", "error" -> "Failed"
                            else -> "Pending"
                        }
                    } else {
                        Log.e("MyID Polling", "Status API non-success response: $code")
                        "Pending"
                    }
                }
            } catch (e: Exception) {
                Log.e("MyID Polling", "Network error while checking status: ${e.message}", e)
                "Pending"
            }
        }
    }

    private suspend fun getDeviceInfo(): JSONObject {
        return withContext(Dispatchers.IO) {
            val locationJson = JSONObject().apply {
                try {
                    if (ContextCompat.checkSelfPermission(this@MyIDLoginActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val location = fusedLocationClient.lastLocation.await()
                        put("latitude", location?.latitude ?: 0.0)
                        put("longitude", location?.longitude ?: 0.0)
                    } else {
                        put("latitude", 0.0)
                        put("longitude", 0.0)
                    }
                } catch (e: Exception) {
                    put("latitude", 0.0)
                    put("longitude", 0.0)
                }
            }

            JSONObject().apply {
                put("ipAddress", "192.168.1.1")
                put("deviceInfo", JSONObject().apply {
                    put("deviceModel", android.os.Build.MODEL)
                    put("deviceOS", "Android ${android.os.Build.VERSION.RELEASE}")
                    put("deviceId", getUniqueDeviceId())
                })
                put("location", locationJson)
            }
        }
    }

    private fun getUniqueDeviceId(): String {
        val sharedPref = getSharedPreferences("myid_prefs", MODE_PRIVATE)
        var deviceId = sharedPref.getString("device_uuid", null)

        if (deviceId == null) {
            deviceId = "android_${android.os.Build.MODEL}_${UUID.randomUUID().toString().substring(0, 8)}"
            sharedPref.edit().putString("device_uuid", deviceId).apply()
        }

        return deviceId
    }

    private fun startPollingAuthenticationStatus(orderId: String) {
        scope.launch {
            var attempts = 0
            var delayInterval = POLLING_INITIAL_INTERVAL
            var isScanned = false

            while (attempts < POLLING_MAX_ATTEMPTS) {
                try {
                    val status = checkAuthenticationStatus(orderId, attempts)
                    Log.d("MyID Polling", "Attempt $attempts: Status = '$status' for orderId $orderId")

                    when (status) {
                        "Scanned" -> {
                            if (!isScanned) {
                                Log.d("MyID", "QR Scanned - Showing PIN UI")
                                withContext(Dispatchers.Main) {
                                    showPinVerificationUI()
                                    binding.statusText.text = "QR Code Scanned - Enter PIN in MyID App"
                                }
                                isScanned = true
                            }
                            delayInterval = 2000L
                        }
                        "Completed" -> {
                            Log.d("MyID", "Authentication Completed - Getting user name")

                            // Get user name from backend
                            val userName = getUserNameFromBackend(orderId)

                            withContext(Dispatchers.Main) {
                                showSuccessUI()
                                binding.statusText.text = "Authentication Successful!"

                                launch {
                                    delay(1500)
                                    navigateToHome(userName)
                                }
                            }
                            return@launch
                        }
                        "Failed" -> {
                            Log.e("MyID", "Authentication Failed")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MyIDLoginActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                                showLoading(false)
                                binding.statusText.text = "Authentication Failed"
                                resetUI()
                            }
                            return@launch
                        }
                        else -> {
                            attempts++
                            withContext(Dispatchers.Main) {
                                val statusMessage = when {
                                    isScanned -> "Enter PIN in MyID app... Attempt ${attempts + 1}/$POLLING_MAX_ATTEMPTS"
                                    else -> "Waiting for authentication... Attempt ${attempts + 1}/$POLLING_MAX_ATTEMPTS"
                                }
                                binding.statusText.text = statusMessage
                            }
                            delayInterval = if (isScanned) {
                                2000L
                            } else {
                                (delayInterval * 1.25).toLong().coerceAtMost(POLLING_MAX_INTERVAL)
                            }
                        }
                    }

                    delay(delayInterval)

                } catch (e: Exception) {
                    Log.e("MyID Polling", "Polling error: ${e.message}", e)
                    attempts++
                    delayInterval = (delayInterval * 1.5).toLong().coerceAtMost(POLLING_MAX_INTERVAL)
                    delay(delayInterval)
                }
            }

            Log.w("MyID", "Polling timeout after $POLLING_MAX_ATTEMPTS attempts")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MyIDLoginActivity, "Authentication timeout", Toast.LENGTH_SHORT).show()
                showLoading(false)
                binding.statusText.text = "Authentication Timeout"
                resetUI()
            }
        }
    }

    private suspend fun getUserNameFromBackend(orderId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE_URL/user/details?orderId=${Uri.encode(orderId)}"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    val code = resp.code
                    val body = resp.body?.string() ?: ""
                    Log.d("MyID User", "User Name API Response code: $code")
                    Log.d("MyID User", "User Name API Response body: $body")

                    if (code in 200..299 && body.isNotBlank()) {
                        try {
                            val json = JSONObject(body)
                            extractUserNameFromJson(json)
                        } catch (je: Exception) {
                            Log.e("MyID User", "Failed to parse user name JSON: ${je.message}")
                            null
                        }
                    } else {
                        Log.e("MyID User", "User Name API returned non-success: $code")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("MyID User", "Network error fetching user name: ${e.message}")
                null
            }
        }
    }

    private fun extractUserNameFromJson(json: JSONObject): String? {
        return try {
            when {
                json.has("name") -> json.getString("name")
                json.has("userName") -> json.getString("userName")
                json.has("fullName") -> json.getString("fullName")
                json.has("data") -> {
                    val data = json.getJSONObject("data")
                    when {
                        data.has("name") -> data.getString("name")
                        data.has("userName") -> data.getString("userName")
                        data.has("fullName") -> data.getString("fullName")
                        else -> null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("MyID", "Error extracting user name from JSON: ${e.message}")
            null
        }
    }

    private fun resetUI() {
        binding.initialOptionsContainer.visibility = View.VISIBLE
        binding.qrCodeContainer.visibility = View.GONE
        binding.pinVerificationContainer.visibility = View.GONE
        binding.successContainer.visibility = View.GONE
        currentOrderId = null
    }

    private fun showLoading(show: Boolean) {
        binding.startMyIDButton.isEnabled = !show
        binding.differentDeviceButton.isEnabled = !show

        if (show) {
            binding.startMyIDButton.text = "Initializing..."
            binding.startMyIDButton.alpha = 0.7f
            binding.differentDeviceButton.text = "Initializing..."
            binding.differentDeviceButton.alpha = 0.7f
        } else {
            binding.startMyIDButton.text = "Start myID"
            binding.startMyIDButton.alpha = 1.0f
            binding.differentDeviceButton.text = "Use Different Device"
            binding.differentDeviceButton.alpha = 1.0f
        }
    }

    private fun showQRCodeUI() {
        binding.initialOptionsContainer.visibility = View.GONE
        binding.qrCodeContainer.visibility = View.VISIBLE
        binding.pinVerificationContainer.visibility = View.GONE
        binding.successContainer.visibility = View.GONE
        binding.statusText.visibility = View.VISIBLE
        startLoadingDotsAnimation()
    }

    private fun showPinVerificationUI() {
        binding.initialOptionsContainer.visibility = View.GONE
        binding.qrCodeContainer.visibility = View.GONE
        binding.pinVerificationContainer.visibility = View.VISIBLE
        binding.successContainer.visibility = View.GONE
        binding.statusText.visibility = View.VISIBLE
        startLoadingDotsAnimation()
    }

    private fun showSuccessUI() {
        binding.initialOptionsContainer.visibility = View.GONE
        binding.qrCodeContainer.visibility = View.GONE
        binding.pinVerificationContainer.visibility = View.GONE
        binding.successContainer.visibility = View.VISIBLE
        binding.statusText.visibility = View.VISIBLE
        animateProgressBar()
    }

    private fun startLoadingDotsAnimation() {
        val dots = listOf(
            binding.loadingDots.getChildAt(0),
            binding.loadingDots.getChildAt(1),
            binding.loadingDots.getChildAt(2)
        )

        dots.forEachIndexed { index, view ->
            view?.animate()
                ?.alpha(if (index == 0) 1.0f else 0.2f)
                ?.setDuration(500)
                ?.setStartDelay((index * 200).toLong())
                ?.start()
        }
    }

    private fun animateProgressBar() {
        val animator = ValueAnimator.ofInt(0, 100)
        animator.duration = 1500
        animator.addUpdateListener { animation ->
            binding.redirectProgress.progress = animation.animatedValue as Int
        }
        animator.start()
    }

    private fun openDeepLink(deepLinkUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUrl))
            startActivity(intent)
            Log.d("MyID", "Deep link opened successfully: $deepLinkUrl")
        } catch (e: Exception) {
            Log.e("MyID", "Failed to open deep link: ${e.message}", e)
            Toast.makeText(this, "Please install MyID app from Play Store", Toast.LENGTH_LONG).show()
            showLoading(false)
            resetUI()
        }
    }

    private fun generateMovingQRCode(qrToken: String, qrSecret: String) {
        scope.launch(Dispatchers.IO) {
            var running = true
            val startTime = System.currentTimeMillis()
            val expiryTime = 180000

            while (running && (System.currentTimeMillis() - startTime) < expiryTime) {
                try {
                    val timestamp = System.currentTimeMillis() / 1000
                    val message = timestamp.toString()
                    val hmac = generateHMAC(qrSecret, message)
                    val encodedClientApp = Uri.encode(CLIENT_APP)

                    val qrData = "myapp://identify.$qrToken.$timestamp.$hmac.$encodedClientApp"
                    Log.d("MyID QR", "QR Data: $qrData")

                    val qrBitmap = createQRBitmap(qrData, 300, 300)

                    withContext(Dispatchers.Main) {
                        if (qrBitmap != null) {
                            binding.qrCodeImage.setImageBitmap(qrBitmap)
                            val secondsLeft = (expiryTime - (System.currentTimeMillis() - startTime)) / 1000
                            binding.qrExpiryText.text = "Expires in: ${secondsLeft}s"
                            binding.statusText.text = "Scan this QR code with MyID app"
                        } else {
                            binding.statusText.text = "Failed to generate QR code"
                            running = false
                        }
                    }

                    delay(1000)

                } catch (e: Exception) {
                    Log.e("MyID", "QR generation error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        binding.statusText.text = "QR code generation failed"
                    }
                    running = false
                }
            }

            withContext(Dispatchers.Main) {
                if (System.currentTimeMillis() - startTime >= expiryTime) {
                    Toast.makeText(this@MyIDLoginActivity, "QR code expired", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    resetUI()
                }
            }
        }
    }

    private fun generateHMAC(secret: String, message: String): String {
        return try {
            val normalizedSecret = secret
                .replace("-", "+")
                .replace("_", "/")
                .padEnd(secret.length + (4 - secret.length % 4) % 4, '=')

            val secretBytes = android.util.Base64.decode(normalizedSecret, android.util.Base64.DEFAULT)
            val secretKeySpec = SecretKeySpec(secretBytes, "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKeySpec)

            val messageBytes = message.toByteArray(Charsets.UTF_8)
            val hmacBytes = mac.doFinal(messageBytes)
            val hexResult = hmacBytes.joinToString("") { "%02x".format(it) }

            Log.d("MyID HMAC", "HMAC Result: $hexResult")
            hexResult

        } catch (e: Exception) {
            Log.e("MyID HMAC", "HMAC generation failed: ${e.message}", e)
            "fallback_${System.currentTimeMillis()}"
        }
    }

    private fun createQRBitmap(text: String, width: Int, height: Int): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2
            )
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e("MyID", "QR bitmap creation failed: ${e.message}")
            null
        }
    }

    private fun navigateToHome(userName: String? = null) {
        Log.d("MyID Navigation", "Navigating to HomeActivity with user name: $userName")

        currentOrderId?.let { authToken ->
            SharedPrefManager.getInstance(this).apply {
                saveMyIDAuth(authToken)
                saveUserName(userName ?: "MyID User")
            }

            Log.d("MyID Navigation", "User name saved: ${userName ?: "MyID User"}")
        }

        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()

        Log.d("MyID Navigation", "HomeActivity started successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        currentOrderId = null
    }
}
