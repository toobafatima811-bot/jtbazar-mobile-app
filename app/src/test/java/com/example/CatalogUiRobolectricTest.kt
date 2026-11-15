package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.SuccessResult
import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.repository.CatalogPaginator
import com.example.data.repository.PaginatedProductsPage
import com.example.ui.components.FriendlyEmptyState
import com.example.ui.components.FriendlyErrorState
import com.example.ui.components.PaginationFooter
import com.example.ui.screens.CategoryProductsScreen
import com.example.ui.theme.JTBazarTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogUiRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val foodCategory = CategoryItem(
    id = "cat_food",
    title = "Food",
    countText = "",
    sampleItems = "Fresh groceries, fruits and daily essentials",
    slug = "food",
    isFoodCategory = true,
    displayOrder = 1
  )
  private val fruitsSubcategory = CategoryItem(
    id = "cat_fruits",
    title = "Fruits",
    countText = "",
    sampleItems = "",
    slug = "fruits",
    parentId = "cat_food",
    isFoodCategory = false,
    displayOrder = 2
  )
  private val electronicsCategory = CategoryItem(
    id = "cat_elec",
    title = "Electronics",
    countText = "",
    sampleItems = "Smartphones, audio, and tech gadgets",
    slug = "electronics",
    isFoodCategory = false,
    displayOrder = 3
  )

  private fun fakeProduct(id: String, title: String, category: String, categoryId: String) = Product(
    id = id,
    title = title,
    vendor = "JTBazar Direct",
    category = category,
    categoryId = categoryId,
    price = 299,
    originalPrice = 399,
    discountPercent = 25,
    rating = 4.7,
    reviewCount = 42,
    isAvailable = true,
    availabilityKnown = true,
    inStockText = "In Stock"
  )

  @Before
  fun setUpCoil() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakeLoader = ImageLoader.Builder(context)
      .components {
        add(coil.intercept.Interceptor { chain ->
          val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
          SuccessResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            request = chain.request,
            dataSource = DataSource.MEMORY
          )
        })
      }
      .build()
    Coil.setImageLoader(fakeLoader)
  }

  @After
  fun tearDownCoil() {
    Coil.reset()
  }

  @Test
  fun foodCategoryRendersFreshHealthyBannerSubcategoryStripAndFoodProducts() {
    val foodProducts = listOf(
      fakeProduct("f_1", "Premium Basmati Rice", "Food", "cat_food"),
      fakeProduct("f_2", "Red Apples 1kg", "Fruits", "cat_fruits")
    )

    composeTestRule.setContent {
      val scope = rememberCoroutineScope()
      val paginator = CatalogPaginator(scope) { _, page, _ ->
        Result.success(PaginatedProductsPage(items = foodProducts, page = page, hasMore = false))
      }
      JTBazarTheme(darkTheme = false) {
        CategoryProductsScreen(
          category = foodCategory,
          onBack = {},
          onProductClick = {},
          onCartClick = {},
          paginator = paginator,
          allCategoriesOverride = listOf(foodCategory, fruitsSubcategory, electronicsCategory)
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("food_hero_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Fresh & Healthy").assertIsDisplayed()
    composeTestRule.onNodeWithText("Food at Your Doorstep").assertIsDisplayed()
    composeTestRule.onNodeWithTag("food_subcat_all").assertIsDisplayed()
    composeTestRule.onNodeWithTag("food_subcat_cat_fruits").assertIsDisplayed()
    composeTestRule.onNodeWithText("Premium Basmati Rice").assertIsDisplayed()
    composeTestRule.onNodeWithText("Red Apples 1kg").assertIsDisplayed()
  }

  @Test
  fun electronicsCategoryDoesNotRenderFoodBannerOrFoodChips() {
    val techProducts = listOf(
      fakeProduct("e_1", "Wireless Earbuds Pro", "Electronics", "cat_elec"),
      fakeProduct("e_2", "USB-C Fast Charger", "Electronics", "cat_elec")
    )

    composeTestRule.setContent {
      val scope = rememberCoroutineScope()
      val paginator = CatalogPaginator(scope) { _, page, _ ->
        Result.success(PaginatedProductsPage(items = techProducts, page = page, hasMore = false))
      }
      JTBazarTheme(darkTheme = false) {
        CategoryProductsScreen(
          category = electronicsCategory,
          onBack = {},
          onProductClick = {},
          onCartClick = {},
          paginator = paginator,
          allCategoriesOverride = listOf(foodCategory, fruitsSubcategory, electronicsCategory)
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("food_hero_banner").assertDoesNotExist()
    composeTestRule.onNodeWithText("Fresh & Healthy").assertDoesNotExist()
    composeTestRule.onNodeWithText("All Food").assertDoesNotExist()
    composeTestRule.onNodeWithText("Wireless Earbuds Pro").assertIsDisplayed()
    composeTestRule.onNodeWithText("USB-C Fast Charger").assertIsDisplayed()
    composeTestRule.onNodeWithTag("category_sort_newest").assertIsDisplayed()
  }

  @Test
  fun paginationFooterLoadingErrorAndRetryCallbacksWork() {
    var retryCount = 0
    val isLoadingState = androidx.compose.runtime.mutableStateOf(true)
    val errorState = androidx.compose.runtime.mutableStateOf<String?>(null)

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        PaginationFooter(
          isLoadingMore = isLoadingState.value,
          paginationError = errorState.value,
          hasMore = true,
          itemCount = 12,
          onRetry = { retryCount++ }
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pagination_loading_footer").assertIsDisplayed()
    composeTestRule.onNodeWithText("Loading more products...").assertIsDisplayed()

    isLoadingState.value = false
    errorState.value = "Products load nahi ho sake. Please try again."
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("pagination_error_footer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pagination_retry_button").performClick()
    composeTestRule.onNodeWithTag("pagination_retry_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, retryCount)
  }

  @Test
  fun friendlyErrorAndEmptyStatesRenderActionsCleanly() {
    var errorRetried = 0
    var emptyActionTapped = 0
    val showEmptyState = androidx.compose.runtime.mutableStateOf(false)

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        if (!showEmptyState.value) {
          FriendlyErrorState(
            title = "Connection issue",
            message = "Internet connection nahi mil raha. Please check your connection and try again.",
            onRetry = { errorRetried++ }
          )
        } else {
          FriendlyEmptyState(
            title = "No matching products",
            message = "Try another search word.",
            actionLabel = "Clear Filters",
            onAction = { emptyActionTapped++ }
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("friendly_error_state").assertIsDisplayed()
    composeTestRule.onNodeWithTag("friendly_error_retry_button").performClick()
    assertEquals(1, errorRetried)

    showEmptyState.value = true
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("friendly_empty_state").assertIsDisplayed()
    composeTestRule.onNodeWithTag("friendly_empty_action_button").performClick()
    assertEquals(1, emptyActionTapped)
  }
}
