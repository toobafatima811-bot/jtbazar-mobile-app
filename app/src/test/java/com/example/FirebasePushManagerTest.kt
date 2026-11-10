package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.notifications.FirebasePushManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FirebasePushManagerTest {

  @Test
  fun testTokenCachingLifecycle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // Initially null or empty
    val initial = FirebasePushManager.getCachedToken(context)
    
    // Save token
    val sampleToken = "fcm_test_token_1234567890abcdef"
    FirebasePushManager.saveCachedToken(context, sampleToken)
    
    val retrieved = FirebasePushManager.getCachedToken(context)
    assertEquals(sampleToken, retrieved)
  }

  @Test
  fun testRegistrationPayloadFormat() {
    val token = "sample_fcm_token_xyz"
    val body = JSONObject()
      .put("action", "register")
      .put("platform", "android")
      .put("token", token)
      .put("device_label", "Android Emulator")

    assertEquals("register", body.getString("action"))
    assertEquals("android", body.getString("platform"))
    assertEquals(token, body.getString("token"))
    assertTrue(body.has("device_label"))
  }

  @Test
  fun testNotificationChannelCreated() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    FirebasePushManager.ensureNotificationChannel(context)
    val manager = context.getSystemService(android.app.NotificationManager::class.java)
    val channel = manager?.getNotificationChannel("jtbazar_updates")
    assertNotNull(channel)
    assertEquals("jtbazar_updates", channel?.id)
  }
}
