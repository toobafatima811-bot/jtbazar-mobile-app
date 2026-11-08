package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Patterns
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
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseConfig
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
  onNavigateToRegister: () -> Unit,
  onNavigateToForgotPassword: () -> Unit,
  onLoginOtpRequired: (email: String) -> Unit,
  onLoginSuccess: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val isEmailValid = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
  val isFormValid = isEmailValid && password.isNotEmpty()

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
          modifier = Modifier.testTag("login_back_button")
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
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Text(
        text = "Welcome Back",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Sign in to track orders, manage addresses & wishlist",
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

      // Email
      OutlinedTextField(
        value = email,
        onValueChange = { email = it; errorMessage = null },
        label = { Text("Email Address") },
        placeholder = { Text("customer@example.com") },
        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = JTPurplePrimary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("login_email_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Password
      OutlinedTextField(
        value = password,
        onValueChange = { password = it; errorMessage = null },
        label = { Text("Password") },
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
          .testTag("login_password_input")
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Forgot Password Link
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        Text(
          text = "Forgot password?",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = JTOrange,
          modifier = Modifier
            .clickable(onClick = onNavigateToForgotPassword)
            .padding(vertical = 4.dp)
            .testTag("login_forgot_password_button")
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Login Button
      Button(
        onClick = {
          if (!isFormValid) {
            errorMessage = if (!isEmailValid) "Please enter a valid email." else "Please enter your password."
            return@Button
          }

          isLoading = true
          errorMessage = null
          coroutineScope.launch {
            // First validate password with Supabase auth
            val loginResult = SupabaseAuthService.signIn(email.trim(), password, context)
            if (loginResult.isSuccess) {
              // Now trigger OTP verification for two-factor login matching website rules
              val otpResult = SupabaseAuthService.sendLoginOtp(email.trim())
              isLoading = false
              if (otpResult.isSuccess) {
                onLoginOtpRequired(email.trim())
              } else {
                // If OTP request is not mandatory or direct sign-in allowed, finish login
                onLoginSuccess()
              }
            } else {
              isLoading = false
              errorMessage = loginResult.exceptionOrNull()?.message ?: "Invalid email or password."
            }
          }
        },
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("login_submit_button")
      ) {
        if (isLoading) {
          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Signing In...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
          Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
          .testTag("login_google_button")
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

      Spacer(modifier = Modifier.height(28.dp))

      // Link to Register
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Don't have an account? ",
          fontSize = 13.sp,
          color = JTTextSecondary
        )
        Text(
          text = "Register",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = JTOrange,
          modifier = Modifier
            .clickable(onClick = onNavigateToRegister)
            .testTag("login_goto_register")
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
