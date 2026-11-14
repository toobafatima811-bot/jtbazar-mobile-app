package com.example.data.supabase

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes real internet reachability, not just whether Wi-Fi/mobile data is enabled.
 * A null value means the first connectivity check has not completed yet.
 */
object NetworkConnectivity {
  private val _isOnline = MutableStateFlow<Boolean?>(null)
  val isOnline: StateFlow<Boolean?> = _isOnline.asStateFlow()

  @Volatile private var connectivityManager: ConnectivityManager? = null
  @Volatile private var callback: ConnectivityManager.NetworkCallback? = null

  @Synchronized
  fun start(context: Context) {
    if (callback != null) return
    val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: run {
        _isOnline.value = false
        return
      }
    connectivityManager = manager

    fun update(network: Network?) {
      val capabilities = network?.let { manager.getNetworkCapabilities(it) }
      val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
      val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
      } else {
        hasInternet
      }
      _isOnline.value = hasInternet && validated
    }

    val networkCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        update(network)
      }

      override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
          hasInternet
        }
        _isOnline.value = hasInternet && validated
      }

      override fun onLost(network: Network) {
        update(manager.activeNetwork)
      }
    }

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        manager.registerDefaultNetworkCallback(networkCallback)
      } else {
        @Suppress("DEPRECATION")
        manager.registerNetworkCallback(
          android.net.NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build(),
          networkCallback
        )
      }
      callback = networkCallback
      update(manager.activeNetwork)
    } catch (_: Exception) {
      _isOnline.value = false
    }
  }

  @Synchronized
  fun stop(context: Context) {
    val manager = connectivityManager ?: return
    val registered = callback ?: return
    try {
      manager.unregisterNetworkCallback(registered)
    } catch (_: Exception) {
    } finally {
      callback = null
      connectivityManager = null
    }
  }
}
