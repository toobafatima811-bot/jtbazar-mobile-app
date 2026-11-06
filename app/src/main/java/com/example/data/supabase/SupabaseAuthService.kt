package com.example.data.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Service managing Supabase GoTrue authentication and user profile synchronization.
 */
object SupabaseAuthService {
  private const val TAG = "SupabaseAuthService"
  private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

  private const val PREFS_NAME = "jtbazar_auth_prefs"
  private const val KEY_ACCESS_TOKEN = "access_token"
  private const val KEY_REFRESH_TOKEN = "refresh_token"
  private const val KEY_USER_ID = "user_id"
  private const val KEY_USER_EMAIL = "user_email"
  private const val KEY_USER_NAME = "user_name"
  private const val KEY_USER_PHONE = "user_phone"

  private val _userProfile = MutableStateFlow<SupabaseProfile?>(null)
  val userProfile: StateFlow<SupabaseProfile?> = _userProfile.asStateFlow()

  /**
   * Initialize and restore persisted session if available.
   */
  fun init(context: Context) {
    try {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
      val userId = prefs.getString(KEY_USER_ID, null)
      val userEmail = prefs.getString(KEY_USER_EMAIL, null)
      val userName = prefs.getString(KEY_USER_NAME, "JTBazar Customer")
      val userPhone = prefs.getString(KEY_USER_PHONE, "")

      if (!accessToken.isNullOrBlank() && !userId.isNullOrBlank()) {
        val user = SupabaseUser(
          id = userId,
          email = userEmail,
          phone = userPhone,
          fullName = userName
        )
        val session = SupabaseSession(
          accessToken = accessToken,
          refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null),
          user = user
        )
        SupabaseClient.setSession(session)
        _userProfile.value = SupabaseProfile(
          id = userId,
          fullName = userName ?: "JTBazar Customer",
          phone = userPhone ?: "",
          email = userEmail ?: ""
        )
        Log.d(TAG, "Restored active session for user: $userId")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to restore persisted session", e)
    }
  }

  private fun persistSession(context: Context?, session: SupabaseSession?) {
    if (context == null) return
    try {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val editor = prefs.edit()
      if (session != null) {
        editor.putString(KEY_ACCESS_TOKEN, session.accessToken)
        editor.putString(KEY_REFRESH_TOKEN, session.refreshToken)
        editor.putString(KEY_USER_ID, session.user.id)
        editor.putString(KEY_USER_EMAIL, session.user.email)
        editor.putString(KEY_USER_NAME, session.user.fullName)
        editor.putString(KEY_USER_PHONE, session.user.phone)
      } else {
        editor.clear()
      }
      editor.apply()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to persist session to preferences", e)
    }
  }

  /**
   * Register a new user with Supabase GoTrue Auth
   */
  suspend fun signUp(
    email: String,
    password: String,
    fullName: String,
    phone: String,
    context: Context? = null
  ): Result<SupabaseSession> = withContext(Dispatchers.IO) {
    try {
      val bodyJson = JSONObject().apply {
        put("email", email.trim())
        put("password", password)
        val dataObj = JSONObject().apply {
          put("full_name", fullName.trim())
          put("phone", phone.trim())
        }
        put("data", dataObj)
      }

      val request = Request.Builder()
        .url("${SupabaseConfig.authUrl}/signup")
        .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
          val errorMsg = parseErrorMessage(responseBody, "Registration failed (${response.code})")
          return@withContext Result.failure(IOException(errorMsg))
        }

        val json = JSONObject(responseBody)
        val sessionObj = json.optJSONObject("session")
        val userObj = json.optJSONObject("user") ?: json

        val userId = userObj.optString("id", "")
        val userEmail = userObj.optString("email", email)
        val userMetadata = userObj.optJSONObject("user_metadata")
        val metaName = userMetadata?.optString("full_name", fullName) ?: fullName
        val metaPhone = userMetadata?.optString("phone", phone) ?: phone

        val token = sessionObj?.optString("access_token", "") ?: ""
        val refreshToken = if (sessionObj != null && sessionObj.has("refresh_token")) sessionObj.optString("refresh_token") else null

        val user = SupabaseUser(
          id = userId,
          email = userEmail,
          phone = metaPhone,
          fullName = metaName
        )
        val session = SupabaseSession(
          accessToken = token,
          refreshToken = refreshToken,
          user = user
        )

        if (token.isNotBlank()) {
          SupabaseClient.setSession(session)
          persistSession(context, session)
        }

        val profile = SupabaseProfile(
          id = userId,
          fullName = metaName,
          phone = metaPhone,
          email = userEmail
        )
        _userProfile.value = profile

        // Also ensure profile record in profiles table
        upsertProfile(profile)

        Result.success(session)
      }
    } catch (e: Exception) {
      Log.e(TAG, "signUp error", e)
      Result.failure(e)
    }
  }

  /**
   * Sign in existing customer with email & password
   */
  suspend fun signIn(
    email: String,
    password: String,
    context: Context? = null
  ): Result<SupabaseSession> = withContext(Dispatchers.IO) {
    try {
      val bodyJson = JSONObject().apply {
        put("email", email.trim())
        put("password", password)
      }

      val request = Request.Builder()
        .url("${SupabaseConfig.authUrl}/token?grant_type=password")
        .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
          val errorMsg = parseErrorMessage(responseBody, "Invalid email or password")
          return@withContext Result.failure(IOException(errorMsg))
        }

        val json = JSONObject(responseBody)
        val accessToken = json.getString("access_token")
        val refreshToken = if (json.has("refresh_token")) json.optString("refresh_token") else null
        val userObj = json.getJSONObject("user")

        val userId = userObj.getString("id")
        val userEmail = userObj.optString("email", email)
        val metadata = userObj.optJSONObject("user_metadata")
        val fullName = metadata?.optString("full_name", "JTBazar Customer") ?: "JTBazar Customer"
        val phone = metadata?.optString("phone", "") ?: ""

        val user = SupabaseUser(
          id = userId,
          email = userEmail,
          phone = phone,
          fullName = fullName
        )
        val session = SupabaseSession(
          accessToken = accessToken,
          refreshToken = refreshToken,
          user = user
        )

        SupabaseClient.setSession(session)
        persistSession(context, session)

        // Fetch or create profile
        val profile = fetchProfile(userId).getOrNull() ?: SupabaseProfile(
          id = userId,
          fullName = fullName,
          phone = phone,
          email = userEmail
        )
        _userProfile.value = profile

        Result.success(session)
      }
    } catch (e: Exception) {
      Log.e(TAG, "signIn error", e)
      Result.failure(e)
    }
  }

  /**
   * Sign out current customer
   */
  suspend fun signOut(context: Context? = null): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${SupabaseConfig.authUrl}/logout")
        .post("{}".toRequestBody(JSON_MEDIA_TYPE))
        .build()

      // We attempt the backend call, but always clear local session
      try {
        SupabaseClient.okHttpClient.newCall(request).execute().close()
      } catch (_: Exception) {}

      SupabaseClient.clearSession()
      _userProfile.value = null
      persistSession(context, null)

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "signOut error", e)
      Result.failure(e)
    }
  }

  /**
   * Request password reset email
   */
  suspend fun resetPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val bodyJson = JSONObject().apply {
        put("email", email.trim())
      }

      val request = Request.Builder()
        .url("${SupabaseConfig.authUrl}/recover")
        .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          Result.success(Unit)
        } else {
          val body = response.body?.string() ?: ""
          Result.failure(IOException(parseErrorMessage(body, "Password reset request failed")))
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "resetPassword error", e)
      Result.failure(e)
    }
  }

  /**
   * Fetch customer profile from profiles table
   */
  suspend fun fetchProfile(userId: String): Result<SupabaseProfile> = withContext(Dispatchers.IO) {
    val res = SupabaseClient.get("profiles?id=eq.$userId&select=*")
    res.mapCatching { jsonStr ->
      val arr = JSONArray(jsonStr)
      if (arr.length() == 0) throw IOException("Profile not found")
      val obj = arr.getJSONObject(0)
      val profile = SupabaseProfile(
        id = obj.getString("id"),
        fullName = obj.optString("full_name", obj.optString("name", "Customer")),
        phone = obj.optString("phone", ""),
        email = obj.optString("email", ""),
        avatarUrl = if (obj.has("avatar_url")) obj.optString("avatar_url") else null,
        city = obj.optString("city", "Lahore")
      )
      _userProfile.value = profile
      profile
    }
  }

  /**
   * Upsert customer profile
   */
  suspend fun upsertProfile(profile: SupabaseProfile): Result<Unit> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("id", profile.id)
      put("full_name", profile.fullName)
      put("phone", profile.phone)
      put("email", profile.email)
      if (!profile.avatarUrl.isNullOrBlank()) put("avatar_url", profile.avatarUrl)
      put("city", profile.city)
    }
    val res = SupabaseClient.post("profiles", json.toString(), preferReturn = false)
    if (res.isSuccess) {
      _userProfile.value = profile
      Result.success(Unit)
    } else {
      // Try patch if duplicate key
      val patchRes = SupabaseClient.patch("profiles?id=eq.${profile.id}", json.toString())
      if (patchRes.isSuccess) {
        _userProfile.value = profile
        Result.success(Unit)
      } else {
        Result.failure(res.exceptionOrNull() ?: IOException("Failed to save profile"))
      }
    }
  }

  private fun parseErrorMessage(jsonStr: String, fallback: String): String {
    return try {
      val json = JSONObject(jsonStr)
      json.optString("error_description",
        json.optString("msg",
          json.optString("message", fallback)
        )
      )
    } catch (_: Exception) {
      fallback
    }
  }
}
