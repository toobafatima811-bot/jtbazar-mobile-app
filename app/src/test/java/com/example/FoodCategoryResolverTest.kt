package com.example

import com.example.data.model.CategoryItem
import com.example.data.repository.FoodCategoryResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodCategoryResolverTest {

  private val foodRoot = CategoryItem(
    id = "cat_food",
    title = "Food",
    countText = "",
    sampleItems = "Fresh groceries and daily kitchen essentials",
    slug = "food",
    isFoodCategory = true,
    displayOrder = 1
  )
  private val catGrocery = CategoryItem(
    id = "cat_grocery",
    title = "Grocery",
    countText = "",
    sampleItems = "",
    slug = "grocery",
    parentId = "cat_food",
    isFoodCategory = false, // Should still resolve to Food via parentId
    displayOrder = 2
  )
  private val catFruits = CategoryItem(
    id = "cat_fruits",
    title = "Fruits",
    countText = "",
    sampleItems = "",
    slug = "fruits",
    parentId = "cat_food",
    isFoodCategory = false,
    displayOrder = 3
  )
  private val catVegetables = CategoryItem(
    id = "cat_veg",
    title = "Vegetables",
    countText = "",
    sampleItems = "",
    slug = "vegetables",
    parentId = "cat_food",
    isFoodCategory = false,
    displayOrder = 4
  )
  private val catMeat = CategoryItem(
    id = "cat_meat",
    title = "Meat",
    countText = "",
    sampleItems = "",
    slug = "meat",
    parentId = "cat_food",
    isFoodCategory = false,
    displayOrder = 5
  )
  private val catBakery = CategoryItem(
    id = "cat_bakery",
    title = "Bakery",
    countText = "",
    sampleItems = "",
    slug = "bakery",
    parentId = "cat_food",
    isFoodCategory = false,
    displayOrder = 6
  )
  private val catElectronics = CategoryItem(
    id = "cat_elec",
    title = "Electronics",
    countText = "",
    sampleItems = "Phones and accessories",
    slug = "electronics",
    isFoodCategory = false,
    displayOrder = 7
  )
  private val catFashion = CategoryItem(
    id = "cat_fashion",
    title = "Fashion",
    countText = "",
    sampleItems = "",
    slug = "fashion",
    isFoodCategory = false,
    displayOrder = 8
  )
  private val catHomeLiving = CategoryItem(
    id = "cat_home",
    title = "Home & Living",
    countText = "",
    sampleItems = "",
    slug = "home-living",
    isFoodCategory = false,
    displayOrder = 9
  )
  private val catKitchenUtensils = CategoryItem(
    id = "cat_utensils",
    title = "Kitchen Utensils",
    countText = "",
    sampleItems = "",
    slug = "kitchen-utensils",
    isFoodCategory = false,
    displayOrder = 10
  )
  private val catBeauty = CategoryItem(
    id = "cat_beauty",
    title = "Beauty",
    countText = "",
    sampleItems = "",
    slug = "beauty",
    isFoodCategory = false,
    displayOrder = 11
  )
  private val catBooks = CategoryItem(
    id = "cat_books",
    title = "Books",
    countText = "",
    sampleItems = "",
    slug = "books",
    isFoodCategory = false,
    displayOrder = 12
  )
  private val catSports = CategoryItem(
    id = "cat_sports",
    title = "Sports",
    countText = "",
    sampleItems = "",
    slug = "sports",
    isFoodCategory = false,
    displayOrder = 13
  )

  private val allCategories = listOf(
    foodRoot, catGrocery, catFruits, catVegetables, catMeat, catBakery,
    catElectronics, catFashion, catHomeLiving, catKitchenUtensils, catBeauty, catBooks, catSports
  )

  @Test
  fun foodAndFoodSubcategoriesReturnTrueAndFoodDrawable() {
    val foodGroup = listOf(foodRoot, catGrocery, catFruits, catVegetables, catMeat, catBakery)
    foodGroup.forEach { category ->
      assertTrue("${category.title} should be recognized as Food", FoodCategoryResolver.isFoodCategory(category, allCategories))
      assertNotNull("${category.title} should provide food drawable", FoodCategoryResolver.resolveFoodDrawableOrNull(category, allCategories))
    }
  }

  @Test
  fun nonFoodCategoriesNeverReceiveFoodImagesOrFoodFlag() {
    val nonFoodGroup = listOf(
      catElectronics,
      catFashion,
      catHomeLiving,
      catKitchenUtensils,
      catBeauty,
      catBooks,
      catSports
    )
    nonFoodGroup.forEach { category ->
      assertFalse("${category.title} must NOT be detected as Food", FoodCategoryResolver.isFoodCategory(category, allCategories))
      assertNull("${category.title} must return null for food drawable", FoodCategoryResolver.resolveFoodDrawableOrNull(category, allCategories))
      assertTrue("${category.title} must have empty food subcategories", FoodCategoryResolver.getFoodSubcategories(category, allCategories).isEmpty())
    }
  }

  @Test
  fun collectDescendantIdsWalksHierarchySafelyWithoutCyclesAndSubcategoriesAreSorted() {
    val subLeaf = CategoryItem(
      id = "cat_organic_apples",
      title = "Organic Apples",
      countText = "",
      sampleItems = "",
      slug = "organic-apples",
      parentId = "cat_fruits",
      displayOrder = 20
    )
    // Add an intentional cyclic reference to verify loop protection
    val cyclic = catFoodWithCycle(parentId = "cat_organic_apples")
    val tree = listOf(cyclic, catGrocery, catFruits, catVegetables, catMeat, catBakery, subLeaf)

    val descendants = FoodCategoryResolver.collectDescendantIds("cat_food", tree)
    assertEquals(
      listOf("cat_food", "cat_grocery", "cat_fruits", "cat_veg", "cat_meat", "cat_bakery", "cat_organic_apples"),
      descendants
    )

    val sortedChildren = FoodCategoryResolver.getFoodSubcategories(foodRoot, allCategories)
    assertEquals(
      listOf("cat_grocery", "cat_fruits", "cat_veg", "cat_meat", "cat_bakery"),
      sortedChildren.map { it.id }
    )
  }

  private fun catFoodWithCycle(parentId: String) = foodRoot.copy(parentId = parentId)
}
