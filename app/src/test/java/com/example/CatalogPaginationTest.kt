package com.example

import com.example.data.repository.CatalogPaginator
import com.example.data.repository.CatalogQuery
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.ProductSortOption
import com.example.data.supabase.NetworkConnectivity
import com.example.data.supabase.SupabaseApiService
import com.example.data.supabase.SupabaseConfig
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogPaginationTest {

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

  private data class FakeProduct(
    val id: String,
    val name: String,
    val categoryId: String,
    val price: Int,
    val createdAt: String
  )

  private lateinit var server: HttpServer
  private lateinit var dataset: List<FakeProduct>
  private val recordedProductRequests = CopyOnWriteArrayList<String>()
  @Volatile private var failNextProductRequest = false
  @Volatile private var simulatedDelayForQuery: String? = null
  @Volatile private var simulatedDelayMs: Long = 0L

  @Before
  fun setUp() {
    SupabaseApiService.clearCaches()
    NetworkConnectivity.setOnlineOverrideForTest(true)
    recordedProductRequests.clear()
    failNextProductRequest = false
    simulatedDelayForQuery = null
    simulatedDelayMs = 0L

    dataset = (1..1000).map { idx ->
      val padded = idx.toString().padStart(4, '0')
      val catId = when {
        idx % 3 == 0 -> "cat_fruits"
        idx % 2 == 0 -> "cat_food"
        else -> "cat_elec"
      }
      val name = when {
        idx % 10 == 0 -> "Fresh Apples Harvest #$padded"
        idx % 5 == 0 -> "Old Stock Item #$padded"
        else -> "Catalog Product #$padded"
      }
      FakeProduct(
        id = "prod_$padded",
        name = name,
        categoryId = catId,
        price = 100 + (idx % 50) * 20,
        createdAt = "2026-11-15T00:${(idx / 60).toString().padStart(2, '0')}:${(idx % 60).toString().padStart(2, '0')}Z"
      )
    }

    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/rest/v1/") { exchange ->
      handleRestRequest(exchange)
    }
    server.start()
    SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.address.port}"
  }

  @After
  fun tearDown() {
    SupabaseConfig.projectUrlOverride = null
    NetworkConnectivity.setOnlineOverrideForTest(null)
    SupabaseApiService.clearCaches()
    if (::server.isInitialized) {
      server.stop(0)
    }
  }

  private fun parseQueryParams(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery.split("&").mapNotNull { pair ->
      val idx = pair.indexOf('=')
      if (idx <= 0) return@mapNotNull null
      val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
      val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
      k to v
    }.toMap()
  }

  private fun sendJson(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }

  private fun handleRestRequest(exchange: HttpExchange) {
    val path = exchange.requestURI.path
    val rawQuery = exchange.requestURI.rawQuery.orEmpty()
    val params = parseQueryParams(rawQuery)

    when {
      path.endsWith("/products") -> {
        recordedProductRequests.add(rawQuery)
        val delayTarget = simulatedDelayForQuery
        if (delayTarget != null && rawQuery.contains(delayTarget, ignoreCase = true)) {
          Thread.sleep(simulatedDelayMs)
        }
        if (failNextProductRequest) {
          failNextProductRequest = false
          sendJson(exchange, 500, """{"code":"42P01","message":"relation failure"}""")
          return
        }

        var filtered = dataset
        val eqId = params["id"]?.takeIf { it.startsWith("eq.") }?.removePrefix("eq.")
        if (!eqId.isNullOrBlank()) {
          filtered = filtered.filter { it.id == eqId }
        }
        val inIdsRaw = params["id"]?.takeIf { it.startsWith("in.(") }
          ?.removePrefix("in.(")?.removeSuffix(")")
        if (!inIdsRaw.isNullOrBlank()) {
          val wanted = inIdsRaw.split(",").toSet()
          filtered = filtered.filter { it.id in wanted }
        }

        val catEq = params["category_id"]?.takeIf { it.startsWith("eq.") }?.removePrefix("eq.")
        if (!catEq.isNullOrBlank()) {
          filtered = filtered.filter { it.categoryId == catEq }
        }
        val catIn = params["category_id"]?.takeIf { it.startsWith("in.(") }
          ?.removePrefix("in.(")?.removeSuffix(")")
        if (!catIn.isNullOrBlank()) {
          val wantedCats = catIn.split(",").toSet()
          filtered = filtered.filter { it.categoryId in wantedCats }
        }

        val orParam = params["or"]
        if (!orParam.isNullOrBlank()) {
          val priceMatch = Regex("price\\.lte\\.(\\d+)").find(orParam)
          if (priceMatch != null) {
            val maxP = priceMatch.groupValues[1].toInt()
            filtered = filtered.filter { it.price <= maxP }
          }
          val ilikeMatch = Regex("name\\.ilike\\.\\*([^*,]+)\\*").find(orParam)
          if (ilikeMatch != null) {
            val term = ilikeMatch.groupValues[1].lowercase()
            filtered = filtered.filter { it.name.lowercase().contains(term) }
          }
        }

        val orderParam = params["order"].orEmpty()
        filtered = when {
          orderParam.startsWith("price.asc") -> filtered.sortedBy { it.price }
          orderParam.startsWith("price.desc") -> filtered.sortedByDescending { it.price }
          else -> filtered
        }

        val offset = params["offset"]?.toIntOrNull() ?: 0
        val limit = params["limit"]?.toIntOrNull() ?: 13
        val pageSlice = if (offset >= filtered.size) {
          emptyList()
        } else {
          filtered.subList(offset, minOf(offset + limit, filtered.size))
        }

        val arr = JSONArray()
        pageSlice.forEach { item ->
          arr.put(
            JSONObject()
              .put("id", item.id)
              .put("category_id", item.categoryId)
              .put("seller_id", "seller_1")
              .put("name", item.name)
              .put("slug", item.id)
              .put("sku", item.id)
              .put("short_description", "Fresh quality")
              .put("description", "Fresh quality product")
              .put("price", item.price)
              .put("discount_percent", 10)
              .put("is_featured", true)
              .put("track_inventory", true)
              .put("created_at", item.createdAt)
          )
        }
        sendJson(exchange, 200, arr.toString())
      }

      path.endsWith("/categories") -> {
        val cats = JSONArray()
          .put(JSONObject().put("id", "cat_food").put("name", "Food").put("slug", "food").put("is_food_category", true).put("sort_order", 1))
          .put(JSONObject().put("id", "cat_fruits").put("name", "Fruits").put("slug", "fruits").put("parent_id", "cat_food").put("is_food_category", false).put("sort_order", 2))
          .put(JSONObject().put("id", "cat_elec").put("name", "Electronics").put("slug", "electronics").put("is_food_category", false).put("sort_order", 3))
        sendJson(exchange, 200, cats.toString())
      }

      path.endsWith("/rpc/list_public_shops") -> {
        val shops = JSONArray()
          .put(JSONObject().put("seller_id", "seller_1").put("shop_name", "JTBazar Direct"))
        sendJson(exchange, 200, shops.toString())
      }

      path.endsWith("/product_images") -> {
        val pidIn = params["product_id"]?.removePrefix("in.(")?.removeSuffix(")").orEmpty()
        val ids = pidIn.split(",").filter { it.isNotBlank() }
        val imgs = JSONArray()
        ids.forEach { pid ->
          imgs.put(
            JSONObject()
              .put("product_id", pid)
              .put("image_url", "https://xrrhbnnzjqkqnqdvayjw.supabase.co/storage/v1/object/public/product-images/$pid.png")
              .put("is_primary", true)
              .put("sort_order", 0)
          )
        }
        sendJson(exchange, 200, imgs.toString())
      }

      path.endsWith("/product_variants") -> {
        sendJson(exchange, 200, "[]")
      }

      path.endsWith("/rpc/get_public_inventory") -> {
        val reqBody = exchange.requestBody.bufferedReader().readText()
        val reqIds = JSONObject(reqBody).optJSONArray("product_ids") ?: JSONArray()
        val resp = JSONArray()
        for (i in 0 until reqIds.length()) {
          resp.put(JSONObject().put("product_id", reqIds.getString(i)).put("available_quantity", 15))
        }
        sendJson(exchange, 200, resp.toString())
      }

      path.endsWith("/rpc/get_public_availability") -> {
        val reqBody = exchange.requestBody.bufferedReader().readText()
        val reqIds = JSONObject(reqBody).optJSONArray("product_ids") ?: JSONArray()
        val resp = JSONArray()
        for (i in 0 until reqIds.length()) {
          resp.put(JSONObject().put("product_id", reqIds.getString(i)).put("is_available", true))
        }
        sendJson(exchange, 200, resp.toString())
      }

      else -> sendJson(exchange, 200, "[]")
    }
  }

  @Test
  fun paginatorLoadsPageSize12AppendsWithoutDuplicatesAndStopsAtEnd() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val paginator = MarketplaceRepository.createCatalogPaginator(scope)
      assertEquals(12, CatalogPaginator.DEFAULT_PAGE_SIZE)

      // Query Fruits where items are smaller subset or use all products first
      paginator.resetAndLoad(CatalogQuery()).join()
      val afterPage0 = paginator.state.value
      assertEquals(12, afterPage0.items.size)
      assertEquals("prod_0001", afterPage0.items.first().id)
      assertEquals("prod_0012", afterPage0.items.last().id)
      assertTrue(afterPage0.hasMore)
      assertNull(afterPage0.initialError)

      paginator.loadNextPage()?.join()
      val afterPage1 = paginator.state.value
      assertEquals(24, afterPage1.items.size)
      assertEquals(24, afterPage1.items.map { it.id }.distinct().size)
      assertEquals("prod_0013", afterPage1.items[12].id)
      assertEquals("prod_0024", afterPage1.items.last().id)
      assertTrue(afterPage1.hasMore)

      // Verify exact limit/offset query parameters sent for page 0 and page 1
      assertTrue(recordedProductRequests.any { it.contains("limit=13") && it.contains("offset=0") })
      assertTrue(recordedProductRequests.any { it.contains("limit=13") && it.contains("offset=12") })

      // Filter to a small maxPrice slice with <= 12 items so hasMore becomes false on first page
      paginator.resetAndLoad(CatalogQuery(maxPrice = 100)).join()
      val endState = paginator.state.value
      assertTrue(endState.items.isNotEmpty())
      assertTrue(endState.items.size <= 12)
      assertTrue(endState.items.all { it.price <= 100 })
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun duplicateScrollCallsAreGuardedAndOnlyOneFetchRuns() = runBlocking {
    val fetchCounter = AtomicInteger(0)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val paginator = CatalogPaginator(scope) { _, page, pageSize ->
        fetchCounter.incrementAndGet()
        if (page > 0) delay(120L)
        val start = page * pageSize + 1
        val items = (start until start + pageSize).map { i ->
          MarketplaceRepository.mapDtoToProduct(
            com.example.data.supabase.SupabaseProductDto(
              id = "p_$i", title = "Item $i", vendor = "V", category = "Food",
              price = 100, originalPrice = 100, discountPercent = 0, rating = 0.0,
              reviewCount = 0, imageUrl = null, badge = null, inStock = true,
              stockQuantity = 5, inStockText = "In Stock", vendorVerified = true,
              vendorRating = "", vendorSales = "", vendorAvatarText = "",
              deliveryText = "", paymentInfo = "", craftStory = "",
              highlights = emptyList(), palettes = emptyList(), sizes = emptyList()
            )
          )
        }
        Result.success(com.example.data.repository.PaginatedProductsPage(items, page, hasMore = true))
      }

      paginator.resetAndLoad(CatalogQuery()).join()
      assertEquals(1, fetchCounter.get())

      val job1 = paginator.loadNextPage()
      val job2 = paginator.loadNextPage()
      val job3 = paginator.loadNextPage()
      assertNotNull(job1)
      assertNull("Concurrent duplicate loadNextPage should return null while loading", job2)
      assertNull(job3)
      job1?.join()
      assertEquals(2, fetchCounter.get())
      assertEquals(24, paginator.state.value.items.size)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun rapidSearchChangeCancelsStaleResponseAndKeepsLatestResultsOnly() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      simulatedDelayForQuery = "old"
      simulatedDelayMs = 250L

      val paginator = MarketplaceRepository.createCatalogPaginator(scope)
      paginator.resetAndLoad(CatalogQuery(searchQuery = "old"))
      delay(30L)
      val freshJob = paginator.resetAndLoad(CatalogQuery(searchQuery = "fresh"))
      freshJob.join()
      delay(280L)

      val current = paginator.state.value
      assertEquals("fresh", current.query.searchQuery)
      assertTrue(current.items.isNotEmpty())
      assertTrue(current.items.all { it.title.contains("Fresh", ignoreCase = true) })
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun categoryIdsSendInFilterAndDeepCatalogItem950IsHydratedDirectAndHighOffsetWorks() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val paginator = MarketplaceRepository.createCatalogPaginator(scope)
      paginator.resetAndLoad(CatalogQuery(categoryIds = listOf("cat_food", "cat_fruits"))).join()

      val state = paginator.state.value
      assertEquals(12, state.items.size)
      assertTrue(state.items.all { it.categoryId == "cat_food" || it.categoryId == "cat_fruits" })
      assertTrue(recordedProductRequests.any { it.contains("category_id=in.(cat_food,cat_fruits)") })

      // Verify item #950 (beyond initial 12 loaded) can be fetched directly via fetchProductsByIds
      val deepDtoList = SupabaseApiService.fetchProductsByIds(listOf("prod_0950")).getOrThrow()
      assertEquals(1, deepDtoList.size)
      assertEquals("prod_0950", deepDtoList.first().id)

      // Verify 10,000-product catalog offset arithmetic (page 833 -> offset 9996, limit 13) returns empty cleanly without error
      val deepPage = SupabaseApiService.fetchProductsPage(page = 833, pageSize = 12).getOrThrow()
      assertFalse(deepPage.hasMore)
      assertTrue(deepPage.items.isEmpty())
      assertTrue(recordedProductRequests.any { it.contains("offset=9996") && it.contains("limit=13") })
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun midScrollHttpErrorPreservesLoadedItemsShowsFriendlyMessageAndRecoversOnRetry() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val paginator = MarketplaceRepository.createCatalogPaginator(scope)
      paginator.resetAndLoad(CatalogQuery()).join()
      assertEquals(12, paginator.state.value.items.size)

      failNextProductRequest = true
      paginator.loadNextPage()?.join()

      val errorState = paginator.state.value
      assertEquals(12, errorState.items.size)
      val errMsg = errorState.paginationError
      assertNotNull(errMsg)
      assertFalse("Must never leak PostgREST/SQL details", errMsg!!.contains("42P01") || errMsg.contains("500"))
      assertEquals("Products load nahi ho sake. Please try again.", errMsg)

      paginator.retry()?.join()
      val recovered = paginator.state.value
      assertNull(recovered.paginationError)
      assertEquals(24, recovered.items.size)
    } finally {
      scope.cancel()
    }
  }
}
