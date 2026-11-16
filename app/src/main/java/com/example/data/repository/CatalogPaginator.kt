package com.example.data.repository

import com.example.data.model.Product
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ProductSortOption(val label: String, val orderParam: String) {
  NEWEST("Newest", "created_at.desc,id.desc"),
  PRICE_LOW_HIGH("Price: Low-High", "price.asc,id.desc"),
  PRICE_HIGH_LOW("Price: High-Low", "price.desc,id.desc"),
  BIGGEST_DISCOUNT("Best Deals", "discount_percent.desc,created_at.desc,id.desc")
}

data class CatalogQuery(
  val categoryIds: List<String> = emptyList(),
  val searchQuery: String = "",
  val sortBy: ProductSortOption = ProductSortOption.NEWEST,
  val maxPrice: Int? = null,
  val isFeatured: Boolean? = null
)

data class PaginatedProductsPage(
  val items: List<Product>,
  val page: Int,
  val hasMore: Boolean
)

data class CatalogFeedState(
  val query: CatalogQuery = CatalogQuery(),
  val items: List<Product> = emptyList(),
  val nextPage: Int = 0,
  val hasMore: Boolean = true,
  val isInitialLoading: Boolean = false,
  val isLoadingMore: Boolean = false,
  val initialError: String? = null,
  val paginationError: String? = null,
  val hasLoadedOnce: Boolean = false
)

/**
 * Thread-safe, generation-guarded catalog paginator for Alibaba-scale product feeds.
 * - Fixed page size = 12 (2-column friendly, fast initial render).
 * - Mutex + active Job cancellation guarantees rapid filter/search changes cancel stale requests
 *   and rapid scroll events cannot double-fetch the same page.
 */
class CatalogPaginator(
  private val scope: CoroutineScope,
  private val pageSize: Int = DEFAULT_PAGE_SIZE,
  private val errorContext: ErrorContext = ErrorContext.PRODUCT_LOAD,
  private val fetcher: suspend (query: CatalogQuery, page: Int, pageSize: Int) -> Result<PaginatedProductsPage>
) {
  companion object {
    const val DEFAULT_PAGE_SIZE = 12
  }

  private val mutex = Mutex()
  private val loadMoreLock = Any()
  private var activeJob: Job? = null
  @Volatile private var generation: Long = 0L
  @Volatile private var queuedNextPage: Int? = null
  @Volatile private var queuedNextPageGen: Long = -1L

  private val _state = MutableStateFlow(CatalogFeedState())
  val state: StateFlow<CatalogFeedState> = _state.asStateFlow()

  fun resetAndLoad(query: CatalogQuery): Job {
    activeJob?.cancel()
    val requestGen: Long
    synchronized(loadMoreLock) {
      requestGen = ++generation
      queuedNextPage = null
      queuedNextPageGen = -1L
    }
    val job = scope.launch {
      mutex.withLock {
        if (generation != requestGen) return@withLock
        _state.value = CatalogFeedState(
          query = query,
          items = emptyList(),
          nextPage = 0,
          hasMore = true,
          isInitialLoading = true,
          isLoadingMore = false,
          initialError = null,
          paginationError = null,
          hasLoadedOnce = false
        )
        val result = fetcher(query, 0, pageSize)
        if (generation != requestGen) return@withLock
        result.fold(
          onSuccess = { page ->
            val unique = page.items.distinctBy { it.id }
            _state.value = CatalogFeedState(
              query = query,
              items = unique,
              nextPage = 1,
              hasMore = page.hasMore,
              isInitialLoading = false,
              isLoadingMore = false,
              initialError = null,
              paginationError = null,
              hasLoadedOnce = true
            )
          },
          onFailure = { error ->
            if (error is CancellationException) throw error
            _state.value = CatalogFeedState(
              query = query,
              items = emptyList(),
              nextPage = 0,
              hasMore = false,
              isInitialLoading = false,
              isLoadingMore = false,
              initialError = AppErrorMapper.toCustomerMessage(error, errorContext),
              paginationError = null,
              hasLoadedOnce = true
            )
          }
        )
      }
    }
    activeJob = job
    return job
  }

  fun seedInitialPage(query: CatalogQuery, items: List<Product>, hasMore: Boolean) {
    synchronized(loadMoreLock) {
      val current = _state.value
      if (current.query != query) return
      if (current.isInitialLoading || current.isLoadingMore) return
      if (current.nextPage > 1 && current.items.isNotEmpty()) return
      _state.value = CatalogFeedState(
        query = query,
        items = items.distinctBy { it.id },
        nextPage = 1,
        hasMore = hasMore,
        isInitialLoading = false,
        isLoadingMore = false,
        initialError = null,
        paginationError = null,
        hasLoadedOnce = true
      )
    }
  }

  fun clear() {
    activeJob?.cancel()
    synchronized(loadMoreLock) {
      generation++
      queuedNextPage = null
      queuedNextPageGen = -1L
    }
    _state.value = CatalogFeedState()
  }

  fun loadNextPage(): Job? {
    val snapshot = _state.value
    if (snapshot.isInitialLoading || snapshot.isLoadingMore || !snapshot.hasMore) return null
    val requestGen = generation
    val targetPage = snapshot.nextPage
    val targetQuery = snapshot.query

    synchronized(loadMoreLock) {
      if (generation != requestGen) return null
      if (queuedNextPage == targetPage && queuedNextPageGen == requestGen) {
        return null
      }
      queuedNextPage = targetPage
      queuedNextPageGen = requestGen
    }

    val job = scope.launch {
      try {
        mutex.withLock {
          val current = _state.value
          if (generation != requestGen ||
            current.isInitialLoading ||
            current.isLoadingMore ||
            !current.hasMore ||
            current.nextPage != targetPage
          ) {
            return@withLock
          }
          _state.update { it.copy(isLoadingMore = true, paginationError = null) }

          val result = fetcher(targetQuery, targetPage, pageSize)
          if (generation != requestGen) return@withLock

          result.fold(
            onSuccess = { page ->
              _state.update { latest ->
                val merged = (latest.items + page.items).distinctBy { it.id }
                latest.copy(
                  items = merged,
                  nextPage = targetPage + 1,
                  hasMore = page.hasMore,
                  isLoadingMore = false,
                  paginationError = null,
                  hasLoadedOnce = true
                )
              }
            },
            onFailure = { error ->
              if (error is CancellationException) throw error
              _state.update { latest ->
                latest.copy(
                  isLoadingMore = false,
                  paginationError = AppErrorMapper.toCustomerMessage(error, errorContext)
                )
              }
            }
          )
        }
      } finally {
        synchronized(loadMoreLock) {
          if (queuedNextPage == targetPage && queuedNextPageGen == requestGen) {
            queuedNextPage = null
            queuedNextPageGen = -1L
          }
        }
      }
    }
    activeJob = job
    return job
  }

  fun retry(): Job? {
    val current = _state.value
    return if (current.items.isEmpty()) {
      resetAndLoad(current.query)
    } else {
      _state.update { it.copy(paginationError = null, hasMore = true) }
      loadNextPage()
    }
  }

  fun syncWishlistStates(wishlistedIds: Set<String>) {
    _state.update { current ->
      current.copy(
        items = current.items.map { item ->
          val shouldBeWishlisted = item.id in wishlistedIds
          if (item.isWishlisted == shouldBeWishlisted) item else item.copy(isWishlisted = shouldBeWishlisted)
        }
      )
    }
  }

  fun updateSingleWishlist(productId: String, isWishlisted: Boolean) {
    _state.update { current ->
      current.copy(
        items = current.items.map { item ->
          if (item.id == productId) item.copy(isWishlisted = isWishlisted) else item
        }
      )
    }
  }
}
