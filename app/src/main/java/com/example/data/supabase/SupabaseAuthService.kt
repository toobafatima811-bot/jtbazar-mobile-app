package com.example.data.supabase

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import com.example.notifications.FirebasePushManager

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
  private const val KEY_GOOGLE_CODE_VERIFIER = "google_code_verifier"

  private val _googleOAuthStatus = MutableStateFlow<String?>(null)
  val googleOAuthStatus: StateFlow<String?> = _googleOAuthStatus.asStateFlow()

  private val _userProfile = MutableStateFlow<SupabaseProfile?>(null)
  val userProfile: StateFlow<SupabaseProfile?> = _userProfile.asStateFlow()

  fun triggerPushRegistration(context: Context?) {
    val ctx = context?.applicationContext ?: SupabaseClient.context ?: return
    CoroutineScope(Dispatchers.IO).launch {
      try {
        FirebasePushManager.register(ctx)
      } catch (e: Exception) {
        Log.w(TAG, "Push registration trigger skipped: ${e.message}")
      }
    }
  }

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
        triggerPushRegistration(context)
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

  /** Build a PKCE-protected Google OAuth URL that returns to the Android app. */
  fun createGoogleOAuthUrl(context: Context): Result<String> {
    return try {
      val verifierBytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
      val verifier = Base64.encodeToString(
        verifierBytes,
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
      )
      val challengeBytes = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII))
      val challenge = Base64.encodeToString(
        challengeBytes,
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
      )
      context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_GOOGLE_CODE_VERIFIER, verifier)
        .apply()

      _googleOAuthStatus.value = null
      val url = Uri.parse("${SupabaseConfig.authUrl}/authorize").buildUpon()
        .appendQueryParameter("provider", "google")
        .appendQueryParameter("redirect_to", "jtbazar://auth-callback")
        .appendQueryParameter("flow_type", "pkce")
        .appendQueryParameter("code_challenge", challenge)
        .appendQueryParameter("code_challenge_method", "s256")
        .build()
        .toString()
      Result.success(url)
    } catch (e: Exception) {
      Log.e(TAG, "Could not start Google OAuth", e)
      Result.failure(e)
    }
  }

  /** Exchange the one-time Supabase PKCE code and persist the authenticated app session. */
  suspend fun handleGoogleOAuthCallback(uri: Uri, context: Context): Result<SupabaseSession> =
    withContext(Dispatchers.IO) {
      try {
        val callbackError = uri.getQueryParameter("error_description")
          ?: uri.getQueryParameter("error")
        if (!callbackError.isNullOrBlank()) {
          throw IOException(callbackError)
        }
        val authCode = uri.getQueryParameter("code")
          ?: throw IOException("Google sign-in did not return an authorization code.")
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val verifier = prefs.getString(KEY_GOOGLE_CODE_VERIFIER, null)
          ?: throw IOException("Google sign-in expired. Please try again.")
        prefs.edit().remove(KEY_GOOGLE_CODE_VERIFIER).apply()

        val body = JSONObject()
          .put("auth_code", authCode)
          .put("code_verifier", verifier)
        val request = Request.Builder()
          .url("${SupabaseConfig.authUrl}/token?grant_type=pkce")
          .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
          .build()

        SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
          val responseBody = response.body?.string() ?: ""
          if (!response.isSuccessful) {
            throw IOException(parseErrorMessage(responseBody, "Google sign-in failed (${response.code})."))
          }
          val json = JSONObject(responseBody)
          val accessToken = json.optString("access_token", "")
          val refreshToken = json.optString("refresh_token", "")
          val userObj = json.optJSONObject("user")
            ?: throw IOException("Google sign-in response did not include a user.")
          if (accessToken.isBlank()) throw IOException("Google sign-in did not create a session.")

          val metadata = userObj.optJSONObject("user_metadata")
          val fullName = metadata?.optString("full_name")?.takeIf { it.isNotBlank() }
            ?: metadata?.optString("name")?.takeIf { it.isNotBlank() }
            ?: "JTBazar Customer"
          val email = userObj.optString("email", "")
          val user = SupabaseUser(
            id = userObj.optString("id"),
            email = email,
            phone = metadata?.optString("phone", "") ?: "",
            fullName = fullName
          )
          val session = SupabaseSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user
          )
          SupabaseClient.setSession(session)
          persistSession(context.applicationContext, session)

          val profile = fetchProfile(user.id).getOrNull() ?: SupabaseProfile(
            id = user.id,
            fullName = fullName,
            phone = user.phone ?: "",
            email = email
          )
          _userProfile.value = profile
          _googleOAuthStatus.value = "success"
          triggerPushRegistration(context)
          Result.success(session)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Google OAuth callback failed", e)
        _googleOAuthStatus.value = e.message ?: "Google sign-in failed. Please try again."
        Result.failure(e)
      }
    }

  fun clearGoogleOAuthStatus() {
    _googleOAuthStatus.value = null
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
          triggerPushRegistration(context)
        } else {
          // Account is pending email verification. Do not mark it as signed in,
          // persist an empty token, or create a customer profile before OTP.
          Log.d(TAG, "Signup created pending email verification for $userEmail")
          val identities = userObj.optJSONArray("identities")
          if (identities != null && identities.length() == 0) {
            // Supabase obfuscates repeated signups; request a fresh code for an existing unverified account.
            val resendBody = JSONObject().put("type", "signup").put("email", email.trim())
            val resendRequest = Request.Builder()
              .url("${SupabaseConfig.authUrl}/resend")
              .post(resendBody.toString().toRequestBody(JSON_MEDIA_TYPE))
              .build()
            SupabaseClient.okHttpClient.newCall(resendRequest).execute().use { resendResponse ->
              if (!resendResponse.isSuccessful) {
                val resendError = resendResponse.body?.string().orEmpty()
                Log.w(TAG, "Signup resend failed (${resendResponse.code}): ${parseErrorMessage(resendError, "Email verification code resend failed")}")
              }
            }
          }
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
        triggerPushRegistration(context)
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
        triggerPushRegistration(context)

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
        triggerPushRegistration(context)
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

      // Remove this device's push token before clearing the authenticated session.
      val ctx = context?.applicationContext ?: SupabaseClient.context
      if (ctx != null) FirebasePushManager.unregister(ctx, SupabaseClient.session.value?.accessToken)

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

  /** Update the password for the currently authenticated Supabase user. */
  suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      require(newPassword.length >= 6) { "Password must be at least 6 characters." }
      val body = JSONObject().put("password", newPassword).toString().toRequestBody(JSON_MEDIA_TYPE)
      val request = Request.Builder()
        .url("${SupabaseConfig.authUrl}/user")
        .patch(body)
        .build()
      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string().orEmpty()
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(IOException(parseErrorMessage(responseBody, "Password update failed")))
      }
    } catch (e: Exception) {
      Log.e(TAG, "updatePassword error", e)
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
    val res = SupabaseClient.get("profiles?id=eq.$userId&select=id,full_name,phone,avatar_url")
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
   * Upsert the signed-in customer's own profile. This also repairs legacy accounts
   * whose auth user exists but whose profiles row was never created.
   */
  suspend fun upsertProfile(profile: SupabaseProfile): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val user = SupabaseClient.currentUser.value
        ?: throw IOException("Please sign in again before updating your profile.")
      require(profile.id == user.id) { "You can only update your own profile." }
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
      // Update only the authenticated customer's existing row. Avoid an upsert
      // that can be rejected by projects whose RLS policy intentionally forbids INSERT.
      val result = SupabaseClient.patch(
        "profiles?id=eq.${user.id}",
        json.toString()
      )
      result.fold(
        onSuccess = { responseBody ->
          val rows = JSONArray(responseBody)
          if (rows.length() == 0) {
            Result.failure(IOException("The profile was not saved. Please retry."))
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
      Log.e(TAG, "upsertProfile error", e)
      Result.failure(e)
    }
  }

  private fun parseErrorMessage(jsonStr: String, fallback: String): String {
    val json = try { JSONObject(jsonStr) } catch (_: Exception) { JSONObject() }
    val rawCode = json.optString("error_code").ifBlank { json.optString("code") }.lowercase()
    val rawMessage = json.optString("error_description")
      .ifBlank { json.optString("msg") }
      .ifBlank { json.optString("message") }
      .ifBlank { json.optString("error") }
      .ifBlank { fallback }
    val message = rawMessage.lowercase()
    return when {
      rawCode.contains("invalid_credentials") || message.contains("invalid login credentials") ->
        "Email or password is incorrect. Please check and try again."
      rawCode.contains("email_not_confirmed") || message.contains("email not confirmed") ->
        "Please verify your email with the 6-digit code before signing in."
      rawCode.contains("user_already_exists") || message.contains("user already registered") || message.contains("already been registered") ->
        "An account with this email already exists. Please sign in or request a new verification code."
      rawCode.contains("weak_password") || message.contains("password should be") ->
        "Choose a stronger password with at least 6 characters."
      rawCode.contains("over_email_send_rate_limit") || message.contains("rate limit") || message.contains("too many requests") ->
        "Too many email requests. Please wait a few minutes before trying again."
      rawCode.contains("email_address_invalid") || message.contains("invalid email") ->
        "Please enter a valid email address."
      rawCode.contains("otp_expired") || message.contains("expired") ->
        "This verification code has expired. Request a new code and try again."
      rawCode.contains("otp_disabled") ->
        "Email verification is temporarily unavailable. Please contact support."
      rawCode.contains("provider_disabled") || message.contains("provider is not enabled") ->
        "This sign-in method is currently unavailable. Please try another method."
      rawCode.contains("identity_already_exists") || message.contains("identity already exists") ->
        "This Google account is already connected to another JTBazar account."
      rawCode.contains("bad_code_verifier") || message.contains("code verifier") ->
        "Google sign-in session expired. Please start Google sign-in again."
      message.contains("network is unreachable") || message.contains("unable to resolve host") || message.contains("failed to connect") ->
        "Cannot connect to JTBazar right now. Check your internet connection and try again."
      message.contains("timeout") ->
        "The request took too long. Please check your connection and try again."
      rawMessage == fallback && fallback.isBlank() -> "Something went wrong. Please try again."
      else -> rawMessage.take(220)
    }
  }

  private fun authFailureMessage(error: Throwable, fallback: String): String {
    val detail = error.message.orEmpty()
    return when {
      error is java.net.UnknownHostException || detail.contains("Unable to resolve host", true) ->
        "No internet connection. Please reconnect and try again."
      error is java.net.SocketTimeoutException || detail.contains("timeout", true) ->
        "Connection timed out. Please try again."
      error is java.io.InterruptedIOException ->
        "The request was interrupted. Please try again."
      detail.isNotBlank() -> detail.take(220)
      else -> fallback
    }
  }
}
