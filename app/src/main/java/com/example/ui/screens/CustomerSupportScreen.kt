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
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CustomerSupportScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var subject by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Order Delivery") }
  var isSubmitting by remember { mutableStateOf(false) }

  val categories = listOf(
    "Order Delivery" to appText("Order Delivery", "آرڈر ڈیلیوری"),
    "Payment & Refund" to appText("Payment & Refund", "ادائیگی اور واپسی"),
    "Product Quality" to appText("Product Quality", "پروڈکٹ کا معیار"),
    "Vendor Experience" to appText("Vendor Experience", "فروشندہ کا تجربہ"),
    "App Issue" to appText("App Issue", "ایپ کا مسئلہ"),
    "Other" to appText("Other", "دیگر")
  )

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
          modifier = Modifier.testTag("support_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = appText("Back", "واپس"),
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = appText("Customer Support & Complaints", "کسٹمر سپورٹ اور شکایات"),
          fontSize = 17.sp,
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
      // Direct Contact Card
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = JTPurpleContainer,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.HeadsetMic, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(appText("Need Quick Assistance?", "فوری مدد درکار ہے؟"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = JTPurplePrimary)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = appText(
              "JTBazar customer support is available through our official support email. Please include your order number so we can help you faster:",
              "JTBazar کسٹمر سپورٹ ہماری سرکاری سپورٹ ای میل کے ذریعے دستیاب ہے۔ براہ کرم اپنا آرڈر نمبر شامل کریں تاکہ ہم آپ کی جلد مدد کر سکیں:"
            ),
            fontSize = 12.sp,
            color = JTTextSecondary,
            lineHeight = 16.sp
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                  data = Uri.parse("mailto:support@jtbazar.pk?subject=Customer Support Inquiry")
                }
                context.startActivity(intent)
              },
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("support@jtbazar.pk", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      Text(
        text = appText("Submit a Support Ticket / Complaint", "سپورٹ ٹکٹ / شکایت جمع کریں"),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )

      // Category Selection Chips
      Text(appText("Inquiry Category *", "استفسار کا زمرہ *"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = JTTextPrimary)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        categories.take(3).forEach { (catKey, catLabel) ->
          FilterChip(
            selected = category == catKey,
            onClick = { category = catKey },
            label = { Text(catLabel, fontSize = 11.sp) }
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        categories.drop(3).forEach { (catKey, catLabel) ->
          FilterChip(
            selected = category == catKey,
            onClick = { category = catKey },
            label = { Text(catLabel, fontSize = 11.sp) }
          )
        }
      }

      // Subject
      OutlinedTextField(
        value = subject,
        onValueChange = { subject = it },
        label = { Text(appText("Subject *", "موضوع *")) },
        placeholder = { Text(appText("Brief summary of your inquiry", "اپنے استفسار کا مختصر خلاصہ")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("support_subject_input")
      )

      // Message
      OutlinedTextField(
        value = message,
        onValueChange = { message = it },
        label = { Text(appText("Detailed Description *", "مکمل تفصیل *")) },
        placeholder = { Text(appText("Please describe the issue or inquiry in detail...", "براہ کرم مسئلے یا استفسار کی مکمل تفصیل بیان کریں...")) },
        minLines = 4,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth().testTag("support_message_input")
      )

      Button(
        onClick = {
          if (subject.isBlank() || message.isBlank()) {
            Toast.makeText(context, AppLanguageManager.tr("Please complete all required fields", "براہ کرم تمام ضروری خانے پُر کریں"), Toast.LENGTH_SHORT).show()
            return@Button
          }

          isSubmitting = true
          coroutineScope.launch {
            val result = MarketplaceRepository.submitComplaint(null, "[$category] ${subject.trim()}", message.trim())
            isSubmitting = false
            if (result.isSuccess) {
              Toast.makeText(context, AppLanguageManager.tr("Support request submitted successfully.", "سپورٹ کی درخواست کامیابی سے جمع ہو گئی۔"), Toast.LENGTH_LONG).show()
              subject = ""
              message = ""
            } else {
              Toast.makeText(
                context,
                AppErrorMapper.toCustomerMessage(result.exceptionOrNull(), ErrorContext.SUPPORT),
                Toast.LENGTH_LONG
              ).show()
            }
          }
        },
        enabled = !isSubmitting && subject.isNotBlank() && message.isNotBlank(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("support_submit_button")
      ) {
        Text(appText("Submit Support Request", "سپورٹ درخواست جمع کریں"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
      }
    }
  }
}
