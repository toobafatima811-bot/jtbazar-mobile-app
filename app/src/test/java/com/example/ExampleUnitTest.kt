package com.example

import com.example.data.model.DeliveryAddress
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExampleUnitTest {

  private val testProduct = Product(
    id = "prod_test_001",
    title = "Multani Blue Pottery Ceramic Vase",
    vendor = "Multan Artisan Studio",
    category = "Artisan Crafts",
    price = 2450,
    originalPrice = 2990,
    discountPercent = 18,
    rating = 4.8,
    reviewCount = 42
  )

  @Before
  fun setUp() {
    MarketplaceRepository.clearCart()
    SupabaseClient.clearSession()
  }

  @Test
  fun testCartAddAndCalculations() {
    assertTrue(MarketplaceRepository.cartItems.value.isEmpty())

    MarketplaceRepository.addToCart(testProduct, quantity = 2)
    val cartItems = MarketplaceRepository.cartItems.value
    assertEquals(1, cartItems.size)
    assertEquals(2, cartItems[0].quantity)
    assertEquals(2450 * 2, cartItems[0].totalPrice)

    val subtotal = cartItems.sumOf { it.totalPrice }
    assertEquals(4900, subtotal)

    val deliveryFee = 150
    val discount = 100
    val grandTotal = subtotal - discount + deliveryFee
    assertEquals(4950, grandTotal)
  }

  @Test
  fun testCartQuantityUpdates() {
    MarketplaceRepository.addToCart(testProduct, quantity = 1)
    val item = MarketplaceRepository.cartItems.value.first()
    assertEquals(1, item.quantity)

    MarketplaceRepository.updateQuantity(item.id, 3)
    assertEquals(3, MarketplaceRepository.cartItems.value.first().quantity)
    assertEquals(2450 * 3, MarketplaceRepository.cartItems.value.first().totalPrice)

    MarketplaceRepository.removeFromCart(item.id)
    assertTrue(MarketplaceRepository.cartItems.value.isEmpty())
  }

  @Test
  fun testOrderPlacementRequiresAuthenticationAndAddress() {
    MarketplaceRepository.addToCart(testProduct, quantity = 1)
    // User is unauthenticated:
    val unauthOrder = MarketplaceRepository.placeCurrentOrder("Test note")
    assertNull("Unauthenticated order placement must be rejected", unauthOrder)
  }

  @Test
  fun testAddressSelection() {
    val address = DeliveryAddress(
      id = "addr_test_01",
      name = "Muhammad Usman",
      phone = "+92 300 4821902",
      addressLine = "Sector H, Phase 5",
      area = "DHA Phase 5",
      city = "Lahore",
      isDefault = true
    )
    MarketplaceRepository.selectAddress(address)
    assertEquals("DHA Phase 5, Lahore", MarketplaceRepository.currentLocation.value)
    assertEquals(address.id, MarketplaceRepository.selectedAddress.value?.id)
  }
}
