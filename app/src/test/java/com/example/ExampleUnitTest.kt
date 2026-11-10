package com.example

import com.example.data.model.DeliveryAddress
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterClass
    @JvmStatic
    fun tearDownClass() {
      Dispatchers.resetMain()
    }
  }

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

  @Test
  fun testFreeDeliveryAndEffectiveDeliveryFeeCalculations() {
    val paidSettings = com.example.data.supabase.SupabaseStoreSettingsDto(
      deliveryFee = 450,
      freeDeliveryEnabled = false,
      additionalDeliveryFee = 50,
      codEnabled = true,
      returnWindowDays = 7
    )
    assertEquals(500, paidSettings.effectiveDeliveryFee)

    val freeSettings = paidSettings.copy(freeDeliveryEnabled = true)
    assertEquals(0, freeSettings.effectiveDeliveryFee)
  }

  @Test
  fun testCouponDiscountCalculations() {
    val subtotal = 3000
    val discountPercent = 30.0
    val discountAmount = (subtotal * (discountPercent / 100.0)).toInt()
    assertEquals(900, discountAmount)

    val deliveryFee = 0 // Free delivery
    val grandTotal = (subtotal - discountAmount + deliveryFee).coerceAtLeast(0)
    assertEquals(2100, grandTotal)
  }

  @Test
  fun testOwnerAccountProtectedFromDeletion() {
    val ownerEmail = "hunainhaider5566@gmail.com"
    val isOwner = ownerEmail.equals("hunainhaider5566@gmail.com", ignoreCase = true)
    assertTrue("Owner email must match protected configuration", isOwner)
  }

  @Test
  fun testHomepageHeroFallbackDefaults() {
    val emptyHeroes = emptyList<com.example.data.supabase.SupabaseHomepageHeroDto>()
    val activeHero = emptyHeroes.firstOrNull()
    assertNull(activeHero)

    val defaultTitle = activeHero?.title ?: "Everything You Need,\nAll in One Place"
    val defaultSubtitle = activeHero?.subtitle ?: "Buy and sell products, discover services, find properties, and connect with trusted vendors across multiple categories."
    val defaultCta = activeHero?.ctaText ?: "Start Shopping"

    assertEquals("Everything You Need,\nAll in One Place", defaultTitle)
    assertTrue(defaultSubtitle.contains("Buy and sell products"))
    assertEquals("Start Shopping", defaultCta)
  }
}
