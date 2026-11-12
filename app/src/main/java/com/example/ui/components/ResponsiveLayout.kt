package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * Chooses a product/category grid column count from the current app window width.
 * Uses the window configuration so split-screen and resized windows are handled too.
 */
@Composable
fun rememberAdaptiveColumnCount(
  minCellWidth: Dp = 138.dp,
  horizontalPadding: Dp = 32.dp,
  spacing: Dp = 12.dp,
  maxColumns: Int = 4
): Int {
  val configuration = LocalConfiguration.current
  val availableWidth = (configuration.screenWidthDp.dp - horizontalPadding).coerceAtLeast(minCellWidth)
  val count = floor(((availableWidth + spacing) / (minCellWidth + spacing)).toDouble()).toInt()
  return count.coerceIn(1, maxColumns.coerceAtLeast(1))
}
