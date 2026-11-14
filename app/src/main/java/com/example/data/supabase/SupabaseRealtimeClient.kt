package com.example.data.supabase

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight Supabase Realtime (Phoenix Channels) WebSocket client.
 * Subscribes to `public.promotions` (and product campaigns / heroes) so admin
 * creates, edits, approvals, rejections, disables, and deletions reflect
 * automatically on the Android homepage without manual refreshes.
 */
class SupabaseRealtimeClient(
  private val onPromotionsChanged: () -> Unit,
  private val onCatalogChanged: () -> Unit
) {
  companion object {
    private const val TAG = "SupabaseRealtime"
    private const val HEARTBEAT_INTERVAL_MS = 25_000L
    private const val DEBOUNCE_MS = 350L
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val isRunning = AtomicBoolean(false)
  private val refCounter = AtomicInteger(1)

  @Volatile private var webSocket: WebSocket? = null
  private var heartbeatJob: Job? = null
  private var reconnectJob: Job? = null
  private var promoDebounceJob: Job? = null
  private var catalogDebounceJob: Job? = null
  private var reconnectAttempts = 0

  fun start() {
    if (!SupabaseConfig.isConfigured) return
    if (isRunning.compareAndSet(false, true)) {
      reconnectAttempts = 0
      connect()
    }
  }

  fun stop() {
    if (!isRunning.compareAndSet(true, false)) return
    heartbeatJob?.cancel()
    reconnectJob?.cancel()
    promoDebounceJob?.cancel()
    catalogDebounceJob?.cancel()
    runCatching { webSocket?.close(1000, "Lifecycle paused") }
    webSocket = null
  }

  private fun buildRealtimeUrl(): String {
    val wsBase = SupabaseConfig.projectUrl
      .replaceFirst("https://", "wss://", ignoreCase = true)
      .replaceFirst("http://", "ws://", ignoreCase = true)
    return "$wsBase/realtime/v1/websocket?apikey=${SupabaseConfig.publishableKey}&vsn=1.0.0"
  }

  private fun connect() {
    if (!isRunning.get()) return
    runCatching {
      val request = Request.Builder()
        .url(buildRealtimeUrl())
        .build()

      webSocket = SupabaseClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          Log.d(TAG, "Connected to Supabase Realtime")
          reconnectAttempts = 0
          subscribeTable(webSocket, "realtime:public:promotions", "promotions")
          subscribeTable(webSocket, "realtime:public:homepage_heroes", "homepage_heroes")
          subscribeTable(webSocket, "realtime:public:product_campaigns", "product_campaigns")
          startHeartbeat(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          handleIncomingMessage(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          Log.d(TAG, "Realtime closed ($code): $reason")
          cleanupSocketAndScheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          Log.d(TAG, "Realtime connection unavailable (${t.message}); fallback polling remains active")
          cleanupSocketAndScheduleReconnect()
        }
      })
    }.onFailure {
      cleanupSocketAndScheduleReconnect()
    }
  }

  private fun subscribeTable(ws: WebSocket, topic: String, table: String) {
    val pgRule = JSONObject()
      .put("event", "*")
      .put("schema", "public")
      .put("table", table)
    val config = JSONObject()
      .put("broadcast", JSONObject().put("self", false))
      .put("presence", JSONObject().put("key", ""))
      .put("postgres_changes", JSONArray().put(pgRule))
    val payload = JSONObject().put("config", config)
    val joinMsg = JSONObject()
      .put("topic", topic)
      .put("event", "phx_join")
      .put("payload", payload)
      .put("ref", refCounter.getAndIncrement().toString())
    ws.send(joinMsg.toString())
  }

  private fun startHeartbeat(ws: WebSocket) {
    heartbeatJob?.cancel()
    heartbeatJob = scope.launch {
      while (isActive && isRunning.get()) {
        delay(HEARTBEAT_INTERVAL_MS)
        val hb = JSONObject()
          .put("topic", "phoenix")
          .put("event", "heartbeat")
          .put("payload", JSONObject())
          .put("ref", refCounter.getAndIncrement().toString())
        val sent = runCatching { ws.send(hb.toString()) }.getOrDefault(false)
        if (!sent) break
      }
    }
  }

  private fun handleIncomingMessage(text: String) {
    val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
    val event = msg.optString("event")
    val topic = msg.optString("topic")
    if (event != "postgres_changes") return

    val table = msg.optJSONObject("payload")
      ?.optJSONObject("data")
      ?.optString("table")
      ?.takeIf { it.isNotBlank() }
      ?: topic.substringAfterLast(":")

    when (table) {
      "promotions", "homepage_heroes" -> {
        promoDebounceJob?.cancel()
        promoDebounceJob = scope.launch {
          delay(DEBOUNCE_MS)
          onPromotionsChanged()
        }
      }
      "product_campaigns" -> {
        catalogDebounceJob?.cancel()
        catalogDebounceJob = scope.launch {
          delay(DEBOUNCE_MS)
          onCatalogChanged()
        }
      }
    }
  }

  private fun cleanupSocketAndScheduleReconnect() {
    heartbeatJob?.cancel()
    webSocket = null
    if (!isRunning.get()) return
    reconnectJob?.cancel()
    reconnectJob = scope.launch {
      val backoffMs = when (reconnectAttempts) {
        0 -> 3_000L
        1 -> 6_000L
        2 -> 15_000L
        else -> 30_000L
      }
      reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(5)
      delay(backoffMs)
      if (isRunning.get()) connect()
    }
  }
}
