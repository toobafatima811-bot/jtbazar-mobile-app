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
    SupabaseClient.initialize(context)
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
          // Supabase only returns a session after email confirmation is complete
          // (or when email confirmation is disabled).
          SupabaseClient.setSession(session)
          persistSession(context, session)

          val profile = SupabaseProfile(
            id = userId,
            fullName = metaName,
            phone = metaPhone,
            email = userEmail
          )
          _userProfile.value = profile
          upsertProfile(profile)
        } else {
          // Account is pending email verification. Do not mark it as signed in,
          // persist an empty token, or create a customer profile before OTP.
          Log.d(TAG, "Signup created pending email verification for $userEmail")
        }

        Result.success(session)
      }
    } catch (e: Exception) {
      Log.e(TAG, "signUp error", e)
      Result.failure(e)
    }
  }

  /**
   * Verify the six-digit signup code using the same Supabase flow as the website.
   */
  suspend fun verifySignupOtp(
    email: String,
    token: String,
    context: Context? = null
  ): Result<SupabaseSession> = withContext(Dispatchers.IO) {
    try {
      val body = JSONObject().apply {
        put("email", email.trim())
        put("token", token.trim())
        put("type", "signup")
      }
      val request = Request.Builder()
        .url("${SupabaseConfig.authUrl}/verify")
        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()
      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
          return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Email verification failed")))
        }
        val json = JSONObject(responseBody)
        val accessToken = json.optString("access_token", "")
        val refreshToken = json.optString("refresh_token", "")
        val userObj = json.optJSONObject("user") ?: throw IOException("Verification response did not include a user.")
        if (accessToken.isBlank()) throw IOException("Email verification did not create a session.")
        val metadata = userObj.optJSONObject("user_metadata")
        val user = SupabaseUser(
          id = userObj.optString("id"),
          email = userObj.optString("email", email),
          phone = metadata?.optString("phone", "") ?: "",
          fullName = metadata?.optString("full_name", "JTBazar Customer") ?: "JTBazar Customer"
        )
        val session = SupabaseSession(accessToken = accessToken, refreshToken = refreshToken, user = user)
        SupabaseClient.setSession(session)
        persistSession(context, session)
        val profile = SupabaseProfile(
          id = user.id,
          fullName = user.fullName ?: "JTBazar Customer",
          phone = user.phone ?: "",
          email = user.email ?: ""
        )
        _userProfile.value = profile
        upsertProfile(profile)
        Result.success(session)
      }
    } catch (e: Exception) {
      Log.e(TAG, "verifySignupOtp error", e)
      Result.failure(e)
    }
  }

  /**
   * Resend the signup confirmation code, matching the website's resend behavior.
   */
  suspend fun resendSignupOtp(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val body = JSONObject().apply {
        put("type", "signup")
        put("email", email.trim())
      }
      val request = Request.Builder()
        .url("${SupabaseConfig.authUrl}/resend")
        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()
      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(IOException(parseErrorMessage(responseBody, "Could not resend verification code")))
      }
    } catch (e: Exception) {
      Log.e(TAG, "resendSignupOtp error", e)
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
   * Send a passwordless email OTP for an existing account, matching the website.
   * shouldCreateUser=false prevents accidentally creating accounts from the login screen.
   */
  suspend fun sendLoginOtp(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val body = JSONObject().put("email", email.trim()).put("create_user", false)
      val request = Request.Builder()
        .url("${SupabaseConfig.authUrl}/otp")
        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()
      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(IOException(parseErrorMessage(responseBody, "Could not send sign-in code")))
      }
    } catch (e: Exception) {
      Log.e(TAG, "sendLoginOtp error", e)
      Result.failure(e)
    }
  }

  /** Verify an existing customer's passwordless email OTP. */
  suspend fun verifyLoginOtp(email: String, token: String, context: Context? = null): Result<SupabaseSession> = withContext(Dispatchers.IO) {
    try {
      val body = JSONObject().put("email", email.trim()).put("token", token.trim()).put("type", "email")
      val request = Request.Builder()
        .url("${SupabaseConfig.authUrl}/verify")
        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()
      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Sign-in code verification failed")))
        val json = JSONObject(responseBody)
        val accessToken = json.optString("access_token", "")
        val refreshToken = json.optString("refresh_token", "")
        val userObj = json.optJSONObject("user") ?: throw IOException("Verification response did not include a user.")
        if (accessToken.isBlank()) throw IOException("Verification did not create a session.")
        val metadata = userObj.optJSONObject("user_metadata")
        val user = SupabaseUser(
          id = userObj.optString("id"),
          email = userObj.optString("email", email),
          phone = metadata?.optString("phone", "") ?: "",
          fullName = metadata?.optString("full_name", "JTBazar Customer") ?: "JTBazar Customer"
        )
        val session = SupabaseSession(accessToken = accessToken, refreshToken = refreshToken, user = user)
        SupabaseClient.setSession(session)
        persistSession(context, session)
        val profile = fetchProfile(user.id).getOrNull() ?: SupabaseProfile(
          id = user.id,
          fullName = user.fullName ?: "JTBazar Customer",
          phone = user.phone ?: "",
          email = user.email ?: ""
        )
        _userProfile.value = profile
        Result.success(session)
      }
    } catch (e: Exception) {
      Log.e(TAG, "verifyLoginOtp error", e)
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
    val res = SupabaseClient.get("profiles?id=eq.$userId&select=id,full_name,phone,avatar_url,email")
    res.mapCatching { jsonStr ->
      val arr = JSONArray(jsonStr)
      if (arr.length() == 0) throw IOException("Profile not found")
      val obj = arr.getJSONObject(0)
      fun value(key: String, fallback: String = ""): String {
        val raw = obj.opt(key)
        return if (raw == null || raw == JSONObject.NULL) fallback else raw.toString().takeUnless { it.equals("null", true) } ?: fallback
      }
      val current = SupabaseClient.currentUser.value
      val profile = SupabaseProfile(
        id = value("id", userId),
        fullName = value("full_name").ifBlank { value("name").ifBlank { current?.fullName?.takeIf { it.isNotBlank() } ?: "JTBazar Customer" } },
        phone = value("phone", current?.phone ?: ""),
        email = value("email", current?.email ?: ""),
        avatarUrl = value("avatar_url").ifBlank { null },
        city = value("city")
      )
      _userProfile.value = profile
      profile
    }
  }

  /**
   * Update the existing customer profile using the same columns and UPDATE flow as the website.
   * The profiles table is provisioned by the auth signup trigger; inserting an extra row here
   * can fail its RLS/required-column constraints.
   */
  suspend fun upsertProfile(profile: SupabaseProfile): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val name = profile.fullName.trim()
      require(name.isNotBlank() && name.length <= 120) { "Enter a name of up to 120 characters." }
      val rawPhone = profile.phone.trim()
      val normalizedPhone = when {
        rawPhone.isBlank() -> null
        rawPhone.matches(Regex("^03[0-9]{9}$")) -> "+92" + rawPhone.substring(1)
        rawPhone.matches(Regex("^\\+923[0-9]{9}$")) -> rawPhone
        else -> throw IllegalArgumentException("Enter exactly 03001234567 or +923001234567.")
      }
      val json = JSONObject().apply {
        put("full_name", name)
        if (normalizedPhone == null) put("phone", JSONObject.NULL) else put("phone", normalizedPhone)
        if (!profile.avatarUrl.isNullOrBlank()) put("avatar_url", profile.avatarUrl)
      }
      val result = SupabaseClient.patch("profiles?id=eq.${profile.id}", json.toString())
      result.fold(
        onSuccess = { responseBody ->
          val rows = JSONArray(responseBody)
          if (rows.length() == 0) {
            Result.failure(IOException("No profile row was updated. Please sign out and sign in again, then retry."))
          } else {
            val row = rows.getJSONObject(0)
            val avatar = row.optString("avatar_url").takeUnless { it.isBlank() || it == "null" }
            val saved = profile.copy(
              fullName = name,
              phone = normalizedPhone ?: "",
              avatarUrl = avatar ?: profile.avatarUrl
            )
            _userProfile.value = saved
            SupabaseClient.updateCurrentUserProfile(name, normalizedPhone ?: "")
            Result.success(Unit)
          }
        },
        onFailure = { error -> Result.failure(error) }
      )
    } catch (e: Exception) {
      Log.e(TAG, "updateProfile error", e)
      Result.failure(e)
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
