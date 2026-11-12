package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  onFinish: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scale = remember { Animatable(0.85f) }
  val alpha = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    alpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
    scale.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    // Automatically transition to the home screen after 2.3s
    delay(2300)
    onFinish()
  }

  Surface(
    color = Color.White,
    modifier = modifier.fillMaxSize()
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.White)
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Top Banner Tagline on crisp white
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(top = 16.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = JTOrangeContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, JTOrangeBorder)
          ) {
            Text(
              text = "— ONE MARKETPLACE, MORE POSSIBILITIES —",
              fontSize = 10.sp,
              fontWeight = FontWeight.ExtraBold,
              color = JTOrangeDark,
              letterSpacing = 1.sp,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "Good things,\nmade for everyday.",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = JTNavyPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
          )
        }

        // Mascot 3D Illustration on crisp white
        Box(
          modifier = Modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(1f)
            .scale(scale.value),
          contentAlignment = Alignment.Center
        ) {
          // Soft warm glow behind mascot
          Box(
            modifier = Modifier
              .fillMaxSize(0.86f)
              .clip(CircleShape)
              .background(JTOrangeContainer.copy(alpha = 0.6f))
          )

          Image(
            painter = painterResource(id = R.drawable.splash_mascot),
            contentDescription = "JTBazar Mascot",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
          )
        }

        // Bottom Brand & Action
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
        ) {
          // Official JTBAZAR Logo
          Image(
            painter = painterResource(id = R.drawable.logo_jtbazar_header),
            contentDescription = "JTBazar Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(34.dp)
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "Pakistan's Trusted Marketplace • Authentic & Fresh",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = JTTextSecondary
          )

          Spacer(modifier = Modifier.height(20.dp))

          // Explore collection CTA in Vibrant Orange
          Button(
            onClick = onFinish,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = JTOrange,
              contentColor = Color.White
            ),
            modifier = Modifier
              .fillMaxWidth(0.85f)
              .height(48.dp)
              .testTag("splash_enter_button")
          ) {
            Text(
              text = "Explore the collection",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }
  }
}
