package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.localization.AppLanguageManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface LocationFetchResult {
  data class Success(val location: Location) : LocationFetchResult
  data class NeedsResolution(val exception: ResolvableApiException) : LocationFetchResult
  data class Failure(val message: String) : LocationFetchResult
}

object LocationResolver {
  fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  private fun isUsable(location: Location?): Boolean {
    return location != null &&
      location.latitude in -90.0..90.0 &&
      location.longitude in -180.0..180.0 &&
      !(location.latitude == 0.0 && location.longitude == 0.0)
  }

  @SuppressLint("MissingPermission")
  suspend fun fetchCurrentLocation(context: Context): LocationFetchResult {
    if (!hasLocationPermission(context)) {
      return LocationFetchResult.Failure(
        AppLanguageManager.tr(
          "Location permission is required.",
          "مقام تک رسائی کی اجازت ضروری ہے۔"
        )
      )
    }

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val fused = LocationServices.getFusedLocationProviderClient(context)

    // 1. Check device location settings first; if off, offer native turn-on dialog when possible.
    if (!LocationManagerCompat.isLocationEnabled(manager)) {
      val resolvable = checkLocationSettingsResolution(context)
      return if (resolvable != null) {
        LocationFetchResult.NeedsResolution(resolvable)
      } else {
        LocationFetchResult.Failure(
          AppLanguageManager.tr(
            "Device location is turned off. Turn on Location / GPS and try again.",
            "آلے کی لوکیشن بند ہے۔ لوکیشن / جی پی ایس آن کر کے دوبارہ کوشش کریں۔"
          )
        )
      }
    }

    // 2. Fast path: if a recent (< 60s) cached location already exists, return immediately.
    val initialCached = findBestCachedLocation(fused, manager, maxAgeMs = 60_000L)
    if (initialCached != null) {
      return LocationFetchResult.Success(initialCached)
    }

    // 3. Try Fused High Accuracy current fix (4s timeout)
    val highAccuracy = awaitFusedCurrentLocation(fused, Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
    if (isUsable(highAccuracy)) return LocationFetchResult.Success(highAccuracy!!)

    // 4. Fallback to Balanced Power (Wi-Fi / Cell tower indoor fix, 3.5s timeout)
    val balanced = awaitFusedCurrentLocation(fused, Priority.PRIORITY_BALANCED_POWER_ACCURACY, 3_500L)
    if (isUsable(balanced)) return LocationFetchResult.Success(balanced!!)

    // 5. Fallback to a single active fused location update (helps when provider was cold)
    val singleUpdate = awaitSingleFusedUpdate(fused, 3_500L)
    if (isUsable(singleUpdate)) return LocationFetchResult.Success(singleUpdate!!)

    // 6. Fallback to platform LocationManager.getCurrentLocation (API 30+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val providers = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
          add(LocationManager.FUSED_PROVIDER)
        }
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
      }
      for (provider in providers) {
        val loc = awaitPlatformCurrentLocation(context, manager, provider, 3_000L)
        if (isUsable(loc)) return LocationFetchResult.Success(loc!!)
      }
    }

    // 7. Final fallback: freshest cached location of any age from Fused or LocationManager
    val freshestCached = findBestCachedLocation(fused, manager, maxAgeMs = null)
    if (freshestCached != null) {
      return LocationFetchResult.Success(freshestCached)
    }

    val resolvable = checkLocationSettingsResolution(context)
    return if (resolvable != null) {
      LocationFetchResult.NeedsResolution(resolvable)
    } else {
      LocationFetchResult.Failure(
        AppLanguageManager.tr(
          "Could not get current location. Move near a window or turn on Wi-Fi/GPS and try again.",
          "موجودہ لوکیشن حاصل نہیں ہو سکی۔ کھڑکی کے قریب جائیں یا وائی فائی/جی پی ایس آن کر کے دوبارہ کوشش کریں۔"
        )
      )
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun findBestCachedLocation(
    fused: com.google.android.gms.location.FusedLocationProviderClient,
    manager: LocationManager,
    maxAgeMs: Long?
  ): Location? {
    val now = System.currentTimeMillis()
    val lastFused = awaitFusedLastLocation(fused, 1_500L)
    val candidates = buildList {
      if (isUsable(lastFused)) add(lastFused!!)
      runCatching { manager.getProviders(true) }.getOrDefault(emptyList()).forEach { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let { loc ->
          if (isUsable(loc)) add(loc)
        }
      }
    }.filter { loc ->
      maxAgeMs == null || (loc.time > 0L && (now - loc.time) in 0L..maxAgeMs)
    }
    return candidates.maxByOrNull { it.time }
  }

  private suspend fun checkLocationSettingsResolution(context: Context): ResolvableApiException? {
    val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L).build()
    val settingsReq = LocationSettingsRequest.Builder()
      .addLocationRequest(req)
      .setAlwaysShow(true)
      .build()
    return withTimeoutOrNull(2_500L) {
      suspendCancellableCoroutine { cont ->
        LocationServices.getSettingsClient(context)
          .checkLocationSettings(settingsReq)
          .addOnSuccessListener { if (cont.isActive) cont.resume(null) }
          .addOnFailureListener { ex ->
            if (cont.isActive) cont.resume(ex as? ResolvableApiException)
          }
      }
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun awaitFusedCurrentLocation(
    fused: com.google.android.gms.location.FusedLocationProviderClient,
    priority: Int,
    timeoutMs: Long
  ): Location? {
    val tokenSource = CancellationTokenSource()
    return withTimeoutOrNull(timeoutMs) {
      suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { tokenSource.cancel() }
        val request = CurrentLocationRequest.Builder()
          .setPriority(priority)
          .setMaxUpdateAgeMillis(30_000L)
          .setDurationMillis(timeoutMs)
          .build()
        fused.getCurrentLocation(request, tokenSource.token)
          .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
          .addOnFailureListener { if (cont.isActive) cont.resume(null) }
          .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
      }
    }.also {
      tokenSource.cancel()
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun awaitSingleFusedUpdate(
    fused: com.google.android.gms.location.FusedLocationProviderClient,
    timeoutMs: Long
  ): Location? {
    return withTimeoutOrNull(timeoutMs) {
      suspendCancellableCoroutine { cont ->
        val callback = object : LocationCallback() {
          override fun onLocationResult(result: LocationResult) {
            fused.removeLocationUpdates(this)
            if (cont.isActive) cont.resume(result.lastLocation)
          }
        }
        cont.invokeOnCancellation { fused.removeLocationUpdates(callback) }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1_000L)
          .setWaitForAccurateLocation(false)
          .setMaxUpdates(1)
          .build()
        runCatching {
          fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener {
              fused.removeLocationUpdates(callback)
              if (cont.isActive) cont.resume(null)
            }
        }.onFailure {
          if (cont.isActive) cont.resume(null)
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun awaitPlatformCurrentLocation(
    context: Context,
    manager: LocationManager,
    provider: String,
    timeoutMs: Long
  ): Location? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val signal = CancellationSignal()
    return withTimeoutOrNull(timeoutMs) {
      suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { signal.cancel() }
        runCatching {
          manager.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
            if (cont.isActive) cont.resume(loc)
          }
        }.onFailure {
          if (cont.isActive) cont.resume(null)
        }
      }
    }.also {
      signal.cancel()
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun awaitFusedLastLocation(
    fused: com.google.android.gms.location.FusedLocationProviderClient,
    timeoutMs: Long
  ): Location? {
    return withTimeoutOrNull(timeoutMs) {
      suspendCancellableCoroutine { cont ->
        fused.lastLocation
          .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
          .addOnFailureListener { if (cont.isActive) cont.resume(null) }
      }
    }
  }
}
