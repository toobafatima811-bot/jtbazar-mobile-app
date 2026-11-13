package com.example.data.supabase

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

object SellerReviewService {
  private fun fail(result: Result<String>): String = result.getOrElse { throw it }

  suspend fun listApproved(sellerId: String): JSONArray =
    JSONArray(fail(SupabaseClient.get("seller_reviews?seller_id=eq.$sellerId&status=eq.approved&select=id,rating,comment,created_at&order=created_at.desc&limit=100")))

  /**
   * Mirrors the website review eligibility flow:
   * - signed-in buyer only
   * - never allow reviewing their own shop
   * - find the buyer's delivered orders by customer_id OR customer_email
   * - require an order_items row for this seller
   * - allow one review per delivered order
   */
  suspend fun submitForDeliveredOrder(sellerId: String, rating: Int, comment: String) {
    val user = SupabaseClient.currentUser.value ?: throw IOException("Sign in to leave a review.")
    require(rating in 1..5) { "Choose a rating from 1 to 5." }
    require(comment.trim().length >= 3) { "Write at least 3 characters in your review." }

    val ownShopRows = JSONArray(fail(SupabaseClient.get(
      "seller_profiles?id=eq.$sellerId&select=user_id&limit=1"
    )))
    val shopOwnerId = ownShopRows.optJSONObject(0)?.optString("user_id").orEmpty()
    if (shopOwnerId == user.id) throw IOException("You cannot review your own seller account.")

    val email = user.email?.trim()?.takeIf { it.isNotBlank() }
    val orderFilter = if (email != null) {
      val encodedEmail = URLEncoder.encode(email, "UTF-8").replace("+", "%20")
      "or=(customer_id.eq.${user.id},customer_email.eq.$encodedEmail)"
    } else {
      "customer_id=eq.${user.id}"
    }
    val orderRows = JSONArray(fail(SupabaseClient.get(
      "orders?select=id&order_status=eq.delivered&$orderFilter&order=created_at.desc&limit=100"
    )))
    val deliveredIds = (0 until orderRows.length())
      .mapNotNull { orderRows.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
      .distinct()
    if (deliveredIds.isEmpty()) throw IOException("You can review this shop after a delivered order from it.")

    val orderIdFilter = deliveredIds.joinToString(",")
    val itemRows = JSONArray(fail(SupabaseClient.get(
      "order_items?select=order_id&seller_id=eq.$sellerId&order_id=in.($orderIdFilter)&limit=100"
    )))
    val eligibleIds = (0 until itemRows.length())
      .mapNotNull { itemRows.optJSONObject(it)?.optString("order_id")?.takeIf(String::isNotBlank) }
      .distinct()
    if (eligibleIds.isEmpty()) throw IOException("You can review this shop after a delivered order from it.")

    val eligibleFilter = eligibleIds.joinToString(",")
    val existingRows = JSONArray(fail(SupabaseClient.get(
      "seller_reviews?select=order_id&seller_id=eq.$sellerId&buyer_id=eq.${user.id}&order_id=in.($eligibleFilter)&limit=100"
    )))
    val alreadyReviewed = (0 until existingRows.length())
      .mapNotNull { existingRows.optJSONObject(it)?.optString("order_id") }
      .toSet()
    val orderId = eligibleIds.firstOrNull { it !in alreadyReviewed }
      ?: throw IOException("You have already reviewed this shop for your delivered orders.")

    val body = JSONObject()
      .put("seller_id", sellerId)
      .put("order_id", orderId)
      .put("buyer_id", user.id)
      .put("rating", rating)
      .put("comment", comment.trim())
      .put("status", "approved")
    fail(SupabaseClient.post("seller_reviews", body.toString(), preferHeader = "return=representation"))
  }
}
