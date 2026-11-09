package com.example.ui.screens

import android.widget.Toast
import android.net.Uri
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
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

  var fullName by remember(userProfile, currentUser) { mutableStateOf(userProfile?.fullName?.takeIf { it.isNotBlank() && !it.equals("null", true) } ?: currentUser?.fullName?.takeIf { it.isNotBlank() && !it.equals("null", true) } ?: "") }
  var phone by remember(userProfile, currentUser) { mutableStateOf(userProfile?.phone?.takeIf { !it.equals("null", true) } ?: currentUser?.phone ?: "") }
  var isSaving by remember { mutableStateOf(false) }
  var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedPhoto = uri }

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
      Box(contentAlignment = Alignment.BottomEnd) {
        if (selectedPhoto != null) {
          AsyncImage(
            model = selectedPhoto,
            contentDescription = "Selected profile photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp).clip(CircleShape).testTag("profile_avatar_large")
          )
        } else {
          UserAvatarView(
            avatarUrl = userProfile?.avatarUrl,
            fullName = fullName,
            size = 96.dp,
            modifier = Modifier.testTag("profile_avatar_large")
          )
        }
        FilledIconButton(
          onClick = { photoPicker.launch("image/*") },
          modifier = Modifier.size(34.dp),
          colors = IconButtonDefaults.filledIconButtonColors(containerColor = JTOrange, contentColor = Color.White)
        ) {
          Icon(Icons.Filled.CameraAlt, contentDescription = "Change profile photo", modifier = Modifier.size(18.dp))
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text("Tap the camera to change your photo (JPG, PNG or WebP, max 5 MB)", fontSize = 11.sp, color = JTTextSecondary)

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

      Spacer(modifier = Modifier.height(28.dp))

      Button(
        onClick = {
          val uid = currentUser?.id ?: userProfile?.id
          if (uid.isNullOrBlank()) {
            Toast.makeText(context, "Please sign in again before updating your profile.", Toast.LENGTH_LONG).show()
            return@Button
          }
          isSaving = true
          coroutineScope.launch {
            try {
              var avatarUrl = userProfile?.avatarUrl
              selectedPhoto?.let { uri ->
                val mime = context.contentResolver.getType(uri) ?: throw IllegalArgumentException("Choose a JPG, PNG or WebP image.")
                if (mime !in setOf("image/jpeg", "image/png", "image/webp")) throw IllegalArgumentException("Choose a JPG, PNG or WebP image.")
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("Could not read the selected photo.")
                if (bytes.size > 5 * 1024 * 1024) throw IllegalArgumentException("Profile photo must be 5 MB or smaller.")
                val extension = when (mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; else -> "webp" }
                val path = "$uid/${java.util.UUID.randomUUID()}.$extension"
                avatarUrl = SupabaseClient.uploadPublicFile("customer-avatars", path, bytes, mime).getOrThrow()
              }
              val updated = SupabaseProfile(
                id = uid,
                fullName = fullName.trim(),
                phone = phone.trim(),
                email = currentUser?.email ?: userProfile?.email ?: "",
                avatarUrl = avatarUrl,
                city = city.trim()
              )
              val res = SupabaseAuthService.upsertProfile(updated)
              if (res.isSuccess) {
                selectedPhoto = null
                Toast.makeText(context, "Profile saved successfully", Toast.LENGTH_SHORT).show()
              } else {
                Toast.makeText(context, "Failed to save profile: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
              }
            } catch (error: Exception) {
              Toast.makeText(context, "Failed to update profile: ${error.message}", Toast.LENGTH_LONG).show()
            } finally { isSaving = false }
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
