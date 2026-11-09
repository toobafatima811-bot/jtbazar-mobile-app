package com.example

import android.os.Bundle
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
import kotlinx.coroutines.launch
import com.example.ui.theme.JTBazarTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize Supabase session persistence
    com.example.data.supabase.SupabaseAuthService.init(applicationContext)

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

    handleGoogleOAuthIntent(intent)
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
