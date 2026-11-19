package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.VendorService
import com.example.localization.appText
import com.example.ui.components.AppCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.VendorShimmerBox
import com.example.ui.components.formatPkrPrice
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun VendorProductDetailsScreen(
  product: JSONObject,
  onBack: () -> Unit,
  onEdit: () -> Unit
) {
  val id = product.optString("id")
  var images by remember(id) { mutableStateOf<List<String>>(emptyList()) }
  var loading by remember(id) { mutableStateOf(true) }

  LaunchedEffect(id) {
    runCatching { VendorService.listProductImageUrls(id) }.onSuccess { images = it }
    loading = false
  }

  val name = product.optString("name", "").takeUnless { it == "null" }?.ifBlank { null } ?: appText("Product", "پروڈکٹ")
  val moderation = product.optString("moderation_status", product.optString("status", "pending"))
    .takeUnless { it == "null" }?.lowercase() ?: "pending"

  val (badgeLabel, badgeTone, badgeIcon) = when (moderation) {
    "approved" -> Triple(appText("Approved", "منظور شدہ"), StatusTone.SUCCESS, Icons.Rounded.CheckCircle)
    "rejected" -> Triple(appText("Rejected", "مسترد شدہ"), StatusTone.DANGER, Icons.Rounded.ErrorOutline)
    "pending" -> Triple(appText("Pending", "زیر التوا"), StatusTone.WARNING, Icons.Rounded.Schedule)
    else -> Triple(moderation.replace('_', ' ').replaceFirstChar { it.uppercase() }, StatusTone.WARNING, Icons.Rounded.Schedule)
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(VendorColors.Background),
    contentPadding = PaddingValues(VendorSpacing.md),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
      ) {
        SecondaryButton(
          text = appText("Products", "مصنوعات"),
          icon = Icons.AutoMirrored.Rounded.ArrowBack,
          onClick = onBack,
          modifier = Modifier.weight(1f)
        )
        PrimaryButton(
          text = appText("Edit product", "پروڈکٹ میں ترمیم"),
          icon = Icons.Rounded.Edit,
          onClick = onEdit,
          modifier = Modifier.weight(1f)
        )
      }
    }

    item {
      Text(
        text = appText("Product details", "پروڈکٹ کی تفصیلات"),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = VendorColors.TextPrimary
      )
    }

    item {
      when {
        loading -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
          ) {
            VendorShimmerBox(modifier = Modifier.size(160.dp), height = 160.dp, cornerRadius = 16.dp)
            VendorShimmerBox(modifier = Modifier.size(160.dp), height = 160.dp, cornerRadius = 16.dp)
          }
        }

        images.isNotEmpty() -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
          ) {
            images.forEachIndexed { index, url ->
              AsyncImage(
                model = url,
                contentDescription = appText("Product image ${index + 1}", "پروڈکٹ تصویر ${index + 1}"),
                modifier = Modifier
                  .size(168.dp)
                  .clip(VendorShapes.card),
                contentScale = ContentScale.Crop
              )
            }
          }
        }

        else -> {
          AppCard {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(VendorShapes.input)
                .background(VendorColors.PrimaryOrangeTint),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
              ) {
                Icon(
                  imageVector = Icons.Rounded.Inventory2,
                  contentDescription = null,
                  tint = VendorColors.PrimaryOrange,
                  modifier = Modifier.size(40.dp)
                )
                Text(
                  text = appText("No product images saved yet", "ابھی کوئی پروڈکٹ تصویر محفوظ نہیں"),
                  style = MaterialTheme.typography.bodySmall,
                  color = VendorColors.TextSecondary
                )
              }
            }
          }
        }
      }
    }

    item {
      AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(VendorSpacing.xxs)
            ) {
              Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VendorColors.TextPrimary
              )
              Text(
                text = formatPkrPrice(product.optString("price", "0")),
                style = MaterialTheme.typography.headlineSmall,
                color = VendorColors.PrimaryOrange,
                fontWeight = FontWeight.Bold
              )
            }
            StatusBadge(text = badgeLabel, tone = badgeTone, icon = badgeIcon)
          }

          HorizontalDivider(color = VendorColors.Divider)

          DetailRow(appText("SKU", "ایس کے یو (SKU)"), product.optString("sku").takeUnless { it == "null" }.orEmpty().ifBlank { "—" })
          DetailRow(appText("Category", "کیٹیگری"), product.optString("category_name").takeUnless { it == "null" }.orEmpty().ifBlank { "—" })
          DetailRow(appText("Discount", "رعایت"), "${product.optString("discount_percent", "0").takeUnless { it == "null" } ?: "0"}%")
          DetailRow(appText("Shipping weight", "شپنگ وزن"), "${product.optString("weight_grams").takeUnless { it == "null" }.orEmpty().ifBlank { "—" }} g")
        }
      }
    }

    item {
      DetailSection(
        appText("Short description", "مختصر تفصیل"),
        product.optString("short_description").takeUnless { it == "null" }.orEmpty()
      )
    }
    item {
      DetailSection(
        appText("Full description", "مکمل تفصیل"),
        product.optString("description").takeUnless { it == "null" }.orEmpty()
      )
    }
    item {
      DetailSection(
        appText("Specifications", "خصوصیات"),
        (product.optJSONArray("specifications") ?: JSONArray()).toString(2)
      )
    }
    item {
      DetailSection(
        appText("Dimensions", "پیمائش"),
        (product.optJSONObject("dimensions") ?: JSONObject()).toString(2)
      )
    }
    item {
      val seo = listOf(product.optString("seo_title"), product.optString("seo_description"))
        .filter { it.isNotBlank() && it != "null" }
        .joinToString("\n")
        .ifBlank { appText("No SEO details saved.", "کوئی ایس ای او تفصیلات محفوظ نہیں۔") }
      DetailSection(appText("SEO", "ایس ای او"), seo)
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, color = VendorColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    Text(
      value,
      color = VendorColors.TextPrimary,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold
    )
  }
}

@Composable
private fun DetailSection(title: String, value: String) {
  AppCard {
    Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
      Text(
        text = title,
        color = VendorColors.PrimaryOrange,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = value.ifBlank { appText("Not provided.", "فراہم نہیں کیا گیا۔") },
        color = VendorColors.TextPrimary,
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}
