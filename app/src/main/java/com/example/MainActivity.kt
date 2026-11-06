package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.MainApp
import com.example.ui.theme.JTBazarTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

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
  }
}
