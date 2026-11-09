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
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object FirebasePushManager {
  private const val TAG = "FirebasePushManager"
  private val jsonType = "application/json; charset=utf-8".toMediaType()

  fun hasNotificationPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  suspend fun register(context: Context, tokenOverride: String? = null) = withContext(Dispatchers.IO) {
    val session = SupabaseClient.session.value ?: return@withContext
    if (!hasNotificationPermission(context)) return@withContext
    try {
      val token = tokenOverride ?: Tasks.await(FirebaseMessaging.getInstance().token)
      if (token.isBlank()) return@withContext
      val body = JSONObject().put("action", "register").put("platform", "android")
        .put("token", token).put("device_label", Build.MANUFACTURER + " " + Build.MODEL)
      val request = Request.Builder()
        .url(SupabaseConfig.projectUrl + "/functions/v1/firebase-push-notifications")
        .header("apikey", SupabaseConfig.publishableKey)
        .header("Authorization", "Bearer " + session.accessToken)
        .post(body.toString().toRequestBody(jsonType)).build()
      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) Log.w(TAG, "Token registration failed: HTTP ${response.code} ${response.body?.string()}")
        else Log.d(TAG, "FCM token registered for signed-in user")
      }
    } catch (error: Exception) { Log.e(TAG, "FCM token registration failed", error) }
  }

  suspend fun unregister(context: Context, accessToken: String?) = withContext(Dispatchers.IO) {
    if (accessToken.isNullOrBlank()) return@withContext
    try {
      val token = Tasks.await(FirebaseMessaging.getInstance().token)
      if (token.isNullOrBlank()) return@withContext
      val body = JSONObject().put("action", "unregister").put("token", token)
      val request = Request.Builder()
        .url(SupabaseConfig.projectUrl + "/functions/v1/firebase-push-notifications")
        .header("apikey", SupabaseConfig.publishableKey)
        .header("Authorization", "Bearer " + accessToken)
        .post(body.toString().toRequestBody(jsonType)).build()
      SupabaseClient.okHttpClient.newCall(request).execute().close()
    } catch (error: Exception) { Log.w(TAG, "FCM token unregister skipped", error) }
  }
}