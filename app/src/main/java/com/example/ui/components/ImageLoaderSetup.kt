package com.example.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import java.io.File

/**
 * Shared singleton Coil ImageLoader tuned for smooth scrolling across large e-commerce grids.
 * - Memory cache: 25% of app heap
 * - Disk cache: 80 MB for product & category thumbnails
 * - Downsamples cleanly with RGB_565 when alpha is unneeded
 */
object JTBazarImageLoader {
  @Volatile private var instance: ImageLoader? = null

  fun get(context: Context): ImageLoader {
    instance?.let { return it }
    return synchronized(this) {
      instance ?: ImageLoader.Builder(context.applicationContext)
        .memoryCache {
          MemoryCache.Builder(context.applicationContext)
            .maxSizePercent(0.25)
            .build()
        }
        .diskCache {
          DiskCache.Builder()
            .directory(File(context.applicationContext.cacheDir, "jtbazar_image_cache"))
            .maxSizeBytes(80L * 1024L * 1024L)
            .build()
        }
        .respectCacheHeaders(false)
        .crossfade(150)
        .allowRgb565(true)
        .components {
          add(GifDecoder.Factory())
        }
        .build()
        .also { instance = it }
    }
  }
}

@Composable
fun rememberSizedImageRequest(
  data: Any?,
  widthPx: Int = 420,
  heightPx: Int = 420
): ImageRequest {
  val context = LocalContext.current
  return remember(data, widthPx, heightPx, context) {
    ImageRequest.Builder(context)
      .data(data)
      .size(widthPx, heightPx)
      .scale(Scale.FILL)
      .precision(Precision.INEXACT)
      .memoryCachePolicy(CachePolicy.ENABLED)
      .diskCachePolicy(CachePolicy.ENABLED)
      .crossfade(150)
      .build()
  }
}
