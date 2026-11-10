package com.example.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FirebasePushManager {
  private const val TAG = "FirebasePushManager"
  private val jsonType = "application/json; charset=utf-8".toMediaType()

  fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(android.app.NotificationManager::class.java)
      manager.createNotificationChannel(
        android.app.NotificationChannel(
          "jtbazar_updates",
          "JTBazar updates",
          android.app.NotificationManager.IMPORTANCE_DEFAULT
        )
      )
    }
  }

  fun hasNotificationPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  /**
   * Ask Firebase to create/return this installation's FCM token as soon as the app starts.
   * This deliberately does not depend on notification-display permission or a signed-in user.
   * The token is only uploaded to Supabase later, once an authenticated session is available.
   */
  suspend fun prepareToken(context: Context): String? = withContext(Dispatchers.IO) {
    try {
      if (FirebaseApp.getApps(context).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
        Log.e(TAG, "Cannot create FCM token: default Firebase app is not initialized")
        return@withContext null
      }
      FirebaseMessaging.getInstance().isAutoInitEnabled = true
      val token = Tasks.await(FirebaseMessaging.getInstance().token, 30, TimeUnit.SECONDS)
      if (token.isBlank()) {
        Log.w(TAG, "Firebase returned an empty FCM token")
        null
      } else {
        Log.i(TAG, "FCM installation token ready (length=" + token.length + ")")
        token
      }
    } catch (error: Exception) {
      Log.e(TAG, "FCM token creation failed: " + error.javaClass.simpleName + ": " + error.message, error)
      null
    }
  }

  // Create/refresh the FCM token first, then register it when an authenticated session exists.
  suspend fun register(context: Context, tokenOverride: String? = null) = withContext(Dispatchers.IO) {
    Log.d(TAG, "Starting Android push-token registration")
    val token = tokenOverride ?: prepareToken(context) ?: run {
      Log.w(TAG, "Registration deferred: FCM token is not available yet")
      return@withContext
    }

    val session = SupabaseClient.session.value ?: run {
      Log.d(TAG, "FCM token is ready; Supabase registration deferred until sign-in")
      return@withContext
    }
    if (session.accessToken.isBlank()) {
      Log.w(TAG, "Registration deferred: signed-in session has an empty access token")
      return@withContext
    }

    try {
      Log.d(TAG, "Firebase initialized for project " + (FirebaseApp.getInstance().options.projectId ?: "unknown"))
      Log.d(TAG, "Sending authenticated Android FCM registration request (token length=" + token.length + ")")
      val body = JSONObject()
        .put("action", "register")
        .put("platform", "android")
        .put("token", token)
        .put("device_label", Build.MANUFACTURER + " " + Build.MODEL)

      val request = Request.Builder()
        .url(SupabaseConfig.projectUrl + "/functions/v1/firebase-push-notifications")
        .header("apikey", SupabaseConfig.publishableKey)
        .header("Authorization", "Bearer " + session.accessToken)
        .post(body.toString().toRequestBody(jsonType))
        .build()

      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string().orEmpty()
        if (response.isSuccessful) {
          Log.i(TAG, "Android FCM token registered successfully (HTTP " + response.code + ")")
        } else {
          Log.e(TAG, "Android FCM registration rejected (HTTP " + response.code + "): " + responseBody.take(500))
        }
      }
    } catch (error: Exception) {
      Log.e(TAG, "Android FCM registration failed: " + error.javaClass.simpleName + ": " + error.message, error)
    }
  }

  suspend fun unregister(context: Context, accessToken: String?) = withContext(Dispatchers.IO) {
    if (accessToken.isNullOrBlank()) return@withContext
    try {
      val token = Tasks.await(FirebaseMessaging.getInstance().token, 30, TimeUnit.SECONDS)
      if (token.isBlank()) return@withContext
      val body = JSONObject().put("action", "unregister").put("token", token)
      val request = Request.Builder()
        .url(SupabaseConfig.projectUrl + "/functions/v1/firebase-push-notifications")
        .header("apikey", SupabaseConfig.publishableKey)
        .header("Authorization", "Bearer " + accessToken)
        .post(body.toString().toRequestBody(jsonType))
        .build()
      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        Log.d(TAG, "FCM token unregister response: HTTP " + response.code)
      }
    } catch (error: Exception) {
      Log.w(TAG, "FCM token unregister skipped: " + error.javaClass.simpleName + ": " + error.message)
    }
  }
}
