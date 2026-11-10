package com.example

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
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
import com.example.ui.theme.JTBazarTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // The Google Services plugin normally creates these options from google-services.json.
    // Keep an explicit fallback so FCM can initialize even in build environments where
    // generated Google Services resources are missing.
    if (FirebaseApp.getApps(this).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
      val options = FirebaseOptions.Builder()
        .setApplicationId("1:763074877925:android:58381e5cfd5c3a839df70c")
        .setApiKey("AIzaSyC48PiIR-Jnn4Kunx62-2MlSYSZYRrrzU")
        .setProjectId("jtbazar-9fc3d")
        .setGcmSenderId("763074877925")
        .setStorageBucket("jtbazar-9fc3d.firebasestorage.app")
        .build()
      FirebaseApp.initializeApp(this, options)
    }

    FirebasePushManager.ensureNotificationChannel(applicationContext)

    // Initialize Supabase session persistence before starting token registration.
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
      JTBazarTheme(darkTheme = false) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color.White
        ) {
          MainApp()
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
    if (SupabaseClient.session.value != null) {
      lifecycleScope.launch {
        FirebasePushManager.register(applicationContext)
      }
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
    handleGoogleOAuthIntent(intent)
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
