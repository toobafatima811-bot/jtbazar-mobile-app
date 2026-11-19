package com.example

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.MainApp
import com.example.notifications.FirebasePushManager
import com.example.data.supabase.SupabaseClient
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import coil.Coil
import com.example.ui.NotificationNavigation
import com.example.ui.components.JTBazarImageLoader
import com.example.ui.theme.JTBazarTheme

class MainActivity : ComponentActivity() {
  private val notificationTap = MutableStateFlow<NotificationNavigation?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    captureNotificationTap(intent)

    // The Google Services plugin normally creates these options from google-services.json.
    // Keep an explicit fallback so FCM can initialize even in build environments where
    // generated Google Services resources are missing.
    FirebasePushManager.ensureInitialized(this)
    FirebasePushManager.ensureNotificationChannel(applicationContext)

    // Initialize Supabase session persistence, saved language, and shared Coil image cache before UI composition.
    Coil.setImageLoader { JTBazarImageLoader.get(applicationContext) }
    com.example.localization.AppLanguageManager.init(applicationContext)
    com.example.data.supabase.SupabaseAuthService.init(applicationContext)

    // Generate the FCM installation token immediately on app launch, even before sign-in
    // or the Android 13+ notification permission decision.
    lifecycleScope.launch {
      FirebasePushManager.prepareToken(applicationContext)
      if (SupabaseClient.session.value != null) {
        FirebasePushManager.register(applicationContext)
      }
    }

    lifecycleScope.launch {
      SupabaseClient.session.collect { session ->
        if (session != null) {
          FirebasePushManager.register(applicationContext)
        }
      }
    }

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(
        android.graphics.Color.WHITE,
        android.graphics.Color.WHITE
      ),
      navigationBarStyle = SystemBarStyle.light(
        android.graphics.Color.WHITE,
        android.graphics.Color.WHITE
      )
    )
    window.decorView.setBackgroundColor(android.graphics.Color.WHITE)

    setContent {
      val tap by notificationTap.collectAsState()
      JTBazarTheme(darkTheme = false) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color.White
        ) {
          MainApp(notificationTap = tap)
        }
      }
    }

    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
    }
    handleGoogleOAuthIntent(intent)
  }

  // Retry token registration whenever the app returns to the foreground. This covers
  // restored sessions and cases where the initial permission/session events raced.
  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      FirebasePushManager.register(applicationContext)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 7301) {
      lifecycleScope.launch {
        // Permission affects display, not token generation; retry upload after user's choice.
        FirebasePushManager.prepareToken(applicationContext)
        FirebasePushManager.register(applicationContext)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    captureNotificationTap(intent)
    handleGoogleOAuthIntent(intent)
  }


  private fun captureNotificationTap(source: Intent?) {
    if (source == null) return
    val id = source.getStringExtra("notification_id")
    val kind = source.getStringExtra("notification_kind")
    val orderId = source.getStringExtra("related_order_id")
    val link = source.getStringExtra("notification_link")
    val audience = source.getStringExtra("notification_audience")
    if (id != null || kind != null || orderId != null || link != null || audience != null) {
      notificationTap.value = NotificationNavigation(
        id = id,
        kind = kind,
        relatedOrderId = orderId,
        link = link,
        audience = audience
      )
      // Clear extras after consuming so activity recreation does not replay the same tap.
      source.removeExtra("notification_id")
      source.removeExtra("notification_kind")
      source.removeExtra("related_order_id")
      source.removeExtra("notification_link")
      source.removeExtra("notification_audience")
    }
  }

  private fun handleGoogleOAuthIntent(intent: Intent?) {
    val callbackUri = intent?.data ?: return
    if (callbackUri.scheme != "jtbazar" || callbackUri.host != "auth-callback") return

    // Prevent the same one-time authorization code from being processed twice.
    setIntent(Intent(intent).setData(null))
    lifecycleScope.launch {
      com.example.data.supabase.SupabaseAuthService.handleGoogleOAuthCallback(
        callbackUri,
        applicationContext
      )
    }
  }
}
