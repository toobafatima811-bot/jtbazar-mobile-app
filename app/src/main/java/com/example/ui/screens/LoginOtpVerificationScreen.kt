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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.supabase.SupabaseAuthService
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginOtpVerificationScreen(
  email: String,
  onVerified: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var otpCode by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var successMessage by remember { mutableStateOf<String?>(null) }

  var resendCountdown by remember { mutableIntStateOf(60) }
  var canResend by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    while (resendCountdown > 0) {
      delay(1000L)
      resendCountdown--
    }
    canResend = true
  }

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
          modifier = Modifier.testTag("login_otp_back_button")
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
            imageVector = Icons.Filled.Security,
            contentDescription = null,
            tint = JTPurplePrimary,
            modifier = Modifier.size(36.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Security Verification",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Enter the 6-digit sign-in code sent to:",
        fontSize = 13.sp,
        color = JTTextSecondary,
        textAlign = TextAlign.Center
      )

      Text(
        text = email,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = JTPurplePrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 2.dp)
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

      // OTP Text Input
      OutlinedTextField(
        value = otpCode,
        onValueChange = {
          if (it.length <= 6 && it.all { char -> char.isDigit() }) {
            otpCode = it
            errorMessage = null
          }
        },
        label = { Text("6-Digit Code") },
        placeholder = { Text("123456") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
          textAlign = TextAlign.Center,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 8.sp
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("login_otp_code_input")
      )

      Spacer(modifier = Modifier.height(24.dp))

      // Verify Button
      Button(
        onClick = {
          if (otpCode.length != 6) {
            errorMessage = "Please enter the complete 6-digit code."
            return@Button
          }

          isLoading = true
          errorMessage = null
          coroutineScope.launch {
            val result = SupabaseAuthService.verifyLoginOtp(
              email = email.trim(),
              token = otpCode.trim(),
              context = context
            )
            isLoading = false
            if (result.isSuccess) {
              Toast.makeText(context, "Welcome back to JTBazar!", Toast.LENGTH_SHORT).show()
              onVerified()
            } else {
              errorMessage = result.exceptionOrNull()?.message ?: "Invalid or expired code. Please try again."
            }
          }
        },
        enabled = !isLoading && otpCode.length == 6,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("login_otp_verify_button")
      ) {
        if (isLoading) {
          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Verifying...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
          Text("Verify & Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Resend OTP Section
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = "Didn't receive the code? ",
          fontSize = 12.sp,
          color = JTTextSecondary
        )
        if (canResend) {
          Text(
            text = "Resend Code",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = JTOrange,
            modifier = Modifier
              .clickable {
                coroutineScope.launch {
                  canResend = false
                  resendCountdown = 60
                  val res = SupabaseAuthService.sendLoginOtp(email.trim())
                  if (res.isSuccess) {
                    successMessage = "A new verification code has been sent."
                  } else {
                    errorMessage = res.exceptionOrNull()?.message ?: "Failed to resend code."
                  }
                  while (resendCountdown > 0) {
                    delay(1000L)
                    resendCountdown--
                  }
                  canResend = true
                }
              }
              .testTag("login_otp_resend_button")
          )
        } else {
          Text(
            text = "Resend in ${resendCountdown}s",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = JTTextSecondary
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
