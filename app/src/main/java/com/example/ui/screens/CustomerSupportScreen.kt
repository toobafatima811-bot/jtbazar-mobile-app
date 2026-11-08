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
import com.example.ui.theme.*

@Composable
fun CustomerSupportScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var subject by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Order Delivery") }
  var isSubmitting by remember { mutableStateOf(false) }

  val categories = listOf("Order Delivery", "Payment & Refund", "Product Quality", "Vendor Experience", "App Issue", "Other")

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
            contentDescription = "Back",
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Customer Support & Complaints",
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
            Text("Need Quick Assistance?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = JTPurplePrimary)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Our Lahore & Karachi resolution teams operate Mon–Sat 9AM–9PM. Reach out directly via official channels:",
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
        text = "Submit a Support Ticket / Complaint",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )

      // Category Selection Chips
      Text("Inquiry Category *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = JTTextPrimary)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        categories.take(3).forEach { cat ->
          FilterChip(
            selected = category == cat,
            onClick = { category = cat },
            label = { Text(cat, fontSize = 11.sp) }
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        categories.drop(3).forEach { cat ->
          FilterChip(
            selected = category == cat,
            onClick = { category = cat },
            label = { Text(cat, fontSize = 11.sp) }
          )
        }
      }

      // Subject
      OutlinedTextField(
        value = subject,
        onValueChange = { subject = it },
        label = { Text("Subject *") },
        placeholder = { Text("Brief summary of your inquiry") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("support_subject_input")
      )

      // Message
      OutlinedTextField(
        value = message,
        onValueChange = { message = it },
        label = { Text("Detailed Description *") },
        placeholder = { Text("Please describe the issue or inquiry in detail...") },
        minLines = 4,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth().testTag("support_message_input")
      )

      Button(
        onClick = {
          if (subject.isBlank() || message.isBlank()) {
            Toast.makeText(context, "Please complete all required fields", Toast.LENGTH_SHORT).show()
            return@Button
          }

          isSubmitting = true
          MarketplaceRepository.submitComplaint(null, "[$category] ${subject.trim()}", message.trim())
          isSubmitting = false
          Toast.makeText(context, "Ticket submitted successfully. Ticket ID assigned.", Toast.LENGTH_LONG).show()
          subject = ""
          message = ""
        },
        enabled = !isSubmitting && subject.isNotBlank() && message.isNotBlank(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("support_submit_button")
      ) {
        Text("Submit Support Request", fontWeight = FontWeight.Bold, fontSize = 14.sp)
      }
    }
  }
}
