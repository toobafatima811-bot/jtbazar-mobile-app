package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverCenterScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val user by SupabaseClient.currentUser.collectAsState()
  var enabled by remember { mutableStateOf(false) }
  var application by remember { mutableStateOf<JSONObject?>(null) }
  var deliveries by remember { mutableStateOf(listOf<JSONObject>()) }
  var fullName by remember { mutableStateOf(user?.fullName.orEmpty()) }
  var phone by remember { mutableStateOf(user?.phone.orEmpty()) }
  var vehicle by remember { mutableStateOf("Motorcycle") }
  var loading by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var message by remember { mutableStateOf<String?>(null) }

  suspend fun load() {
    loading = true; error = null
    try {
      val settings = SupabaseClient.get("store_settings?select=driver_system_enabled&limit=1").getOrThrow()
      val settingRows = JSONArray(settings)
      enabled = settingRows.length() > 0 && settingRows.getJSONObject(0).optBoolean("driver_system_enabled", false)
      val uid = user?.id ?: throw IllegalStateException("Please sign in first.")
      val appRows = JSONArray(SupabaseClient.get("driver_applications?select=id,full_name,phone,vehicle_type,status,admin_note&user_id=eq.$uid&limit=1").getOrThrow())
      application = if (appRows.length() > 0) appRows.getJSONObject(0) else null
      deliveries = if (application?.optString("status") == "approved") {
        JSONArray(SupabaseClient.rpc("driver_list_assigned_orders").getOrThrow()).let { rows -> (0 until rows.length()).map { rows.getJSONObject(it) } }
      } else emptyList()
    } catch (e: Exception) { error = e.message ?: "Could not load driver information." }
    loading = false
  }
  LaunchedEffect(user?.id) { load() }

  Scaffold(topBar = { TopAppBar(title = { Text("JTBazar Driver") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
      error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
      message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
      if (!enabled) {
        item { Card { Text("Driver applications are currently closed. Please check again later.", Modifier.padding(18.dp)) } }
      } else if (application == null) {
        item { Text("Become a JTBazar Driver", style = MaterialTheme.typography.headlineSmall) }
        item { OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(phone, { phone = it }, label = { Text("Mobile number") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Vehicle type (e.g. Motorcycle)") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(enabled = !busy && fullName.isNotBlank() && phone.isNotBlank(), onClick = {
          scope.launch {
            busy = true; message = null
            val body = JSONObject().put("user_id", user?.id).put("full_name", fullName.trim()).put("phone", phone.trim()).put("vehicle_type", vehicle).put("status", "pending")
            val result = SupabaseClient.post("driver_applications", body.toString(), preferHeader = "return=representation")
            if (result.isSuccess) { message = "Application submitted. Admin approval is required."; load() } else error = result.exceptionOrNull()?.message
            busy = false
          }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Submitting…" else "Submit driver application") } }
      } else {
        val status = application?.optString("status") ?: "pending"
        item { Text("Application status: ${status.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleLarge) }
        application?.optString("admin_note")?.takeIf { it.isNotBlank() && it != "null" }?.let { note -> item { Text("Admin note: $note") } }
        if (status == "approved") {
          item { Text("My assigned deliveries", style = MaterialTheme.typography.titleMedium) }
          if (deliveries.isEmpty()) item { Text("No assigned deliveries right now.") }
          items(deliveries, key = { it.optString("assignment_id") }) { delivery ->
            val lat = delivery.optDouble("delivery_lat", Double.NaN)
            val lng = delivery.optDouble("delivery_lng", Double.NaN)
            Card(Modifier.fillMaxWidth()) {
              Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Order ${delivery.optString("order_number")}", style = MaterialTheme.typography.titleMedium)
                Text(delivery.optString("customer_name"))
                Text(delivery.optString("customer_phone"))
                Text(delivery.optJSONObject("delivery_address")?.toString() ?: delivery.optString("delivery_location_name").ifBlank { "Saved delivery address" })
                Text("Status: ${delivery.optString("assignment_status")}")
                val itemRows = delivery.optJSONArray("items")
                if (itemRows != null) for (index in 0 until itemRows.length()) { val item = itemRows.optJSONObject(index) ?: continue; Text("• ${item.optString("name")} × ${item.optInt("quantity", 1)}") }
                if (lat.isFinite() && lng.isFinite()) {
                  OutlinedButton(onClick = {
                    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving&dir_action=navigate")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                  }) { Icon(Icons.Filled.Navigation, null); Spacer(Modifier.width(6.dp)); Text("Start navigation") }
                } else Text("Customer map pin is unavailable. Contact support before dispatch.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (delivery.optString("assignment_status") == "assigned") Button(enabled = !busy, onClick = {
                    scope.launch { busy = true; val r = SupabaseClient.rpc("driver_update_delivery_status", JSONObject().put("p_assignment_id", delivery.optString("assignment_id")).put("p_action", "picked_up").toString()); if (r.isSuccess) load() else error = r.exceptionOrNull()?.message; busy = false }
                  }) { Text("Picked up") }
                  if (delivery.optString("assignment_status") != "delivered") Button(enabled = !busy, onClick = {
                    scope.launch { busy = true; val r = SupabaseClient.rpc("driver_update_delivery_status", JSONObject().put("p_assignment_id", delivery.optString("assignment_id")).put("p_action", "delivered").toString()); if (r.isSuccess) load() else error = r.exceptionOrNull()?.message; busy = false }
                  }) { Text("Delivered") }
                }
              }
            }
          }
          item { OutlinedButton(onClick = { scope.launch { load() } }, modifier = Modifier.fillMaxWidth()) { Text("Refresh deliveries") } }
        }
      }
    }
  }
}
