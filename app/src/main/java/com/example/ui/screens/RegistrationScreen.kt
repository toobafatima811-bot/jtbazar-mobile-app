package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
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
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseConfig
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun RegistrationScreen(
  onNavigateToLogin: () -> Unit,
  onOtpRequired: (email: String, fullName: String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var fullName by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var confirmPasswordVisible by remember { mutableStateOf(false) }
  var termsAgreed by remember { mutableStateOf(false) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val isEmailValid = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
  val isPasswordMatch = password.isNotEmpty() && password == confirmPassword
  val isFormValid = fullName.isNotBlank() && isEmailValid && password.length >= 6 && isPasswordMatch && termsAgreed

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
          modifier = Modifier.testTag("reg_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Image(
          painter = painterResource(id = R.drawable.logo_jtbazar_header),
          contentDescription = "JTBazar Logo",
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
        .padding(horizontal = 24.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Text(
        text = "Create an Account",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Join Pakistan's authentic marketplace & fresh food network",
        fontSize = 13.sp,
        color = JTTextSecondary
      )

      Spacer(modifier = Modifier.height(20.dp))

      if (errorMessage != null) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFFFEE2E2),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
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

      // Full Name
      OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it; errorMessage = null },
        label = { Text("Full Name *") },
        placeholder = { Text("e.g. Muhammad Ali") },
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = JTPurplePrimary) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reg_fullname_input")
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Email
      OutlinedTextField(
        value = email,
        onValueChange = { email = it; errorMessage = null },
        label = { Text("Email Address *") },
        placeholder = { Text("customer@example.com") },
        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = JTPurplePrimary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        isError = email.isNotBlank() && !isEmailValid,
        supportingText = {
          if (email.isNotBlank() && !isEmailValid) {
            Text("Please enter a valid email address", color = Color(0xFFDC2626), fontSize = 11.sp)
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reg_email_input")
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Password
      OutlinedTextField(
        value = password,
        onValueChange = { password = it; errorMessage = null },
        label = { Text("Password * (min. 6 characters)") },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = JTPurplePrimary) },
        trailingIcon = {
          IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(
              imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
              contentDescription = if (passwordVisible) "Hide password" else "Show password",
              tint = JTTextSecondary
            )
          }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reg_password_input")
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Confirm Password
      OutlinedTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it; errorMessage = null },
        label = { Text("Confirm Password *") },
        leadingIcon = { Icon(Icons.Filled.LockClock, contentDescription = null, tint = JTPurplePrimary) },
        trailingIcon = {
          IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
            Icon(
              imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
              contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
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
            Text("Passwords do not match", color = Color(0xFFDC2626), fontSize = 11.sp)
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reg_confirm_password_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Terms & Conditions Checkbox
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { termsAgreed = !termsAgreed }
          .padding(vertical = 4.dp)
      ) {
        Checkbox(
          checked = termsAgreed,
          onCheckedChange = { termsAgreed = it },
          colors = CheckboxDefaults.colors(checkedColor = JTOrange),
          modifier = Modifier.testTag("reg_terms_checkbox")
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "I agree to the Terms of Service & Privacy Policy",
          fontSize = 12.sp,
          color = JTTextPrimary,
          fontWeight = FontWeight.Medium
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Submit Button
      Button(
        onClick = {
          if (!isFormValid) {
            errorMessage = when {
              fullName.isBlank() -> "Please enter your full name."
              !isEmailValid -> "Please enter a valid email address."
              password.length < 6 -> "Password must be at least 6 characters."
              !isPasswordMatch -> "Passwords do not match."
              !termsAgreed -> "Please agree to the Terms & Conditions."
              else -> "Please fill all required fields correctly."
            }
            return@Button
          }

          isLoading = true
          errorMessage = null
          coroutineScope.launch {
            val result = SupabaseAuthService.signUp(
              email = email.trim(),
              password = password,
              fullName = fullName.trim(),
              phone = "",
              context = context
            )
            isLoading = false
            if (result.isSuccess) {
              // Successfully triggered Supabase signup OTP
              onOtpRequired(email.trim(), fullName.trim())
            } else {
              errorMessage = result.exceptionOrNull()?.message ?: "Registration failed. Please check details."
            }
          }
        },
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("reg_submit_button")
      ) {
        if (isLoading) {
          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Creating Account...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
          Text("Create Account", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Divider with "OR"
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = JTBorder)
        Text(
          text = "  OR  ",
          fontSize = 11.sp,
          color = JTTextSecondary,
          fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = JTBorder)
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Continue with Google
      OutlinedButton(
        onClick = {
          // Open Supabase Google OAuth provider URL
          val authUrl = "${SupabaseConfig.authUrl}/authorize?provider=google"
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
          context.startActivity(intent)
        },
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = JTTextPrimary),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("reg_google_button")
      ) {
        Icon(
          imageVector = Icons.Filled.AccountCircle,
          contentDescription = "Google",
          tint = JTPurplePrimary,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "Continue with Google",
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.sp,
          color = JTTextPrimary
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Link to Login
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Already have an account? ",
          fontSize = 13.sp,
          color = JTTextSecondary
        )
        Text(
          text = "Sign In",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = JTOrange,
          modifier = Modifier
            .clickable(onClick = onNavigateToLogin)
            .testTag("reg_goto_login")
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
