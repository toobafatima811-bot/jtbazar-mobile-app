package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.Product
import com.example.data.model.ProductVariant
import com.example.localization.AppLanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object ProductShareHelper {
  private const val TAG = "ProductShareHelper"
  private const val BASE_WEB_URL = "https://jtbazar.vercel.app"

  private val imageClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(6, TimeUnit.SECONDS)
      .build()
  }

  fun buildProductUrl(productId: String): String {
    val cleanId = productId.trim()
    return "$BASE_WEB_URL/products/$cleanId"
  }

  fun buildShareText(product: Product, selectedVariant: ProductVariant? = null): String {
    val sellingPrice = selectedVariant?.priceOverride ?: product.price
    val formattedPrice = "Rs. ${String.format(Locale.US, "%,d", sellingPrice)}"
    val titleLine = if (selectedVariant != null) {
      "${product.title} (${selectedVariant.displayText})"
    } else {
      product.title
    }
    val snippet = product.craftStory
      .trim()
      .replace(Regex("\\s+"), " ")
      .take(180)
    val url = buildProductUrl(product.id)

    return buildString {
      appendLine(titleLine)
      appendLine(AppLanguageManager.tr("Price: $formattedPrice", "قیمت: $formattedPrice"))
      if (snippet.isNotBlank()) {
        appendLine(snippet)
      }
      append(url)
    }.trim()
  }

  fun buildShareIntent(
    context: Context,
    product: Product,
    selectedVariant: ProductVariant? = null,
    cachedImageUri: Uri? = null
  ): Intent {
    val shareText = buildShareText(product, selectedVariant)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      putExtra(Intent.EXTRA_SUBJECT, product.title)
      putExtra(Intent.EXTRA_TEXT, shareText)
      if (cachedImageUri != null) {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, cachedImageUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, product.title, cachedImageUri)
      } else {
        type = "text/plain"
      }
    }
    return Intent.createChooser(
      sendIntent,
      AppLanguageManager.tr("Share Product", "پروڈکٹ شیئر کریں")
    ).apply {
      if (cachedImageUri != null) {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      if (context !is Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }

  fun shareProduct(
    context: Context,
    product: Product,
    selectedVariant: ProductVariant? = null,
    scope: CoroutineScope
  ) {
    val activeImageUrl = selectedVariant?.imageUrl?.takeIf { it.isNotBlank() }
      ?: product.imageUrl?.takeIf { it.isNotBlank() }
      ?: product.imageUrls.firstOrNull { it.isNotBlank() }

    scope.launch {
      val imageUri = if (!activeImageUrl.isNullOrBlank()) {
        withContext(Dispatchers.IO) {
          cacheImageForShare(context, product.id, activeImageUrl)
        }
      } else {
        null
      }
      val chooser = buildShareIntent(
        context = context,
        product = product,
        selectedVariant = selectedVariant,
        cachedImageUri = imageUri
      )
      runCatching {
        context.startActivity(chooser)
      }.onFailure { err ->
        Log.w(TAG, "Unable to launch Android share chooser: ${err.message}")
      }
    }
  }

  internal fun cacheImageForShare(
    context: Context,
    productId: String,
    imageUrl: String
  ): Uri? = runCatching {
    val shareDir = File(context.cacheDir, "shared_products").apply { mkdirs() }
    val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
    shareDir.listFiles()?.forEach { old ->
      if (old.lastModified() < cutoff) old.delete()
    }

    val request = Request.Builder().url(imageUrl).get().build()
    imageClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) return@runCatching null
      val body = response.body ?: return@runCatching null
      val bytes = body.bytes()
      if (bytes.isEmpty()) return@runCatching null
      val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
      val ext = when {
        contentType.contains("png") || imageUrl.contains(".png", ignoreCase = true) -> "png"
        contentType.contains("webp") || imageUrl.contains(".webp", ignoreCase = true) -> "webp"
        else -> "jpg"
      }
      val safeId = productId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "item" }
      val target = File(shareDir, "share_${safeId}_${System.currentTimeMillis()}.$ext")
      target.writeBytes(bytes)
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }
  }.onFailure { err ->
    Log.w(TAG, "Image share cache failed, falling back to text share: ${err.message}")
  }.getOrNull()
}
