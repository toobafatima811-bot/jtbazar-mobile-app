package com.example.ui.screens

import com.example.data.supabase.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject

enum class CustomerDeliveryOtpEligibility {
  ELIGIBLE,
  NOT_SIGNED_IN,
  ORDER_TERMINAL,
  MISSING_DRIVER_ASSIGNMENT,
  PARCEL_NOT_PICKED_UP
}

enum class CustomerOtpErrorReason {
  UNAUTHENTICATED,
  WRONG_CUSTOMER,
  MISSING_ASSIGNMENT,
  NOT_PICKED_UP,
  EMAIL_DELIVERY_FAILED,
  RATE_LIMITED,
  GENERIC
}

data class CustomerOtpDispatchResult(
  val dispatched: Boolean,
  val maskedEmail: String?,
  val expiresInMinutes: Int = 30
)

fun maskEmailForDisplay(rawEmail: String?): String? {
  val email = rawEmail?.trim()?.takeIf { it.contains("@") } ?: return null
  val parts = email.split("@", limit = 2)
  val local = parts[0]
  val domain = parts.getOrNull(1).orEmpty()
  if (local.isBlank() || domain.isBlank()) return null
  val visiblePrefix = when {
    local.length <= 1 -> local
    local.length == 2 -> local.take(1)
    else -> local.take(2)
  }
  return "$visiblePrefix***@$domain"
}

fun evaluateDeliveryOtpEligibility(
  isAuthenticated: Boolean,
  orderStatus: String,
  assignmentStatus: String?
): CustomerDeliveryOtpEligibility {
  if (!isAuthenticated) return CustomerDeliveryOtpEligibility.NOT_SIGNED_IN
  val normalizedOrder = orderStatus.trim().uppercase()
  if (normalizedOrder in setOf("DELIVERED", "CANCELLED", "NOT_DELIVERED")) {
    return CustomerDeliveryOtpEligibility.ORDER_TERMINAL
  }
  val normalizedAssignment = assignmentStatus?.trim()?.lowercase()
  if (normalizedAssignment != null) {
    return when (normalizedAssignment) {
      "picked_up", "in_transit" -> CustomerDeliveryOtpEligibility.ELIGIBLE
      "none", "unassigned", "" -> CustomerDeliveryOtpEligibility.MISSING_DRIVER_ASSIGNMENT
      "delivered", "cancelled", "failed" -> CustomerDeliveryOtpEligibility.ORDER_TERMINAL
      else -> CustomerDeliveryOtpEligibility.PARCEL_NOT_PICKED_UP
    }
  }
  // When direct driver_order_assignments read is hidden by RLS or unavailable,
  // JTBazar orders move to SHIPPED when picked up by the assigned driver.
  return when (normalizedOrder) {
    "SHIPPED" -> CustomerDeliveryOtpEligibility.ELIGIBLE
    "PENDING", "CONFIRMED", "PROCESSING", "READY_FOR_PICKUP" -> CustomerDeliveryOtpEligibility.PARCEL_NOT_PICKED_UP
    else -> CustomerDeliveryOtpEligibility.PARCEL_NOT_PICKED_UP
  }
}

fun deliveryOtpEligibilityHint(eligibility: CustomerDeliveryOtpEligibility): String? = when (eligibility) {
  CustomerDeliveryOtpEligibility.ELIGIBLE -> null
  CustomerDeliveryOtpEligibility.NOT_SIGNED_IN ->
    "Sign in with the customer account that placed this order to request a delivery OTP."
  CustomerDeliveryOtpEligibility.ORDER_TERMINAL ->
    "Delivery OTP is no longer needed because this order is closed."
  CustomerDeliveryOtpEligibility.MISSING_DRIVER_ASSIGNMENT ->
    "No driver has been assigned to this order yet. You can request the OTP once a driver picks up your parcel."
  CustomerDeliveryOtpEligibility.PARCEL_NOT_PICKED_UP ->
    "Your delivery OTP unlocks once the assigned driver picks up your parcel. It will be sent to your registered email."
}

/**
 * Parses `customer_generate_delivery_otp` output WITHOUT ever returning, storing, or exposing
 * any plaintext OTP code in memory/UI. Only email dispatch status, masked email, and TTL are kept.
 */
fun parseCustomerOtpDispatchResponse(
  rawBody: String,
  registeredEmail: String?
): CustomerOtpDispatchResult {
  val trimmed = rawBody.trim()
  if (trimmed.equals("false", ignoreCase = true)) {
    throw IllegalStateException("Delivery OTP email could not be dispatched right now.")
  }
  if (trimmed.startsWith("{")) {
    val obj = JSONObject(trimmed)
    if (obj.has("sent") && !obj.optBoolean("sent", true)) {
      val serverMsg = obj.optCleanString("error").ifBlank { obj.optCleanString("message") }
      throw IllegalStateException(serverMsg.ifBlank { "Delivery OTP email could not be sent." })
    }
    val payloadEmail = obj.optCleanString("email")
      .ifBlank { obj.optCleanString("recipient_email") }
      .ifBlank { obj.optCleanString("masked_email") }
      .takeIf { it.isNotBlank() }
    val ttlMinutes = obj.optInt("expires_in_minutes", 30).coerceIn(5, 120)
    val displayEmail = payloadEmail?.let { if (it.contains("***")) it else maskEmailForDisplay(it) }
      ?: maskEmailForDisplay(registeredEmail)
    return CustomerOtpDispatchResult(
      dispatched = true,
      maskedEmail = displayEmail,
      expiresInMinutes = ttlMinutes
    )
  }
  return CustomerOtpDispatchResult(
    dispatched = true,
    maskedEmail = maskEmailForDisplay(registeredEmail),
    expiresInMinutes = 30
  )
}

fun mapCustomerDeliveryOtpError(
  throwable: Throwable?,
  orderStatus: String? = null,
  assignmentStatus: String? = null
): Pair<CustomerOtpErrorReason, String> {
  val raw = throwable?.message.orEmpty()
  val jsonMsg = extractDriverFriendlyError(throwable, "")
  val combined = "$raw $jsonMsg".lowercase()
  val normalizedOrder = orderStatus?.trim()?.uppercase().orEmpty()
  val normalizedAssign = assignmentStatus?.trim()?.lowercase()

  return when {
    combined.contains("authentication required") ||
      combined.contains("jwt") ||
      combined.contains("not authenticated") ||
      combined.contains("error 401") ||
      combined.contains("please sign in") -> {
      CustomerOtpErrorReason.UNAUTHENTICATED to
        "Please sign in with the customer account that placed this order."
    }

    combined.contains("wrong customer") ||
      combined.contains("not your order") ||
      combined.contains("belongs to another") ||
      combined.contains("not the owner") ||
      combined.contains("unauthorized order") -> {
      CustomerOtpErrorReason.WRONG_CUSTOMER to
        "This order belongs to a different account. Sign in with the customer account that placed the order."
    }

    combined.contains("no driver") ||
      combined.contains("assignment not found") ||
      combined.contains("not assigned") ||
      combined.contains("missing driver") -> {
      CustomerOtpErrorReason.MISSING_ASSIGNMENT to
        "No driver is assigned to this order yet. Please wait until a driver picks up your parcel."
    }

    combined.contains("not picked up") ||
      combined.contains("has not picked up") ||
      combined.contains("picked_up") ||
      combined.contains("in transit") ||
      combined.contains("must pick up") -> {
      CustomerOtpErrorReason.NOT_PICKED_UP to
        "The driver has not picked up your parcel yet. You can request the OTP once pickup is confirmed."
    }

    combined.contains("delivery not found for this customer") ||
      combined.contains("42501") ||
      combined.contains("error 403") -> {
      when {
        normalizedAssign == "none" -> {
          CustomerOtpErrorReason.MISSING_ASSIGNMENT to
            "No driver is assigned to this order yet. You can send the delivery OTP once a driver picks up your parcel."
        }
        normalizedAssign != null && normalizedAssign !in setOf("picked_up", "in_transit") -> {
          CustomerOtpErrorReason.NOT_PICKED_UP to
            "Your parcel has not been picked up by the driver yet. OTP becomes available after pickup."
        }
        normalizedOrder in setOf("PENDING", "CONFIRMED", "PROCESSING", "READY_FOR_PICKUP") -> {
          CustomerOtpErrorReason.NOT_PICKED_UP to
            "No picked-up driver delivery is active yet. Please wait until your driver picks up the parcel."
        }
        else -> {
          CustomerOtpErrorReason.WRONG_CUSTOMER to
            "We could not verify an active picked-up delivery for your account on this order. Please make sure you are signed in with the account that placed it."
        }
      }
    }

    combined.contains("email") ||
      combined.contains("smtp") ||
      combined.contains("mail") ||
      combined.contains("recipient") -> {
      CustomerOtpErrorReason.EMAIL_DELIVERY_FAILED to
        "We could not send the OTP to your registered email. Verify your account email address and try again."
    }

    combined.contains("rate limit") ||
      combined.contains("too many") ||
      combined.contains("wait") ||
      combined.contains("error 429") -> {
      CustomerOtpErrorReason.RATE_LIMITED to
        "Please wait a moment before requesting another delivery OTP."
    }

    else -> {
      CustomerOtpErrorReason.GENERIC to
        "Could not generate delivery OTP right now. Please refresh your order status and try again."
    }
  }
}

suspend fun fetchCustomerAssignmentStatus(orderId: String): String? {
  if (!SupabaseClient.isAuthenticated || orderId.isBlank()) return null
  val res = SupabaseClient.get(
    "driver_order_assignments?order_id=eq.$orderId&select=id,status&order=updated_at.desc&limit=1"
  )
  val body = res.getOrNull() ?: return null
  return runCatching {
    val arr = JSONArray(body)
    if (arr.length() == 0) "none"
    else arr.getJSONObject(0).optCleanString("status").lowercase().ifBlank { null }
  }.getOrNull()
}

suspend fun requestCustomerDeliveryOtp(
  orderId: String,
  orderStatus: String,
  assignmentStatus: String?
): Result<CustomerOtpDispatchResult> {
  val session = SupabaseClient.session.value
  val uid = session?.user?.id?.trim()
  val token = session?.accessToken?.trim()
  if (session == null || uid.isNullOrBlank() || token.isNullOrBlank()) {
    val (_, msg) = mapCustomerDeliveryOtpError(
      IllegalStateException("Authentication required"),
      orderStatus,
      assignmentStatus
    )
    return Result.failure(IllegalStateException(msg))
  }

  val eligibility = evaluateDeliveryOtpEligibility(true, orderStatus, assignmentStatus)
  if (eligibility != CustomerDeliveryOtpEligibility.ELIGIBLE) {
    val hint = deliveryOtpEligibilityHint(eligibility)
      ?: "Delivery OTP is available after your parcel is picked up by the driver."
    return Result.failure(IllegalStateException(hint))
  }

  val registeredEmail = session.user.email?.trim()?.takeIf { it.isNotBlank() }
    ?: return Result.failure(
      IllegalStateException("Your account does not have a registered email address to receive the OTP.")
    )

  return try {
    val raw = SupabaseClient.rpc(
      functionName = "customer_generate_delivery_otp",
      paramsJson = JSONObject().put("p_order_id", orderId).toString(),
      requireAuth = true
    ).getOrThrow()
    Result.success(parseCustomerOtpDispatchResponse(raw, registeredEmail))
  } catch (e: Exception) {
    val (_, friendlyMessage) = mapCustomerDeliveryOtpError(e, orderStatus, assignmentStatus)
    Result.failure(IllegalStateException(friendlyMessage))
  }
}
