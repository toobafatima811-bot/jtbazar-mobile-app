package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.SupabaseAuthService
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ResetPasswordScreen(
  email: String,
  onResetSuccess: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var token by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var confirmPasswordVisible by remember { mutableStateOf(false) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val isPasswordMatch = newPassword.isNotEmpty() && newPassword == confirmPassword
  val isFormValid = token.isNotBlank() && newPassword.length >= 6 && isPasswordMatch

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
          modifier = Modifier.testTag("reset_pass_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = appText("Back", "واپس"),
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Image(
          painter = painterResource(id = R.drawable.logo_jtbazar_header),
          contentDescription = appText("JTBazar Logo", "JTBazar لوگو"),
          contentScale = ContentScale.Fit,
          modifier = Modifier.height(26.dp)
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
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Text(
        text = appText("Set New Password", "نیا پاس ورڈ مقرر کریں"),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = appText(
          "Enter the reset code sent to $email and your new password.",
          "$email پر بھیجا گیا ری سیٹ کوڈ اور اپنا نیا پاس ورڈ درج کریں۔"
        ),
        fontSize = 13.sp,
        color = JTTextSecondary
      )

      Spacer(modifier = Modifier.height(24.dp))

      if (errorMessage != null) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFFFEE2E2),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = errorMessage ?: "",
              color = Color(0xFFB91C1C),
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }

      // Reset Token / Code
      OutlinedTextField(
        value = token,
        onValueChange = { token = it; errorMessage = null },
        label = { Text(appText("Reset Code *", "ری سیٹ کوڈ *")) },
        placeholder = { Text(appText("Enter 6-digit code or token", "6 ہندسوں کا کوڈ یا ٹوکن درج کریں")) },
        leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null, tint = JTPurplePrimary) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reset_pass_token_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      // New Password
      OutlinedTextField(
        value = newPassword,
        onValueChange = { newPassword = it; errorMessage = null },
        label = { Text(appText("New Password * (min. 6 characters)", "نیا پاس ورڈ * (کم از کم 6 حروف)")) },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = JTPurplePrimary) },
        trailingIcon = {
          IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(
              imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
              contentDescription = if (passwordVisible) appText("Hide password", "پاس ورڈ چھپائیں") else appText("Show password", "پاس ورڈ دکھائیں"),
              tint = JTTextSecondary
            )
          }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reset_pass_new_password_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Confirm Password
      OutlinedTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it; errorMessage = null },
        label = { Text(appText("Confirm New Password *", "نئے پاس ورڈ کی تصدیق کریں *")) },
        leadingIcon = { Icon(Icons.Filled.LockClock, contentDescription = null, tint = JTPurplePrimary) },
        trailingIcon = {
          IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
            Icon(
              imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
              contentDescription = if (confirmPasswordVisible) appText("Hide password", "پاس ورڈ چھپائیں") else appText("Show password", "پاس ورڈ دکھائیں"),
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
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reset_pass_confirm_input")
      )

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = {
          if (!isFormValid) {
            errorMessage = when {
              token.isBlank() -> AppLanguageManager.tr("Please enter the reset code.", "براہ کرم ری سیٹ کوڈ درج کریں۔")
              newPassword.length < 6 -> AppLanguageManager.tr("Password must be at least 6 characters.", "پاس ورڈ کم از کم 6 حروف کا ہونا ضروری ہے۔")
              !isPasswordMatch -> AppLanguageManager.tr("Passwords do not match.", "پاس ورڈ آپس میں مطابقت نہیں رکھتے۔")
              else -> AppLanguageManager.tr("Please verify all fields.", "براہ کرم تمام خانے چیک کریں۔")
            }
            return@Button
          }

          isLoading = true
          errorMessage = null
          coroutineScope.launch {
            // Verify recovery token and update password
            val verifyRes = SupabaseAuthService.verifyLoginOtp(email, token, context)
            if (verifyRes.isSuccess) {
              Toast.makeText(context, AppLanguageManager.tr("Password updated successfully!", "پاس ورڈ کامیابی سے اپ ڈیٹ ہو گیا!"), Toast.LENGTH_SHORT).show()
              onResetSuccess()
            } else {
              isLoading = false
              errorMessage = AppErrorMapper.toCustomerMessage(verifyRes.exceptionOrNull(), ErrorContext.AUTH)
            }
          }
        },
        enabled = !isLoading && isFormValid,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("reset_pass_submit_button")
      ) {
        if (isLoading) {
          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text(appText("Updating Password...", "پاس ورڈ اپ ڈیٹ ہو رہا ہے..."), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
          Text(appText("Update Password & Sign In", "پاس ورڈ اپ ڈیٹ کریں اور سائن ان ہوں"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
      }
    }
  }
}
