package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.supabase.VendorService
import com.example.ui.theme.JTOrange
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun VendorProductEditScreen(product: JSONObject, onBack: () -> Unit, onSaved: suspend () -> Unit) {
  val scope = rememberCoroutineScope()
  val id = product.optString("id")
  var name by remember { mutableStateOf(product.optString("name")) }
  var slug by remember { mutableStateOf(product.optString("slug")) }
  var sku by remember { mutableStateOf(product.optString("sku")) }
  var category by remember { mutableStateOf(product.optString("category_id")) }
  var shortDescription by remember { mutableStateOf(product.optString("short_description")) }
  var description by remember { mutableStateOf(product.optString("description")) }
  var price by remember { mutableStateOf(product.optString("vendor_base_price", product.optString("price"))) }
  var discount by remember { mutableStateOf(product.optString("discount_percent", "0")) }
  var supplierCost by remember { mutableStateOf(product.optString("supplier_cost").takeUnless { product.isNull("supplier_cost") }.orEmpty()) }
  var fulfilment by remember { mutableStateOf(product.optString("fulfillment_expense", "0")) }
  var minimumProfit by remember { mutableStateOf(product.optString("minimum_profit", "0")) }
  var weight by remember { mutableStateOf(product.optString("weight_grams").takeUnless { product.isNull("weight_grams") }.orEmpty()) }
  var specs by remember { mutableStateOf((product.optJSONArray("specifications") ?: JSONArray()).toString(2)) }
  var dimensions by remember { mutableStateOf((product.optJSONObject("dimensions") ?: JSONObject()).toString(2)) }
  var seoTitle by remember { mutableStateOf(product.optString("seo_title")) }
  var seoDescription by remember { mutableStateOf(product.optString("seo_description")) }
  var saving by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Edit product", style = MaterialTheme.typography.headlineSmall)
      TextButton(onClick = onBack) { Text("Back") }
    }
    Text("Changes are sent through JTBazar product moderation. Stock remains managed separately in Inventory.", style = MaterialTheme.typography.bodySmall)
    EditProductField("Product name", name) { name = it }
    EditProductField("URL slug", slug) { slug = it.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-') }
    EditProductField("SKU", sku) { sku = it }
    EditProductField("Category ID", category) { category = it }
    EditProductField("Short description", shortDescription, 2) { shortDescription = it }
    EditProductField("Full description", description, 4) { description = it }
    EditProductField("Vendor base price (PKR)", price) { price = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Discount (%)", discount) { discount = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Supplier cost (private PKR)", supplierCost) { supplierCost = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Fulfilment expense (PKR)", fulfilment) { fulfilment = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Minimum profit (PKR)", minimumProfit) { minimumProfit = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Shipping weight (grams)", weight) { weight = it.filter(Char::isDigit) }
    EditProductField("Specifications (JSON array)", specs, 3) { specs = it }
    EditProductField("Dimensions (JSON object)", dimensions, 3) { dimensions = it }
    EditProductField("SEO title", seoTitle) { seoTitle = it }
    EditProductField("SEO description", seoDescription, 2) { seoDescription = it }
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    Button(enabled = !saving, onClick = {
      scope.launch {
        saving = true; error = ""; message = ""
        try {
          val basePrice = price.toDoubleOrNull() ?: error("Enter a valid price.")
          val discountValue = discount.toDoubleOrNull() ?: error("Enter a valid discount.")
          require(discountValue in 0.0..100.0) { "Discount must be between 0 and 100%." }
          val payload = JSONObject()
            .put("p_category_id", category.ifBlank { JSONObject.NULL })
            .put("p_name", name.trim()).put("p_slug", slug.trim())
            .put("p_sku", sku.trim().ifBlank { JSONObject.NULL })
            .put("p_short_description", shortDescription.trim())
            .put("p_description", description.trim()).put("p_price", basePrice)
            .put("p_supplier_cost", supplierCost.toDoubleOrNull() ?: JSONObject.NULL)
            .put("p_fulfillment_expense", fulfilment.toDoubleOrNull() ?: error("Enter valid fulfilment expense."))
            .put("p_minimum_profit", minimumProfit.toDoubleOrNull() ?: error("Enter valid minimum profit."))
            .put("p_discount_percent", discountValue)
            .put("p_weight_grams", weight.toIntOrNull() ?: JSONObject.NULL)
            .put("p_specifications", JSONArray(specs)).put("p_dimensions", JSONObject(dimensions))
            .put("p_seo_title", seoTitle.trim().ifBlank { JSONObject.NULL })
            .put("p_seo_description", seoDescription.trim().ifBlank { JSONObject.NULL })
          VendorService.updateProductFull(id, payload)
          message = "Product details saved."
          onSaved()
        } catch (e: Exception) { error = e.message ?: "Product could not be updated." }
        finally { saving = false }
      }
    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
      Text(if (saving) "Saving…" else "Save product")
    }
    OutlinedButton(onClick = onBack, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
  }
}

@Composable
private fun EditProductField(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = lines, singleLine = lines == 1)
}
