package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JTBazarFirebaseMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    super.onNewToken(token)
    android.util.Log.i("JTBazarFCM", "[FCM] Firebase issued new Android token (length=" + token.length + ")")
    FirebasePushManager.saveCachedToken(applicationContext, token)
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      FirebasePushManager.register(applicationContext, token)
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val title = message.notification?.title ?: message.data["title"] ?: "JTBazar"
    val body = message.notification?.body ?: message.data["body"] ?: com.example.localization.AppLanguageManager.tr(
      "You have a new update.",
      "آپ کے لیے ایک نئی اپ ڈیٹ ہے۔"
    )
    val channelId = FirebasePushManager.NOTIFICATION_CHANNEL_ID
    val manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
        NotificationChannel(
          channelId,
          com.example.localization.AppLanguageManager.tr("JTBazar notifications", "جے ٹی بازار اطلاعات"),
          NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
          description = com.example.localization.AppLanguageManager.tr(
            "Order updates, delivery alerts and important JTBazar notifications",
            "آرڈر کی تازہ ترین صورتحال، ترسیل کے الرٹس اور جے ٹی بازار کی اہم اطلاعات"
          )
        setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
          android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        enableVibration(true)
      })
    }
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      message.data["link"]?.let { putExtra("notification_link", it) }
      message.data["notification_id"]?.let { putExtra("notification_id", it) }
      message.data["kind"]?.let { putExtra("notification_kind", it) }
      message.data["related_order_id"]?.let { putExtra("related_order_id", it) }
      message.data["audience"]?.let { putExtra("notification_audience", it) }
    }
    val stableKey = message.data["notification_id"] ?: message.messageId ?: message.data["related_order_id"] ?: title
    val requestCode = stableKey.hashCode()
    val pending = PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(this, channelId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setAutoCancel(true).setContentIntent(pending).build()
    if (FirebasePushManager.hasNotificationPermission(this)) {
      // A stable ID updates an already displayed copy instead of creating duplicates.
      NotificationManagerCompat.from(this).notify(requestCode, notification)
    }
  }
}