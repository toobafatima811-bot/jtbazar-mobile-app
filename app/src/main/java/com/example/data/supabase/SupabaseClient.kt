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

/**
 * Singleton Supabase HTTP Client.
 * Handles authenticated communication with Supabase PostgREST, Auth, and Storage.
 */
object SupabaseClient {
  private const val TAG = "SupabaseClient"
  private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

  @Volatile private var appContext: Context? = null

  fun initialize(context: Context) { appContext = context.applicationContext }
  fun preferences(name: String) = appContext?.getSharedPreferences(name, Context.MODE_PRIVATE)

  private val _session = MutableStateFlow<SupabaseSession?>(null)
  val session: StateFlow<SupabaseSession?> = _session.asStateFlow()

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

        chain.proceed(requestBuilder.build())
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

  suspend fun uploadPublicFile(bucket: String, path: String, bytes: ByteArray, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
      val url = "${SupabaseConfig.storageUrl}/object/$bucket/$encodedPath"
      val body = bytes.toRequestBody(mimeType.toMediaType())
      val request = Request.Builder().url(url).post(body).header("x-upsert", "true").build()
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
        .header("x-upsert", "false")
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
