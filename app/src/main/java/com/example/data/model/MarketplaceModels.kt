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
  val id: String,
  val title: String,
  val vendor: String,
  val category: String,
  val price: Int,
  val originalPrice: Int,
  val discountPercent: Int,
  val rating: Double,
  val reviewCount: Int,
  @param:DrawableRes val drawableRes: Int = com.example.R.drawable.item_ceramic_plate,
  val imageUrl: String? = null,
  val badge: String? = null,
  val inStockText: String = "In Stock",
  val savingsText: String? = null,
  val vendorVerified: Boolean = true,
  val vendorRating: String = "98% Positive Seller",
  val vendorSales: String = "4.2k Sales",
  val vendorAvatarText: String = "MC",
  val deliveryText: String = "Delivery to Lahore in 2–3 business days",
  val paymentInfo: String = "Cash on Delivery (COD) & Online Bank Transfer Supported",
  val craftStory: String = "",
  val highlights: List<String> = emptyList(),
  val palettes: List<PaletteOption> = emptyList(),
  val sizes: List<SizeOption> = emptyList(),
  val isArtisanCollection: Boolean = false,
  val isWishlisted: Boolean = false
)

data class CartItem(
  val id: String,
  val product: Product,
  var quantity: Int,
  val selectedPalette: String? = null,
  val selectedSize: String? = null
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
  @param:DrawableRes val drawableRes: Int = com.example.R.drawable.cat_groceries,
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
  val city: String = "Lahore",
  val province: String = "Punjab",
  val tag: String = "HOME",
  val isDefault: Boolean = true
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
  val timeline: List<OrderTimelineStep> = emptyList()
)
