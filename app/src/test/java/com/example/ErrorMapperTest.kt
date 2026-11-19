package com.example

import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.NetworkConnectivity
import com.example.localization.AppLanguage
import com.example.localization.AppLanguageManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMapperTest {

  private val forbiddenTechnicalTerms = listOf(
    "sqlstate",
    "postgrest",
    "postgres",
    "pgrst",
    "{\"code\"",
    "violates",
    "foreign key",
    "relation \"",
    "java.",
    "exception",
    "http/1.",
    "https://",
    "500",
    "401"
  )

  @After
  fun tearDown() {
    NetworkConnectivity.setOnlineOverrideForTest(null)
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
  }

  private fun assertCustomerSafe(message: String) {
    assertTrue("Message must not be blank", message.isNotBlank())
    val lower = message.lowercase()
    forbiddenTechnicalTerms.forEach { marker ->
      assertFalse("Customer message leaked technical term '$marker': $message", lower.contains(marker))
    }
  }

  @Test
  fun sqlAndPostgrestPayloadErrorsNeverLeakToCustomers() {
    val sqlError = IOException("PostgREST GET error 500: {\"code\":\"42P01\",\"details\":\"relation \\\"products\\\" does not exist\",\"hint\":null,\"message\":\"SQLSTATE 42P01\"}")
    val mapped = AppErrorMapper.toCustomerMessage(sqlError, ErrorContext.PRODUCT_LOAD, isOnline = true)

    assertCustomerSafe(mapped)
    assertEquals("Could not load products. Please try again.", mapped)

    val fkError = IOException("violates foreign key constraint cart_items_product_id_fkey on table cart_items")
    val cartMapped = AppErrorMapper.toCustomerMessage(fkError, ErrorContext.CART_ADD, isOnline = true)
    assertCustomerSafe(cartMapped)
    assertEquals("Your item could not be added to the cart. Please try again.", cartMapped)

    val orderMapped = AppErrorMapper.sanitizeMessage("PostgREST RPC error 500: {\"code\":\"23505\",\"message\":\"duplicate key\"}", ErrorContext.ORDER_PLACE)
    assertCustomerSafe(orderMapped)
    assertEquals("Your order could not be placed. Please check your details and try again.", orderMapped)
  }

  @Test
  fun networkAndOfflineFailuresMapToClearConnectionPrompt() {
    val dnsError = UnknownHostException("Unable to resolve host \"xrrhbnnzjqkqnqdvayjw.supabase.co\": No address associated with hostname")
    val msgOnlineCheck = AppErrorMapper.toCustomerMessage(dnsError, ErrorContext.PRODUCT_LOAD, isOnline = true)
    assertCustomerSafe(msgOnlineCheck)
    assertEquals("No internet connection. Please check your connection and try again.", msgOnlineCheck)

    val offlineMsg = AppErrorMapper.toCustomerMessage(IOException("Something failed"), ErrorContext.ORDER_PLACE, isOnline = false)
    assertCustomerSafe(offlineMsg)
    assertEquals("No internet connection. Please check your connection and try again.", offlineMsg)
  }

  @Test
  fun timeoutAndAuthExpiryMapToAppropriateMessages() {
    val timeoutMsg = AppErrorMapper.toCustomerMessage(SocketTimeoutException("Read timed out"), ErrorContext.PRODUCT_LOAD, isOnline = true)
    assertCustomerSafe(timeoutMsg)
    assertTrue(timeoutMsg.contains("timed out", ignoreCase = true))

    val jwtExpired = AppErrorMapper.toCustomerMessage(IOException("Error 401: JWT expired"), ErrorContext.ORDER_PLACE, isOnline = true)
    assertCustomerSafe(jwtExpired)
    assertEquals("Your session has expired. Please sign in again.", jwtExpired)
  }

  @Test
  fun domainMessagesArePreservedWhileAllContextsProvideFriendlyDefaults() {
    val closedStore = AppErrorMapper.toCustomerMessage(
      IOException("JTBazar is currently closed and is not accepting new customer orders."),
      ErrorContext.ORDER_PLACE,
      isOnline = true
    )
    assertCustomerSafe(closedStore)
    assertTrue(closedStore.contains("currently closed"))

    ErrorContext.entries.forEach { ctx ->
      val fallback = AppErrorMapper.defaultMessageFor(ctx)
      assertCustomerSafe(fallback)
    }

    val sanitizedSupport = AppErrorMapper.sanitizeMessage("Failed to connect to https://xrrhbnnzjqkqnqdvayjw.supabase.co/rest/v1", ErrorContext.SUPPORT)
    assertCustomerSafe(sanitizedSupport)
    assertEquals("No internet connection. Please check your connection and try again.", sanitizedSupport)

    val sanitizedReview = AppErrorMapper.sanitizeMessage("java.lang.IllegalStateException: unexpected token", ErrorContext.REVIEWS)
    assertCustomerSafe(sanitizedReview)
    assertEquals("Could not submit your review right now. Please try again.", sanitizedReview)

    val sanitizedCancel = AppErrorMapper.sanitizeMessage("PGRST116 JSON object requested", ErrorContext.ORDER_CANCEL)
    assertCustomerSafe(sanitizedCancel)
    assertEquals("Your order could not be cancelled right now. Please try again.", sanitizedCancel)

    val sanitizedAuth = AppErrorMapper.sanitizeMessage("Supabase auth Error 502: bad gateway", ErrorContext.AUTH)
    assertCustomerSafe(sanitizedAuth)
    assertEquals("Authentication could not be completed. Please try again.", sanitizedAuth)
  }
}
