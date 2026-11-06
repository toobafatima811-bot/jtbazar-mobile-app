package com.example.data.supabase

data class SupabaseUser(
  val id: String,
  val email: String? = null,
  val phone: String? = null,
  val fullName: String? = null,
  val createdAt: String? = null
)

data class SupabaseSession(
  val accessToken: String,
  val refreshToken: String? = null,
  val expiresIn: Long = 3600,
  val tokenType: String = "bearer",
  val user: SupabaseUser
)

data class SupabaseProfile(
  val id: String,
  val fullName: String,
  val phone: String,
  val email: String,
  val avatarUrl: String? = null,
  val defaultAddress: String? = null,
  val city: String = "Lahore"
)

data class SupabaseProductDto(
  val id: String,
  val title: String,
  val vendor: String = "JTBazar Official",
  val category: String,
  val price: Int,
  val originalPrice: Int = price,
  val discountPercent: Int = 0,
  val rating: Double = 4.8,
  val reviewCount: Int = 0,
  val imageUrl: String? = null,
  val badge: String? = null,
  val inStock: Boolean = true,
  val stockQuantity: Int = 50,
  val inStockText: String = "In Stock",
  val savingsText: String? = null,
  val vendorVerified: Boolean = true,
  val vendorRating: String = "98% Positive Seller",
  val vendorSales: String = "1k+ Sales",
  val vendorAvatarText: String = "JT",
  val deliveryText: String = "Delivery to Lahore in 2–3 business days",
  val paymentInfo: String = "Cash on Delivery (COD) Supported",
  val craftStory: String = "",
  val highlights: List<String> = emptyList(),
  val palettes: List<String> = emptyList(),
  val sizes: List<String> = emptyList(),
  val isArtisanCollection: Boolean = false,
  val isFeatured: Boolean = false,
  val isBestSeller: Boolean = false,
  val isNewArrival: Boolean = false
)

data class SupabaseCategoryDto(
  val id: String,
  val title: String,
  val countText: String = "100+ Products",
  val sampleItems: String = "",
  val imageUrl: String? = null,
  val badge: String? = null,
  val displayOrder: Int = 0
)

data class SupabaseAddressDto(
  val id: String,
  val userId: String,
  val name: String,
  val phone: String,
  val addressLine: String,
  val area: String,
  val city: String = "Lahore",
  val province: String = "Punjab",
  val postalCode: String? = null,
  val tag: String = "HOME",
  val isDefault: Boolean = false
)

data class SupabaseOrderDto(
  val id: String,
  val orderNumber: String,
  val userId: String,
  val subtotal: Int,
  val discount: Int,
  val deliveryFee: Int,
  val grandTotal: Int,
  val paymentMethod: String = "COD",
  val paymentStatus: String = "PENDING",
  val orderStatus: String = "PENDING",
  val deliveryNote: String? = null,
  val addressName: String,
  val addressPhone: String,
  val addressLine: String,
  val addressArea: String,
  val addressCity: String,
  val createdAt: String
)

data class SupabaseOrderItemDto(
  val id: String,
  val orderId: String,
  val productId: String,
  val productTitle: String,
  val unitPrice: Int,
  val quantity: Int,
  val totalPrice: Int,
  val selectedPalette: String? = null,
  val selectedSize: String? = null,
  val imageUrl: String? = null
)

data class SupabaseReviewDto(
  val id: String,
  val productId: String,
  val userId: String,
  val userName: String,
  val rating: Int,
  val title: String,
  val comment: String,
  val createdAt: String
)

data class SupabaseReturnRequestDto(
  val id: String,
  val orderId: String,
  val userId: String,
  val reason: String,
  val description: String,
  val status: String = "PENDING",
  val evidenceUrl: String? = null,
  val adminResponse: String? = null,
  val createdAt: String
)

data class SupabaseComplaintDto(
  val id: String,
  val orderId: String? = null,
  val userId: String,
  val subject: String,
  val message: String,
  val status: String = "OPEN",
  val response: String? = null,
  val createdAt: String
)

data class SupabaseNotificationDto(
  val id: String,
  val userId: String,
  val title: String,
  val message: String,
  val type: String = "GENERAL",
  val isRead: Boolean = false,
  val createdAt: String
)
