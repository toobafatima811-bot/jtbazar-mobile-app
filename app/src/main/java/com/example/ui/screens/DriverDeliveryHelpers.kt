package com.example.ui.screens

import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import org.json.JSONObject

internal const val MAX_DRIVER_IMAGE_BYTES = 8 * 1024 * 1024

internal data class ValidatedProofImage(
  val mimeType: String,
  val extension: String,
  val bytes: ByteArray
)

internal fun JSONObject.optCleanString(key: String): String {
  if (isNull(key)) return ""
  val raw = optString(key, "").trim()
  return if (raw.equals("null", ignoreCase = true)) "" else raw
}

internal fun isManualOrderDelivery(delivery: JSONObject): Boolean {
  return delivery.optCleanString("order_type").equals("manual", ignoreCase = true)
}

internal fun driverDeliveryRpcName(delivery: JSONObject, normalRpc: String, manualRpc: String): String {
  return if (isManualOrderDelivery(delivery)) manualRpc else normalRpc
}

internal fun hasDriverDeliveryAccess(
  isAuthenticated: Boolean,
  accessEnabled: Boolean,
  applicationStatus: String?
): Boolean {
  return isAuthenticated &&
    accessEnabled &&
    applicationStatus?.trim().equals("approved", ignoreCase = true)
}

internal fun isDeliveryOtpVerified(delivery: JSONObject): Boolean {
  val boolKeys = listOf(
    "otp_verified",
    "delivery_otp_verified",
    "is_otp_verified",
    "otp_confirmed",
    "customer_otp_verified"
  )
  if (boolKeys.any { delivery.optBoolean(it, false) }) return true

  val timestampKeys = listOf(
    "otp_verified_at",
    "delivery_otp_verified_at",
    "otp_confirmed_at",
    "verified_at"
  )
  if (timestampKeys.any { delivery.optCleanString(it).isNotBlank() }) return true

  val status = delivery.optCleanString("delivery_otp_status")
    .ifBlank { delivery.optCleanString("otp_status") }
  return status.equals("verified", ignoreCase = true)
}

internal fun hasDeliveryProofSaved(delivery: JSONObject): Boolean {
  return delivery.optCleanString("delivery_proof_path").isNotBlank()
}

internal fun canCompleteDelivery(
  assignmentStatus: String,
  otpVerified: Boolean,
  proofSaved: Boolean,
  isBusy: Boolean = false
): Boolean {
  return !isBusy &&
    assignmentStatus.trim().equals("picked_up", ignoreCase = true) &&
    otpVerified &&
    proofSaved
}

internal fun missingCompletionStepsMessage(
  otpVerified: Boolean,
  proofSaved: Boolean
): String? = when {
  !otpVerified && !proofSaved ->
    "Incomplete steps: Verify customer OTP and upload a delivery photo proof before completing delivery."
  !otpVerified ->
    "Incomplete step: Verify customer OTP before completing delivery."
  !proofSaved ->
    "Incomplete step: Upload a delivery photo proof before completing delivery."
  else -> null
}

internal fun buildDeliveryProofPath(
  userId: String,
  assignmentId: String,
  extension: String,
  timestampMs: Long = System.currentTimeMillis()
): String {
  val cleanUid = userId.trim()
  val cleanAssignment = assignmentId.trim()
  val cleanExt = extension.trim().lowercase().ifBlank { "jpg" }
  return "$cleanUid/delivery_proofs/${cleanAssignment}_${timestampMs}.$cleanExt"
}

internal fun detectImageMimeFromMagicBytes(bytes: ByteArray): String? {
  if (bytes.size >= 3 &&
    bytes[0] == 0xFF.toByte() &&
    bytes[1] == 0xD8.toByte() &&
    bytes[2] == 0xFF.toByte()
  ) {
    return "image/jpeg"
  }
  if (bytes.size >= 8 &&
    bytes[0] == 0x89.toByte() &&
    bytes[1] == 0x50.toByte() &&
    bytes[2] == 0x4E.toByte() &&
    bytes[3] == 0x47.toByte()
  ) {
    return "image/png"
  }
  if (bytes.size >= 12 &&
    bytes[0] == 'R'.code.toByte() &&
    bytes[1] == 'I'.code.toByte() &&
    bytes[2] == 'F'.code.toByte() &&
    bytes[3] == 'F'.code.toByte() &&
    bytes[8] == 'W'.code.toByte() &&
    bytes[9] == 'E'.code.toByte() &&
    bytes[10] == 'B'.code.toByte() &&
    bytes[11] == 'P'.code.toByte()
  ) {
    return "image/webp"
  }
  return null
}

internal fun validateDeliveryProofImage(
  bytes: ByteArray,
  rawMimeType: String?
): ValidatedProofImage {
  if (bytes.isEmpty()) {
    throw IllegalArgumentException("Selected delivery photo is empty. Please choose another image.")
  }

  val normalizedMime = rawMimeType?.trim()?.lowercase()?.substringBefore(';').orEmpty()
  val resolvedMime = when {
    normalizedMime == "image/jpeg" || normalizedMime == "image/jpg" -> "image/jpeg"
    normalizedMime == "image/png" -> "image/png"
    normalizedMime == "image/webp" -> "image/webp"
    normalizedMime.startsWith("image/") -> normalizedMime
    normalizedMime.isBlank() || normalizedMime == "application/octet-stream" ->
      detectImageMimeFromMagicBytes(bytes)
        ?: throw IllegalArgumentException("Choose an image file (JPEG, PNG, or WebP) for delivery proof.")
    else ->
      throw IllegalArgumentException("Choose an image file (JPEG, PNG, or WebP) for delivery proof.")
  }

  if (bytes.size > MAX_DRIVER_IMAGE_BYTES) {
    throw IllegalArgumentException("Choose a delivery photo up to 8 MB.")
  }

  val extension = when (resolvedMime) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    else -> "jpg"
  }
  return ValidatedProofImage(mimeType = resolvedMime, extension = extension, bytes = bytes)
}

internal fun extractDriverRejectionReason(application: JSONObject?): String {
  if (application == null) return ""
  return listOf("admin_note", "rejection_reason", "rejected_reason", "review_note")
    .firstNotNullOfOrNull { key -> application.optCleanString(key).takeIf { it.isNotBlank() } }
    .orEmpty()
}

internal fun extractDriverFriendlyError(
  throwable: Throwable?,
  fallback: String = "Something went wrong. Please try again."
): String {
  val raw = throwable?.message?.trim().orEmpty()
  if (raw.isBlank()) return AppErrorMapper.toCustomerMessage(throwable, ErrorContext.GENERAL)

  // Extract PostgREST / Supabase JSON "message" field when wrapped inside HTTP error strings.
  val jsonStart = raw.indexOf('{')
  val jsonEnd = raw.lastIndexOf('}')
  if (jsonStart >= 0 && jsonEnd > jsonStart) {
    val jsonSlice = raw.substring(jsonStart, jsonEnd + 1)
    val parsedMsg = runCatching {
      JSONObject(jsonSlice).optCleanString("message")
    }.getOrNull()
    if (!parsedMsg.isNullOrBlank()) {
      return AppErrorMapper.sanitizeMessage(parsedMsg, ErrorContext.GENERAL)
    }
  }

  val translated = AppErrorMapper.toCustomerMessage(throwable, ErrorContext.GENERAL)
  return if (translated == AppErrorMapper.defaultMessageFor(ErrorContext.GENERAL) && fallback.isNotBlank()) {
    fallback
  } else {
    translated
  }
}

internal fun markAssignmentOtpVerifiedInList(
  deliveries: List<JSONObject>,
  assignmentId: String
): List<JSONObject> {
  return deliveries.map { item ->
    if (item.optCleanString("assignment_id") == assignmentId) {
      JSONObject(item.toString()).apply {
        put("otp_verified", true)
        put("delivery_otp_verified", true)
      }
    } else {
      item
    }
  }
}

internal fun updateAssignmentProofPathInList(
  deliveries: List<JSONObject>,
  assignmentId: String,
  storagePath: String
): List<JSONObject> {
  return deliveries.map { item ->
    if (item.optCleanString("assignment_id") == assignmentId) {
      JSONObject(item.toString()).apply {
        put("delivery_proof_path", storagePath)
      }
    } else {
      item
    }
  }
}
