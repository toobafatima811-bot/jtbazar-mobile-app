package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private const val OWNER_PROTECTED_EMAIL = "hunainhaider5566@gmail.com"

@Composable
fun AccountSettingsScreen(
  onNavigateToSecurity: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val currentUser by SupabaseClient.currentUser.collectAsState()
  val userProfile by SupabaseAuthService.userProfile.collectAsState()
  val storeSettings by MarketplaceRepository.storeSettings.collectAsState()

  var notificationsEnabled by remember { mutableStateOf(true) }
  var smsUpdatesEnabled by remember { mutableStateOf(true) }
  var selectedLanguage by remember { mutableStateOf("English") }
  var showDeleteDialog by remember { mutableStateOf(false) }

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
          modifier = Modifier.testTag("settings_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (selectedLanguage == "اردو") "اکاؤنٹ کی ترتیبات" else "Account Settings",
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
        text = if (selectedLanguage == "اردو") "ترجیحات" else "Preferences",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = JTPurplePrimary
      )

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = JTCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          // Language selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = if (selectedLanguage == "اردو") "زبان / Language" else "Language / زبان",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
              )
              Text(
                text = if (selectedLanguage == "اردو") "اردو اور انگریزی کے درمیان سوئچ کریں" else "Switch between English and Urdu",
                fontSize = 11.sp,
                color = JTTextSecondary
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              FilterChip(
                selected = selectedLanguage == "English",
                onClick = { selectedLanguage = "English" },
                label = { Text("English", fontSize = 11.sp) }
              )
              FilterChip(
                selected = selectedLanguage == "اردو",
                onClick = { selectedLanguage = "اردو" },
                label = { Text("اردو", fontSize = 11.sp) }
              )
            }
          }

          HorizontalDivider(color = JTBorder)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = if (selectedLanguage == "اردو") "آرڈر پش اطلاعات" else "Order Push Notifications",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
              )
              Text(
                text = if (selectedLanguage == "اردو") "آرڈر کی تصدیق اور ترسیل کی اطلاعات حاصل کریں" else "Get alerts on order confirmation & dispatch",
                fontSize = 11.sp,
                color = JTTextSecondary
              )
            }
            Switch(
              checked = notificationsEnabled,
              onCheckedChange = { isChecked ->
                notificationsEnabled = isChecked
                if (isChecked) {
                  coroutineScope.launch {
                    val success = com.example.notifications.FirebasePushManager.register(context)
                    if (success) {
                      Toast.makeText(context, "Push notifications registered!", Toast.LENGTH_SHORT).show()
                    } else {
                      Toast.makeText(context, "Push sync attempted. Check network & sign-in.", Toast.LENGTH_SHORT).show()
                    }
                  }
                }
              },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JTOrange)
            )
          }

          if (notificationsEnabled) {
            OutlinedButton(
              onClick = {
                coroutineScope.launch {
                  Toast.makeText(
                    context,
                    if (selectedLanguage == "اردو") "اطلاعات اپ ڈیٹ ہو رہی ہیں..." else "Refreshing notifications...",
                    Toast.LENGTH_SHORT
                  ).show()
                  val ok = com.example.notifications.FirebasePushManager.register(context)
                  if (ok) {
                    Toast.makeText(
                      context,
                      if (selectedLanguage == "اردو") "اطلاعات کامیابی سے فعال ہو گئیں!" else "Notifications successfully synced!",
                      Toast.LENGTH_LONG
                    ).show()
                  } else {
                    Toast.makeText(
                      context,
                      if (selectedLanguage == "اردو") "براہ کرم پہلے لاگ ان کریں" else "Please make sure you are logged in.",
                      Toast.LENGTH_LONG
                    ).show()
                  }
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("sync_push_token_button"),
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = JTOrange)
            ) {
              Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (selectedLanguage == "اردو") "اطلاعات ریفریش کریں" else "Sync Notifications",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }

          HorizontalDivider(color = JTBorder)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = if (selectedLanguage == "اردو") "ایس ایم ایس آرڈر اپ ڈیٹس" else "SMS Order Updates",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
              )
              Text(
                text = if (selectedLanguage == "اردو") "ایس ایم ایس کے ذریعے ڈیلیوری او ٹی پی وصول کریں" else "Receive delivery OTP codes via SMS",
                fontSize = 11.sp,
                color = JTTextSecondary
              )
            }
            Switch(
              checked = smsUpdatesEnabled,
              onCheckedChange = { smsUpdatesEnabled = it },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JTOrange)
            )
          }
        }
      }

      Text(
        text = if (selectedLanguage == "اردو") "سیکیورٹی اور رازداری" else "Security & Privacy",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = JTPurplePrimary
      )

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = JTCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable(onClick = onNavigateToSecurity)
              .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.Lock, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = if (selectedLanguage == "اردو") "پاس ورڈ اور سیکیورٹی" else "Password & Security",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
              )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = JTTextSecondary, modifier = Modifier.size(16.dp))
          }

          HorizontalDivider(color = JTBorder)

          // Website Parity: Request Account Deletion
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showDeleteDialog = true }
              .padding(16.dp)
              .testTag("request_account_deletion_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Color(0xFFC53030), modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = if (selectedLanguage == "اردو") "اکاؤنٹ ختم کرنے کی درخواست" else "Request Account Deletion",
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.sp,
                  color = Color(0xFFC53030)
                )
                Text(
                  text = if (selectedLanguage == "اردو") "ذاتی ڈیٹا اور اکاؤنٹ کو مستقل ختم کریں" else "Request deletion of your account and personal data",
                  fontSize = 11.sp,
                  color = JTTextSecondary
                )
              }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = JTTextSecondary, modifier = Modifier.size(16.dp))
          }
        }
      }

      Text(
        text = if (selectedLanguage == "اردو") "جے ٹی بازار کے بارے میں" else "About JTBazar",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = JTPurplePrimary
      )

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = JTCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("JTBazar Mobile App (Version 1.0.0)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Text(
            text = if (selectedLanguage == "اردو")
              "مستند پاکستانی مارکیٹ پلیس اور ایکسپریس تازہ کھانے کی ترسیل جو صارفین کو تصدیق شدہ کاریگروں، محلے کے باورچی خانوں اور پروڈیوسرز سے جوڑتی ہے۔"
            else
              "Authentic Pakistani marketplace and express fresh food delivery connecting consumers with verified artisans, neighborhood kitchens, and producers.",
            fontSize = 11.sp,
            color = JTTextSecondary,
            lineHeight = 15.sp
          )
        }
      }
    }
  }

  // Account Deletion Dialog matching website DeleteAccountPage.tsx
  if (showDeleteDialog) {
    var requesterName by remember { mutableStateOf(userProfile?.fullName ?: currentUser?.fullName ?: "") }
    var requesterEmail by remember { mutableStateOf(currentUser?.email ?: userProfile?.email ?: "") }
    var requesterReason by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val supportEmail = storeSettings?.supportEmail ?: "jtbazar2423@gmail.com"
    val supportWhatsapp = storeSettings?.supportWhatsapp ?: "+923700822507"

    Dialog(
      onDismissRequest = { if (!isSubmitting) showDeleteDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier
          .fillMaxWidth(0.92f)
          .padding(vertical = 24.dp)
      ) {
        Column(
          modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = Color(0xFFC53030), modifier = Modifier.size(24.dp))
            Column {
              Text("JTBazar · Privacy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E4E38))
              Text("Request Account Deletion", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
            }
          }

          Text(
            text = "Use this form to request deletion of your JTBazar account and associated personal information. In accordance with privacy and security standards, your identity will be verified before action is taken.",
            fontSize = 12.sp,
            color = JTTextSecondary,
            lineHeight = 16.sp
          )

          OutlinedTextField(
            value = requesterName,
            onValueChange = { requesterName = it },
            label = { Text("Your Name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("delete_name_input")
          )

          OutlinedTextField(
            value = requesterEmail,
            onValueChange = { requesterEmail = it },
            label = { Text("JTBazar Account Email *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("delete_email_input")
          )

          OutlinedTextField(
            value = requesterReason,
            onValueChange = { requesterReason = it },
            label = { Text("Reason (Optional)") },
            placeholder = { Text("e.g. No longer needed") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("delete_reason_input")
          )

          // Legal Notice matching website
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF7F5EE),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("What happens next:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF263A2B))
              Text(
                text = "After verification, JTBazar will delete or de-identify personal account data that is not required to be retained. Limited order, transaction, fraud-prevention or legal records may be retained where required by law.",
                fontSize = 10.5.sp,
                color = Color(0xFF5A6652),
                lineHeight = 14.sp
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = { showDeleteDialog = false },
              modifier = Modifier.weight(1f)
            ) {
              Text("Cancel")
            }

            Button(
              onClick = {
                val emailTrim = requesterEmail.trim()
                if (emailTrim.equals(OWNER_PROTECTED_EMAIL, ignoreCase = true)) {
                  Toast.makeText(context, "The primary store owner account cannot be deleted or suspended.", Toast.LENGTH_LONG).show()
                  return@Button
                }
                if (requesterName.isBlank() || emailTrim.isBlank()) {
                  Toast.makeText(context, "Please enter your name and account email.", Toast.LENGTH_SHORT).show()
                  return@Button
                }

                val subject = "JTBazar account deletion request"
                val body = "I am requesting deletion of my JTBazar account and associated personal data.\n\n" +
                  "Name: ${requesterName.trim()}\n" +
                  "JTBazar account email: $emailTrim\n" +
                  "Reason: ${requesterReason.trim().ifBlank { "Not provided" }}\n\n" +
                  "Please verify my identity using a secure process before taking action and confirm when completed."

                val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                  data = Uri.parse("mailto:$supportEmail")
                  putExtra(Intent.EXTRA_SUBJECT, subject)
                  putExtra(Intent.EXTRA_TEXT, body)
                }

                runCatching {
                  context.startActivity(Intent.createChooser(mailIntent, "Send deletion request via email"))
                  Toast.makeText(context, "Email client opened. Send email to complete your request.", Toast.LENGTH_LONG).show()
                  showDeleteDialog = false
                }.onFailure {
                  // Fallback to WhatsApp
                  val cleanPhone = supportWhatsapp.replace("+", "").replace(" ", "").trim()
                  val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(body)}"))
                  runCatching {
                    context.startActivity(waIntent)
                    showDeleteDialog = false
                  }.onFailure {
                    Toast.makeText(context, "Support email: $supportEmail", Toast.LENGTH_LONG).show()
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC53030)),
              modifier = Modifier.weight(1.3f).testTag("delete_submit_button")
            ) {
              Text("Submit Request", color = Color.White)
            }
          }
        }
      }
    }
  }
}
