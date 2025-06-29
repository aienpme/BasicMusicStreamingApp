package com.bma.android.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object ApiClient {
    private var baseUrl: String = ""
    private var authToken: String? = null
    
    // Callback for authentication failures
    var onAuthFailure: (() -> Unit)? = null
    
    fun setServerUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
        resetClient()
    }
    
    fun getServerUrl(): String = baseUrl
    
    fun setAuthToken(token: String?) {
        authToken = token
    }
    
    fun getAuthToken(): String? = authToken
    
    fun getAuthHeader(): String? {
        return authToken?.let { "Bearer $it" }
    }
    
    fun isAuthenticated(): Boolean = authToken != null
    
    enum class ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        NO_CREDENTIALS,
        TOKEN_EXPIRED
    }
    
    suspend fun checkConnection(context: android.content.Context): ConnectionStatus {
        return try {
            // First check if we have credentials
            if (baseUrl.isEmpty() || authToken == null) {
                return ConnectionStatus.NO_CREDENTIALS
            }
            
            // Temporarily disable auth failure callback during connection check
            val originalCallback = onAuthFailure
            onAuthFailure = null
            
            try {
                // Test server connectivity first (without auth)
                try {
                    val response = api.checkHealth()
                    android.util.Log.d("ApiClient", "Server is reachable")
                } catch (e: Exception) {
                    android.util.Log.e("ApiClient", "Server unreachable", e)
                    return ConnectionStatus.DISCONNECTED
                }
                
                // Server is reachable, now check if token is valid
                if (isTokenExpired(context)) {
                    android.util.Log.d("ApiClient", "Token expired but server is up")
                    return ConnectionStatus.TOKEN_EXPIRED
                }
                
                // Token appears valid based on expiry time, verify with server
                // Only make this call if token is not expired to avoid unnecessary 401s
                try {
                    val songs = api.getSongs(getAuthHeader()!!)
                    ConnectionStatus.CONNECTED
                } catch (e: Exception) {
                    android.util.Log.e("ApiClient", "Auth check failed despite valid expiry time", e)
                    // If auth fails but server is up and token isn't expired by time,
                    // token might be invalidated on server side
                    ConnectionStatus.TOKEN_EXPIRED
                }
            } finally {
                // Restore the original callback
                onAuthFailure = originalCallback
            }
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "Connection check failed", e)
            ConnectionStatus.DISCONNECTED
        }
    }
    
    fun isTokenExpired(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("BMA", android.content.Context.MODE_PRIVATE)
        val expiresAt = prefs.getString("token_expires_at", null) ?: return true
        
        return try {
            // Try multiple date formats that might be used
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "MM/dd/yyyy HH:mm:ss"
            )
            
            var expiryTime = 0L
            for (format in formats) {
                try {
                    expiryTime = java.text.SimpleDateFormat(format, java.util.Locale.US).parse(expiresAt)?.time ?: 0
                    if (expiryTime > 0) break
                } catch (e: Exception) {
                    // Try next format
                }
            }
            
            if (expiryTime == 0L) {
                android.util.Log.e("ApiClient", "Could not parse expiration date: $expiresAt")
                // If we can't parse but have a token, assume it's valid for now
                // This handles cases where the date format is unexpected
                return authToken == null
            }
            
            val isExpired = System.currentTimeMillis() > expiryTime
            android.util.Log.d("ApiClient", "Token expiration check - Current: ${System.currentTimeMillis()}, Expires: $expiryTime, Expired: $isExpired")
            return isExpired
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "Error checking token expiration", e)
            return true // If we can't parse, assume expired
        }
    }
    
    private var retrofit: Retrofit? = null
    
    private fun resetClient() {
        retrofit = null
    }
    
    private fun getClient(): Retrofit {
        if (retrofit == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            val clientBuilder = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (response.code == 401 || response.code == 403) {
                        onAuthFailure?.invoke()
                    }
                    response
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
            
            // For HTTPS support, we need to handle both secure and insecure connections
            // In production, you'd want proper certificate validation
            if (baseUrl.startsWith("https://")) {
                try {
                    // Create a trust manager that accepts all certificates
                    // Note: This is for development/local use only
                    val trustAllCerts = arrayOf<TrustManager>(
                        object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        }
                    )
                    
                    val sslContext = SSLContext.getInstance("SSL")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                    
                    clientBuilder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    clientBuilder.hostnameVerifier { _, _ -> true }
                } catch (e: Exception) {
                    // Fall back to default SSL handling
                }
            }
            
            val client = clientBuilder.build()
            
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }
    
    val api: BmaApi by lazy {
        getClient().create(BmaApi::class.java)
    }
    
    // Complete state reset for clean disconnection/re-pairing
    fun clearAll() {
        baseUrl = ""
        authToken = null
        onAuthFailure = null
        resetClient() // Clears retrofit instance
        android.util.Log.d("ApiClient", "All state cleared completely")
    }
    
    // Additional methods for offline mode integration
    
    /**
     * Quick check if server is reachable (without authentication)
     */
    suspend fun isServerReachable(): Boolean {
        return try {
            if (baseUrl.isEmpty()) return false
            api.checkHealth()
            true
        } catch (e: Exception) {
            android.util.Log.d("ApiClient", "Server unreachable: ${e.message}")
            false
        }
    }
    
    /**
     * Check if we have valid credentials and server connection
     */
    suspend fun isFullyConnected(context: android.content.Context): Boolean {
        return checkConnection(context) == ConnectionStatus.CONNECTED
    }
} 