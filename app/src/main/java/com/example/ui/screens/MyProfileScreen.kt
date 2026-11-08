package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseProfile
import com.example.ui.components.UserAvatarView
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MyProfileScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val userProfile by SupabaseAuthService.userProfile.collectAsState()
  val currentUser by SupabaseClient.currentUser.collectAsState()

  var fullName by remember(userProfile) { mutableStateOf(userProfile?.fullName ?: currentUser?.fullName ?: "") }
  var phone by remember(userProfile) { mutableStateOf(userProfile?.phone ?: currentUser?.phone ?: "") }
  var city by remember(userProfile) { mutableStateOf(userProfile?.city ?: "") }
  var isSaving by remember { mutableStateOf(false) }

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
          modifier = Modifier.testTag("my_profile_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "My Profile",
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
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Profile Picture Section - Real picture or initial badge only
      UserAvatarView(
        avatarUrl = userProfile?.avatarUrl,
        fullName = fullName,
        size = 84.dp,
        modifier = Modifier.testTag("profile_avatar_large")
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = fullName.ifBlank { "JTBazar Customer" },
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )

      Text(
        text = currentUser?.email ?: userProfile?.email ?: "",
        fontSize = 13.sp,
        color = JTTextSecondary
      )

      Spacer(modifier = Modifier.height(28.dp))

      // Form Fields
      OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = { Text("Full Name") },
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = JTPurplePrimary) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("profile_fullname_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = currentUser?.email ?: userProfile?.email ?: "",
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text("Email (Account Identifier)") },
        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = JTTextSecondary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Mobile Number") },
        placeholder = { Text("0300 1234567") },
        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = JTPurplePrimary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("profile_phone_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = city,
        onValueChange = { city = it },
        label = { Text("City") },
        placeholder = { Text("e.g. Lahore, Karachi, Islamabad") },
        leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null, tint = JTPurplePrimary) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("profile_city_input")
      )

      Spacer(modifier = Modifier.height(28.dp))

      Button(
        onClick = {
          val uid = currentUser?.id ?: userProfile?.id ?: return@Button
          isSaving = true
          coroutineScope.launch {
            val updated = SupabaseProfile(
              id = uid,
              fullName = fullName.trim(),
              phone = phone.trim(),
              email = currentUser?.email ?: userProfile?.email ?: "",
              avatarUrl = userProfile?.avatarUrl,
              city = city.trim()
            )
            val res = SupabaseAuthService.upsertProfile(updated)
            isSaving = false
            if (res.isSuccess) {
              Toast.makeText(context, "Profile saved successfully", Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(context, "Failed to save profile: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
          }
        },
        enabled = !isSaving,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("profile_save_button")
      ) {
        if (isSaving) {
          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Saving...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
          Text("Save Profile Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
      }
    }
  }
}
