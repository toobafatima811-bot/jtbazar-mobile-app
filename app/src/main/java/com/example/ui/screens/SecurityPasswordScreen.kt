package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseClient
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SecurityPasswordScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val currentUser by SupabaseClient.currentUser.collectAsState()

  var currentPassword by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var currentPasswordVisible by remember { mutableStateOf(false) }
  var passwordVisible by remember { mutableStateOf(false) }
  var confirmPasswordVisible by remember { mutableStateOf(false) }
  var isUpdating by remember { mutableStateOf(false) }

  val isPasswordMatch = newPassword.isNotEmpty() && newPassword == confirmPassword

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
          modifier = Modifier.testTag("security_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = appText("Back", "واپس"),
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = appText("Password & Security", "پاس ورڈ اور سیکیورٹی"),
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
        text = appText("Change Account Password", "اکاؤنٹ کا پاس ورڈ تبدیل کریں"),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )

      Text(
        text = appText(
          "For security, ensure your password is at least 6 characters long and combines letters and numbers.",
          "سیکیورٹی کے لیے یقینی بنائیں کہ آپ کا پاس ورڈ کم از کم 6 حروف پر مشتمل ہو اور اس میں حروف اور اعداد شامل ہوں۔"
        ),
        fontSize = 12.sp,
        color = JTTextSecondary,
        lineHeight = 16.sp
      )

      OutlinedTextField(
        value = currentPassword,
        onValueChange = { currentPassword = it },
        label = { Text(appText("Current Password *", "موجودہ پاس ورڈ *")) },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = JTPurplePrimary) },
        trailingIcon = { IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) { Icon(if (currentPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null) } },
        visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("security_current_password_input")
      )
      OutlinedTextField(
        value = newPassword,
        onValueChange = { newPassword = it },
        label = { Text(appText("New Password *", "نیا پاس ورڈ *")) },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = JTPurplePrimary) },
        trailingIcon = {
          IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(
              imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
              contentDescription = null,
              tint = JTTextSecondary
            )
          }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("security_new_password_input")
      )

      OutlinedTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it },
        label = { Text(appText("Confirm New Password *", "نئے پاس ورڈ کی تصدیق کریں *")) },
        leadingIcon = { Icon(Icons.Filled.LockClock, contentDescription = null, tint = JTPurplePrimary) },
        trailingIcon = {
          IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
            Icon(
              imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
              contentDescription = null,
              tint = JTTextSecondary
            )
          }
        },
        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        isError = confirmPassword.isNotBlank() && !isPasswordMatch,
        supportingText = {
          if (confirmPassword.isNotBlank() && !isPasswordMatch) {
            Text(appText("Passwords do not match", "پاس ورڈ آپس میں مطابقت نہیں رکھتے"), color = Color(0xFFDC2626), fontSize = 11.sp)
          }
        },
        modifier = Modifier.fillMaxWidth().testTag("security_confirm_password_input")
      )

      Button(
        onClick = {
          val email = currentUser?.email ?: return@Button
          if (currentPassword.isBlank() || newPassword.length < 6 || !isPasswordMatch) {
            Toast.makeText(
              context,
              AppLanguageManager.tr(
                "Enter your current password and a matching new password (at least 6 characters).",
                "اپنا موجودہ پاس ورڈ اور مطابقت رکھنے والا نیا پاس ورڈ (کم از کم 6 حروف) درج کریں۔"
              ),
              Toast.LENGTH_LONG
            ).show()
            return@Button
          }
          isUpdating = true
          coroutineScope.launch {
            val verified = SupabaseAuthService.signIn(email, currentPassword, context)
            val result = if (verified.isSuccess) SupabaseAuthService.updatePassword(newPassword) else Result.failure(verified.exceptionOrNull() ?: IllegalStateException(AppLanguageManager.tr("Current password is incorrect.", "موجودہ پاس ورڈ غلط ہے۔")))
            isUpdating = false
            if (result.isSuccess) {
              Toast.makeText(context, AppLanguageManager.tr("Password updated successfully.", "پاس ورڈ کامیابی سے اپ ڈیٹ ہو گیا۔"), Toast.LENGTH_LONG).show()
              currentPassword = ""
              newPassword = ""
              confirmPassword = ""
            } else {
              Toast.makeText(
                context,
                AppLanguageManager.tr("Could not update password: ", "پاس ورڈ اپ ڈیٹ نہیں ہو سکا: ") + (result.exceptionOrNull()?.message ?: ""),
                Toast.LENGTH_LONG
              ).show()
            }
          }
        },
        enabled = !isUpdating && currentPassword.isNotBlank() && newPassword.length >= 6 && isPasswordMatch,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("security_update_button")
      ) {
        if (isUpdating) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(appText("Update Password", "پاس ورڈ اپ ڈیٹ کریں"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
      }

      TextButton(
        onClick = {
          val email = currentUser?.email
          if (email.isNullOrBlank()) Toast.makeText(context, AppLanguageManager.tr("Your account email is unavailable.", "آپ کے اکاؤنٹ کی ای میل دستیاب نہیں ہے۔"), Toast.LENGTH_SHORT).show()
          else {
            isUpdating = true
            coroutineScope.launch {
              val result = SupabaseAuthService.resetPassword(email)
              isUpdating = false
              val message = if (result.isSuccess) {
                AppLanguageManager.tr("Password reset email sent to $email", "پاس ورڈ ری سیٹ ای میل $email پر بھیج دی گئی ہے")
              } else {
                AppLanguageManager.tr("Could not send reset email: ", "ری سیٹ ای میل نہیں بھیجی جا سکی: ") + (result.exceptionOrNull()?.message ?: "")
              }
              Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
          }
        },
        enabled = !isUpdating,
        modifier = Modifier.fillMaxWidth()
      ) { Text(appText("Forgot password? Send reset email", "پاس ورڈ بھول گئے؟ ری سیٹ ای میل بھیجیں"), color = JTPurplePrimary, fontWeight = FontWeight.SemiBold) }
    }
  }
}
