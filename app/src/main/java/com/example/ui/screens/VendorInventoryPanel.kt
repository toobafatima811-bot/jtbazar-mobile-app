package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warehouse
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.supabase.VendorService
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.components.AppCard
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.VendorAlertBanner
import com.example.ui.components.VendorOutlinedTextField
import com.example.ui.components.VendorSkeletonCard
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Locale

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
        errorMessage = e.message ?: AppLanguageManager.tr(
          "Inventory could not be loaded.",
          "انوینٹری لوڈ نہیں ہو سکی۔"
        )
      } finally {
        loading = false
      }
    }
  }

  LaunchedEffect(Unit) { refresh() }

  val inventoryRows = remember(rows) {
    (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
  }
  val totalUnits = remember(inventoryRows) {
    inventoryRows.sumOf { it.optInt("quantity", 0) }
  }
  val groupedUiModels = remember(inventoryRows) {
    inventoryRows.groupBy { it.optString("product_id") }.map { (productId, productRows) ->
      val product = productRows.first().optJSONObject("products")
      val productName = product?.optString("name", "")?.takeUnless { it == "null" }?.ifBlank { null }
        ?: AppLanguageManager.tr("Product", "پروڈکٹ")
      VendorInventoryGroupUiModel(
        productId = productId,
        productName = productName,
        rows = productRows.map { row ->
          val variantRaw = row.optString("variant_id")
          val variantId = variantRaw.takeUnless { it.isBlank() || it == "null" }
          val key = "$productId:${variantId.orEmpty()}"
          val variant = row.optJSONObject("product_variants")
          val label = if (variant != null) {
            "${variant.optString("option_label")}: ${variant.optString("option_value")}"
          } else {
            AppLanguageManager.tr("Base product stock", "بنیادی پروڈکٹ اسٹاک")
          }
          VendorInventoryRowUiModel(
            key = key,
            productId = productId,
            variantId = variantId,
            variantLabel = label,
            quantity = row.optInt("quantity", 0),
            lowStockThreshold = row.optInt("low_stock_threshold", 5).coerceAtLeast(1)
          )
        }
      )
    }
  }

  val sectionState: VendorSectionState<List<VendorInventoryGroupUiModel>> = when {
    loading -> VendorSectionState.Loading
    errorMessage.isNotBlank() && groupedUiModels.isEmpty() -> VendorSectionState.Error(errorMessage)
    groupedUiModels.isEmpty() -> VendorSectionState.Empty
    else -> VendorSectionState.Content(groupedUiModels)
  }

  val uiState = VendorInventoryUiState(
    stockRecordsCount = inventoryRows.size,
    totalUnitsCount = totalUnits,
    contentState = sectionState,
    savingKey = savingId,
    actionError = if (groupedUiModels.isNotEmpty()) errorMessage else "",
    infoMessage = info
  )

  VendorInventoryContent(
    uiState = uiState,
    onRefresh = { refresh() },
    onOpenEditStock = { row ->
      quantities = quantities.toMutableMap().also { it[row.key] = row.quantity.toString() }
      dialogError = ""
      editingKey = row.key
    },
    onDismissAlert = {
      errorMessage = ""
      info = ""
    }
  )

  val editingRow = remember(editingKey, groupedUiModels) {
    groupedUiModels.flatMap { it.rows }.firstOrNull { it.key == editingKey }
  }

  if (editingRow != null) {
    AlertDialog(
      onDismissRequest = { if (savingId != editingRow.key) editingKey = null },
      containerColor = VendorColors.CardWhite,
      shape = VendorShapes.card,
      title = {
        Text(
          text = appText("Edit stock", "اسٹاک میں ترمیم"),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
          Text(
            text = appText(
              "${editingRow.variantLabel} • Current: ${editingRow.quantity} units • Low-stock alert at ${editingRow.lowStockThreshold} units",
              "${editingRow.variantLabel} • موجودہ: ${editingRow.quantity} یونٹس • کم اسٹاک الرٹ: ${editingRow.lowStockThreshold} یونٹس پر"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = VendorColors.TextSecondary
          )
          VendorOutlinedTextField(
            value = quantities[editingRow.key] ?: editingRow.quantity.toString(),
            onValueChange = { value ->
              dialogError = ""
              quantities = quantities.toMutableMap().also {
                it[editingRow.key] = value.filter(Char::isDigit).take(9)
              }
            },
            label = appText("New quantity *", "نئی مقدار *"),
            leadingIcon = Icons.Rounded.Numbers,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = savingId != editingRow.key
          )
          VendorOutlinedTextField(
            value = adjustmentReasons[editingRow.key].orEmpty(),
            onValueChange = { value ->
              dialogError = ""
              adjustmentReasons = adjustmentReasons.toMutableMap().also {
                it[editingRow.key] = value.take(240)
              }
            },
            label = appText("Adjustment reason / note *", "تبدیلی کی وجہ / نوٹ *"),
            leadingIcon = Icons.Rounded.Notes,
            minLines = 2,
            enabled = savingId != editingRow.key
          )
          if (dialogError.isNotBlank()) {
            VendorAlertBanner(message = dialogError, isError = true)
          }
        }
      },
      confirmButton = {
        PrimaryButton(
          text = if (savingId == editingRow.key) appText("Saving...", "محفوظ ہو رہا ہے...") else appText("Save changes", "تبدیلیاں محفوظ کریں"),
          loading = savingId == editingRow.key,
          onClick = {
            val quantity = quantities[editingRow.key]?.toIntOrNull()
            if (quantity == null) {
              dialogError = AppLanguageManager.tr("Enter a valid stock quantity.", "درست اسٹاک مقدار درج کریں۔")
              return@PrimaryButton
            }
            val reason = adjustmentReasons[editingRow.key]?.trim().orEmpty()
            if (reason.length < 3) {
              dialogError = AppLanguageManager.tr(
                "Add a reason for this stock adjustment (at least 3 characters).",
                "اسٹاک تبدیلی کی وجہ درج کریں (کم از کم 3 حروف)۔"
              )
              return@PrimaryButton
            }
            scope.launch {
              savingId = editingRow.key
              dialogError = ""
              errorMessage = ""
              info = ""
              try {
                VendorService.setVariantInventory(
                  editingRow.productId,
                  editingRow.variantId,
                  quantity,
                  reason
                )
                adjustmentReasons = adjustmentReasons.toMutableMap().also { it.remove(editingRow.key) }
                editingKey = null
                info = AppLanguageManager.tr(
                  "Stock updated for ${editingRow.variantLabel}.",
                  "${editingRow.variantLabel} کا اسٹاک اپڈیٹ ہو گیا۔"
                )
                refresh()
              } catch (e: Exception) {
                dialogError = e.message ?: AppLanguageManager.tr("Stock update failed.", "اسٹاک اپڈیٹ نہیں ہو سکا۔")
              } finally {
                savingId = null
              }
            }
          }
        )
      },
      dismissButton = {
        TextButton(
          enabled = savingId != editingRow.key,
          onClick = { editingKey = null },
          modifier = Modifier.height(VendorSpacing.touchMin)
        ) {
          Text(
            appText("Cancel", "منسوخ کریں"),
            color = VendorColors.TextSecondary,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    )
  }
}

@Composable
fun VendorInventoryContent(
  uiState: VendorInventoryUiState,
  onRefresh: () -> Unit,
  onOpenEditStock: (VendorInventoryRowUiModel) -> Unit,
  onDismissAlert: () -> Unit,
  modifier: Modifier = Modifier
) {
  val numberFormatter = remember { NumberFormat.getIntegerInstance(Locale.US) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(VendorColors.Background),
    contentPadding = PaddingValues(VendorSpacing.md),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
  ) {
    item {
      SectionHeader(
        title = appText("Stock & inventory", "اسٹاک اور انوینٹری"),
        subtitle = appText(
          "Manage live product and variant quantities.",
          "لائیو پروڈکٹ اور ویرینٹ کی مقداریں منظم کریں۔"
        ),
        actionLabel = appText("Refresh", "ریفریش"),
        actionIcon = Icons.Rounded.Refresh,
        onActionClick = onRefresh
      )
    }

    if (uiState.actionError.isNotBlank()) {
      item {
        VendorAlertBanner(
          message = uiState.actionError,
          isError = true,
          onDismiss = onDismissAlert
        )
      }
    }

    if (uiState.infoMessage.isNotBlank()) {
      item {
        VendorAlertBanner(
          message = uiState.infoMessage,
          isError = false,
          onDismiss = onDismissAlert
        )
      }
    }

    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
      ) {
        StatCard(
          icon = Icons.Rounded.Inventory2,
          value = numberFormatter.format(uiState.stockRecordsCount),
          label = appText("Stock records", "اسٹاک ریکارڈز"),
          supportingText = appText("Tracked SKUs & variants", "ٹریک شدہ SKU اور ویرینٹس"),
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
        )
        StatCard(
          icon = Icons.Rounded.Warehouse,
          value = numberFormatter.format(uiState.totalUnitsCount),
          label = appText("Total units", "کل یونٹس"),
          supportingText = appText("Available across shop", "پوری دکان میں دستیاب"),
          iconTint = VendorColors.NavyHeader,
          iconBackground = VendorColors.NavyHeader.copy(alpha = 0.10f),
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
        )
      }
    }

    when (val state = uiState.contentState) {
      VendorSectionState.Loading -> {
        items(3) {
          VendorSkeletonCard()
        }
      }

      is VendorSectionState.Error -> {
        item {
          ErrorState(
            title = appText("Inventory could not load", "انوینٹری لوڈ نہیں ہو سکی"),
            message = state.message,
            onRetry = onRefresh
          )
        }
      }

      VendorSectionState.Empty -> {
        item {
          EmptyState(
            icon = Icons.Rounded.Inventory2,
            title = appText("No inventory records", "کوئی انوینٹری ریکارڈ نہیں"),
            description = appText(
              "Create a product listing with inventory tracking enabled to manage unit levels here.",
              "یہاں یونٹس کی مقدار منظم کرنے کے لیے انوینٹری ٹریکنگ کے ساتھ پروڈکٹ شامل کریں۔"
            ),
            actionLabel = appText("Refresh inventory", "انوینٹری ریفریش کریں"),
            actionIcon = Icons.Rounded.Refresh,
            onAction = onRefresh
          )
        }
      }

      is VendorSectionState.Content -> {
        items(state.data, key = { it.productId }) { group ->
          AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = group.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VendorColors.TextPrimary
                  )
                  Text(
                    text = appText("${group.rows.size} stock option(s)", "${group.rows.size} اسٹاک آپشنز"),
                    style = MaterialTheme.typography.bodySmall,
                    color = VendorColors.TextSecondary
                  )
                }
              }

              group.rows.forEachIndexed { index, row ->
                if (index > 0 || group.rows.isNotEmpty()) {
                  HorizontalDivider(color = VendorColors.Divider)
                }
                val isLowStock = row.quantity <= row.lowStockThreshold
                val barTarget = (row.lowStockThreshold * 4).coerceAtLeast(20)
                val progress = (row.quantity.toFloat() / barTarget.toFloat()).coerceIn(0.04f, 1f)
                val progressColor = if (isLowStock) {
                  VendorColors.WarningAmberAccent
                } else {
                  VendorColors.SuccessGreen
                }
                val trackColor = if (isLowStock) {
                  VendorColors.WarningAmberBg
                } else {
                  VendorColors.SuccessGreenBg
                }

                Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = row.variantLabel,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = VendorColors.TextPrimary,
                      modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(VendorSpacing.xs))
                    StatusBadge(
                      text = appText(
                        "${numberFormatter.format(row.quantity)} units",
                        "${numberFormatter.format(row.quantity)} یونٹس"
                      ),
                      tone = if (isLowStock) StatusTone.WARNING else StatusTone.SUCCESS,
                      icon = if (isLowStock) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle
                    )
                  }

                  LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(8.dp)
                      .clip(VendorShapes.pill),
                    color = progressColor,
                    trackColor = trackColor,
                    strokeCap = StrokeCap.Round
                  )

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = appText(
                        "Low-stock alert at ${row.lowStockThreshold} units",
                        "کم اسٹاک الرٹ ${row.lowStockThreshold} یونٹس پر"
                      ),
                      style = MaterialTheme.typography.bodySmall,
                      color = if (isLowStock) VendorColors.WarningAmber else VendorColors.TextSecondary,
                      fontWeight = if (isLowStock) FontWeight.SemiBold else FontWeight.Normal,
                      modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(VendorSpacing.xs))
                    SecondaryButton(
                      text = appText("Edit stock", "اسٹاک میں ترمیم"),
                      icon = Icons.Rounded.Edit,
                      enabled = uiState.savingKey != row.key,
                      onClick = { onOpenEditStock(row) }
                    )
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

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorInventoryContentPreview() {
  VendorCenterTheme {
    VendorInventoryContent(
      uiState = VendorInventoryUiState(
        stockRecordsCount = 3,
        totalUnitsCount = 54,
        contentState = VendorSectionState.Content(
          listOf(
            VendorInventoryGroupUiModel(
              productId = "p1",
              productName = "Multani Blue Pottery Dinner Set",
              rows = listOf(
                VendorInventoryRowUiModel("p1:v1", "p1", "v1", "Size: 10.5 inch", 4, 5),
                VendorInventoryRowUiModel("p1:v2", "p1", "v2", "Size: 8.5 inch", 50, 10)
              )
            )
          )
        )
      ),
      onRefresh = {},
      onOpenEditStock = {},
      onDismissAlert = {}
    )
  }
}
