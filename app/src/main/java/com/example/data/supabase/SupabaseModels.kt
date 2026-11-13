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
  val city: String = ""
)

data class SupabaseProductDto(
  val sellerId: String? = null,
  val id: String,
  val title: String,
  val vendor: String = "",
  val categoryId: String = "",
  val category: String,
  val price: Int,
  val originalPrice: Int = price,
  val discountPercent: Int = 0,
  val rating: Double = 0.0,
  val reviewCount: Int = 0,
  val imageUrl: String? = null,
  val badge: String? = null,
  val inStock: Boolean = false,
  val stockQuantity: Int = 0,
  val inStockText: String = "In Stock",
  val savingsText: String? = null,
  val vendorVerified: Boolean = false,
  val vendorRating: String = "",
  val vendorSales: String = "",
  val vendorAvatarText: String = "",
  val deliveryText: String = "Delivered by JTBazar",
  val paymentInfo: String = "Cash on Delivery (COD) Supported",
  val craftStory: String = "",
  val highlights: List<String> = emptyList(),
  val palettes: List<String> = emptyList(),
  val sizes: List<String> = emptyList(),
  val variantIds: Map<String, String> = emptyMap(),
  val isArtisanCollection: Boolean = false,
  val isFeatured: Boolean = false,
  val isBestSeller: Boolean = false,
  val isNewArrival: Boolean = false,
  val availabilityKnown: Boolean = false,
  val dailyStatus: String? = null,
  val foodSellingUnit: String? = null,
  val variantAvailability: Map<String, Boolean> = emptyMap()
)

data class SupabaseCategoryDto(
  val id: String,
  val title: String,
  val countText: String = "",
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
  val city: String = "",
  val province: String = "",
  val postalCode: String? = null,
  val tag: String = "HOME",
  val isDefault: Boolean = false,
  val latitude: Double? = null,
  val longitude: Double? = null
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
  val createdAt: String,
  val addressProvince: String = "",
  val postalCode: String? = null,
  val customerEmail: String? = null,
  val idempotencyKey: String = ""
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
  val imageUrl: String? = null,
  val variantId: String? = null
)

data class SupabaseReviewDto(
  val id: String,
  val productId: String,
  val orderId: String? = null,
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
  val type: String = "store",
  val audience: String? = null,
  val relatedOrderId: String? = null,
  val readAt: String? = null,
  val isRead: Boolean = false,
  val createdAt: String
)

/** Active product campaign retrieved via Supabase RPC `list_active_product_campaigns()`. */
data class SupabaseProductCampaignDto(
  val id: String,
  val name: String,
  val campaignType: String,
  val displayTitle: String,
  val description: String? = null,
  val discountPercent: Double = 0.0,
  val buyQuantity: Int = 1,
  val getQuantity: Int = 1,
  val bundlePrice: Double? = null,
  val startsAt: String? = null,
  val endsAt: String? = null,
  val productIds: List<String> = emptyList()
)

data class SupabaseStoreSettingsDto(
  val deliveryFee: Int = 0,
  val freeDeliveryEnabled: Boolean = false,
  val additionalDeliveryFee: Int = 0,
  val codEnabled: Boolean = false,
  val returnWindowDays: Int = 0,
  val supportEmail: String? = null,
  val supportPhone: String? = null,
  val supportWhatsapp: String? = null,
  val storeName: String = "JTBazar"
) {
  val effectiveDeliveryFee: Int
    get() = if (freeDeliveryEnabled) 0 else (deliveryFee + additionalDeliveryFee)
}


/** Published homepage hero content configured by the JTBazar website/admin tools. */
data class SupabaseHomepageHeroDto(
  val id: String,
  val title: String,
  val subtitle: String? = null,
  val ctaText: String? = null,
  val ctaDestination: String? = null,
  val imageUrl: String,
  val mobileImageUrl: String? = null,
  val imageAltText: String? = null,
  val displayOrder: Int = 0,
  val startDate: String? = null,
  val endDate: String? = null
)

/** Approved seller promotion content configured in the marketplace. */
data class SupabasePromotionDto(
  val id: String,
  val title: String,
  val description: String? = null,
  val bannerImageUrl: String? = null,
  val bannerImageUrls: List<String> = emptyList(),
  val ctaText: String? = null,
  val ctaDestination: String? = null,
  val displayPosition: String = "",
  val priority: Int = 0,
  val targetPlatform: String = "both",
  val campaignType: String = "banner",
  val showOnLaunch: Boolean = false,
  val startsAt: String? = null,
  val endsAt: String? = null,
  val couponCode: String? = null,
  val discountPercent: Double? = null
)

