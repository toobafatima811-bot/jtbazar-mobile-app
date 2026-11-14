package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.supabase.VendorService
import com.example.ui.theme.JTOrange
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun VendorInventoryPanel() {
  val scope = rememberCoroutineScope()
  var rows by remember { mutableStateOf(JSONArray()) }
  var quantities by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var adjustmentReasons by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var loading by remember { mutableStateOf(true) }
  var savingId by remember { mutableStateOf<String?>(null) }
  var errorMessage by remember { mutableStateOf("") }
  var dialogError by remember { mutableStateOf("") }
  var info by remember { mutableStateOf("") }
  var editingKey by remember { mutableStateOf<String?>(null) }

  fun refresh() {
    scope.launch {
      loading = true
      errorMessage = ""
      try {
        rows = VendorService.listInventory()
        val next = mutableMapOf<String, String>()
        for (i in 0 until rows.length()) {
          val row = rows.optJSONObject(i) ?: continue
          val productId = row.optString("product_id")
          val variantRaw = row.optString("variant_id")
          val variantId = variantRaw.takeUnless { it.isBlank() || it == "null" }
          next["$productId:${variantId.orEmpty()}"] = row.optInt("quantity").toString()
        }
        quantities = next
      } catch (e: Exception) {
        errorMessage = e.message ?: "Inventory could not be loaded."
      } finally { loading = false }
    }
  }
  LaunchedEffect(Unit) { refresh() }

  Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp)).padding(4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Stock & inventory", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF17243A), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
      TextButton(onClick = { refresh() }) { Text("Refresh") }
    }
    Text("Manage live product and variant quantities. Every adjustment is recorded in inventory history.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF718096))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Card(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.95f)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
          Text("Stock records", color = Color(0xFF718096), style = MaterialTheme.typography.labelSmall)
          Text(rows.length().toString(), color = Color(0xFF17243A), style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
      }
      Card(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.95f)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
          Text("Total units", color = Color(0xFF718096), style = MaterialTheme.typography.labelSmall)
          Text((0 until rows.length()).sumOf { rows.optJSONObject(it)?.optInt("quantity") ?: 0 }.toString(), color = Color(0xFF17243A), style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
      }
    }
    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    if (info.isNotBlank()) Text(info, color = MaterialTheme.colorScheme.primary)
    if (loading) CircularProgressIndicator(color = JTOrange)
    else if (rows.length() == 0) Text("No inventory records found. Create a listing with inventory tracking enabled.")
    else {
      val groupedRows = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.groupBy { it.optString("product_id") }
      groupedRows.values.forEach { productRows ->
        val first = productRows.first()
        val product = first.optJSONObject("products")
        val productName = product?.optString("name", "Product") ?: "Product"
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.95f)), elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(productName, style = MaterialTheme.typography.titleLarge, color = Color(0xFF17243A), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("${productRows.size} stock option(s)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF718096))
            productRows.forEach { row ->
              val productId = row.optString("product_id")
              val variantRaw = row.optString("variant_id")
              val variantId = variantRaw.takeUnless { it.isBlank() || it == "null" }
              val key = "$productId:${variantId.orEmpty()}"
              val variant = row.optJSONObject("product_variants")
              val label = if (variant != null) "${variant.optString("option_label")}: ${variant.optString("option_value")}" else "Base product stock"
              HorizontalDivider()
              Text(label, style = MaterialTheme.typography.titleSmall, color = Color(0xFF26354A), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
              Text("Available: ${row.optInt("quantity")}  •  Low stock alert: ${row.optInt("low_stock_threshold")}", style = MaterialTheme.typography.bodyMedium, color = if (row.optInt("quantity") <= row.optInt("low_stock_threshold")) Color(0xFFB45309) else Color(0xFF187548), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(enabled = savingId != key, onClick = {
                  quantities = quantities.toMutableMap().also { it[key] = row.optInt("quantity").toString() }
                  dialogError = ""
                  editingKey = key
                }) { Text("Edit stock") }
              }
              if (editingKey == key) {
                AlertDialog(
                  onDismissRequest = { if (savingId != key) editingKey = null },
                  title = { Text("Edit inventory — $label") },
                  text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                      Text("Current stock: ${row.optInt("quantity")} • Low stock threshold: ${row.optInt("low_stock_threshold")}")
                      OutlinedTextField(
                        value = quantities[key] ?: row.optInt("quantity").toString(),
                        onValueChange = { value -> dialogError = ""; quantities = quantities.toMutableMap().also { it[key] = value.filter(Char::isDigit).take(9) } },
                        label = { Text("New quantity") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                      )
                      OutlinedTextField(
                        value = adjustmentReasons[key].orEmpty(),
                        onValueChange = { value -> dialogError = ""; adjustmentReasons = adjustmentReasons.toMutableMap().also { it[key] = value.take(240) } },
                        label = { Text("Adjustment reason / note *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                      )
                      if (dialogError.isNotBlank()) {
                        Text(dialogError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                      }
                    }
                  },
                  confirmButton = {
                    Button(enabled = savingId != key, onClick = {
                      val quantity = quantities[key]?.toIntOrNull()
                      if (quantity == null) { dialogError = "Enter a valid stock quantity."; return@Button }
                      val reason = adjustmentReasons[key]?.trim().orEmpty()
                      if (reason.length < 3) { dialogError = "Add a reason for this stock adjustment (at least 3 characters)."; return@Button }
                      scope.launch {
                        savingId = key; dialogError = ""; errorMessage = ""; info = ""
                        try {
                          VendorService.setVariantInventory(productId, variantId, quantity, reason)
                          adjustmentReasons = adjustmentReasons.toMutableMap().also { it.remove(key) }
                          editingKey = null
                          info = "Stock updated for $label."
                          refresh()
                        } catch (e: Exception) { dialogError = e.message ?: "Stock update failed." }
                        finally { savingId = null }
                      }
                    }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
                      Text(if (savingId == key) "Saving" else "Save changes")
                    }
                  },
                  dismissButton = { TextButton(enabled = savingId != key, onClick = { editingKey = null }) { Text("Cancel") } }
                )
              }
            }
          }
        }
      }
    }
  }
}
