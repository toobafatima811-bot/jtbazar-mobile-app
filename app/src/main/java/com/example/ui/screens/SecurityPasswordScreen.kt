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

  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
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
            contentDescription = "Back",
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Password & Security",
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
        text = "Change Account Password",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )

      Text(
        text = "For security, ensure your password is at least 6 characters long and combines letters and numbers.",
        fontSize = 12.sp,
        color = JTTextSecondary,
        lineHeight = 16.sp
      )

      OutlinedTextField(
        value = newPassword,
        onValueChange = { newPassword = it },
        label = { Text("New Password *") },
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
        label = { Text("Confirm New Password *") },
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
            Text("Passwords do not match", color = Color(0xFFDC2626), fontSize = 11.sp)
          }
        },
        modifier = Modifier.fillMaxWidth().testTag("security_confirm_password_input")
      )

      Button(
        onClick = {
          val email = currentUser?.email ?: return@Button
          if (newPassword.length < 6 || !isPasswordMatch) {
            Toast.makeText(context, "Password must be at least 6 characters and match confirmation", Toast.LENGTH_SHORT).show()
            return@Button
          }

          isUpdating = true
          coroutineScope.launch {
            // Trigger password update via reset password or direct request
            val res = SupabaseAuthService.resetPassword(email)
            isUpdating = false
            if (res.isSuccess) {
              Toast.makeText(context, "Password reset confirmation sent to $email. Follow instructions to finalize.", Toast.LENGTH_LONG).show()
              newPassword = ""
              confirmPassword = ""
            } else {
              Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
          }
        },
        enabled = !isUpdating && newPassword.length >= 6 && isPasswordMatch,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("security_update_button")
      ) {
        if (isUpdating) {
          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Text("Update Password", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
      }
    }
  }
}
