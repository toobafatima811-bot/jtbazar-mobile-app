package com.example.data.supabase

import android.util.Log
import com.example.localization.AppLanguageManager.tr
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class ErrorContext {
  PRODUCT_LOAD,
  CATEGORY_LOAD,
  SEARCH,
  CART_ADD,
  CART_UPDATE,
  WISHLIST,
  ADDRESS,
  ORDER_PLACE,
  ORDER_CANCEL,
  REVIEWS,
  SUPPORT,
  AUTH,
  STORE_SETTINGS,
  GENERAL
}

/**
 * Centralized customer-friendly error translator for JTBazar.
 * Never exposes SQL errors, PostgREST payloads, HTTP codes, stack traces, or raw exceptions.
 */
object AppErrorMapper {
  private const val TAG = "AppErrorMapper"

  private val technicalMarkers = listOf(
    "postgrest", "supabase", "postgres", "pgrst", "sqlstate", "select ", "insert into",
    "update ", "delete from", "constraint", "violates", "relation \"", "column \"",
    "syntax error", "json", "exception", "throwable", "stacktrace", "okhttp",
    "java.", "javax.", "kotlin.", "retrofit", "ktor", "timeoutexception",
    "unknownhostexception", "connectexception", "failed to connect", "econnrefused",
    "broken pipe", "reset by peer", "end of input", "expected begin_array",
    "{\"code\"", "{\"message\"", "\"details\"", "\"hint\"", "http/1.", "sslhandshake",
    "trust anchor", "cleartext", "chain validation"
  )

  fun toCustomerMessage(
    throwable: Throwable?,
    context: ErrorContext = ErrorContext.GENERAL,
    isOnline: Boolean? = NetworkConnectivity.currentOnlineState()
  ): String {
    if (throwable != null) {
      runCatching { Log.e(TAG, "Technical error in $context: ${throwable.message}", throwable) }
    }
    if (isOnline == false || isNetworkError(throwable)) {
      return tr(
        "No internet connection. Please check your connection and try again.",
        "انٹرنیٹ کنکشن دستیاب نہیں ہے۔ براہ کرم اپنا کنکشن چیک کریں اور دوبارہ کوشش کریں۔"
      )
    }
    if (isTimeoutError(throwable)) {
      return when (context) {
        ErrorContext.PRODUCT_LOAD, ErrorContext.SEARCH ->
          tr(
            "Could not load products because the connection timed out. Please try again.",
            "کنکشن کا وقت ختم ہونے کی وجہ سے مصنوعات لوڈ نہیں ہو سکیں۔ براہ کرم دوبارہ کوشش کریں۔"
          )
        ErrorContext.ORDER_PLACE ->
          tr(
            "The request took too long. Please check your order status before trying again.",
            "درخواست میں بہت زیادہ وقت لگ گیا۔ دوبارہ کوشش کرنے سے پہلے براہ کرم اپنے آرڈر کی صورتحال دیکھیں۔"
          )
        else -> tr(
          "The connection timed out. Please try again.",
          "کنکشن کا وقت ختم ہو گیا۔ براہ کرم دوبارہ کوشش کریں۔"
        )
      }
    }

    val raw = throwable?.message.orEmpty()
    val extractedServerMessage = extractEmbeddedErrorMessage(raw)
    val inspectText = listOfNotNull(extractedServerMessage, raw).joinToString(" ").lowercase()

    if (isVendorShopClosedMessage(inspectText)) {
      return tr(
        "This vendor shop is currently closed and is not accepting orders right now. Please check the shop's opening hours and try again later.",
        "یہ دکان اس وقت بند ہے اور فی الحال آرڈر قبول نہیں کر رہی۔ براہ کرم دکان کے کھلنے کے اوقات دیکھیں اور بعد میں دوبارہ کوشش کریں۔"
      )
    }
    if (inspectText.contains("jtbazar is currently closed") ||
      inspectText.contains("currently closed") ||
      inspectText.contains("not accepting new customer orders")
    ) {
      return tr(
        "JTBazar is currently closed and is not accepting new orders. Please try again during store hours.",
        "جے ٹی بازار اس وقت بند ہے اور نئے آرڈرز قبول نہیں کر رہا۔ براہ کرم اسٹور کے اوقات میں دوبارہ کوشش کریں۔"
      )
    }
    val lower = raw.lowercase()
    if (lower.contains("5 km food delivery") || lower.contains("current location pin")) {
      return tr(
        "Please share your current location pin so we can verify fresh food delivery availability.",
        "براہ کرم اپنا موجودہ لوکیشن پن شیئر کریں تاکہ ہم تازہ کھانے کی ترسیل کی دستیابی کی تصدیق کر سکیں۔"
      )
    }
    if (lower.contains("invalid login credentials")) {
      return tr(
        "Incorrect email or password. Please check and try again.",
        "غلط ای میل یا پاس ورڈ۔ براہ کرم چیک کریں اور دوبارہ کوشش کریں۔"
      )
    }
    if (lower.contains("email not confirmed")) {
      return tr(
        "Please verify your email address with the OTP code before signing in.",
        "سائن ان کرنے سے پہلے براہ کرم او ٹی پی کوڈ کے ذریعے اپنے ای میل ایڈریس کی تصدیق کریں۔"
      )
    }
    if (lower.contains("user already registered") || lower.contains("already been registered")) {
      return tr(
        "An account with this email already exists. Try signing in instead.",
        "اس ای میل پر پہلے سے ایک اکاؤنٹ موجود ہے۔ براہ کرم سائن ان کریں۔"
      )
    }
    if (lower.contains("rate limit") || lower.contains("too many requests") || lower.contains("429")) {
      return tr(
        "Too many attempts right now. Please wait a moment and try again.",
        "اس وقت بہت زیادہ کوششیں ہو چکی ہیں۔ براہ کرم تھوڑا انتظار کریں اور دوبارہ کوشش کریں۔"
      )
    }
    if (isAuthExpired(lower)) {
      return tr(
        "Your session has expired. Please sign in again.",
        "آپ کا سیشن ختم ہو چکا ہے۔ براہ کرم دوبارہ سائن ان کریں۔"
      )
    }
    if (isOutOfStockMessage(lower)) {
      return tr(
        "This item is currently out of stock or unavailable.",
        "یہ آئٹم اس وقت دستیاب نہیں ہے یا اسٹاک ختم ہو چکا ہے۔"
      )
    }
    if (isServerError(lower)) {
      return defaultMessageFor(context)
    }

    // Allow clean pre-validated domain/user messages only if completely free of technical markers.
    val cleaned = sanitizeMessage(raw, context)
    return cleaned
  }

  private fun extractEmbeddedErrorMessage(raw: String): String? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val jsonSlice = raw.substring(start, end + 1)
    return runCatching {
      val obj = org.json.JSONObject(jsonSlice)
      listOf("message", "details", "hint", "error_description", "error")
        .mapNotNull { key -> obj.optString(key, "").takeIf { it.isNotBlank() && it != "null" } }
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
    }.getOrNull()
  }

  internal fun isVendorShopClosedMessage(lower: String): Boolean {
    if (lower.isBlank()) return false
    if (lower.contains("jtbazar is currently closed") || lower.contains("جے ٹی بازار اس وقت بند ہے")) return false
    val mentionsShopOrVendor = lower.contains("vendor") ||
      lower.contains("shop") ||
      lower.contains("seller") ||
      lower.contains("storefront") ||
      lower.contains("opening hours") ||
      lower.contains("shop_hours") ||
      lower.contains("shop_is_open") ||
      lower.contains("enforce_vendor_daily_order_rules") ||
      lower.contains("دکان")
    val mentionsClosed = lower.contains("closed") ||
      lower.contains("outside") ||
      lower.contains("not open") ||
      lower.contains("not accepting") ||
      lower.contains("unavailable") ||
      lower.contains("بند")
    return mentionsShopOrVendor && mentionsClosed
  }

  fun sanitizeMessage(rawMessage: String?, context: ErrorContext = ErrorContext.GENERAL): String {
    val trimmed = rawMessage?.trim().orEmpty()
    if (trimmed.isBlank()) return defaultMessageFor(context)
    val lower = trimmed.lowercase()
    if (isNetworkText(lower)) {
      return tr(
        "No internet connection. Please check your connection and try again.",
        "انٹرنیٹ کنکشن دستیاب نہیں ہے۔ براہ کرم اپنا کنکشن چیک کریں اور دوبارہ کوشش کریں۔"
      )
    }
    if (isTimeoutText(lower)) {
      return tr(
        "The connection timed out. Please try again.",
        "کنکشن کا وقت ختم ہو گیا۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    }
    if (isAuthExpired(lower)) {
      return tr(
        "Your session has expired. Please sign in again.",
        "آپ کا سیشن ختم ہو چکا ہے۔ براہ کرم دوبارہ سائن ان کریں۔"
      )
    }
    if (containsTechnicalLeak(lower)) {
      runCatching { Log.w(TAG, "Suppressed technical error string for $context: $trimmed") }
      return defaultMessageFor(context)
    }
    return trimmed.take(180)
  }

  fun defaultMessageFor(context: ErrorContext): String = when (context) {
    ErrorContext.PRODUCT_LOAD, ErrorContext.SEARCH ->
      tr(
        "Could not load products. Please try again.",
        "مصنوعات لوڈ نہیں ہو سکیں۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.CATEGORY_LOAD ->
      tr(
        "Could not load categories. Please try again.",
        "اقسام لوڈ نہیں ہو سکیں۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.CART_ADD ->
      tr(
        "Your item could not be added to the cart. Please try again.",
        "آپ کا آئٹم کارٹ میں شامل نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.CART_UPDATE ->
      tr(
        "Your cart could not be updated. Please try again.",
        "آپ کا کارٹ اپ ڈیٹ نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.WISHLIST ->
      tr(
        "Could not update your wishlist. Please try again.",
        "آپ کی پسندیدہ فہرست اپ ڈیٹ نہیں ہو سکی۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.ADDRESS ->
      tr(
        "Your delivery address could not be saved. Please try again.",
        "آپ کا ترسیلی پتہ محفوظ نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.ORDER_PLACE ->
      tr(
        "Your order could not be placed. Please check your details and try again.",
        "آپ کا آرڈر مکمل نہیں ہو سکا۔ براہ کرم اپنی تفصیلات چیک کریں اور دوبارہ کوشش کریں۔"
      )
    ErrorContext.ORDER_CANCEL ->
      tr(
        "Your order could not be cancelled right now. Please try again.",
        "آپ کا آرڈر اس وقت منسوخ نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.REVIEWS ->
      tr(
        "Could not submit your review right now. Please try again.",
        "اس وقت آپ کا تبصرہ جمع نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.SUPPORT ->
      tr(
        "Could not send your support request. Please try again.",
        "آپ کی مدد کی درخواست نہیں بھیجی جا سکی۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.AUTH ->
      tr(
        "Authentication could not be completed. Please try again.",
        "تصدیق مکمل نہیں ہو سکی۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.STORE_SETTINGS ->
      tr(
        "Store information could not be refreshed. Please try again.",
        "اسٹور کی معلومات تازہ نہیں ہو سکیں۔ براہ کرم دوبارہ کوشش کریں۔"
      )
    ErrorContext.GENERAL ->
      tr(
        "Something went wrong. Please try again.",
        "کچھ غلط ہو گیا ہے۔ براہ کرم دوبارہ کوشش کریں۔"
      )
  }

  private fun isNetworkError(t: Throwable?): Boolean {
    var current = t
    while (current != null) {
      if (current is UnknownHostException ||
        current is ConnectException ||
        current is NoRouteToHostException ||
        current is SSLException
      ) {
        return true
      }
      val msg = current.message?.lowercase().orEmpty()
      if (isNetworkText(msg)) return true
      current = current.cause
    }
    return false
  }

  private fun isTimeoutError(t: Throwable?): Boolean {
    var current = t
    while (current != null) {
      if (current is SocketTimeoutException) return true
      val msg = current.message?.lowercase().orEmpty()
      if (isTimeoutText(msg)) return true
      current = current.cause
    }
    return false
  }

  private fun isNetworkText(lower: String): Boolean {
    return lower.contains("unable to resolve host") ||
      lower.contains("failed to connect") ||
      lower.contains("network is unreachable") ||
      lower.contains("no address associated") ||
      lower.contains("connection reset") ||
      lower.contains("connection refused") ||
      lower.contains("software caused connection abort")
  }

  private fun isTimeoutText(lower: String): Boolean {
    return lower.contains("timed out") || lower.contains("timeout")
  }

  private fun isAuthExpired(lower: String): Boolean {
    return lower.contains("jwt expired") ||
      lower.contains("invalid jwt") ||
      lower.contains("error 401") ||
      lower.contains("http 401") ||
      lower.contains("token is expired") ||
      lower.contains("refresh_token_not_found")
  }

  private fun isOutOfStockMessage(lower: String): Boolean {
    return lower.contains("out of stock") ||
      lower.contains("insufficient stock") ||
      lower.contains("not available") ||
      lower.contains("unavailable")
  }

  private fun isServerError(lower: String): Boolean {
    return Regex("\\berror\\s+5\\d{2}\\b").containsMatchIn(lower) ||
      Regex("\\bhttp\\s+5\\d{2}\\b").containsMatchIn(lower) ||
      lower.contains("error 400") ||
      lower.contains("error 403") ||
      lower.contains("error 404")
  }

  private fun containsTechnicalLeak(lower: String): Boolean {
    if (technicalMarkers.any { lower.contains(it) }) return true
    if (Regex("\\b(4\\d{2}|5\\d{2})\\s*:").containsMatchIn(lower)) return true
    if (Regex("https?://\\S+").containsMatchIn(lower)) return true
    return false
  }
}
