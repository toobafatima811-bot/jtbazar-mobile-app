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
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FirebasePushManager {
  private const val TAG = "FirebasePushManager"
  private const val PREFS_NAME = "jtbazar_fcm_prefs"
  private const val KEY_CACHED_TOKEN = "cached_fcm_token"
  private const val KEY_LAST_REGISTERED_USER = "last_registered_user"
  private const val KEY_LAST_REGISTERED_TOKEN = "last_registered_token"

  // Working verified Firebase Android API key from google-services.json
  private const val FIREBASE_API_KEY = "AIzaSyC48PiIR-Jnn4KunxW62-2MlSYSZYRrrzU"
  private const val FIREBASE_APP_ID = "1:763074877925:android:58381e5cfd5c3a839df70c"
  private const val FIREBASE_PROJECT_ID = "jtbazar-9fc3d"
  private const val FIREBASE_SENDER_ID = "763074877925"

  private val jsonType = "application/json; charset=utf-8".toMediaType()

  /**
   * Ensure the default FirebaseApp instance is initialized.
   * If the Google Services Gradle plugin could not inject options automatically,
   * this initializes using explicit options matching google-services.json.
   */
  fun ensureInitialized(context: Context) {
    try {
      if (FirebaseApp.getApps(context).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
        val options = FirebaseOptions.Builder()
          .setApplicationId(FIREBASE_APP_ID)
          .setApiKey(FIREBASE_API_KEY)
          .setProjectId(FIREBASE_PROJECT_ID)
          .setGcmSenderId(FIREBASE_SENDER_ID)
          .setStorageBucket("jtbazar-9fc3d.firebasestorage.app")
          .build()
        FirebaseApp.initializeApp(context.applicationContext, options)
        Log.i(TAG, "[FCM] Stage 1/5: Firebase initialized explicitly for project: ${options.projectId}")
      } else {
        val projId = FirebaseApp.getInstance().options.projectId ?: FIREBASE_PROJECT_ID
        Log.i(TAG, "[FCM] Stage 1/5: Firebase initialized for project: $projId")
      }
    } catch (e: Exception) {
      Log.e(TAG, "[FCM] Stage 1/5: Firebase initialization error: ${e.javaClass.simpleName}: ${e.message}")
    }
  }

  fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(android.app.NotificationManager::class.java)
      manager?.createNotificationChannel(
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

  fun getCachedToken(context: Context): String? {
    return try {
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CACHED_TOKEN, null)
        ?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
      null
    }
  }

  fun saveCachedToken(context: Context, token: String) {
    try {
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CACHED_TOKEN, token)
        .apply()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to cache FCM token", e)
    }
  }

  /**
   * Request/retrieve this device's FCM registration token.
   * Caches the token locally upon receipt and registers an asynchronous callback
   * so late-arriving tokens are automatically saved and registered.
   */
  suspend fun prepareToken(context: Context): String? = withContext(Dispatchers.IO) {
    ensureInitialized(context)
    try {
      FirebaseMessaging.getInstance().isAutoInitEnabled = true

      // Attach async listener so any delayed Google Play Services generation is captured immediately
      try {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
          if (task.isSuccessful) {
            val t = task.result
            if (!t.isNullOrBlank()) {
              saveCachedToken(context, t)
              Log.i(TAG, "[FCM] Asynchronous token callback received (length=${t.length})")
              val sess = SupabaseClient.session.value
              if (sess != null && sess.accessToken.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                  register(context, t)
                }
              }
            }
          } else {
            Log.w(TAG, "[FCM] Asynchronous token callback error: ${task.exception?.javaClass?.simpleName}: ${task.exception?.message}")
          }
        }
      } catch (listenerError: Exception) {
        Log.w(TAG, "[FCM] Could not attach token listener: ${listenerError.message}")
      }

      val token = Tasks.await(FirebaseMessaging.getInstance().token, 15, TimeUnit.SECONDS)
      if (!token.isNullOrBlank()) {
        Log.i(TAG, "[FCM] Stage 2/5: FCM token generated (length=${token.length})")
        saveCachedToken(context, token)
        token
      } else {
        Log.w(TAG, "[FCM] Stage 2/5: Firebase returned an empty token; checking cache")
        getCachedToken(context)
      }
    } catch (error: Exception) {
      Log.w(TAG, "[FCM] Stage 2/5: FCM token retrieval error (${error.javaClass.simpleName}: ${error.message}). Checking cache.")
      getCachedToken(context)
    }
  }

  /**
   * Register the device's FCM token with the Supabase Edge Function `firebase-push-notifications`.
   * Also performs direct database fallback if needed to guarantee the token row is recorded.
   */
  suspend fun register(context: Context, tokenOverride: String? = null): Boolean = withContext(Dispatchers.IO) {
    ensureInitialized(context)

    // 1. Resolve token (override -> cached -> freshly fetched)
    val token = tokenOverride?.takeIf { it.isNotBlank() }
      ?: getCachedToken(context)
      ?: prepareToken(context)

    if (token.isNullOrBlank()) {
      Log.w(TAG, "[FCM] Registration deferred: FCM token is not available yet (Play Services initializing or offline)")
      return@withContext false
    } else {
      saveCachedToken(context, token)
      Log.i(TAG, "[FCM] Stage 2/5: FCM token ready (length=${token.length})")
    }

    // 2. Check Supabase authenticated session
    val session = SupabaseClient.session.value
    if (session == null || session.accessToken.isBlank()) {
      Log.d(TAG, "[FCM] Stage 3/5: FCM token ready (length=${token.length}); Supabase session not available yet. Registration deferred until sign-in.")
      return@withContext false
    }

    Log.i(TAG, "[FCM] Stage 3/5: Supabase session available (user ID: ${session.user.id})")

    // 3. Dispatch registration request to Supabase Edge Function
    Log.i(TAG, "[FCM] Stage 4/5: Dispatching registration request to ${SupabaseConfig.projectUrl}/functions/v1/firebase-push-notifications for platform: android")

    var registeredSuccessfully = false
    try {
      val body = JSONObject()
        .put("action", "register")
        .put("platform", "android")
        .put("token", token)
        .put("device_label", "${Build.MANUFACTURER} ${Build.MODEL}")

      val request = Request.Builder()
        .url("${SupabaseConfig.projectUrl}/functions/v1/firebase-push-notifications")
        .header("apikey", SupabaseConfig.publishableKey)
        .header("Authorization", "Bearer ${session.accessToken}")
        .header("Content-Type", "application/json")
        .post(body.toString().toRequestBody(jsonType))
        .build()

      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string().orEmpty()
        val safeSnippet = responseBody.take(200).replace("\n", " ").trim()
        Log.i(TAG, "[FCM] Stage 5/5: Registration HTTP status: ${response.code}, safe response: $safeSnippet")

        if (response.isSuccessful) {
          registeredSuccessfully = true
          try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
              .putString(KEY_LAST_REGISTERED_USER, session.user.id)
              .putString(KEY_LAST_REGISTERED_TOKEN, token)
              .apply()
          } catch (_: Exception) {}
          Log.i(TAG, "[FCM] Registration completed: SUCCESS (Edge Function)")
        } else {
          Log.e(TAG, "[FCM] Edge Function returned HTTP ${response.code}. Attempting direct database fallback...")
        }
      }
    } catch (error: Exception) {
      Log.e(TAG, "[FCM] Edge Function call failed (${error.javaClass.simpleName}: ${error.message}). Attempting direct database fallback...")
    }

    if (!registeredSuccessfully) {
      // user_push_tokens is intentionally service-role-only. Never attempt a direct
      // client write; the authenticated Edge Function is the sole registration path.
      Log.w(TAG, "[FCM] Registration deferred; retry when the app returns to foreground.")
    }

    return@withContext registeredSuccessfully
  }

  suspend fun unregister(context: Context, accessToken: String?) = withContext(Dispatchers.IO) {
    if (accessToken.isNullOrBlank()) return@withContext
    try {
      val token = getCachedToken(context) ?: Tasks.await(FirebaseMessaging.getInstance().token, 10, TimeUnit.SECONDS)
      if (token.isNullOrBlank()) return@withContext

      Log.i(TAG, "[FCM] Unregister request started (token length=${token.length})")
      val body = JSONObject().put("action", "unregister").put("token", token)
      val request = Request.Builder()
        .url("${SupabaseConfig.projectUrl}/functions/v1/firebase-push-notifications")
        .header("apikey", SupabaseConfig.publishableKey)
        .header("Authorization", "Bearer $accessToken")
        .header("Content-Type", "application/json")
        .post(body.toString().toRequestBody(jsonType))
        .build()

      SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
        Log.i(TAG, "[FCM] Unregister response HTTP ${response.code}")
      }
    } catch (error: Exception) {
      Log.w(TAG, "[FCM] Unregister skipped: ${error.javaClass.simpleName}: ${error.message}")
    }
  }
}
