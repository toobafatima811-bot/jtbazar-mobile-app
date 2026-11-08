package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
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
fun ReturnsRefundsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val returnWindowDays by MarketplaceRepository.returnWindowDays.collectAsState()
  val orders by MarketplaceRepository.orders.collectAsState()
  val deliveredOrders = orders.filter { it.orderStatus.equals("DELIVERED", ignoreCase = true) }

  var selectedOrderId by remember { mutableStateOf(deliveredOrders.firstOrNull()?.orderId ?: "") }
  var returnReason by remember { mutableStateOf("Damaged on delivery") }
  var description by remember { mutableStateOf("") }
  var isSubmitting by remember { mutableStateOf(false) }

  val returnReasons = listOf(
    "Damaged on delivery",
    "Defective or broken item",
    "Item not as described",
    "Missing parts or accessories",
    "Wrong item received"
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
          modifier = Modifier.testTag("returns_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Returns & Refunds",
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
      // Policy Banner
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = JTPurpleContainer,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.AutoMirrored.Filled.AssignmentReturn, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(28.dp))
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "$returnWindowDays-Day Return Guarantee",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = JTPurplePrimary
            )
            Text(
              text = "Eligible items can be returned or exchanged within $returnWindowDays days of delivery with intact packaging.",
              fontSize = 12.sp,
              color = JTTextSecondary,
              lineHeight = 16.sp
            )
          }
        }
      }

      Text(
        text = "File a Return Request",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextPrimary
      )

      if (deliveredOrders.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = JTSurfaceVariant,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = JTTextSecondary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No Delivered Orders Eligible",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = JTTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "You do not have any recently delivered orders that qualify for return requests.",
              fontSize = 12.sp,
              color = JTTextSecondary,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      } else {
        // Delivered order selector
        Text("Select Delivered Order *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = JTTextPrimary)
        deliveredOrders.forEach { ord ->
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (selectedOrderId == ord.orderId) JTPurpleContainer.copy(alpha = 0.5f) else JTCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedOrderId == ord.orderId) JTPurplePrimary else JTBorder),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedOrderId = ord.orderId }
              .padding(vertical = 2.dp)
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = selectedOrderId == ord.orderId,
                onClick = { selectedOrderId = ord.orderId },
                colors = RadioButtonDefaults.colors(selectedColor = JTOrange)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(ord.orderNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = JTTextPrimary)
                Text("Delivered on: ${ord.date.take(10)} • Rs. ${String.format("%,d", ord.grandTotal)}", fontSize = 11.sp, color = JTTextSecondary)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Return Reason
        Text("Reason for Return *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = JTTextPrimary)
        returnReasons.forEach { reason ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { returnReason = reason }
              .padding(vertical = 4.dp)
          ) {
            RadioButton(
              selected = returnReason == reason,
              onClick = { returnReason = reason },
              colors = RadioButtonDefaults.colors(selectedColor = JTOrange)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(reason, fontSize = 13.sp, color = JTTextPrimary)
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Details
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Describe the issue *") },
          placeholder = { Text("Please explain the defect or discrepancy...") },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier.fillMaxWidth().testTag("return_description_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
          onClick = {
            if (description.isBlank()) {
              Toast.makeText(context, "Please provide description details", Toast.LENGTH_SHORT).show()
              return@Button
            }
            isSubmitting = true
            MarketplaceRepository.submitReturnRequest(selectedOrderId, returnReason, description.trim())
            isSubmitting = false
            Toast.makeText(context, "Return request logged. Support team will inspect and respond.", Toast.LENGTH_LONG).show()
            description = ""
          },
          enabled = !isSubmitting && description.isNotBlank(),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("return_submit_button")
        ) {
          Text("Submit Return Request", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
      }
    }
  }
}
