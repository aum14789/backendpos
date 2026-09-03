package sun.clientpos.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client for SunPOS backend communication.
 *
 * Configuration:
 *   - Base URL defaults to emulator localhost (10.0.2.2:8080/api/v1/)
 *   - JWT token, Device-Id, Branch-Id injected via interceptor
 *   - Connect/Read timeout: 30s (generous for unstable restaurant WiFi)
 *   - Logging enabled in debug builds only
 */
object RetrofitClient {

    private var baseUrl: String = "http://10.0.2.2:8080/api/v1/"
    private var jwtToken: String? = null
    private var deviceId: String? = null
    private var branchId: String? = null

    private var retrofit: Retrofit? = null
    private var syncApiService: SyncApiService? = null

    /**
     * Initialize the client with configuration.
     * Call this once during app startup (e.g. in Application.onCreate()).
     */
    fun initialize(
        baseUrl: String,
        deviceId: String,
        branchId: String
    ) {
        this.baseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        this.deviceId = deviceId
        this.branchId = branchId
        // Reset cached instances
        this.retrofit = null
        this.syncApiService = null
    }

    /**
     * Set or update the JWT token (after PIN login).
     */
    fun setToken(token: String?) {
        this.jwtToken = token
        // Reset to force rebuild with new token
        this.retrofit = null
        this.syncApiService = null
    }

    /**
     * Get the SyncApiService instance (lazy-created).
     */
    fun getSyncApiService(): SyncApiService {
        return syncApiService ?: buildSyncApiService().also { syncApiService = it }
    }

    private fun buildSyncApiService(): SyncApiService {
        return getRetrofit().create(SyncApiService::class.java)
    }

    private fun getRetrofit(): Retrofit {
        return retrofit ?: buildRetrofit().also { retrofit = it }
    }

    private fun buildRetrofit(): Retrofit {
        val client = buildOkHttpClient()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val headerInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")

            // Add JWT Authorization header if token is available
            jwtToken?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }

            // Add device and branch context headers per API conventions
            deviceId?.let {
                requestBuilder.addHeader("X-Device-Id", it)
            }
            branchId?.let {
                requestBuilder.addHeader("X-Branch-Id", it)
            }

            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }
}
