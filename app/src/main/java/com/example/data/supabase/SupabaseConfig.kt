package com.example.data.supabase

import com.example.BuildConfig

/**
 * Configuration manager for Supabase backend.
 * Reads credentials securely injected via BuildConfig from .env / .env.example.
 */
object SupabaseConfig {
  val projectUrl: String by lazy {
    val raw = BuildConfig.SUPABASE_URL.trim()
    when {
      raw.isEmpty() -> "https://xrrhbnnzjqkqnqdvayjw.supabase.co"
      raw.startsWith("http://") || raw.startsWith("https://") -> raw.removeSuffix("/")
      else -> "https://${raw.removeSuffix("/")}"
    }
  }

  val publishableKey: String by lazy {
    val raw = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
    if (raw.isBlank() || raw.contains("placeholder")) {
      "sb_publishable_fBu4PTDFMxgcNZ8luVkpOQ_lIIbZUHI"
    } else {
      raw
    }
  }

  val projectReference: String by lazy {
    // Extract project reference from URL (e.g. https://xrrhbnnzjqkqnqdvayjw.supabase.co -> xrrhbnnzjqkqnqdvayjw)
    val host = projectUrl.removePrefix("https://").removePrefix("http://")
    host.split(".").firstOrNull() ?: "xrrhbnnzjqkqnqdvayjw"
  }

  val restUrl: String
    get() = "$projectUrl/rest/v1"

  val authUrl: String
    get() = "$projectUrl/auth/v1"

  val storageUrl: String
    get() = "$projectUrl/storage/v1"

  val isConfigured: Boolean
    get() = projectUrl.isNotBlank() && publishableKey.isNotBlank() && !publishableKey.contains("placeholder")
}
