package com.example.ui.screens

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
import com.example.localization.AppLanguage
import com.example.localization.AppLanguageManager
import com.example.localization.LocalAppLanguage
import com.example.localization.appText
import com.example.ui.theme.*

private const val OWNER_PROTECTED_EMAIL = "hunainhaider5566@gmail.com"

@Composable
fun AccountSettingsScreen(
  onNavigateToSecurity: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val currentUser by SupabaseClient.currentUser.collectAsState()
  val userProfile by SupabaseAuthService.userProfile.collectAsState()
  val storeSettings by MarketplaceRepository.storeSettings.collectAsState()

  val activeLanguage = LocalAppLanguage.current
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
            contentDescription = appText("Back", "واپس"),
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = appText("Account Settings", "اکاؤنٹ کی ترتیبات"),
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
        text = appText("Preferences", "ترجیحات"),
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
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text(
                text = appText("Language / زبان", "زبان / Language"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
              )
              Text(
                text = appText("Switch app language between English and Urdu", "ایپ کی زبان انگریزی اور اردو کے درمیان تبدیل کریں"),
                fontSize = 11.sp,
                color = JTTextSecondary
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              FilterChip(
                selected = activeLanguage == AppLanguage.ENGLISH,
                onClick = { AppLanguageManager.setLanguage(AppLanguage.ENGLISH, context) },
                label = { Text("English", fontSize = 11.sp) },
                modifier = Modifier.testTag("language_chip_english")
              )
              FilterChip(
                selected = activeLanguage == AppLanguage.URDU,
                onClick = { AppLanguageManager.setLanguage(AppLanguage.URDU, context) },
                label = { Text("اردو", fontSize = 11.sp) },
                modifier = Modifier.testTag("language_chip_urdu")
              )
            }
          }

          HorizontalDivider(color = JTBorder)
        }
      }

      Text(
        text = appText("Security & Privacy", "سیکیورٹی اور رازداری"),
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
                text = appText("Password & Security", "پاس ورڈ اور سیکیورٹی"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
              )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = JTTextSecondary, modifier = Modifier.size(16.dp))
          }

          HorizontalDivider(color = JTBorder)

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showDeleteDialog = true }
              .padding(16.dp)
              .testTag("request_account_deletion_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Color(0xFFC53030), modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = appText("Request Account Deletion", "اکاؤنٹ ختم کرنے کی درخواست"),
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.sp,
                  color = Color(0xFFC53030)
                )
                Text(
                  text = appText("Request deletion of your account and personal data", "اپنے اکاؤنٹ اور ذاتی ڈیٹا کو مستقل ختم کرنے کی درخواست کریں"),
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
        text = appText("About JTBazar", "جے ٹی بازار کے بارے میں"),
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
          Text(
            text = appText("JTBazar Mobile App (Version 1.0.0)", "جے ٹی بازار موبائل ایپ (ورژن 1.0.0)"),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
          Text(
            text = appText(
              "Authentic Pakistani marketplace and express fresh food delivery connecting consumers with verified artisans, neighborhood kitchens, and producers.",
              "مستند پاکستانی مارکیٹ پلیس اور ایکسپریس تازہ کھانے کی ترسیل جو صارفین کو تصدیق شدہ کاریگروں، محلے کے باورچی خانوں اور پروڈیوسرز سے جوڑتی ہے۔"
            ),
            fontSize = 11.sp,
            color = JTTextSecondary,
            lineHeight = 15.sp
          )
        }
      }
    }
  }

  if (showDeleteDialog) {
    var requesterName by remember { mutableStateOf(userProfile?.fullName ?: currentUser?.fullName ?: "") }
    var requesterEmail by remember { mutableStateOf(currentUser?.email ?: userProfile?.email ?: "") }
    var requesterReason by remember { mutableStateOf("") }
    val isSubmitting by remember { mutableStateOf(false) }

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
              Text(
                appText("JTBazar · Privacy", "جے ٹی بازار · رازداری"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E4E38)
              )
              Text(
                appText("Request Account Deletion", "اکاؤنٹ ختم کرنے کی درخواست"),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = JTTextPrimary
              )
            }
          }

          Text(
            text = appText(
              "Use this form to request deletion of your JTBazar account and associated personal information. In accordance with privacy and security standards, your identity will be verified before action is taken.",
              "اپنا جے ٹی بازار اکاؤنٹ اور ذاتی معلومات ختم کرانے کی درخواست کے لیے یہ فارم استعمال کریں۔ رازداری اور سیکیورٹی کے معیار کے مطابق کارروائی سے پہلے آپ کی شناخت کی تصدیق کی جائے گی۔"
            ),
            fontSize = 12.sp,
            color = JTTextSecondary,
            lineHeight = 16.sp
          )

          OutlinedTextField(
            value = requesterName,
            onValueChange = { requesterName = it },
            label = { Text(appText("Your Name *", "آپ کا نام *")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("delete_name_input")
          )

          OutlinedTextField(
            value = requesterEmail,
            onValueChange = { requesterEmail = it },
            label = { Text(appText("JTBazar Account Email *", "جے ٹی بازار اکاؤنٹ ای میل *")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("delete_email_input")
          )

          OutlinedTextField(
            value = requesterReason,
            onValueChange = { requesterReason = it },
            label = { Text(appText("Reason (Optional)", "وجہ (اختیاری)")) },
            placeholder = { Text(appText("e.g. No longer needed", "مثلاً اب ضرورت نہیں")) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("delete_reason_input")
          )

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF7F5EE),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                appText("What happens next:", "آگے کیا ہوگا:"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263A2B)
              )
              Text(
                text = appText(
                  "After verification, JTBazar will delete or de-identify personal account data that is not required to be retained. Limited order, transaction, fraud-prevention or legal records may be retained where required by law.",
                  "تصدیق کے بعد جے ٹی بازار آپ کا وہ ذاتی ڈیٹا ختم یا غیر شناختی کر دے گا جسے محفوظ رکھنا لازمی نہیں ہے۔ قانون کے تقاضوں کے تحت محدود آرڈر، لین دین یا قانونی ریکارڈ محفوظ رکھا جا سکتا ہے۔"
                ),
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
              Text(appText("Cancel", "منسوخ کریں"))
            }

            Button(
              onClick = {
                val emailTrim = requesterEmail.trim()
                if (emailTrim.equals(OWNER_PROTECTED_EMAIL, ignoreCase = true)) {
                  Toast.makeText(
                    context,
                    AppLanguageManager.tr(
                      "The primary store owner account cannot be deleted or suspended.",
                      "بنیادی اسٹور مالک کا اکاؤنٹ ختم یا معطل نہیں کیا جا سکتا۔"
                    ),
                    Toast.LENGTH_LONG
                  ).show()
                  return@Button
                }
                if (requesterName.isBlank() || emailTrim.isBlank()) {
                  Toast.makeText(
                    context,
                    AppLanguageManager.tr(
                      "Please enter your name and account email.",
                      "براہ کرم اپنا نام اور اکاؤنٹ ای میل درج کریں۔"
                    ),
                    Toast.LENGTH_SHORT
                  ).show()
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
                  context.startActivity(
                    Intent.createChooser(
                      mailIntent,
                      AppLanguageManager.tr("Send deletion request via email", "ای میل کے ذریعے درخواست بھیجیں")
                    )
                  )
                  Toast.makeText(
                    context,
                    AppLanguageManager.tr(
                      "Email client opened. Send email to complete your request.",
                      "ای میل ایپ کھل گئی ہے۔ اپنی درخواست مکمل کرنے کے لیے ای میل بھیجیں۔"
                    ),
                    Toast.LENGTH_LONG
                  ).show()
                  showDeleteDialog = false
                }.onFailure {
                  val cleanPhone = supportWhatsapp.replace("+", "").replace(" ", "").trim()
                  val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(body)}"))
                  runCatching {
                    context.startActivity(waIntent)
                    showDeleteDialog = false
                  }.onFailure {
                    Toast.makeText(
                      context,
                      AppLanguageManager.tr("Support email: $supportEmail", "معاونت ای میل: $supportEmail"),
                      Toast.LENGTH_LONG
                    ).show()
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC53030)),
              modifier = Modifier.weight(1.3f).testTag("delete_submit_button")
            ) {
              Text(appText("Submit Request", "درخواست جمع کرائیں"), color = Color.White)
            }
          }
        }
      }
    }
  }
}
