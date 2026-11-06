package com.example

import com.example.data.model.DeliveryAddress
import com.example.data.repository.MarketplaceRepository
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testProductCatalogIntegrity() {
    val products = MarketplaceRepository.sampleProducts
    assertTrue("Product catalog should have items", products.isNotEmpty())

    val multaniPlate = products.find { it.id == "prod_multani_plate" }
    assertNotNull("Multani plate should exist", multaniPlate)
    assertEquals(2450, multaniPlate?.price)
    assertEquals(2990, multaniPlate?.originalPrice)
    assertEquals(18, multaniPlate?.discountPercent)
  }

  @Test
  fun testCartInitialValuesAndCalculation() {
    val cartItems = MarketplaceRepository.cartItems.value
    assertEquals("Initial cart should have 3 items", 3, cartItems.size)

    val subtotal = cartItems.sumOf { it.totalPrice }
    assertEquals(4160, subtotal)

    val discount = 100
    val deliveryFee = 150
    val grandTotal = subtotal - discount + deliveryFee
    assertEquals(4210, grandTotal)
  }

  @Test
  fun testWishlistToggle() {
    val prodId = "prod_multani_plate"
    val initialWishlist = MarketplaceRepository.products.value.find { it.id == prodId }?.isWishlisted ?: false
    MarketplaceRepository.toggleWishlist(prodId)
    val afterToggle = MarketplaceRepository.products.value.find { it.id == prodId }?.isWishlisted ?: false
    assertNotEquals(initialWishlist, afterToggle)
  }

  @Test
  fun testOrderPlacement() {
    val order = MarketplaceRepository.placeCurrentOrder("Leave parcel at gate")
    assertNotNull(order)
    assertTrue("Order ID must follow JT prefix format", order.orderId.startsWith("JT-"))
    assertTrue("Order ID must end with -PK", order.orderId.endsWith("-PK"))
    assertEquals(4210, order.grandTotal)
    assertEquals("Leave parcel at gate", order.deliveryNote)
  }

  @Test
  fun testAddressSelection() {
    val newAddress = DeliveryAddress(
      id = "addr_dha",
      name = "Muhammad Usman",
      phone = "+92 300 4821902",
      addressLine = "Sector H, Phase 5",
      area = "DHA Phase 5",
      city = "Lahore"
    )
    MarketplaceRepository.selectAddress(newAddress)
    assertEquals("DHA Phase 5, Lahore", MarketplaceRepository.currentLocation.value)
    assertEquals(newAddress.id, MarketplaceRepository.selectedAddress.value.id)
  }
}
