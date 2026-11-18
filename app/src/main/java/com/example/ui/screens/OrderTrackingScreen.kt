package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Storefront
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.OrderRecord
import com.example.data.model.StepState
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.example.ui.components.JTBazarTopBar
import com.example.ui.theme.*

@Composable
fun OrderTrackingScreen(
  onBackClick: () -> Unit,
  onContinueShopping: () -> Unit,
  onAccountClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activeOrder by MarketplaceRepository.activeOrder.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()

  val session by SupabaseClient.session.collectAsState()
  var showInvoiceDialog by remember { mutableStateOf(false) }
  var isCancelling by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  var assignmentStatus by remember(activeOrder?.orderId) { mutableStateOf<String?>(null) }
  var otpDispatchResult by remember(activeOrder?.orderId) { mutableStateOf<CustomerOtpDispatchResult?>(null) }
  var otpLoading by remember { mutableStateOf(false) }
  var otpError by remember(activeOrder?.orderId) { mutableStateOf<String?>(null) }

  LaunchedEffect(activeOrder?.orderId, session?.user?.id) {
    val oid = activeOrder?.orderId ?: return@LaunchedEffect
    if (!SupabaseClient.isAuthenticated) {
      assignmentStatus = null
      return@LaunchedEffect
    }
    MarketplaceRepository.refreshOrdersIfAuthenticated()
    assignmentStatus = fetchCustomerAssignmentStatus(oid)
  }

  if (showInvoiceDialog && activeOrder != null) {
    InvoiceDialog(
      order = activeOrder!!,
      onDismiss = { showInvoiceDialog = false }
    )
  }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      JTBazarTopBar(
        title = "Order Status & Live Tracking",
        showBack = true,
        onBackClick = onBackClick,
        onCartClick = { /* cart */ },
        onAvatarClick = onAccountClick,
        cartCount = cartCount,
        currentLocation = currentLocation
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (activeOrder == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp)
        ) {
          Box(
            modifier = Modifier
              .size(76.dp)
              .clip(CircleShape)
              .background(JTPurpleContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
              contentDescription = null,
              tint = JTPurplePrimary,
              modifier = Modifier.size(38.dp)
            )
          }
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "No Active Orders",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = JTTextPrimary
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "You don't have any orders to track yet. Once you place an order with Cash on Delivery, you can track its live timeline, rider dispatch, and invoice here.",
            fontSize = 12.sp,
            color = JTTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
          Spacer(modifier = Modifier.height(20.dp))
          Button(
            onClick = onContinueShopping,
            colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Start Shopping", fontWeight = FontWeight.Bold)
          }
        }
      }
    } else {
      val activeOrder = activeOrder!!
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(bottom = 32.dp)
      ) {
      // 1. Verified Transaction Top Notice with Close 'X'
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = JTPurpleContainer.copy(alpha = 0.5f),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(24.dp)
                  .clip(CircleShape)
                  .background(JTPurplePrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.Shield,
                  contentDescription = null,
                  tint = JTPurplePrimary,
                  modifier = Modifier.size(14.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "VERIFIED TRANSACTION",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = JTPurplePrimary,
                  letterSpacing = 0.5.sp
                )
                Text(
                  text = "Order Status & Live Tracking",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTTextPrimary
                )
              }
            }

            IconButton(
              onClick = onContinueShopping,
              modifier = Modifier.size(26.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = JTTextSecondary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      // 2. Hero Success: Icon + Shukriya + Order Number + Due Amount
      item {
        Spacer(modifier = Modifier.height(10.dp))
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Purple check icon
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(JTPurplePrimary),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(36.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "Order Placed Successfully!",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = JTTextPrimary
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "Shukriya! Your order has been received and\nsent directly to the partner vendors.",
            fontSize = 12.sp,
            color = JTTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 16.sp
          )

          Spacer(modifier = Modifier.height(14.dp))

          // Order ID Copy Pill
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = JTSurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                  contentDescription = null,
                  tint = JTPurplePrimary,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Order #" + activeOrder.orderNumber,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  color = JTTextPrimary
                )
              }

              Surface(
                shape = RoundedCornerShape(6.dp),
                color = JTPurpleContainer,
                modifier = Modifier
                  .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Order Number", activeOrder.orderNumber)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied Order Number: #" + activeOrder.orderNumber, Toast.LENGTH_SHORT).show()
                  }
                  .testTag("copy_order_id_button")
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = JTPurplePrimary,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Copy",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = JTPurplePrimary
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Due Amount Banner
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFF9ED),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.Payments,
                  contentDescription = null,
                  tint = JTAmberDark,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Cash on Delivery (COD)",
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  color = JTTextPrimary
                )
              }

              Text(
                text = "Rs. ${String.format("%,d", activeOrder.grandTotal)} Due",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = JTAmberDark
              )
            }
          }
        }
      }

      // 3. Order Progress Card (Timeline)
      item {
        Spacer(modifier = Modifier.height(18.dp))
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.LocalShipping,
                  contentDescription = null,
                  tint = JTPurplePrimary,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Order Progress",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = JTTextPrimary
                )
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = JTPurpleContainer
              ) {
                Text(
                  text = activeOrder.statusHeader,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTPurplePrimary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Timeline Steps
            activeOrder.timeline.forEachIndexed { index, step ->
              TimelineRow(
                step = step,
                isLast = index == activeOrder.timeline.size - 1
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fresh & Kitchen Note at bottom
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = JTPurpleContainer.copy(alpha = 0.45f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top
              ) {
                Icon(
                  imageVector = Icons.Filled.SoupKitchen,
                  contentDescription = null,
                  tint = JTPurplePrimary,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Fresh & Kitchen Items: Delivery time slot will be verified directly via WhatsApp/Call prior to rider kitchen dispatch.",
                  fontSize = 10.sp,
                  color = JTTextSecondary,
                  lineHeight = 14.sp
                )
              }
            }
          }
        }
      }

      // 3A. Customer-only delivery OTP: emailed only when parcel is picked up; never rendered as plaintext OTP.
      item {
        val otpEligibility = evaluateDeliveryOtpEligibility(
          isAuthenticated = session != null && !session?.user?.id.isNullOrBlank(),
          orderStatus = activeOrder.orderStatus,
          assignmentStatus = assignmentStatus
        )
        val eligibilityHint = deliveryOtpEligibilityHint(otpEligibility)

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFF5F3FF),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDD6FE)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("customer_delivery_otp_card")
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = JTPurplePrimary)
              Spacer(Modifier.width(8.dp))
              Text("Secure delivery OTP", fontWeight = FontWeight.Bold, color = JTTextPrimary)
            }
            Text(
              "Once your JTBazar driver picks up the parcel, tap below to send a 6-digit verification OTP to your registered email. Share that email code only with the driver at your door.",
              fontSize = 11.sp,
              color = JTTextSecondary
            )

            otpDispatchResult?.let { dispatched ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFECFDF5),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("delivery_otp_email_sent_card")
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.MarkEmailRead, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(16.dp))
                    Text("OTP Sent to Registered Email", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF065F46))
                  }
                  Text(
                    text = if (!dispatched.maskedEmail.isNullOrBlank()) {
                      "A 6-digit verification code was sent to ${dispatched.maskedEmail}. Check your inbox or spam folder."
                    } else {
                      "A 6-digit verification code was sent to your registered email address."
                    },
                    fontSize = 11.sp,
                    color = Color(0xFF065F46)
                  )
                  Text(
                    text = "Valid for ${dispatched.expiresInMinutes} minutes. Do not share this code before the rider arrives.",
                    fontSize = 10.sp,
                    color = Color(0xFF047857)
                  )
                }
              }
            }

            if (eligibilityHint != null && otpDispatchResult == null) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFFBEB),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("delivery_otp_status_hint")
              ) {
                Text(
                  text = eligibilityHint,
                  color = Color(0xFF92400E),
                  fontSize = 11.sp,
                  modifier = Modifier.padding(10.dp)
                )
              }
            }

            otpError?.let { errText ->
              Text(
                text = errText,
                color = JTRed,
                fontSize = 11.sp,
                modifier = Modifier.testTag("delivery_otp_error_text")
              )
            }

            Button(
              enabled = !otpLoading && otpEligibility == CustomerDeliveryOtpEligibility.ELIGIBLE,
              onClick = {
                if (otpLoading) return@Button
                scope.launch {
                  otpLoading = true
                  otpError = null
                  MarketplaceRepository.refreshOrdersIfAuthenticated()
                  val latestAssignmentStatus = fetchCustomerAssignmentStatus(activeOrder.orderId)
                  if (latestAssignmentStatus != null) {
                    assignmentStatus = latestAssignmentStatus
                  }
                  val effectiveOrderStatus = MarketplaceRepository.orders.value
                    .firstOrNull { it.orderId == activeOrder.orderId }
                    ?.orderStatus
                    ?: activeOrder.orderStatus
                  val result = requestCustomerDeliveryOtp(
                    orderId = activeOrder.orderId,
                    orderStatus = effectiveOrderStatus,
                    assignmentStatus = latestAssignmentStatus ?: assignmentStatus
                  )
                  result.onSuccess { dispatched ->
                    otpDispatchResult = dispatched
                    otpError = null
                  }.onFailure { err ->
                    otpError = err.message ?: "Delivery OTP is available once the driver picks up your parcel."
                  }
                  otpLoading = false
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("generate_delivery_otp_button")
            ) {
              Text(
                when {
                  otpLoading -> "Sending OTP to email…"
                  otpDispatchResult == null -> "Send Delivery OTP to Email"
                  else -> "Resend Delivery OTP to Email"
                }
              )
            }
          }
        }
      }

      // 4. Destination Details Card
      item {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "DESTINATION DETAILS",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                color = JTTextPrimary,
                letterSpacing = 0.5.sp
              )
              Text(
                text = activeOrder.items.count { !it.vendorFulfillmentStatus.equals("cancelled", true) }.toString() + " Active Items · " + activeOrder.items.count { it.deliveryStatus.equals("delivered", true) }.toString() + " Delivered",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = JTPurplePrimary
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = JTSurfaceVariant,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
              ) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = JTPurplePrimary,
                    modifier = Modifier.size(16.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "${activeOrder.address.area}, ${activeOrder.address.city}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = JTTextPrimary
                  )
                  Text(
                    text = activeOrder.address.addressLine,
                    fontSize = 11.sp,
                    color = JTTextSecondary
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Filled.Phone,
                      contentDescription = null,
                      tint = JTTextSecondary,
                      modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = activeOrder.address.phone,
                      fontSize = 11.sp,
                      color = JTTextSecondary
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actual order lines returned by Supabase; no sample products.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              activeOrder.items.forEach { item ->
                val itemCancelled = item.vendorFulfillmentStatus.equals("cancelled", ignoreCase = true)
                val itemDelivered = item.deliveryStatus.equals("delivered", ignoreCase = true) || activeOrder.orderStatus.equals("DELIVERED", ignoreCase = true)
                val itemReady = item.vendorFulfillmentStatus.lowercase() in listOf("ready", "ready_for_pickup", "picked_up", "shipped")
                Row(
                  modifier = Modifier.fillMaxWidth().background(if (itemCancelled) Color(0xFFF3F4F6) else Color.White, RoundedCornerShape(10.dp)).padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(item.product.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (itemCancelled) JTTextSecondary else JTTextPrimary, textDecoration = if (itemCancelled) TextDecoration.LineThrough else TextDecoration.None)
                    Text(if (itemCancelled) "Cancelled by admin" else if (itemDelivered) "Delivered" else if (itemReady) "Ready" else "Pending", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (itemCancelled) JTTextSecondary else if (itemDelivered) JTEmerald else JTPurplePrimary)
                    Text("Qty: " + item.quantity.toString(), fontSize = 10.sp, color = JTTextSecondary)
                  }
                  Text("PKR " + String.format("%,d", item.totalPrice), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTPurplePrimary)
                }
              }
              if (activeOrder.items.isEmpty()) Text("Order items are not available yet.", fontSize = 11.sp, color = JTTextSecondary)
            }
          }
        }
      }

      // 5. Action Buttons (View Order Details & Invoice, Continue Shopping)
      item {
        Spacer(modifier = Modifier.height(16.dp))
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = { showInvoiceDialog = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = JTPurplePrimary,
              contentColor = Color.White
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(46.dp)
              .testTag("view_invoice_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "View Order Details & Invoice",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }

          if (activeOrder.orderStatus == "PENDING" || activeOrder.orderStatus == "CONFIRMED") {
            OutlinedButton(
              onClick = {
                if (isCancelling) return@OutlinedButton
                isCancelling = true
                val started = MarketplaceRepository.cancelOrder(activeOrder.orderId) { success ->
                  isCancelling = false
                  val message = if (success) {
                    "Order #" + activeOrder.orderNumber + " cancelled"
                  } else {
                    AppErrorMapper.defaultMessageFor(ErrorContext.ORDER_CANCEL)
                  }
                  Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                if (!started) isCancelling = false
              },
              enabled = !isCancelling,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = JTRed),
              border = androidx.compose.foundation.BorderStroke(1.dp, JTRed),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("cancel_order_button")
            ) {
              if (isCancelling) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = JTRed, strokeWidth = 2.dp)
              } else {
                Icon(
                  imageVector = Icons.Filled.Cancel,
                  contentDescription = null,
                  tint = JTRed,
                  modifier = Modifier.size(18.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isCancelling) "Cancelling..." else "Cancel Order",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Button(
            onClick = onContinueShopping,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = JTOrangeContainer,
              contentColor = JTOrangeDark
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(46.dp)
              .testTag("continue_shopping_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.Storefront,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Continue Shopping on JTBazar",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      // 6. Help With Order (WhatsApp support button)
      item {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = JTSurfaceVariant,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(JTAmberContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.HeadsetMic,
                  contentDescription = null,
                  tint = JTAmberDark,
                  modifier = Modifier.size(18.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Need help with this order?",
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  color = JTTextPrimary
                )
                Text(
                  text = "JTBazar central delivery team",
                  fontSize = 10.sp,
                  color = JTTextSecondary
                )
              }
            }

            OutlinedButton(
              onClick = {
                Toast.makeText(context, "Opening WhatsApp Support: +92 300 4821902", Toast.LENGTH_SHORT).show()
              },
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              modifier = Modifier.height(34.dp).testTag("whatsapp_help_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.Chat,
                contentDescription = null,
                tint = JTEmerald,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "WhatsApp",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JTTextPrimary
              )
            }
          }
        }
      }
      }
    }
  }
}

@Composable
private fun TimelineRow(
  step: com.example.data.model.OrderTimelineStep,
  isLast: Boolean
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top
  ) {
    // Indicator Column
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.width(24.dp)
    ) {
      when (step.state) {
        StepState.COMPLETED -> {
          Box(
            modifier = Modifier
              .size(18.dp)
              .clip(CircleShape)
              .background(JTPurplePrimary),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(12.dp)
            )
          }
        }
        StepState.IN_PROGRESS -> {
          Box(
            modifier = Modifier
              .size(18.dp)
              .clip(CircleShape)
              .border(2.dp, JTPurplePrimary, CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(JTPurplePrimary)
            )
          }
        }
        StepState.PENDING -> {
          Box(
            modifier = Modifier
              .size(14.dp)
              .clip(CircleShape)
              .background(JTBorder)
          )
        }
      }

      if (!isLast) {
        Box(
          modifier = Modifier
            .width(2.dp)
            .height(if (step.state == StepState.IN_PROGRESS) 52.dp else 42.dp)
            .background(if (step.state == StepState.COMPLETED) JTPurplePrimary else JTBorder)
        )
      }
    }

    Spacer(modifier = Modifier.width(10.dp))

    // Content
    Column(modifier = Modifier.weight(1f)) {
      if (step.state == StepState.IN_PROGRESS) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = JTSurfaceVariant,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = step.title,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  color = JTTextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(JTAmber)
                )
              }

              Text(
                text = step.timestamp,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = JTPurplePrimary
              )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = step.description,
              fontSize = 10.sp,
              color = JTTextSecondary,
              lineHeight = 14.sp
            )
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = step.title,
            fontWeight = if (step.state == StepState.COMPLETED) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            color = if (step.state == StepState.COMPLETED) JTTextPrimary else JTTextSecondary
          )
          Text(
            text = step.timestamp,
            fontSize = 10.sp,
            color = JTTextSecondary
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = step.description,
          fontSize = 10.sp,
          color = JTTextSecondary,
          lineHeight = 14.sp
        )
      }
    }
  }
}

@Composable
private fun OrderThumbItem(
  imageRes: Int,
  name: String,
  qty: String,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = JTSurfaceVariant,
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color.White),
        contentAlignment = Alignment.Center
      ) {
        Image(
          painter = painterResource(id = imageRes),
          contentDescription = name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = name,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        color = JTTextPrimary,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
      )
      Text(
        text = qty,
        fontSize = 9.sp,
        color = JTTextSecondary
      )
    }
  }
}

@Composable
private fun InvoiceDialog(
  order: OrderRecord,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary)
      ) {
        Text("Done")
      }
    },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = JTPurplePrimary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Order Invoice #" + order.orderNumber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text("Placed: ${order.date}", fontSize = 11.sp, color = JTTextSecondary)
        Text("Payment: Cash on Delivery (COD)", fontSize = 11.sp, color = JTTextSecondary)
        Text("Deliver To: ${order.address.addressLine}, ${order.address.area}", fontSize = 11.sp, color = JTTextSecondary)

        HorizontalDivider(color = JTBorder)

        order.items.forEach { item ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "${item.quantity}x ${item.product.title.split("(")[0]}",
              fontSize = 11.sp,
              color = JTTextPrimary,
              modifier = Modifier.weight(1f)
            )
            Text(
              text = "Rs. ${String.format("%,d", item.totalPrice)}",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = JTPurplePrimary
            )
          }
        }

        HorizontalDivider(color = JTBorder)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Subtotal", fontSize = 11.sp, color = JTTextSecondary)
          Text("Rs. ${String.format("%,d", order.subtotal)}", fontSize = 11.sp)
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Discount", fontSize = 11.sp, color = JTAmberDark)
          Text("- Rs. ${order.discount}", fontSize = 11.sp, color = JTEmerald)
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Courier Delivery", fontSize = 11.sp, color = JTTextSecondary)
          Text("Rs. ${order.deliveryFee}", fontSize = 11.sp)
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Grand Total", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
          Text("Rs. ${String.format("%,d", order.grandTotal)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = JTPurplePrimary)
        }
      }
    }
  )
}
