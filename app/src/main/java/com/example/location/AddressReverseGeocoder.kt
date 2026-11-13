package com.example.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class ReverseGeocodedAddress(
  val street: String = "",
  val area: String = "",
  val city: String = "",
  val province: String = "",
  val postalCode: String? = null
)

object AddressReverseGeocoder {
  suspend fun resolve(context: Context, latitude: Double, longitude: Double): ReverseGeocodedAddress? =
    withContext(Dispatchers.IO) {
      if (!Geocoder.isPresent()) return@withContext null
      runCatching {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
          ?: return@runCatching null
        val street = listOfNotNull(address.subThoroughfare?.takeIf { it.isNotBlank() },
          address.thoroughfare?.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank {
          address.getAddressLine(0).orEmpty()
        }
        ReverseGeocodedAddress(
          street = street,
          area = address.subLocality?.takeIf { it.isNotBlank() }
            ?: address.locality?.takeIf { it.isNotBlank() }
            ?: address.subAdminArea.orEmpty(),
          city = address.locality?.takeIf { it.isNotBlank() }
            ?: address.subAdminArea?.takeIf { it.isNotBlank() }
            ?: address.adminArea.orEmpty(),
          province = address.adminArea.orEmpty(),
          postalCode = address.postalCode?.takeIf { it.isNotBlank() }
        )
      }.getOrNull()
    }
}
