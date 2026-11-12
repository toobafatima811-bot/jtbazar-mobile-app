package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.JTOrange

/**
 * Standardized pull-to-refresh container for JTBazar data screens.
 * Uses Material 3's PullToRefreshBox with JTBazar orange indicator branding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JTBazarPullRefresh(
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable BoxScope.() -> Unit
) {
  if (!enabled) {
    Box(modifier = modifier, content = content)
  } else {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
      isRefreshing = isRefreshing,
      onRefresh = onRefresh,
      modifier = modifier,
      state = state,
      indicator = {
        PullToRefreshDefaults.Indicator(
          state = state,
          isRefreshing = isRefreshing,
          modifier = Modifier.align(Alignment.TopCenter),
          containerColor = Color.White,
          color = JTOrange
        )
      },
      content = content
    )
  }
}
