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
import kotlinx.coroutines.launch
import com.example.ui.theme.JTBazarTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize Supabase session persistence
    com.example.data.supabase.SupabaseAuthService.init(applicationContext)

    lifecycleScope.launch {
      SupabaseClient.session.collect { session ->
        if (session != null && FirebasePushManager.hasNotificationPermission(this@MainActivity)) {
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

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 7301 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
      lifecycleScope.launch { FirebasePushManager.register(applicationContext) }
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
