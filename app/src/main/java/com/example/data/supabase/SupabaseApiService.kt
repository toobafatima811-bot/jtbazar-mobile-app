package com.example.data.supabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Service executing structured PostgREST queries against Supabase tables and RPC endpoints.
 */
object SupabaseApiService {
  private const val TAG = "SupabaseApiService"

  // ----------------------------------------------------
  // PRODUCTS
  // ----------------------------------------------------

  suspend fun fetchProducts(category:String?=null,isFeatured:Boolean?=null,limit:Int=50):Result<List<SupabaseProductDto>> = withContext(Dispatchers.IO){
    try{
      val p=mutableListOf("select=*,category:categories(name),images:product_images(image_url,storage_path,is_primary,sort_order),variants:product_variants(id,option_label,option_value,is_active,sort_order),seller:seller_profiles(shop_name)","publication_status=eq.published","moderation_status=eq.approved","vendor_hidden=eq.false","order=created_at.desc","limit=${limit.coerceIn(1,100)}")
      if(!category.isNullOrBlank())p.add("category_id=eq.${URLEncoder.encode(category.trim(),"UTF-8")}")
      if(isFeatured==true)p.add("is_featured=eq.true")
      SupabaseClient.get("products?${p.joinToString("&")}").mapCatching{json->parseProductsJson(json)}
    }catch(e:Exception){Log.e(TAG,"fetchProducts error",e);Result.failure(e)}
  }

  suspend fun searchProducts(query:String):Result<List<SupabaseProductDto>> = withContext(Dispatchers.IO){
    try{val q=URLEncoder.encode(query.trim(),"UTF-8");val url="products?select=*,category:categories(name),images:product_images(image_url,storage_path,is_primary,sort_order),variants:product_variants(id,option_label,option_value,is_active,sort_order),seller:seller_profiles(shop_name)&publication_status=eq.published&moderation_status=eq.approved&vendor_hidden=eq.false&or=(name.ilike.*$q*,description.ilike.*$q*,short_description.ilike.*$q*)&limit=30";SupabaseClient.get(url).mapCatching{parseProductsJson(it)}}catch(e:Exception){Result.failure(e)}
  }

  suspend fun fetchProductById(id:String):Result<SupabaseProductDto> = fetchProducts(limit=100).mapCatching{it.firstOrNull{p->p.id==id}?:throw IOException("Product not found")}

  private fun parseProductsJson(jsonStr:String):List<SupabaseProductDto>{
    val a=JSONArray(jsonStr);return (0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null
      val c=o.optJSONObject("category")?:o.optJSONArray("category")?.optJSONObject(0);val seller=o.optJSONObject("seller")?:o.optJSONArray("seller")?.optJSONObject(0)
      val base=o.optDouble("price",0.0);val discount=o.optDouble("discount_percent",0.0).toInt().coerceIn(0,100);val price=(base*(100-discount)/100.0).toInt()
      val images=o.optJSONArray("images");var image:String?=null;if(images!=null){val rows=(0 until images.length()).mapNotNull{images.optJSONObject(it)}.sortedWith(compareByDescending<JSONObject>{it.optBoolean("is_primary")}.thenBy{it.optInt("sort_order")});val im=rows.firstOrNull();if(im!=null){val direct=if(im.isNull("image_url"))"" else im.optString("image_url");val path=if(im.isNull("storage_path"))"" else im.optString("storage_path");image=direct.takeIf{it.startsWith("https://")}?:path.takeIf{it.isNotBlank()}?.let{"${SupabaseConfig.storageUrl}/object/public/product-images/${it.replace(" ","%20")}"} }}
      val colors=mutableListOf<String>();val sizes=mutableListOf<String>();val vs=o.optJSONArray("variants");if(vs!=null)for(j in 0 until vs.length()){val v=vs.optJSONObject(j)?:continue;if(!v.optBoolean("is_active",true))continue;val l=v.optString("option_label");val value=v.optString("option_value");if(l.contains("color",true)||l.contains("colour",true))colors.add(value);if(l.contains("size",true))sizes.add(value)}
      val highlights=mutableListOf<String>();val specs=o.optJSONArray("specifications");if(specs!=null)for(j in 0 until specs.length()){val v=specs.optJSONObject(j)?:continue;if(v.optString("label").isNotBlank()&&v.optString("value").isNotBlank())highlights.add("${v.optString("label")}: ${v.optString("value")}")}
      SupabaseProductDto(id=o.optString("id"),title=o.optString("name",""),vendor=seller?.optString("shop_name")?.takeIf{it.isNotBlank()}?:"JTBazar",category=c?.optString("name")?:"",price=price,originalPrice=base.toInt(),discountPercent=discount,rating=0.0,reviewCount=0,imageUrl=image,badge=if(discount>0)"$discount% OFF" else null,inStock=false,stockQuantity=0,inStockText="Stock status unavailable",vendorVerified=false,vendorRating="",vendorSales="",vendorAvatarText="",deliveryText="Delivered by JTBazar",paymentInfo="Cash on Delivery",craftStory=o.optString("description",""),highlights=highlights,palettes=colors.distinct(),sizes=sizes.distinct(),isArtisanCollection=false,isFeatured=o.optBoolean("is_featured",false),isBestSeller=false,isNewArrival=false)
    }
  }

  // CATEGORIES
  // ----------------------------------------------------

  suspend fun fetchCategories():Result<List<SupabaseCategoryDto>> = withContext(Dispatchers.IO){
    try{SupabaseClient.get("categories?select=id,name,description,image_url,sort_order,is_coming_soon&is_active=eq.true&order=sort_order.asc").mapCatching{json->val a=JSONArray(json);(0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;SupabaseCategoryDto(id=o.optString("id"),title=o.optString("name"),countText="",sampleItems=o.optString("description",""),imageUrl=if(o.isNull("image_url"))null else o.optString("image_url"),badge=if(o.optBoolean("is_coming_soon"))"Coming soon" else null,displayOrder=o.optInt("sort_order",i))}}}catch(e:Exception){Log.e(TAG,"fetchCategories error",e);Result.failure(e)}
  }

  // CART
  // ----------------------------------------------------

  suspend fun fetchCart(userId: String): Result<String> = withContext(Dispatchers.IO) {
    SupabaseClient.get("cart_items?user_id=eq.$userId&select=*,product:products(*)")
  }

  suspend fun addToCart(
    userId: String,
    productId: String,
    quantity: Int,
    palette: String?,
    size: String?
  ): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("user_id", userId)
      put("product_id", productId)
      put("quantity", quantity)
      if (!palette.isNullOrBlank()) put("selected_palette", palette)
      if (!size.isNullOrBlank()) put("selected_size", size)
    }
    SupabaseClient.post("cart_items", json.toString())
  }

  suspend fun updateCartQuantity(cartItemId: String, quantity: Int): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("quantity", quantity)
    }
    SupabaseClient.patch("cart_items?id=eq.$cartItemId", json.toString())
  }

  suspend fun removeCartItem(cartItemId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    SupabaseClient.delete("cart_items?id=eq.$cartItemId")
  }

  suspend fun clearCart(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    SupabaseClient.delete("cart_items?user_id=eq.$userId")
  }

  // ----------------------------------------------------
  // WISHLIST
  // ----------------------------------------------------

  suspend fun fetchWishlist(userId: String): Result<String> = withContext(Dispatchers.IO) {
    SupabaseClient.get("wishlist_items?user_id=eq.$userId&select=*,product:products(*)")
  }

  suspend fun addToWishlist(userId: String, productId: String): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("user_id", userId)
      put("product_id", productId)
    }
    SupabaseClient.post("wishlist_items", json.toString())
  }

  suspend fun removeFromWishlist(userId: String, productId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    SupabaseClient.delete("wishlist_items?user_id=eq.$userId&product_id=eq.$productId")
  }

  // ----------------------------------------------------
  // ADDRESSES
  // ----------------------------------------------------

  suspend fun fetchAddresses(userId: String): Result<List<SupabaseAddressDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("customer_addresses?user_id=eq.$userId&select=*&order=is_default.desc")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseAddressDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseAddressDto(
              id = obj.optString("id", ""),
              userId = obj.optString("user_id", userId),
              name = obj.optString("name", obj.optString("recipient_name", "")),
              phone = obj.optString("phone", ""),
              addressLine = obj.optString("address_line", obj.optString("address", "")),
              area = obj.optString("area", ""),
              city = obj.optString("city", "Lahore"),
              province = obj.optString("province", "Punjab"),
              postalCode = if (obj.has("postal_code")) obj.optString("postal_code") else null,
              tag = obj.optString("tag", "HOME"),
              isDefault = obj.optBoolean("is_default", false)
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchAddresses error", e)
      Result.failure(e)
    }
  }

  suspend fun saveAddress(address: SupabaseAddressDto): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      if (address.id.isNotBlank()) put("id", address.id)
      put("user_id", address.userId)
      put("name", address.name)
      put("phone", address.phone)
      put("address_line", address.addressLine)
      put("area", address.area)
      put("city", address.city)
      put("province", address.province)
      put("tag", address.tag)
      put("is_default", address.isDefault)
    }
    if (address.id.isNotBlank()) {
      SupabaseClient.patch("customer_addresses?id=eq.${address.id}", json.toString())
    } else {
      SupabaseClient.post("customer_addresses", json.toString())
    }
  }

  // ----------------------------------------------------
  // ORDERS
  // ----------------------------------------------------

  suspend fun fetchOrders(userId:String):Result<List<SupabaseOrderDto>> = withContext(Dispatchers.IO){
    try{SupabaseClient.get("orders?customer_id=eq.$userId&select=*&order=created_at.desc").mapCatching{json->val a=JSONArray(json);(0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;val ad=o.optJSONObject("shipping_address_snapshot")?:JSONObject();SupabaseOrderDto(id=o.optString("id"),orderNumber=o.optString("order_number"),userId=o.optString("customer_id",userId),subtotal=o.optDouble("subtotal").toInt(),discount=o.optDouble("discount_amount").toInt(),deliveryFee=o.optDouble("delivery_fee").toInt(),grandTotal=o.optDouble("total").toInt(),paymentMethod=o.optString("payment_method","cod").uppercase(),paymentStatus=o.optString("payment_status","pending").uppercase(),orderStatus=o.optString("order_status","pending").uppercase(),deliveryNote=if(o.isNull("delivery_note"))null else o.optString("delivery_note"),addressName=ad.optString("recipient_name",o.optString("customer_name","")),addressPhone=ad.optString("phone",o.optString("customer_phone","")),addressLine=ad.optString("address_line_1",""),addressArea=ad.optString("address_line_2",""),addressCity=ad.optString("city",""),createdAt=o.optString("created_at",""))}}}catch(e:Exception){Log.e(TAG,"fetchOrders error",e);Result.failure(e)}
  }
  suspend fun fetchOrderItems(orderId:String):Result<List<SupabaseOrderItemDto>> = withContext(Dispatchers.IO){
    try{SupabaseClient.get("order_items?order_id=eq.$orderId&select=*&order=created_at.asc").mapCatching{json->val a=JSONArray(json);(0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;val v=o.optJSONObject("variant_snapshot")?:JSONObject();SupabaseOrderItemDto(id=o.optString("id"),orderId=o.optString("order_id"),productId=o.optString("product_id",""),productTitle=o.optString("product_name_snapshot",""),unitPrice=o.optDouble("unit_price").toInt(),quantity=o.optInt("quantity",1),totalPrice=o.optDouble("line_total").toInt(),selectedPalette=v.optString("value").takeIf{v.optString("label").contains("color",true)},selectedSize=v.optString("value").takeIf{v.optString("label").contains("size",true)})}}}catch(e:Exception){Log.e(TAG,"fetchOrderItems error",e);Result.failure(e)}
  }
  suspend fun createOrder(order:SupabaseOrderDto,items:List<SupabaseOrderItemDto>):Result<String> = withContext(Dispatchers.IO){
    try{val address=JSONObject().apply{put("recipient_name",order.addressName);put("phone",order.addressPhone);put("address_line_1",order.addressLine);if(order.addressArea.isNotBlank())put("address_line_2",order.addressArea);put("city",order.addressCity);put("province",order.addressProvince);if(!order.postalCode.isNullOrBlank())put("postal_code",order.postalCode)}
      val lines=JSONArray();items.forEach{item->lines.put(JSONObject().apply{put("product_id",item.productId);if(!item.variantId.isNullOrBlank())put("variant_id",item.variantId);put("quantity",item.quantity)})}
      val params=JSONObject().apply{put("p_customer_name",order.addressName);put("p_customer_phone",order.addressPhone);put("p_customer_email",order.customerEmail?:JSONObject.NULL);put("p_shipping_address",address);put("p_items",lines);put("p_idempotency_key",order.idempotencyKey);put("p_customer_note",order.deliveryNote?:"")}
      val body=SupabaseClient.rpc("place_order",params.toString()).getOrThrow();val rows=JSONArray(body);if(rows.length()==0)throw IOException("Order was not created");val id=rows.getJSONObject(0).optString("order_id");if(id.isBlank())throw IOException("Server did not return an order ID");Result.success(id)
    }catch(e:Exception){Log.e(TAG,"Secure place_order RPC failed",e);Result.failure(e)}
  }
  suspend fun cancelOrder(orderId:String):Result<Boolean> = withContext(Dispatchers.IO){
    try{val body=SupabaseClient.rpc("cancel_customer_order",JSONObject().put("p_order_id",orderId).toString()).getOrThrow();Result.success(body.trim()=="true"||body.trim()=="\"true\"")}catch(e:Exception){Log.e(TAG,"cancel_customer_order RPC failed",e);Result.failure(e)}
  }

  // REVIEWS & RATINGS
  // ----------------------------------------------------

  suspend fun fetchProductReviews(productId: String): Result<List<SupabaseReviewDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("product_reviews?product_id=eq.$productId&select=*&order=created_at.desc")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseReviewDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseReviewDto(
              id = obj.optString("id", ""),
              productId = obj.optString("product_id", productId),
              userId = obj.optString("user_id", ""),
              userName = obj.optString("user_name", "Verified Buyer"),
              rating = obj.optInt("rating", 5),
              title = obj.optString("title", "Great product"),
              comment = obj.optString("comment", obj.optString("description", "")),
              createdAt = obj.optString("created_at", "")
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchProductReviews error", e)
      Result.failure(e)
    }
  }

  suspend fun submitProductReview(review: SupabaseReviewDto): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("product_id", review.productId)
      put("user_id", review.userId)
      put("user_name", review.userName)
      put("rating", review.rating)
      put("title", review.title)
      put("comment", review.comment)
    }
    SupabaseClient.post("product_reviews", json.toString())
  }

  // ----------------------------------------------------
  // CASES: RETURNS & COMPLAINTS
  // ----------------------------------------------------

  suspend fun submitReturnRequest(req: SupabaseReturnRequestDto): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("order_id", req.orderId)
      put("user_id", req.userId)
      put("reason", req.reason)
      put("description", req.description)
      put("status", "PENDING")
      if (!req.evidenceUrl.isNullOrBlank()) put("evidence_url", req.evidenceUrl)
    }
    SupabaseClient.post("marketplace_cases", json.toString())
  }

  suspend fun submitComplaint(complaint: SupabaseComplaintDto): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      if (!complaint.orderId.isNullOrBlank()) put("order_id", complaint.orderId)
      put("user_id", complaint.userId)
      put("subject", complaint.subject)
      put("message", complaint.message)
      put("status", "OPEN")
    }
    SupabaseClient.post("marketplace_cases", json.toString())
  }

  // ----------------------------------------------------
  // NOTIFICATIONS
  // ----------------------------------------------------

  suspend fun fetchNotifications(userId: String): Result<List<SupabaseNotificationDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("notifications?user_id=eq.$userId&select=*&order=created_at.desc")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseNotificationDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseNotificationDto(
              id = obj.optString("id", ""),
              userId = obj.optString("user_id", userId),
              title = obj.optString("title", "Notification"),
              message = obj.optString("message", ""),
              type = obj.optString("type", "GENERAL"),
              isRead = obj.optBoolean("is_read", false),
              createdAt = obj.optString("created_at", "")
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchNotifications error", e)
      Result.failure(e)
    }
  }
}
