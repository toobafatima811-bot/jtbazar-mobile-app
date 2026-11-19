package com.example.data.supabase

import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.repository.FoodCategoryResolver
import com.example.localization.AppLanguageManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

val PAKISTAN_ZONE_ID: ZoneId = ZoneId.of("Asia/Karachi")

enum class ShopDayOfWeek(
  val key: String,
  val javaDayOfWeek: DayOfWeek,
  val englishName: String,
  val urduName: String,
  val shortAliases: Set<String>
) {
  MONDAY("monday", DayOfWeek.MONDAY, "Monday", "پیر", setOf("monday", "mon", "1")),
  TUESDAY("tuesday", DayOfWeek.TUESDAY, "Tuesday", "منگل", setOf("tuesday", "tue", "tues", "2")),
  WEDNESDAY("wednesday", DayOfWeek.WEDNESDAY, "Wednesday", "بدھ", setOf("wednesday", "wed", "3")),
  THURSDAY("thursday", DayOfWeek.THURSDAY, "Thursday", "جمعرات", setOf("thursday", "thu", "thur", "thurs", "4")),
  FRIDAY("friday", DayOfWeek.FRIDAY, "Friday", "جمعہ", setOf("friday", "fri", "5")),
  SATURDAY("saturday", DayOfWeek.SATURDAY, "Saturday", "ہفتہ", setOf("saturday", "sat", "6")),
  SUNDAY("sunday", DayOfWeek.SUNDAY, "Sunday", "اتوار", setOf("sunday", "sun", "0", "7"));

  fun localizedName(): String = AppLanguageManager.tr(englishName, urduName)

  companion object {
    fun fromJavaDay(day: DayOfWeek): ShopDayOfWeek =
      entries.first { it.javaDayOfWeek == day }

    fun parseKey(raw: String): ShopDayOfWeek? {
      val clean = raw.trim().lowercase(Locale.US)
      return entries.firstOrNull { clean == it.key || clean in it.shortAliases }
    }
  }
}

data class DailyShopHours(
  val day: ShopDayOfWeek,
  val isOpen: Boolean,
  val openTime: String = "09:00",
  val closeTime: String = "22:00"
) {
  fun openLocalTime(): LocalTime? = ShopTimeParser.parseLocalTime(openTime)
  fun closeLocalTime(): LocalTime? = ShopTimeParser.parseLocalTime(closeTime)

  fun isValidRange(): Boolean {
    if (!isOpen) return true
    val open = openLocalTime() ?: return false
    val close = closeLocalTime() ?: return false
    return close.isAfter(open)
  }

  fun formattedOpenTime(): String = ShopTimeParser.format12Hour(openTime)
  fun formattedCloseTime(): String = ShopTimeParser.format12Hour(closeTime)

  fun formattedRange(): String {
    return if (!isOpen) {
      AppLanguageManager.tr("Closed", "بند")
    } else {
      "${formattedOpenTime()} – ${formattedCloseTime()}"
    }
  }

  fun toJson(): JSONObject = JSONObject().apply {
    put("is_open", isOpen)
    put("enabled", isOpen)
    put("closed", !isOpen)
    put("open", ShopTimeParser.normalize24Hour(openTime) ?: "09:00")
    put("close", ShopTimeParser.normalize24Hour(closeTime) ?: "22:00")
  }
}

object ShopTimeParser {
  private val format24Hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
  private val format12Hm: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
  private val parsePatterns: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("H:mm", Locale.US),
    DateTimeFormatter.ofPattern("HH:mm", Locale.US),
    DateTimeFormatter.ofPattern("H:mm:ss", Locale.US),
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US),
    DateTimeFormatter.ofPattern("h:mm a", Locale.US),
    DateTimeFormatter.ofPattern("hh:mm a", Locale.US),
    DateTimeFormatter.ofPattern("h:mma", Locale.US),
    DateTimeFormatter.ofPattern("hh:mma", Locale.US)
  )

  fun parseLocalTime(raw: String?): LocalTime? {
    val clean = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) } ?: return null
    if (clean == "24:00" || clean == "24:00:00") return LocalTime.of(23, 59)
    val upper = clean.uppercase(Locale.US)
    for (formatter in parsePatterns) {
      try {
        return LocalTime.parse(upper, formatter)
      } catch (_: DateTimeParseException) {
        // Try next pattern
      }
    }
    return runCatching { LocalTime.parse(clean) }.getOrNull()
  }

  fun normalize24Hour(raw: String?): String? {
    val parsed = parseLocalTime(raw) ?: return null
    return parsed.format(format24Hm)
  }

  fun format12Hour(raw: String?): String {
    val parsed = parseLocalTime(raw) ?: return raw.orEmpty()
    return parsed.format(format12Hm)
  }

  fun fromHourMinute(hour: Int, minute: Int): String {
    val h = hour.coerceIn(0, 23)
    val m = minute.coerceIn(0, 59)
    return LocalTime.of(h, m).format(format24Hm)
  }
}

data class WeeklyShopHours(
  val days: Map<ShopDayOfWeek, DailyShopHours>
) {
  fun forDay(day: ShopDayOfWeek): DailyShopHours =
    days[day] ?: defaultDayHours(day)

  fun orderedDays(): List<DailyShopHours> =
    ShopDayOfWeek.entries.map { forDay(it) }

  fun withDayUpdated(
    day: ShopDayOfWeek,
    isOpen: Boolean? = null,
    openTime: String? = null,
    closeTime: String? = null
  ): WeeklyShopHours {
    val current = forDay(day)
    val updated = current.copy(
      isOpen = isOpen ?: current.isOpen,
      openTime = openTime?.let { ShopTimeParser.normalize24Hour(it) ?: it } ?: current.openTime,
      closeTime = closeTime?.let { ShopTimeParser.normalize24Hour(it) ?: it } ?: current.closeTime
    )
    return copy(days = days + (day to updated))
  }

  fun invalidDays(): List<ShopDayOfWeek> =
    orderedDays().filter { it.isOpen && !it.isValidRange() }.map { it.day }

  fun toJson(): JSONObject = JSONObject().apply {
    ShopDayOfWeek.entries.forEach { day ->
      put(day.key, forDay(day).toJson())
    }
  }

  companion object {
    fun defaultDayHours(day: ShopDayOfWeek): DailyShopHours = when (day) {
      ShopDayOfWeek.MONDAY,
      ShopDayOfWeek.TUESDAY,
      ShopDayOfWeek.WEDNESDAY,
      ShopDayOfWeek.THURSDAY,
      ShopDayOfWeek.FRIDAY -> DailyShopHours(day, isOpen = true, openTime = "09:00", closeTime = "22:00")
      ShopDayOfWeek.SATURDAY -> DailyShopHours(day, isOpen = true, openTime = "10:00", closeTime = "23:00")
      ShopDayOfWeek.SUNDAY -> DailyShopHours(day, isOpen = false, openTime = "09:00", closeTime = "22:00")
    }

    fun defaults(): WeeklyShopHours =
      WeeklyShopHours(ShopDayOfWeek.entries.associateWith(::defaultDayHours))

    fun parse(raw: Any?): WeeklyShopHours {
      val defaultsMap = defaults().days.toMutableMap()
      val obj = when (raw) {
        is JSONObject -> raw
        is String -> {
          val trimmed = raw.trim()
          if (trimmed.isBlank() || trimmed == "null" || trimmed == "{}") null
          else runCatching { JSONObject(trimmed) }.getOrNull()
        }
        else -> null
      } ?: return WeeklyShopHours(defaultsMap)

      val keys = obj.keys()
      while (keys.hasNext()) {
        val rawKey = keys.next()
        val day = ShopDayOfWeek.parseKey(rawKey) ?: continue
        val fallback = defaultsMap[day] ?: defaultDayHours(day)
        val dayObj = obj.optJSONObject(rawKey)
        if (dayObj == null) {
          val boolVal = obj.opt(rawKey) as? Boolean
          if (boolVal != null) {
            defaultsMap[day] = fallback.copy(isOpen = boolVal)
          }
          continue
        }

        val isOpen = parseIsOpenFlag(dayObj, fallback.isOpen)
        val openStr = firstTimeString(dayObj, listOf("open_time", "openTime", "open", "start", "from"))
        val closeStr = firstTimeString(dayObj, listOf("close_time", "closeTime", "close", "end", "to"))
        defaultsMap[day] = DailyShopHours(
          day = day,
          isOpen = isOpen,
          openTime = ShopTimeParser.normalize24Hour(openStr) ?: fallback.openTime,
          closeTime = ShopTimeParser.normalize24Hour(closeStr) ?: fallback.closeTime
        )
      }
      return WeeklyShopHours(defaultsMap)
    }

    private fun parseIsOpenFlag(obj: JSONObject, fallback: Boolean): Boolean {
      if (obj.has("closed") && !obj.isNull("closed")) {
        val closed = obj.opt("closed")
        if (closed is Boolean) return !closed
      }
      if (obj.has("is_closed") && !obj.isNull("is_closed")) {
        val closed = obj.opt("is_closed")
        if (closed is Boolean) return !closed
      }
      for (key in listOf("is_open", "isOpen", "enabled", "is_enabled")) {
        if (obj.has(key) && !obj.isNull(key)) {
          val v = obj.opt(key)
          if (v is Boolean) return v
        }
      }
      if (obj.has("open") && !obj.isNull("open")) {
        val v = obj.opt("open")
        if (v is Boolean) return v
      }
      return fallback
    }

    private fun firstTimeString(obj: JSONObject, keys: List<String>): String? {
      for (key in keys) {
        if (!obj.has(key) || obj.isNull(key)) continue
        val v = obj.opt(key)
        if (v is String) {
          val clean = v.trim()
          if (clean.isNotEmpty() && !clean.equals("null", ignoreCase = true)) return clean
        }
      }
      return null
    }
  }
}

data class ShopScheduleStatus(
  val isOpen: Boolean,
  val shopIsOpen: Boolean,
  val withinScheduledHours: Boolean,
  val todayDay: ShopDayOfWeek,
  val todayHours: DailyShopHours,
  val badgeLabel: String,
  val detailLabel: String,
  val timezoneId: String = "Asia/Karachi"
)

data class ShopScheduleInfo(
  val sellerId: String,
  val shopIsOpen: Boolean = true,
  val weeklyHours: WeeklyShopHours = WeeklyShopHours.defaults(),
  val timezoneId: String = "Asia/Karachi",
  val serverIsOpenNow: Boolean? = null,
  val serverReason: String? = null,
  val shopName: String? = null
) {
  fun evaluateAt(nowInstant: Instant = Instant.now()): ShopScheduleStatus {
    val zone = runCatching { ZoneId.of(timezoneId.ifBlank { "Asia/Karachi" }) }.getOrDefault(PAKISTAN_ZONE_ID)
    val karachiNow = ZonedDateTime.ofInstant(nowInstant, zone)
    val today = ShopDayOfWeek.fromJavaDay(karachiNow.dayOfWeek)
    val todayHours = weeklyHours.forDay(today)
    val nowTime = karachiNow.toLocalTime()

    val openTime = todayHours.openLocalTime()
    val closeTime = todayHours.closeLocalTime()
    val withinHours = todayHours.isOpen &&
      openTime != null &&
      closeTime != null &&
      !nowTime.isBefore(openTime) &&
      (nowTime.isBefore(closeTime) || (closeTime == LocalTime.of(23, 59) && nowTime == closeTime))

    if (!shopIsOpen) {
      return ShopScheduleStatus(
        isOpen = false,
        shopIsOpen = false,
        withinScheduledHours = withinHours,
        todayDay = today,
        todayHours = todayHours,
        badgeLabel = AppLanguageManager.tr("Closed", "بند ہے"),
        detailLabel = AppLanguageManager.tr(
          "Temporarily closed by shop owner",
          "دکان کے مالک کی طرف سے عارضی طور پر بند ہے"
        ),
        timezoneId = zone.id
      )
    }

    if (withinHours) {
      return ShopScheduleStatus(
        isOpen = true,
        shopIsOpen = true,
        withinScheduledHours = true,
        todayDay = today,
        todayHours = todayHours,
        badgeLabel = AppLanguageManager.tr("Open", "کھلی ہے"),
        detailLabel = AppLanguageManager.tr(
          "Open until ${todayHours.formattedCloseTime()} PKT",
          "آج ${todayHours.formattedCloseTime()} PKT تک کھلی ہے"
        ),
        timezoneId = zone.id
      )
    }

    // Closed due to weekly schedule or outside today's opening window
    val detail = when {
      todayHours.isOpen && openTime != null && nowTime.isBefore(openTime) ->
        AppLanguageManager.tr(
          "Opens today at ${todayHours.formattedOpenTime()} PKT",
          "آج ${todayHours.formattedOpenTime()} PKT پر کھلے گی"
        )
      else -> {
        val nextOpen = findNextOpening(today)
        if (nextOpen != null) {
          AppLanguageManager.tr(
            "Opens ${nextOpen.day.englishName} at ${nextOpen.formattedOpenTime()} PKT",
            "${nextOpen.day.urduName} کو ${nextOpen.formattedOpenTime()} PKT پر کھلے گی"
          )
        } else {
          AppLanguageManager.tr(
            "Closed according to weekly schedule",
            "ہفتہ وار اوقات کے مطابق بند ہے"
          )
        }
      }
    }

    return ShopScheduleStatus(
      isOpen = false,
      shopIsOpen = true,
      withinScheduledHours = false,
      todayDay = today,
      todayHours = todayHours,
      badgeLabel = AppLanguageManager.tr("Closed", "بند ہے"),
      detailLabel = detail,
      timezoneId = zone.id
    )
  }

  private fun findNextOpening(fromDay: ShopDayOfWeek): DailyShopHours? {
    val all = ShopDayOfWeek.entries
    val startIdx = all.indexOf(fromDay)
    for (offset in 1..7) {
      val candidate = weeklyHours.forDay(all[(startIdx + offset) % all.size])
      if (candidate.isOpen && candidate.isValidRange()) return candidate
    }
    return null
  }

  companion object {
    fun fromRpcResponse(sellerId: String, rawJson: String): ShopScheduleInfo {
      val trimmed = rawJson.trim()
      val obj = when {
        trimmed.startsWith("[") -> JSONArray(trimmed).optJSONObject(0)
        trimmed.startsWith("{") -> JSONObject(trimmed)
        else -> null
      } ?: JSONObject()

      val hasExplicitShopIsOpen = (obj.has("shop_is_open") && !obj.isNull("shop_is_open")) ||
        (obj.has("shopIsOpen") && !obj.isNull("shopIsOpen")) ||
        (obj.has("is_shop_open") && !obj.isNull("is_shop_open"))

      val serverIsOpenNow = when {
        obj.has("is_open_now") && !obj.isNull("is_open_now") -> obj.optBoolean("is_open_now")
        obj.has("isOpenNow") && !obj.isNull("isOpenNow") -> obj.optBoolean("isOpenNow")
        obj.has("currently_open") && !obj.isNull("currently_open") -> obj.optBoolean("currently_open")
        obj.has("is_open") && !obj.isNull("is_open") -> obj.optBoolean("is_open")
        else -> null
      }

      val rawHours = when {
        obj.has("shop_hours") && !obj.isNull("shop_hours") -> obj.opt("shop_hours")
        obj.has("shopHours") && !obj.isNull("shopHours") -> obj.opt("shopHours")
        obj.has("weekly_hours") && !obj.isNull("weekly_hours") -> obj.opt("weekly_hours")
        obj.has("schedule") && !obj.isNull("schedule") -> obj.opt("schedule")
        else -> null
      }

      val shopIsOpen = when {
        obj.has("shop_is_open") && !obj.isNull("shop_is_open") -> obj.optBoolean("shop_is_open", true)
        obj.has("shopIsOpen") && !obj.isNull("shopIsOpen") -> obj.optBoolean("shopIsOpen", true)
        obj.has("is_shop_open") && !obj.isNull("is_shop_open") -> obj.optBoolean("is_shop_open", true)
        !hasExplicitShopIsOpen && rawHours == null && serverIsOpenNow != null -> serverIsOpenNow
        else -> true
      }

      val tz = listOf("timezone", "time_zone", "tz")
        .firstNotNullOfOrNull { key ->
          obj.optString(key, "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        } ?: "Asia/Karachi"

      val reason = listOf("reason", "status_message", "closed_reason", "message")
        .firstNotNullOfOrNull { key ->
          obj.optString(key, "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }

      val resolvedSellerId = obj.optString("seller_id", "").takeIf { it.isNotBlank() && it != "null" }
        ?: obj.optString("id", "").takeIf { it.isNotBlank() && it != "null" }
        ?: sellerId

      return ShopScheduleInfo(
        sellerId = resolvedSellerId,
        shopIsOpen = shopIsOpen,
        weeklyHours = WeeklyShopHours.parse(rawHours),
        timezoneId = tz,
        serverIsOpenNow = serverIsOpenNow,
        serverReason = reason,
        shopName = obj.optString("shop_name", "").takeIf { it.isNotBlank() && it != "null" }
      )
    }
  }
}

data class VendorShopCategoryOption(
  val id: String,
  val title: String,
  val productCount: Int,
  val displayOrder: Int = Int.MAX_VALUE
)

object VendorShopCatalogFilter {
  private val inactiveStatuses = setOf("paused", "inactive", "archived", "rejected", "draft", "hidden")

  fun filterActiveVendorProducts(products: List<Product>): List<Product> {
    return products.filter { product ->
      val daily = product.dailyStatus?.trim()?.lowercase(Locale.US)
      val stockText = product.inStockText.trim().lowercase(Locale.US)
      (daily == null || daily !in inactiveStatuses) && !stockText.contains("paused")
    }
  }

  fun buildCategoriesForProducts(
    products: List<Product>,
    allCategories: List<CategoryItem>
  ): List<VendorShopCategoryOption> {
    val activeProducts = filterActiveVendorProducts(products)
    if (activeProducts.isEmpty()) return emptyList()

    val categoriesById = allCategories.associateBy { it.id }
    val categoriesByTitle = allCategories.associateBy { it.title.trim().lowercase(Locale.US) }

    val grouped = linkedMapOf<String, MutableList<Product>>()
    val resolvedTitles = mutableMapOf<String, String>()
    val resolvedOrder = mutableMapOf<String, Int>()

    activeProducts.forEach { product ->
      val matchedById = product.categoryId.takeIf { it.isNotBlank() }?.let { categoriesById[it] }
      val matchedByName = if (matchedById == null) {
        product.category.trim().takeIf { it.isNotBlank() }?.let { categoriesByTitle[it.lowercase(Locale.US)] }
      } else null
      val matchedCategory = matchedById ?: matchedByName

      val catId = matchedCategory?.id ?: product.categoryId.takeIf { it.isNotBlank() }
        ?: product.category.trim().lowercase(Locale.US).takeIf { it.isNotBlank() }
        ?: return@forEach
      val catTitle = matchedCategory?.title?.takeIf { it.isNotBlank() }
        ?: product.category.trim().takeIf { it.isNotBlank() }
        ?: return@forEach

      grouped.getOrPut(catId) { mutableListOf() }.add(product)
      resolvedTitles.putIfAbsent(catId, catTitle)
      resolvedOrder.putIfAbsent(catId, matchedCategory?.displayOrder ?: Int.MAX_VALUE)
    }

    return grouped.entries
      .filter { it.value.isNotEmpty() }
      .map { (id, items) ->
        VendorShopCategoryOption(
          id = id,
          title = resolvedTitles[id].orEmpty(),
          productCount = items.size,
          displayOrder = resolvedOrder[id] ?: Int.MAX_VALUE
        )
      }
      .sortedWith(compareBy<VendorShopCategoryOption> { it.displayOrder }.thenBy { it.title.lowercase(Locale.US) })
  }

  fun filterProductsByCategory(
    products: List<Product>,
    selectedCategoryId: String?,
    allCategories: List<CategoryItem>
  ): List<Product> {
    val active = filterActiveVendorProducts(products)
    if (selectedCategoryId.isNullOrBlank()) return active
    val selectedCategory = allCategories.firstOrNull { it.id == selectedCategoryId }
    val descendantIds = if (selectedCategory != null) {
      FoodCategoryResolver.collectDescendantIds(selectedCategory.id, allCategories).toSet()
    } else {
      setOf(selectedCategoryId)
    }
    val selectedTitleLower = selectedCategory?.title?.trim()?.lowercase(Locale.US)
    return active.filter { product ->
      val matchesId = product.categoryId.isNotBlank() && product.categoryId in descendantIds
      val matchesFallbackKey = product.category.trim().lowercase(Locale.US) == selectedCategoryId.trim().lowercase(Locale.US)
      val matchesTitle = selectedTitleLower != null && product.category.trim().lowercase(Locale.US) == selectedTitleLower
      matchesId || matchesFallbackKey || matchesTitle
    }
  }
}
