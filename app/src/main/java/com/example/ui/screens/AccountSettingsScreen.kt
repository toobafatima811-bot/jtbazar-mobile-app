package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AccountSettingsScreen(
  onNavigateToSecurity: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var notificationsEnabled by remember { mutableStateOf(true) }
  var smsUpdatesEnabled by remember { mutableStateOf(true) }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("settings_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Account Settings",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = JTTextPrimary
        )
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(
        text = "Preferences",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = JTPurplePrimary
      )

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = JTCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Order Push Notifications", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
              Text("Get alerts on order confirmation & dispatch", fontSize = 11.sp, color = JTTextSecondary)
            }
            Switch(
              checked = notificationsEnabled,
              onCheckedChange = { notificationsEnabled = it },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JTOrange)
            )
          }

          HorizontalDivider(color = JTBorder)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("SMS Order Updates", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
              Text("Receive delivery OTP codes via SMS", fontSize = 11.sp, color = JTTextSecondary)
            }
            Switch(
              checked = smsUpdatesEnabled,
              onCheckedChange = { smsUpdatesEnabled = it },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JTOrange)
            )
          }
        }
      }

      Text(
        text = "Security & Privacy",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = JTPurplePrimary
      )

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = JTCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onNavigateToSecurity)
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Password & Security", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
          }
          Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = JTTextSecondary, modifier = Modifier.size(16.dp))
        }
      }

      Text(
        text = "About JTBazar",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = JTPurplePrimary
      )

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = JTCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("JTBazar Mobile App (Version 1.0.0)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Text(
            text = "Authentic Pakistani marketplace and express fresh food delivery connecting consumers with verified artisans, neighborhood kitchens, and producers.",
            fontSize = 11.sp,
            color = JTTextSecondary,
            lineHeight = 15.sp
          )
        }
      }
    }
  }
}
