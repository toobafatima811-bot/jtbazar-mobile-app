package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.VendorService
import com.example.ui.theme.JTOrange
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun VendorProductDetailsScreen(product: JSONObject, onBack: () -> Unit, onEdit: () -> Unit) {
  val id = product.optString("id")
  var images by remember(id) { mutableStateOf<List<String>>(emptyList()) }
  var loading by remember(id) { mutableStateOf(true) }
  LaunchedEffect(id) {
    runCatching { VendorService.listProductImageUrls(id) }.onSuccess { images = it }
    loading = false
  }
  val navy = Color(0xFF17243A)
  val muted = Color(0xFF718096)
  val name = product.optString("name", "Product")
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        Text("Products")
      }
      Spacer(Modifier.weight(1f))
      Button(onClick = onEdit, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
        Text("Edit product")
      }
    }
    Text("Product details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = navy)
    if (images.isNotEmpty()) {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        images.forEachIndexed { index, url ->
          AsyncImage(
            model = url,
            contentDescription = "Product image " + (index + 1),
            modifier = Modifier.size(176.dp).clip(RoundedCornerShape(20.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
          )
        }
      }
    } else if (loading) {
      Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = JTOrange)
      }
    } else {
      Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFFFFF1E8), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Inventory2, null, tint = JTOrange, modifier = Modifier.size(48.dp))
      }
      Text("No product images saved yet.", color = muted, style = MaterialTheme.typography.bodySmall)
    }
    Card(
      Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8ECF2))
    ) {
      Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = navy)
        Text("PKR " + product.optString("price", "0"), style = MaterialTheme.typography.headlineSmall, color = JTOrange, fontWeight = FontWeight.ExtraBold)
        Text("Status: " + product.optString("moderation_status", product.optString("status", "pending")).replace('_', ' '), color = muted, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = Color(0xFFEEF0F4))
        DetailRow("SKU", product.optString("sku").takeUnless { it == "null" }.orEmpty().ifBlank { "—" })
        DetailRow("Category", product.optString("category_name").takeUnless { it == "null" }.orEmpty().ifBlank { "—" })
        DetailRow("Discount", product.optString("discount_percent", "0") + "%")
        DetailRow("Shipping weight", product.optString("weight_grams").takeUnless { it == "null" }.orEmpty().ifBlank { "—" } + " g")
      }
    }
    DetailSection("Short description", product.optString("short_description").takeUnless { it == "null" }.orEmpty())
    DetailSection("Full description", product.optString("description").takeUnless { it == "null" }.orEmpty())
    DetailSection("Specifications", (product.optJSONArray("specifications") ?: JSONArray()).toString(2))
    DetailSection("Dimensions", (product.optJSONObject("dimensions") ?: JSONObject()).toString(2))
    val seo = listOf(product.optString("seo_title"), product.optString("seo_description"))
      .filter { it.isNotBlank() && it != "null" }.joinToString("\n").ifBlank { "No SEO details saved." }
    DetailSection("SEO", seo)
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Text(label, color = Color(0xFF718096), style = MaterialTheme.typography.bodySmall)
    Text(value, color = Color(0xFF26354A), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun DetailSection(title: String, value: String) {
  Card(
    Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8ECF2))
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
      Text(title, color = JTOrange, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
      Text(value.ifBlank { "Not provided." }, color = Color(0xFF334155), style = MaterialTheme.typography.bodyMedium)
    }
  }
}
