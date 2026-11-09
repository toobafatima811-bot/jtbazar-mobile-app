package com.example.data.supabase

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object SellerReviewService {
  private fun fail(result: Result<String>): String = result.getOrElse { throw it }

  suspend fun listApproved(sellerId: String): JSONArray =
    JSONArray(fail(SupabaseClient.get("seller_reviews?seller_id=eq.$sellerId&status=eq.approved&select=id,rating,comment,created_at&order=created_at.desc&limit=100")))

  suspend fun submitForDeliveredOrder(sellerId: String, rating: Int, comment: String, deliveredOrderIds: List<String>) {
    val user = SupabaseClient.currentUser.value ?: throw IOException("Sign in to leave a seller review.")
    require(rating in 1..5) { "Choose a rating from 1 to 5." }
    require(comment.trim().length >= 3) { "Write at least 3 characters in your review." }
    val ids = deliveredOrderIds.filter { it.matches(Regex("[0-9a-fA-F-]{36}")) }.distinct().take(100)
    if (ids.isEmpty()) throw IOException("A delivered order from this shop is required before reviewing.")
    val inList = ids.joinToString(",")
    val deliveredRows = JSONArray(fail(SupabaseClient.get("orders?select=id&customer_id=eq.${user.id}&order_status=eq.delivered&id=in.($inList)&limit=100")))
    val verifiedIds = (0 until deliveredRows.length()).mapNotNull { deliveredRows.optJSONObject(it)?.optString("id") }.distinct()
    if (verifiedIds.isEmpty()) throw IOException("A delivered order from this shop is required before reviewing.")
    val verifiedList = verifiedIds.joinToString(",")
    val itemRows = JSONArray(fail(SupabaseClient.get("order_items?select=order_id&seller_id=eq.$sellerId&order_id=in.($verifiedList)&limit=100")))
    val eligible = (0 until itemRows.length()).mapNotNull { itemRows.optJSONObject(it)?.optString("order_id") }.distinct()
    if (eligible.isEmpty()) throw IOException("A delivered order from this shop is required before reviewing.")
    val existing = JSONArray(fail(SupabaseClient.get("seller_reviews?select=order_id&seller_id=eq.$sellerId&buyer_id=eq.${user.id}&order_id=in.(${eligible.joinToString(",")})&limit=100")))
    val reviewed = (0 until existing.length()).mapNotNull { existing.optJSONObject(it)?.optString("order_id") }.toSet()
    val orderId = eligible.firstOrNull { it !in reviewed }
      ?: throw IOException("You have already reviewed this shop for your delivered orders.")
    val body = JSONObject().put("seller_id", sellerId).put("order_id", orderId)
      .put("buyer_id", user.id).put("rating", rating).put("comment", comment.trim()).put("status", "approved")
    fail(SupabaseClient.post("seller_reviews", body.toString(), preferHeader = "return=representation"))
  }
}
