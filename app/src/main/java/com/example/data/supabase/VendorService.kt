package com.example.data.supabase

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class VendorApplication(
  val id: String,
  val status: String,
  val shopName: String,
  val rejectionReason: String? = null,
  val vendorLocked: Boolean = false,
  val lockReason: String? = null
)

object VendorService {
  private fun fail(result: Result<String>): String = result.getOrElse { throw it }
  private fun text(o: JSONObject, key: String, fallback: String = "") = o.optString(key, fallback).takeUnless { it == "null" } ?: fallback

  suspend fun getOwnApplication(): VendorApplication? {
    val user = SupabaseClient.currentUser.value ?: return null
    val raw = fail(SupabaseClient.get("seller_profiles?user_id=eq.${user.id}&select=id,status,shop_name,rejection_reason,vendor_locked,vendor_lock_reason&limit=1"))
    val rows = JSONArray(raw)
    if (rows.length() == 0) return null
    val o = rows.getJSONObject(0)
    return VendorApplication(text(o,"id"), text(o,"status"), text(o,"shop_name"), text(o,"rejection_reason").ifBlank { null }, o.optBoolean("vendor_locked", false), text(o,"vendor_lock_reason").ifBlank { null })
  }

  suspend fun isVendor(): Boolean {
    val user = SupabaseClient.currentUser.value ?: return false
    val raw = fail(SupabaseClient.get("user_roles?user_id=eq.${user.id}&role=eq.vendor&select=role&limit=1"))
    return JSONArray(raw).length() > 0
  }

  suspend fun uploadPrivateDocument(path: String, bytes: ByteArray, mime: String): String {
    val result = SupabaseClient.uploadPrivateFile("vendor-documents", path, bytes, mime)
    return result.getOrThrow()
  }

  suspend fun submitApplication(data: JSONObject): VendorApplication {
    val raw = fail(SupabaseClient.post("seller_profiles", data.toString(), preferHeader = "return=representation"))
    val rows = JSONArray(raw)
    if (rows.length() == 0) throw IOException("Application was not returned after submission.")
    val o = rows.getJSONObject(0)
    return VendorApplication(text(o,"id"), text(o,"status","pending"), text(o,"shop_name"))
  }

  suspend fun listProducts(): JSONArray = JSONArray(fail(SupabaseClient.rpc("vendor_list_products")))
  suspend fun listOrders(): JSONArray = JSONArray(fail(SupabaseClient.rpc("vendor_order_items_page", JSONObject().put("p_limit", 30).put("p_offset", 0).put("p_search", JSONObject.NULL).put("p_status", JSONObject.NULL).toString())))
  suspend fun markOrderReady(orderId: String, note: String = "") {
    val raw = fail(SupabaseClient.rpc("vendor_mark_order_ready", JSONObject().put("p_order_id", orderId).put("p_vendor_note", note.ifBlank { JSONObject.NULL }).toString()))
    if (raw == "null" || raw == "false" || raw.isBlank()) throw IOException("Order could not be marked ready.")
  }
  suspend fun setProductVisibility(productId: String, hidden: Boolean) {
    fail(SupabaseClient.rpc("vendor_set_product_visibility", JSONObject().put("p_product_id", productId).put("p_hidden", hidden).toString()))
  }
  suspend fun setInventory(productId: String, quantity: Int, note: String = "Updated from Android app") {
    fail(SupabaseClient.rpc("vendor_set_product_inventory", JSONObject().put("p_product_id", productId).put("p_variant_id", JSONObject.NULL).put("p_new_quantity", quantity).put("p_note", note).toString()))
  }
  suspend fun createProduct(name: String, slug: String, description: String, price: Int, stock: Int, categoryId: String? = null): String {
    val params = JSONObject()
      .put("p_category_id", categoryId ?: JSONObject.NULL)
      .put("p_name", name).put("p_slug", slug).put("p_sku", JSONObject.NULL)
      .put("p_short_description", description.take(180)).put("p_description", description)
      .put("p_price", price).put("p_supplier_cost", JSONObject.NULL)
      .put("p_fulfillment_expense", 0).put("p_minimum_profit", 0)
      .put("p_track_inventory", true).put("p_discount_percent", 0)
      .put("p_weight_grams", JSONObject.NULL).put("p_specifications", JSONObject()).put("p_dimensions", JSONObject())
      .put("p_seo_title", JSONObject.NULL).put("p_seo_description", JSONObject.NULL).put("p_initial_stock", stock)
    return fail(SupabaseClient.rpc("vendor_create_product", params.toString())).trim('"')
  }
}
