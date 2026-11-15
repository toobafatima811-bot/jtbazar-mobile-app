package com.example.data.supabase

import android.util.Log
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
      return "Internet connection nahi mil raha. Please check your connection and try again."
    }
    if (isTimeoutError(throwable)) {
      return when (context) {
        ErrorContext.PRODUCT_LOAD, ErrorContext.SEARCH ->
          "Products load nahi ho sake because the connection timed out. Please try again."
        ErrorContext.ORDER_PLACE ->
          "The request took too long. Please check your order status before trying again."
        else -> "The connection timed out. Please try again."
      }
    }

    val raw = throwable?.message.orEmpty()
    val lower = raw.lowercase()

    if (lower.contains("currently closed") || lower.contains("not accepting new customer orders")) {
      return "JTBazar is currently closed and is not accepting new orders. Please try again during store hours."
    }
    if (lower.contains("5 km food delivery") || lower.contains("current location pin")) {
      return "Please share your current location pin so we can verify fresh food delivery availability."
    }
    if (lower.contains("invalid login credentials")) {
      return "Incorrect email or password. Please check and try again."
    }
    if (lower.contains("email not confirmed")) {
      return "Please verify your email address with the OTP code before signing in."
    }
    if (lower.contains("user already registered") || lower.contains("already been registered")) {
      return "An account with this email already exists. Try signing in instead."
    }
    if (lower.contains("rate limit") || lower.contains("too many requests") || lower.contains("429")) {
      return "Too many attempts right now. Please wait a moment and try again."
    }
    if (isAuthExpired(lower)) {
      return if (context == ErrorContext.SUPPORT || context == ErrorContext.REVIEWS || context == ErrorContext.ORDER_PLACE) {
        "Your session has expired. Please sign in again."
      } else {
        "Your session has expired. Please sign in again."
      }
    }
    if (isOutOfStockMessage(lower)) {
      return "This item is currently out of stock or unavailable."
    }
    if (isServerError(lower)) {
      return defaultMessageFor(context)
    }

    // Allow clean pre-validated domain/user messages only if completely free of technical markers.
    val cleaned = sanitizeMessage(raw, context)
    return cleaned
  }

  fun sanitizeMessage(rawMessage: String?, context: ErrorContext = ErrorContext.GENERAL): String {
    val trimmed = rawMessage?.trim().orEmpty()
    if (trimmed.isBlank()) return defaultMessageFor(context)
    val lower = trimmed.lowercase()
    if (isNetworkText(lower)) {
      return "Internet connection nahi mil raha. Please check your connection and try again."
    }
    if (isTimeoutText(lower)) {
      return "The connection timed out. Please try again."
    }
    if (isAuthExpired(lower)) {
      return "Your session has expired. Please sign in again."
    }
    if (containsTechnicalLeak(lower)) {
      runCatching { Log.w(TAG, "Suppressed technical error string for $context: $trimmed") }
      return defaultMessageFor(context)
    }
    return trimmed.take(180)
  }

  fun defaultMessageFor(context: ErrorContext): String = when (context) {
    ErrorContext.PRODUCT_LOAD, ErrorContext.SEARCH ->
      "Products load nahi ho sake. Please try again."
    ErrorContext.CATEGORY_LOAD ->
      "Categories load nahi ho sakin. Please try again."
    ErrorContext.CART_ADD ->
      "Your item could not be added to the cart. Please try again."
    ErrorContext.CART_UPDATE ->
      "Your cart could not be updated. Please try again."
    ErrorContext.WISHLIST ->
      "Could not update your wishlist. Please try again."
    ErrorContext.ADDRESS ->
      "Your delivery address could not be saved. Please try again."
    ErrorContext.ORDER_PLACE ->
      "Your order could not be placed. Please check your details and try again."
    ErrorContext.ORDER_CANCEL ->
      "Your order could not be cancelled right now. Please try again."
    ErrorContext.REVIEWS ->
      "Could not submit your review right now. Please try again."
    ErrorContext.SUPPORT ->
      "Could not send your support request. Please try again."
    ErrorContext.AUTH ->
      "Authentication could not be completed. Please try again."
    ErrorContext.STORE_SETTINGS ->
      "Store information could not be refreshed. Please try again."
    ErrorContext.GENERAL ->
      "Something went wrong. Please try again."
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
