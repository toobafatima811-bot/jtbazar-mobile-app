package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.supabase.SupabaseAuthService
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(
  onNavigateToLogin: () -> Unit,
  onNavigateToResetPassword: (email: String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val coroutineScope = rememberCoroutineScope()

  var email by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var successMessage by remember { mutableStateOf<String?>(null) }

  val isEmailValid = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

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
          modifier = Modifier.testTag("forgot_pass_back_button")
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
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = JTPurpleContainer,
        modifier = Modifier.size(72.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.LockReset,
            contentDescription = null,
            tint = JTPurplePrimary,
            modifier = Modifier.size(36.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Forgot Password",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Enter your registered email to receive password reset instructions.",
        fontSize = 13.sp,
        color = JTTextSecondary,
        textAlign = TextAlign.Center
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

      if (successMessage != null) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFFDEF7EC),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF0E9F6E), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = successMessage ?: "",
              color = Color(0xFF03543F),
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }

      OutlinedTextField(
        value = email,
        onValueChange = { email = it; errorMessage = null },
        label = { Text("Registered Email") },
        placeholder = { Text("customer@example.com") },
        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = JTPurplePrimary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("forgot_pass_email_input")
      )

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = {
          if (!isEmailValid) {
            errorMessage = "Please enter a valid email address."
            return@Button
          }

          isLoading = true
          errorMessage = null
          coroutineScope.launch {
            val result = SupabaseAuthService.resetPassword(email.trim())
            isLoading = false
            if (result.isSuccess) {
              successMessage = "Password reset link sent! Check your inbox."
            } else {
              errorMessage = result.exceptionOrNull()?.message ?: "Failed to send reset instructions."
            }
          }
        },
        enabled = !isLoading && isEmailValid,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("forgot_pass_submit_button")
      ) {
        if (isLoading) {
          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Sending...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
          Text("Send Instructions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      if (successMessage != null) {
        OutlinedButton(
          onClick = { onNavigateToResetPassword(email.trim()) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
          Text("Enter Reset Code / New Password", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Remembered your password? ",
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
            .testTag("forgot_pass_goto_login")
        )
      }
    }
  }
}
