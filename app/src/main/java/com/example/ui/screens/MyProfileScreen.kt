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
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseProfile
import com.example.localization.AppLanguageManager
import com.example.localization.appText
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
            contentDescription = appText("Back", "واپس"),
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = appText("My Profile", "میرا پروفائل"),
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
            contentDescription = appText("Selected profile photo", "منتخب پروفائل تصویر"),
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
          Icon(Icons.Filled.CameraAlt, contentDescription = appText("Change profile photo", "پروفائل تصویر تبدیل کریں"), modifier = Modifier.size(18.dp))
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        appText("Tap the camera to change your photo (JPG, PNG or WebP, max 5 MB)", "اپنی تصویر تبدیل کرنے کے لیے کیمرہ آئیکن دبائیں (JPG، PNG یا WebP، زیادہ سے زیادہ 5 MB)"),
        fontSize = 11.sp,
        color = JTTextSecondary
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = fullName.ifBlank { appText("JTBazar Customer", "جے ٹی بازار کسٹمر") },
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
        label = { Text(appText("Full Name", "پورا نام")) },
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
        label = { Text(appText("Email (Account Identifier)", "ای میل (اکاؤنٹ شناخت)")) },
        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = JTTextSecondary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text(appText("Mobile Number", "موبائل نمبر")) },
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
            Toast.makeText(
              context,
              AppLanguageManager.tr("Please sign in again before updating your profile.", "پروفائل اپڈیٹ کرنے سے پہلے براہ کرم دوبارہ سائن ان کریں۔"),
              Toast.LENGTH_LONG
            ).show()
            return@Button
          }
          isSaving = true
          coroutineScope.launch {
            try {
              var avatarUrl = userProfile?.avatarUrl
              selectedPhoto?.let { uri ->
                val mime = context.contentResolver.getType(uri)
                  ?: throw IllegalArgumentException(AppLanguageManager.tr("Choose a JPG, PNG or WebP image.", "JPG، PNG یا WebP تصویر منتخب کریں۔"))
                if (mime !in setOf("image/jpeg", "image/png", "image/webp")) {
                  throw IllegalArgumentException(AppLanguageManager.tr("Choose a JPG, PNG or WebP image.", "JPG، PNG یا WebP تصویر منتخب کریں۔"))
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                  ?: throw IllegalArgumentException(AppLanguageManager.tr("Could not read the selected photo.", "منتخب تصویر پڑھی نہیں جا سکی۔"))
                if (bytes.size > 5 * 1024 * 1024) {
                  throw IllegalArgumentException(AppLanguageManager.tr("Profile photo must be 5 MB or smaller.", "پروفائل تصویر 5 MB یا اس سے کم ہونی چاہیے۔"))
                }
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
                city = userProfile?.city ?: ""
              )
              val res = SupabaseAuthService.upsertProfile(updated)
              if (res.isSuccess) {
                selectedPhoto = null
                Toast.makeText(
                  context,
                  AppLanguageManager.tr("Profile saved successfully", "پروفائل کامیابی سے محفوظ ہو گیا"),
                  Toast.LENGTH_SHORT
                ).show()
              } else {
                Toast.makeText(
                  context,
                  AppErrorMapper.toCustomerMessage(res.exceptionOrNull(), ErrorContext.GENERAL),
                  Toast.LENGTH_LONG
                ).show()
              }
            } catch (error: Exception) {
              Toast.makeText(
                context,
                AppErrorMapper.toCustomerMessage(error, ErrorContext.GENERAL),
                Toast.LENGTH_LONG
              ).show()
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
          Text(appText("Saving...", "محفوظ ہو رہا ہے..."), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
          Text(appText("Save Profile Changes", "پروفائل کی تبدیلیاں محفوظ کریں"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
      }
    }
  }
}
