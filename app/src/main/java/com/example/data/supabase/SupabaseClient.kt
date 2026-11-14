package com.example.data.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object GlobalLoadingState {
  private val activeRequests = AtomicInteger(0)
  private val _active = MutableStateFlow(false)
  val active: StateFlow<Boolean> = _active.asStateFlow()

  fun show() {
    activeRequests.incrementAndGet()
    _active.value = true
  }

  fun hide() {
    val remaining = activeRequests.decrementAndGet().coerceAtLeast(0)
    if (remaining == 0) _active.value = false
  }

  suspend fun <T> withLoading(block: suspend () -> T): T {
    show()
    return try {
      block()
    } finally {
      hide()
    }
  }

  suspend fun <T> withoutLoading(block: suspend () -> T): T {
    return block()
  }
}

/**
 * Singleton Supabase HTTP Client.
 * Handles authenticated communication with Supabase PostgREST, Auth, and Storage.
 */
object SupabaseClient {
  private const val TAG = "SupabaseClient"
  private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

  @Volatile private var appContext: Context? = null

  fun initialize(context: Context) { appContext = context.applicationContext }
  val context: Context? get() = appContext
  fun preferences(name: String) = appContext?.getSharedPreferences(name, Context.MODE_PRIVATE)

  private val _session = MutableStateFlow<SupabaseSession?>(null)
  val session: StateFlow<SupabaseSession?> = _session.asStateFlow()
  private val refreshLock = Any()

  /**
   * Refresh a Supabase access token after a protected request returns 401.
   * Uses an independent HTTP client so the refresh request cannot recursively
   * invoke this authenticator. Supabase rotates refresh tokens, so persist both.
   */
  private fun refreshSessionForRequest(failedResponse: Response): Request? {
    val previousCount = generateSequence(failedResponse) { it.priorResponse }.count()
    val failedRequest = failedResponse.request
    if (previousCount >= 2) return null

    synchronized(refreshLock) {
      val current = _session.value ?: return null
      val failedBearer = failedRequest.header("Authorization")?.removePrefix("Bearer ")
      // Another concurrent request may already have refreshed the shared session.
      if (!current.accessToken.isNullOrBlank() && current.accessToken != failedBearer) {
        return failedRequest.newBuilder()
          .header("Authorization", "Bearer ${current.accessToken}")
          .build()
      }

      val refreshToken = current.refreshToken?.takeIf { it.isNotBlank() } ?: return null
      return try {
        val body = JSONObject().put("refresh_token", refreshToken)
          .toString().toRequestBody(JSON_MEDIA_TYPE)
        val refreshRequest = Request.Builder()
          .url("${SupabaseConfig.authUrl}/token?grant_type=refresh_token")
          .post(body)
          .header("apikey", SupabaseConfig.publishableKey)
          .header("Content-Type", "application/json")
          .build()
        val refreshClient = OkHttpClient.Builder()
          .connectTimeout(20, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .build()

        refreshClient.newCall(refreshRequest).execute().use { refreshResponse ->
          val responseBody = refreshResponse.body?.string().orEmpty()
          if (!refreshResponse.isSuccessful) {
            Log.w(TAG, "Supabase session refresh failed: HTTP ${refreshResponse.code}")
            return null
          }
          val json = JSONObject(responseBody)
          val accessToken = json.optString("access_token", "")
          val rotatedRefreshToken = json.optString("refresh_token", refreshToken)
          if (accessToken.isBlank()) return null

          val updatedSession = current.copy(
            accessToken = accessToken,
            refreshToken = rotatedRefreshToken,
            expiresIn = json.optLong("expires_in", current.expiresIn)
          )
          setSession(updatedSession)

          appContext?.getSharedPreferences("jtbazar_auth_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.putString("access_token", accessToken)
            ?.putString("refresh_token", rotatedRefreshToken)
            ?.apply()

          Log.d(TAG, "Supabase session refreshed successfully")
          failedRequest.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
        }
      } catch (error: Exception) {
        Log.e(TAG, "Supabase session refresh request failed", error)
        null
      }
    }
  }

  private val _currentUser = MutableStateFlow<SupabaseUser?>(null)
  val currentUser: StateFlow<SupabaseUser?> = _currentUser.asStateFlow()

  val isAuthenticated: Boolean
    get() = _session.value != null

  val currentUserId: String?
    get() = _session.value?.user?.id

  val okHttpClient: OkHttpClient by lazy {
    val logging = HttpLoggingInterceptor { message ->
      // Keep logging concise and avoid logging sensitive tokens
      if (!message.contains("authorization", ignoreCase = true) && !message.contains("apikey", ignoreCase = true)) {
        Log.d(TAG, message)
      }
    }.apply {
      level = HttpLoggingInterceptor.Level.BASIC
    }

    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .addInterceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()

        // Always inject Supabase apikey header
        if (SupabaseConfig.publishableKey.isNotBlank()) {
          requestBuilder.header("apikey", SupabaseConfig.publishableKey)
        }

        // Attach Authorization header: user token if logged in, otherwise publishable key
        val currentToken = _session.value?.accessToken
        val authHeader = if (!currentToken.isNullOrBlank()) {
          "Bearer $currentToken"
        } else if (SupabaseConfig.publishableKey.isNotBlank()) {
          "Bearer ${SupabaseConfig.publishableKey}"
        } else {
          null
        }

        if (authHeader != null && original.header("Authorization") == null) {
          requestBuilder.header("Authorization", authHeader)
        }

        if (original.header("Content-Type") == null) {
          requestBuilder.header("Content-Type", "application/json")
        }

        if (original.header("Accept") == null) {
          requestBuilder.header("Accept", "application/json")
        }

        val request = requestBuilder.build()
        // Keep the app-wide progress state active during write/submit operations.
        // Background GET polling must not block the entire UI with a full-screen loader.
        val shouldShowOperationLoader = request.method !in setOf("GET", "HEAD")
        if (shouldShowOperationLoader) GlobalLoadingState.show()
        try {
          chain.proceed(request)
        } finally {
          if (shouldShowOperationLoader) GlobalLoadingState.hide()
        }
      }
      .authenticator { _, response ->
        val path = response.request.url.encodedPath
        // Never attempt to refresh an Auth endpoint's own failed request.
        if (path.contains("/auth/v1/token") || path.contains("/auth/v1/refresh")) {
          null
        } else {
          refreshSessionForRequest(response)
        }
      }
      .addInterceptor(logging)
      .build()
  }

  fun setSession(session: SupabaseSession?) {
    _session.value = session
    _currentUser.value = session?.user
  }

  fun clearSession() {
    _session.value = null
    _currentUser.value = null
  }

  fun updateCurrentUserProfile(fullName: String, phone: String) {
    val current = _currentUser.value ?: return
    val updatedUser = current.copy(fullName = fullName, phone = phone)
    _currentUser.value = updatedUser
    _session.value = _session.value?.copy(user = updatedUser)
  }

  /**
   * Health check to test connectivity to the configured Supabase endpoint.
   */
  suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${SupabaseConfig.restUrl}/")
        .get()
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        if (response.isSuccessful || response.code == 404 || response.code == 401 || response.code == 200) {
          Result.success(true)
        } else {
          Result.failure(IOException("Supabase returned HTTP ${response.code}: ${response.message}"))
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Connection check failed", e)
      Result.failure(e)
    }
  }

  /**
   * Raw GET request to PostgREST
   */
  suspend fun get(pathWithQuery: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val url = if (pathWithQuery.startsWith("http")) pathWithQuery else "${SupabaseConfig.restUrl}/$pathWithQuery"
      val request = Request.Builder()
        .url(url)
        .get()
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string() ?: ""
        if (response.isSuccessful) {
          Result.success(body)
        } else {
          Result.failure(IOException("PostgREST GET error ${response.code}: $body"))
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "GET $pathWithQuery failed", e)
      Result.failure(e)
    }
  }

  /**
   * Raw POST request to PostgREST
   */
  suspend fun post(path: String, jsonBody: String, preferReturn: Boolean = true, preferHeader: String? = null): Result<String> = withContext(Dispatchers.IO) {
    try {
      val url = if (path.startsWith("http")) path else "${SupabaseConfig.restUrl}/$path"
      val builder = Request.Builder()
        .url(url)
        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

      when {
        !preferHeader.isNullOrBlank() -> builder.header("Prefer", preferHeader)
        preferReturn -> builder.header("Prefer", "return=representation")
      }

      okHttpClient.newCall(builder.build()).execute().use { response ->
        val body = response.body?.string() ?: ""
        if (response.isSuccessful) {
          Result.success(body)
        } else {
          Result.failure(IOException("PostgREST POST error ${response.code}: $body"))
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "POST $path failed", e)
      Result.failure(e)
    }
  }

  /**
   * Raw PATCH request to PostgREST
   */
  suspend fun patch(pathWithQuery: String, jsonBody: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val url = if (pathWithQuery.startsWith("http")) pathWithQuery else "${SupabaseConfig.restUrl}/$pathWithQuery"
      val request = Request.Builder()
        .url(url)
        .patch(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
        .header("Prefer", "return=representation")
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string() ?: ""
        if (response.isSuccessful) {
          Result.success(body)
        } else {
          Result.failure(IOException("PostgREST PATCH error ${response.code}: $body"))
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "PATCH $pathWithQuery failed", e)
      Result.failure(e)
    }
  }

  /**
   * Raw DELETE request to PostgREST
   */
  suspend fun delete(pathWithQuery: String): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val url = if (pathWithQuery.startsWith("http")) pathWithQuery else "${SupabaseConfig.restUrl}/$pathWithQuery"
      val request = Request.Builder()
        .url(url)
        .delete()
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          Result.success(true)
        } else {
          val body = response.body?.string() ?: ""
          Result.failure(IOException("PostgREST DELETE error ${response.code}: $body"))
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "DELETE $pathWithQuery failed", e)
      Result.failure(e)
    }
  }

  suspend fun uploadPublicFile(
    bucket: String,
    path: String,
    bytes: ByteArray,
    mimeType: String,
    upsert: Boolean = true
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
      val url = "${SupabaseConfig.storageUrl}/object/$bucket/$encodedPath"
      val body = bytes.toRequestBody(mimeType.toMediaType())
      val requestBuilder = Request.Builder().url(url).post(body)
      // Product image paths include a fresh UUID. Avoid Storage upsert because vendor
      // policies intentionally grant INSERT but not UPDATE/SELECT on product-images.
      if (upsert) requestBuilder.header("x-upsert", "true")
      val request = requestBuilder.build()
      okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""
        if (response.isSuccessful) Result.success("${SupabaseConfig.storageUrl}/object/public/$bucket/$encodedPath")
        else Result.failure(IOException("Storage upload error ${response.code}: $responseBody"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Storage upload failed for $bucket/$path", e)
      Result.failure(e)
    }
  }
  suspend fun uploadPrivateFile(bucket: String, path: String, bytes: ByteArray, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
      val url = "${SupabaseConfig.storageUrl}/object/$bucket/$encodedPath"
      val request = Request.Builder().url(url)
        .post(bytes.toRequestBody(mimeType.toMediaType()))
        .header("x-upsert", "true")
        .build()
      okHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string() ?: ""
        if (response.isSuccessful) Result.success(path)
        else Result.failure(IOException("Private storage upload error ${response.code}: $body"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Private storage upload failed for $bucket/$path", e)
      Result.failure(e)
    }
  }

  /**
   * Execute RPC function in Supabase
   */
  suspend fun rpc(functionName: String, paramsJson: String = "{}"): Result<String> = withContext(Dispatchers.IO) {
    try {
      val url = "${SupabaseConfig.restUrl}/rpc/$functionName"
      val request = Request.Builder()
        .url(url)
        .post(paramsJson.toRequestBody(JSON_MEDIA_TYPE))
        .header("Prefer", "return=representation")
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string() ?: ""
        if (response.isSuccessful) {
          Result.success(body)
        } else {
          Result.failure(IOException("RPC $functionName error ${response.code}: $body"))
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "RPC $functionName failed", e)
      Result.failure(e)
    }
  }
}
