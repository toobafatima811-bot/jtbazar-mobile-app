package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun driverAddressText(delivery: JSONObject): String {
  val raw = delivery.opt("delivery_address")
  val address = when (raw) {
    is JSONObject -> raw
    is String -> try { JSONObject(raw) } catch (_: Exception) { null }
    else -> null
  }
  if (address != null) {
    val parts = listOf(
      address.optString("address_line_1"),
      address.optString("address_line_2"),
      address.optString("city"),
      address.optString("province"),
      address.optString("postal_code")
    ).map { it.trim() }.filter { it.isNotBlank() && it != "null" }
    if (parts.isNotEmpty()) return parts.distinct().joinToString(", ")
  }
  return delivery.optString("delivery_location_name")
    .takeIf { it.isNotBlank() && it != "null" }
    ?: "Saved delivery address"
}

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
    loading = true
    error = null
    try {
      val access = SupabaseClient.rpc("get_my_driver_access").getOrThrow()
      enabled = access.trim().equals("true", ignoreCase = true)
      val uid = user?.id ?: throw IllegalStateException("Please sign in first.")
      val appRows = JSONArray(SupabaseClient.get("driver_applications?select=id,full_name,phone,vehicle_type,status,admin_note&user_id=eq.$uid&limit=1").getOrThrow())
      application = if (appRows.length() > 0) appRows.getJSONObject(0) else null
      deliveries = if (application?.optString("status") == "approved") {
        JSONArray(SupabaseClient.rpc("driver_list_assigned_orders").getOrThrow()).let { rows ->
          (0 until rows.length()).map { rows.getJSONObject(it) }
        }
      } else emptyList()
    } catch (e: Exception) {
      error = e.message ?: "Could not load driver information."
    } finally {
      loading = false
    }
  }

  LaunchedEffect(user?.id) { load() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("JTBazar Driver", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
      error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
      message?.let { item {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
          Text(it, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
      } }

      if (!enabled) {
        item {
          Card(Modifier.fillMaxWidth()) {
            Text("Driver applications are currently closed. Please check again later.", Modifier.padding(18.dp))
          }
        }
      } else if (application == null) {
        item { Text("Become a JTBazar Driver", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(phone, { phone = it }, label = { Text("Mobile number") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Vehicle type") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
          Button(enabled = !busy && fullName.isNotBlank() && phone.isNotBlank(), onClick = {
            scope.launch {
              busy = true
              message = null
              val body = JSONObject().put("p_full_name", fullName.trim()).put("p_phone", phone.trim()).put("p_vehicle_type", vehicle)
              val result = SupabaseClient.rpc("submit_driver_application", body.toString())
              if (result.isSuccess) {
                message = "Application submitted. Admin approval is required."
                load()
              } else error = result.exceptionOrNull()?.message
              busy = false
            }
          }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Submitting…" else "Submit driver application")
          }
        }
      } else {
        val status = application?.optString("status") ?: "pending"
        item {
          Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Driver application", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Surface(
                color = when (status) {
                  "approved" -> MaterialTheme.colorScheme.primaryContainer
                  "rejected", "suspended" -> MaterialTheme.colorScheme.errorContainer
                  else -> MaterialTheme.colorScheme.secondaryContainer
                },
                shape = RoundedCornerShape(50)
              ) {
                Text(
                  "Status: ${status.replaceFirstChar { it.uppercase() }}",
                  Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold
                )
              }
              application?.optString("admin_note")?.takeIf { it.isNotBlank() && it != "null" }?.let {
                Text("Admin note: $it", style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }

        if (status == "approved") {
          item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.weight(1f)) {
                Text("My deliveries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${deliveries.size} active assignment${if (deliveries.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              IconButton(onClick = { scope.launch { load() } }, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh deliveries")
              }
            }
          }
          if (deliveries.isEmpty()) item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
              Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                Text("No assigned deliveries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("New orders assigned to you will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
          items(deliveries, key = { it.optString("assignment_id") }) { delivery ->
            val lat = delivery.optDouble("delivery_lat", Double.NaN)
            val lng = delivery.optDouble("delivery_lng", Double.NaN)
            val assignmentStatus = delivery.optString("assignment_status", "assigned")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
              Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("ORDER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(delivery.optString("order_number"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  }
                  Surface(color = if (assignmentStatus == "picked_up") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                    Text(assignmentStatus.replace('_', ' ').replaceFirstChar { it.uppercase() }, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                  }
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text("CUSTOMER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                  Text(delivery.optString("customer_name").ifBlank { "Customer" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(delivery.optString("customer_phone"), style = MaterialTheme.typography.bodyMedium)
                  }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("DELIVERY ADDRESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                  }
                  Text(driverAddressText(delivery), style = MaterialTheme.typography.bodyMedium)
                }
                val itemRows = delivery.optJSONArray("items")
                if (itemRows != null && itemRows.length() > 0) {
                  Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("ORDER ITEMS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    for (index in 0 until itemRows.length()) {
                      val item = itemRows.optJSONObject(index) ?: continue
                      Row(verticalAlignment = Alignment.Top) {
                        Text("•", modifier = Modifier.padding(end = 7.dp), color = MaterialTheme.colorScheme.primary)
                        Text("${item.optString("name")}  ×  ${item.optInt("quantity", 1)}", style = MaterialTheme.typography.bodyMedium)
                      }
                    }
                  }
                }
                if (lat.isFinite() && lng.isFinite()) {
                  OutlinedButton(onClick = {
                    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving&dir_action=navigate")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                  }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Navigation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Navigate to customer")
                  }
                } else {
                  Text("Customer map pin is unavailable. Contact support before dispatch.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                  if (assignmentStatus == "assigned") {
                    Button(enabled = !busy, onClick = {
                      scope.launch {
                        busy = true
                        val r = SupabaseClient.rpc("driver_update_delivery_status", JSONObject().put("p_assignment_id", delivery.optString("assignment_id")).put("p_action", "picked_up").toString())
                        if (r.isSuccess) load() else error = r.exceptionOrNull()?.message
                        busy = false
                      }
                    }, modifier = Modifier.weight(1f)) { Text(if (busy) "Please wait…" else "Picked up") }
                  }
                  if (assignmentStatus != "delivered") {
                    Button(enabled = !busy, onClick = {
                      scope.launch {
                        busy = true
                        val r = SupabaseClient.rpc("driver_update_delivery_status", JSONObject().put("p_assignment_id", delivery.optString("assignment_id")).put("p_action", "delivered").toString())
                        if (r.isSuccess) load() else error = r.exceptionOrNull()?.message
                        busy = false
                      }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                      Text(if (busy) "Please wait…" else "Delivered")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
