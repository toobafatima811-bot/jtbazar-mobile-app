package com.example.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class ReverseGeocodedAddress(
  val street: String = "",
  val area: String = "",
  val city: String = "",
  val province: String = "",
  val postalCode: String? = null,
  val fullAddress: String = ""
)

fun ReverseGeocodedAddress.toReadableFullAddress(): String {
  val explicit = fullAddress.trim()
  if (explicit.isNotBlank()) return explicit
  return listOf(street, area, city, province, postalCode.orEmpty())
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString(", ")
}

object AddressReverseGeocoder {
  private val nominatimClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(4, TimeUnit.SECONDS)
      .readTimeout(4, TimeUnit.SECONDS)
      .build()
  }

  suspend fun resolve(context: Context, latitude: Double, longitude: Double): ReverseGeocodedAddress? =
    withContext(Dispatchers.IO) {
      resolveFromAndroidGeocoder(context, latitude, longitude)
        ?: resolveFromNominatim(context.packageName, latitude, longitude)
    }

  private suspend fun resolveFromAndroidGeocoder(
    context: Context,
    latitude: Double,
    longitude: Double
  ): ReverseGeocodedAddress? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      withTimeoutOrNull(5_000L) {
        suspendCancellableCoroutine { cont ->
          runCatching {
            geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
              override fun onGeocode(addresses: MutableList<Address>) {
                if (cont.isActive) cont.resume(addresses.firstOrNull())
              }

              override fun onError(errorMessage: String?) {
                if (cont.isActive) cont.resume(null)
              }
            })
          }.onFailure {
            if (cont.isActive) cont.resume(null)
          }
        }
      }
    } else {
      withTimeoutOrNull(5_000L) {
        runCatching {
          @Suppress("DEPRECATION")
          geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
        }.getOrNull()
      }
    }
    address ?: return null
    val addressLine = address.getAddressLine(0)?.trim().orEmpty()
    val street = listOfNotNull(
      address.subThoroughfare?.takeIf { it.isNotBlank() },
      address.thoroughfare?.takeIf { it.isNotBlank() }
    ).joinToString(" ").ifBlank {
      addressLine
    }
    val area = address.subLocality?.takeIf { it.isNotBlank() }
      ?: address.locality?.takeIf { it.isNotBlank() }
      ?: address.subAdminArea.orEmpty()
    val city = address.locality?.takeIf { it.isNotBlank() }
      ?: address.subAdminArea?.takeIf { it.isNotBlank() }
      ?: address.adminArea.orEmpty()
    val province = address.adminArea.orEmpty()
    val postalCode = address.postalCode?.takeIf { it.isNotBlank() }
    val combinedFull = addressLine.ifBlank {
      listOf(street, area, city, province, postalCode.orEmpty())
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
    }
    return ReverseGeocodedAddress(
      street = street,
      area = area,
      city = city,
      province = province,
      postalCode = postalCode,
      fullAddress = combinedFull
    )
  }

  private fun resolveFromNominatim(
    packageName: String,
    latitude: Double,
    longitude: Double
  ): ReverseGeocodedAddress? = runCatching {
    val url = String.format(
      Locale.US,
      "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&addressdetails=1",
      latitude,
      longitude
    )
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", "$packageName/1.0")
      .header("Accept", "application/json")
      .build()
    nominatimClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) return null
      val body = response.body?.string().orEmpty()
      if (body.isBlank()) return null
      val json = JSONObject(body)
      val addr = json.optJSONObject("address") ?: JSONObject()
      val roadName = addr.optString("road")
        .ifBlank { addr.optString("pedestrian") }
        .ifBlank { addr.optString("residential") }
        .ifBlank { addr.optString("footway") }
      val street = listOf(
        addr.optString("house_number"),
        roadName
      ).filter { it.isNotBlank() }.joinToString(" ")
        .ifBlank { json.optString("name") }
        .ifBlank { json.optString("display_name").split(",").take(2).joinToString(",").trim() }
      val area = addr.optString("suburb")
        .ifBlank { addr.optString("neighbourhood") }
        .ifBlank { addr.optString("quarter") }
        .ifBlank { addr.optString("town") }
      val city = addr.optString("city")
        .ifBlank { addr.optString("town") }
        .ifBlank { addr.optString("village") }
        .ifBlank { addr.optString("county") }
        .ifBlank { addr.optString("state_district") }
      val province = addr.optString("state")
      val postcode = addr.optString("postcode").ifBlank { null }
      val displayName = json.optString("display_name").trim()
      if (street.isBlank() && area.isBlank() && city.isBlank() && displayName.isBlank()) return null
      val combinedFull = listOf(street, area, city, province, postcode.orEmpty())
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
        .ifBlank { displayName }
      ReverseGeocodedAddress(
        street = street,
        area = area.ifBlank { city },
        city = city.ifBlank { area },
        province = province,
        postalCode = postcode,
        fullAddress = combinedFull
      )
    }
  }.getOrNull()
}
