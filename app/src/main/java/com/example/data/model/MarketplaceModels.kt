package com.example.data.model

import androidx.annotation.DrawableRes

data class PaletteOption(
  val name: String,
  val colorHex: Long,
  val isSelected: Boolean = false
)

data class SizeOption(
  val title: String,
  val subtitle: String,
  val isSelected: Boolean = false
)

data class Product(
  val sellerId: String? = null,
  val id: String,
  val title: String,
  val vendor: String,
  val createdAt: String = "",
  val category: String,
  val categoryId: String = "",
  val price: Int,
  val originalPrice: Int,
  val discountPercent: Int,
  val rating: Double,
  val reviewCount: Int,
  @param:DrawableRes val drawableRes: Int = com.example.R.drawable.ic_launcher_background,
  val imageUrl: String? = null,
  val badge: String? = null,
  val inStockText: String = "Availability not verified",
  val savingsText: String? = null,
  val vendorVerified: Boolean = false,
  val vendorRating: String = "",
  val vendorSales: String = "",
  val vendorAvatarText: String = "",
  val deliveryText: String = "Delivered by JTBazar",
  val paymentInfo: String = "Cash on Delivery (COD) & Online Bank Transfer Supported",
  val craftStory: String = "",
  val highlights: List<String> = emptyList(),
  val palettes: List<PaletteOption> = emptyList(),
  val sizes: List<SizeOption> = emptyList(),
  val variantIds: Map<String, String> = emptyMap(),
  val isArtisanCollection: Boolean = false,
  val isFeatured: Boolean = false,
  val isNewArrival: Boolean = false,
  val isWishlisted: Boolean = false,
  val isAvailable: Boolean = true,
  val availabilityKnown: Boolean = false,
  val dailyStatus: String? = null,
  val foodSellingUnit: String? = null,
  val variantAvailability: Map<String, Boolean> = emptyMap()
)

data class CartItem(
  val id: String,
  val product: Product,
  var quantity: Int,
  val selectedPalette: String? = null,
  val selectedSize: String? = null,
  val variantId: String? = null
) {
  val totalPrice: Int
    get() = product.price * quantity
}

data class VendorCartGroup(
  val vendorName: String,
  val verifiedText: String? = null,
  val isFastDispatch: Boolean = false,
  val items: List<CartItem>
)

data class CategoryItem(
  val id: String,
  val title: String,
  val countText: String,
  val sampleItems: String,
  @param:DrawableRes val drawableRes: Int = com.example.R.drawable.ic_launcher_background,
  val imageUrl: String? = null,
  val badge: String? = null
)

data class QuickHubTile(
  val title: String,
  val subtitle: String,
  val iconEmoji: String
)

data class DeliveryAddress(
  val id: String,
  val name: String,
  val phone: String,
  val addressLine: String,
  val area: String,
  val city: String = "",
  val province: String = "",
  val tag: String = "HOME",
  val isDefault: Boolean = false,
  val latitude: Double? = null,
  val longitude: Double? = null
) {
  val fullDisplayAddress: String
    get() = "$addressLine, $area, $city, $province"
}

enum class StepState {
  COMPLETED,
  IN_PROGRESS,
  PENDING
}

data class OrderTimelineStep(
  val title: String,
  val timestamp: String,
  val description: String,
  val state: StepState
)

data class OrderRecord(
  val orderId: String,
  val date: String,
  val items: List<CartItem>,
  val subtotal: Int,
  val discount: Int,
  val deliveryFee: Int,
  val grandTotal: Int,
  val address: DeliveryAddress,
  val deliveryNote: String,
  val statusHeader: String = "Processing Order",
  val orderStatus: String = "PENDING",
  val timeline: List<OrderTimelineStep> = emptyList(),
  val orderNumber: String = orderId
)

data class ShopRecord(
  val id: String,
  val name: String,
  val slug: String = "",
  val productCount: Int = 0,
  val description: String = "",
  val logoUrl: String? = null,
  val bannerUrl: String? = null,
  val city: String = "",
  val address: String = "",
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  val rating: Double = 0.0,
  val isVerified: Boolean = false,
  val phone: String = "",
  val deliveryRadiusKm: Double = 0.0
)
