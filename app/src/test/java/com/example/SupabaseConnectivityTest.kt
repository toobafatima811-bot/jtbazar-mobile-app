package com.example

import com.example.data.supabase.*
import org.junit.Assert.*
import org.junit.Test

class SupabaseConnectivityTest {

  @Test
  fun testSupabaseConfigUrls() {
    assertNotNull("Project URL should not be null", SupabaseConfig.projectUrl)
    assertTrue("Project URL should start with https://", SupabaseConfig.projectUrl.startsWith("https://"))
    assertTrue("Project URL should contain xrrhbnnzjqkqnqdvayjw", SupabaseConfig.projectUrl.contains("xrrhbnnzjqkqnqdvayjw"))

    assertEquals("Project reference must match project id", "xrrhbnnzjqkqnqdvayjw", SupabaseConfig.projectReference)
    assertEquals(
      "${SupabaseConfig.projectUrl}/rest/v1",
      SupabaseConfig.restUrl
    )
    assertEquals(
      "${SupabaseConfig.projectUrl}/auth/v1",
      SupabaseConfig.authUrl
    )
    assertEquals(
      "${SupabaseConfig.projectUrl}/storage/v1",
      SupabaseConfig.storageUrl
    )
  }

  @Test
  fun testSupabaseSessionLifecycle() {
    SupabaseClient.clearSession()
    assertFalse("Should be unauthenticated initially", SupabaseClient.isAuthenticated)
    assertNull("Current user should be null initially", SupabaseClient.currentUser.value)

    val mockUser = SupabaseUser(
      id = "usr_test_123",
      email = "test@jtbazar.pk",
      fullName = "Muhammad Ali",
      phone = "+92 300 1234567"
    )
    val mockSession = SupabaseSession(
      accessToken = "jwt_access_token_abc",
      refreshToken = "jwt_refresh_token_xyz",
      user = mockUser
    )

    SupabaseClient.setSession(mockSession)
    assertTrue("Should be authenticated after setting session", SupabaseClient.isAuthenticated)
    assertEquals("usr_test_123", SupabaseClient.currentUserId)
    assertEquals("test@jtbazar.pk", SupabaseClient.currentUser.value?.email)

    SupabaseClient.clearSession()
    assertFalse("Should be unauthenticated after clearing session", SupabaseClient.isAuthenticated)
    assertNull("Current user should be null after logout", SupabaseClient.currentUser.value)
  }
}
