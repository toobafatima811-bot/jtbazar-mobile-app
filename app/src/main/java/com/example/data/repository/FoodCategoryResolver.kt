package com.example.data.repository

import androidx.annotation.DrawableRes
import com.example.R
import com.example.data.model.CategoryItem

/**
 * Resolves whether a category belongs to the Food vertical (via database flag, slug, or ancestor parent chain)
 * and ensures Food-themed imagery is ONLY used for Food categories — never for Electronics, Fashion,
 * Home & Kitchen utensils, Beauty, etc.
 */
object FoodCategoryResolver {

  private val foodSlugsAndKeywords = listOf(
    "food",
    "foods",
    "grocery",
    "groceries",
    "fruits",
    "fruit",
    "vegetables",
    "vegetable",
    "meat",
    "bakery",
    "dairy",
    "beverages",
    "drinks",
    "snacks",
    "fast-food",
    "fast food",
    "homemade-food",
    "homemade food",
    "desi food",
    "pantry",
    "spices",
    "honey",
    "biryani"
  )

  private val nonFoodExclusions = listOf(
    "electronic", "gadget", "phone", "laptop", "computer",
    "fashion", "cloth", "apparel", "shoe", "bag", "watch", "jewel",
    "beauty", "cosmetic", "skin", "perfume",
    "home", "living", "furniture", "decor", "lighting", "ceramic", "pottery",
    "cookware", "utensil", "appliance", "stationary", "stationery", "book",
    "sport", "auto", "car", "bike", "toy", "health"
  )

  /**
   * Returns true strictly when [category] is a Food category or child of a Food category.
   * Checks:
   * 1. Explicit `isFoodCategory` flag from Supabase `categories.is_food_category`
   * 2. Ancestor chain in [allCategories] (walk parentId up to depth 5 safely)
   * 3. Exact or clean food slug/title match without matching non-food domains.
   */
  fun isFoodCategory(category: CategoryItem?, allCategories: List<CategoryItem>): Boolean {
    if (category == null) return false
    if (category.isFoodCategory) return true

    val byId = allCategories.associateBy { it.id }
    val visited = mutableSetOf<String>()
    var cursor: CategoryItem? = category
    var depth = 0
    while (cursor != null && depth < 6 && visited.add(cursor.id)) {
      if (cursor.isFoodCategory) return true
      if (matchesStrictFoodName(cursor.slug) || matchesStrictFoodName(cursor.title)) {
        return true
      }
      cursor = cursor.parentId?.let { byId[it] }
      depth++
    }
    return false
  }

  private fun matchesStrictFoodName(raw: String?): Boolean {
    val value = raw?.trim()?.lowercase().orEmpty()
    if (value.isBlank()) return false
    if (nonFoodExclusions.any { value.contains(it) } && !value.contains("food") && !value.contains("grocery")) {
      return false
    }
    return foodSlugsAndKeywords.any { token ->
      value == token ||
        value.startsWith("$token ") ||
        value.endsWith(" $token") ||
        value.contains(" $token ") ||
        value.contains("$token-") ||
        value.contains("-$token") ||
        value.contains("& $token") ||
        value.contains("$token &")
    }
  }

  /**
   * Collects [rootCategoryId] and all descendant subcategory IDs using [allCategories].
   */
  fun collectDescendantIds(rootCategoryId: String, allCategories: List<CategoryItem>): List<String> {
    val result = linkedSetOf(rootCategoryId)
    val childrenByParent = allCategories
      .filter { !it.parentId.isNullOrBlank() }
      .groupBy { it.parentId!! }
    val queue = ArrayDeque<String>()
    queue.add(rootCategoryId)
    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()
      childrenByParent[current].orEmpty().forEach { child ->
        if (result.add(child.id)) {
          queue.add(child.id)
        }
      }
    }
    return result.toList()
  }

  /**
   * Returns direct child categories for a Food parent category (used for Food horizontal chips
   * like Grocery, Fruits, Vegetables, Meat, Bakery), or sibling Food subcategories if the user
   * navigated directly into a child Food category.
   */
  fun getFoodSubcategories(current: CategoryItem, allCategories: List<CategoryItem>): List<CategoryItem> {
    if (!isFoodCategory(current, allCategories)) return emptyList()
    val directChildren = allCategories
      .filter { it.parentId == current.id }
      .sortedBy { it.displayOrder }
    if (directChildren.isNotEmpty()) return directChildren

    // If current is itself a leaf food subcategory with siblings under the same food parent
    val parentId = current.parentId
    if (!parentId.isNullOrBlank()) {
      val siblings = allCategories
        .filter { it.parentId == parentId }
        .sortedBy { it.displayOrder }
      if (siblings.size > 1) return siblings
    }

    // Fallback: other active food categories besides the current root
    return allCategories
      .filter { it.id != current.id && isFoodCategory(it, allCategories) }
      .sortedBy { it.displayOrder }
  }

  /**
   * Returns a curated food-only local drawable resource ONLY when the category is verified
   * as a Food category. Non-food categories ALWAYS return 0 (no food image fallback).
   */
  @DrawableRes
  fun resolveFoodDrawableOrNull(category: CategoryItem?, allCategories: List<CategoryItem>): Int? {
    if (!isFoodCategory(category, allCategories)) return null
    val key = "${category?.slug.orEmpty()} ${category?.title.orEmpty()}".lowercase()
    return when {
      key.contains("homemade") || key.contains("chef") || key.contains("biryani") || key.contains("kitchen") ->
        R.drawable.item_biryani
      key.contains("fast") || key.contains("burger") || key.contains("snack") ->
        R.drawable.cat_fastfood
      key.contains("fruit") || key.contains("dry") || key.contains("nut") ->
        R.drawable.thumb_dry_fruits
      key.contains("honey") ->
        R.drawable.item_honey
      else -> R.drawable.cat_groceries
    }
  }
}
