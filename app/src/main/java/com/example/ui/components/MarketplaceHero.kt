package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Product
import com.example.data.model.ShopRecord
import com.example.data.supabase.SupabaseHomepageHeroDto
import com.example.ui.theme.JTOrange
import kotlinx.coroutines.delay

private const val DEFAULT_HERO_IMAGE = "https://xrrhbnnzjqkqnqdvayjw.supabase.co/storage/v1/object/public/product-images/promotions/2a393677-4644-4a6d-ab20-d2ca21ece04c.png"

@Composable
fun MarketplaceHero(
  heroes: List<SupabaseHomepageHeroDto>,
  featuredProduct: Product? = null,
  featuredShop: ShopRecord? = null,
  sellerStatus: String? = null,
  onPrimaryCtaClick: (String?) -> Unit,
  onSecondaryCtaClick: (String?) -> Unit,
  onProductClick: (Product) -> Unit = {},
  onShopClick: (ShopRecord) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var activeHeroIndex by remember { mutableStateOf(0) }
  var isPaused by remember { mutableStateOf(false) }

  // Synchronize index when heroes list changes
  LaunchedEffect(heroes.size) {
    activeHeroIndex = 0
  }

  // Auto-scroll every 6 seconds when 2+ heroes exist, matching website
  LaunchedEffect(heroes.size, activeHeroIndex, isPaused) {
    if (heroes.size > 1 && !isPaused) {
      delay(6000L)
      activeHeroIndex = (activeHeroIndex + 1) % heroes.size
    }
  }

  // Smooth ambient animation for glowing orange highlights
  val infiniteTransition = rememberInfiniteTransition(label = "hero_anim")
  val orangeGlow by infiniteTransition.animateFloat(
    initialValue = 0.25f,
    targetValue = 0.65f,
    animationSpec = infiniteRepeatable(
      animation = tween(2800, easing = EaseInOutSine),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orange_glow"
  )
  val floatBadgeY by infiniteTransition.animateFloat(
    initialValue = -3f,
    targetValue = 3f,
    animationSpec = infiniteRepeatable(
      animation = tween(2200, easing = EaseInOutSine),
      repeatMode = RepeatMode.Reverse
    ),
    label = "float_badge"
  )

  val activeHero = heroes.getOrNull(activeHeroIndex)
  val title = activeHero?.title ?: "Everything You Need,\nAll in One Place"
  val subtitle = activeHero?.subtitle ?: "Buy and sell products, discover services, find properties, and connect with trusted vendors across multiple categories."
  val primaryLabel = activeHero?.ctaText?.trim()?.takeIf { it.isNotBlank() } ?: "Start Shopping"
  val primaryDestination = activeHero?.ctaDestination

  val sellerLabel = when (sellerStatus) {
    "approved" -> "Open Seller Dashboard"
    "pending", "rejected" -> "View Application Status"
    else -> "Become a Seller"
  }
  val sellerDestination = if (sellerStatus == "approved") "/vendor" else "/vendor/apply"

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .shadow(elevation = 5.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x33FF6B00))
      .testTag("marketplace_hero_card")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          brush = Brush.linearGradient(
            colors = listOf(
              Color(0xFFFFFFFF),
              Color(0xFFFFF9F2),
              Color(0xFFFFF2E2)
            )
          )
        )
        .border(
          width = 1.2.dp,
          brush = Brush.linearGradient(
            listOf(
              Color(0xFFFFD1A9).copy(alpha = orangeGlow + 0.35f),
              Color(0xFFFFB380).copy(alpha = orangeGlow + 0.2f),
              Color(0xFFE5E1D8)
            )
          ),
          shape = RoundedCornerShape(20.dp)
        )
        .padding(18.dp)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // 1. Eyebrow & Animated Live Express Badge
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(width = 22.dp, height = 3.dp)
                .background(JTOrange, RoundedCornerShape(1.5.dp))
            )
            Text(
              text = "ONE MARKETPLACE, MORE POSSIBILITIES",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp,
              color = Color(0xFFC05621)
            )
          }
        }

        // 2. Title & Subtitle with smooth slide crossfade
        AnimatedContent(
          targetState = title to subtitle,
          transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
          label = "hero_text_transition"
        ) { (currentTitle, currentSubtitle) ->
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = currentTitle,
              fontSize = 25.sp,
              fontWeight = FontWeight.ExtraBold,
              lineHeight = 29.sp,
              color = Color(0xFF1E241B)
            )
            Text(
              text = currentSubtitle,
              fontSize = 13.sp,
              lineHeight = 18.sp,
              color = Color(0xFF5A6652)
            )
          }
        }

        // 3. Action Buttons (Primary & Secondary)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Button(
            onClick = {
              if (primaryDestination != null && primaryDestination.startsWith("https://")) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(primaryDestination))) }
              } else {
                onPrimaryCtaClick(primaryDestination)
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = JTOrange),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.testTag("hero_primary_cta")
          ) {
            Text(primaryLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
          }

          OutlinedButton(
            onClick = { onSecondaryCtaClick(sellerDestination) },
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCAD4C2)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF385E42)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.testTag("hero_secondary_cta")
          ) {
            Text(sellerLabel, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
          }
        }

        // 4. Trust Line
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(CircleShape)
              .background(Color(0xFFE7ECE3)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = null,
              tint = Color(0xFF385E42),
              modifier = Modifier.size(13.dp)
            )
          }
          Text(
            text = "Trusted by buyers and sellers",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF5A6652)
          )
        }

        // 5. Visual Asset Frame with Arch & Overlay Badges
        val heroModel = activeHero?.mobileImageUrl?.takeIf { it.isNotBlank() }
          ?: activeHero?.imageUrl?.takeIf { it.isNotBlank() }
          ?: R.drawable.img_hero_default

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(215.dp)
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(Color(0xFFFFEDE0))
            .border(
              width = 1.dp,
              color = Color(0xFFFFD4B3).copy(alpha = orangeGlow + 0.35f),
              shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            )
        ) {
          AsyncImage(
            model = heroModel,
            contentDescription = activeHero?.imageAltText ?: "JTBazar Marketplace Hero",
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.img_hero_default),
            error = painterResource(R.drawable.img_hero_default),
            modifier = Modifier
              .fillMaxSize()
              .clickable {
                if (primaryDestination != null && primaryDestination.startsWith("https://")) {
                  runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(primaryDestination))) }
                } else {
                  onPrimaryCtaClick(primaryDestination)
                }
              }
          )

          // Gradient overlay for bottom readability
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                Brush.verticalGradient(
                  0.55f to Color.Transparent,
                  1.0f to Color.Black.copy(alpha = 0.55f)
                )
              )
          )

          // Overlay 1: Featured Product (top-start or bottom-start)
          if (featuredProduct != null) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color.White.copy(alpha = 0.94f),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCD7CB)),
              shadowElevation = 4.dp,
              modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .widthIn(max = 210.dp)
                .clickable { onProductClick(featuredProduct) }
            ) {
              Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                if (!featuredProduct.imageUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = featuredProduct.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .size(38.dp)
                      .clip(RoundedCornerShape(5.dp))
                  )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                  Text(
                    text = "FEATURED PRODUCT",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7A8374),
                    letterSpacing = 0.5.sp
                  )
                  Text(
                    text = featuredProduct.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E241B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "Rs. ${String.format("%,d", featuredProduct.price)}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF9B4828)
                  )
                }
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  tint = Color(0xFF385E42),
                  modifier = Modifier.size(13.dp)
                )
              }
            }
          }

          // Overlay 2: Featured Marketplace Shop (bottom-end)
          if (featuredShop != null) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color.White.copy(alpha = 0.94f),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCD7CB)),
              shadowElevation = 4.dp,
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .widthIn(max = 195.dp)
                .clickable { onShopClick(featuredShop) }
            ) {
              Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8EDE2)),
                  contentAlignment = Alignment.Center
                ) {
                  if (!featuredShop.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                      model = featuredShop.logoUrl,
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Default.Storefront,
                      contentDescription = null,
                      tint = Color(0xFF385E42),
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                  Text(
                    text = "MARKETPLACE SHOP",
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7A8374),
                    letterSpacing = 0.5.sp
                  )
                  Text(
                    text = featuredShop.name,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E241B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (featuredShop.city.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(9.dp), tint = Color(0xFF7A8374))
                      Text(featuredShop.city, fontSize = 9.sp, color = Color(0xFF7A8374), maxLines = 1)
                    }
                  }
                }
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  tint = Color(0xFF385E42),
                  modifier = Modifier.size(13.dp)
                )
              }
            }
          }
        }

        // 6. Slide Controls (when multiple published heroes exist)
        if (heroes.size > 1) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = {
                activeHeroIndex = (activeHeroIndex - 1 + heroes.size) % heroes.size
                isPaused = true
              },
              modifier = Modifier
                .size(28.dp)
                .border(1.dp, Color(0xFFD7D3CA), CircleShape)
            ) {
              Icon(Icons.Default.ChevronLeft, contentDescription = "Previous slide", modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
              text = "${String.format("%02d", activeHeroIndex + 1)} / ${String.format("%02d", heroes.size)}",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF5A6652)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
              heroes.indices.forEach { index ->
                Box(
                  modifier = Modifier
                    .size(width = if (index == activeHeroIndex) 16.dp else 6.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index == activeHeroIndex) Color(0xFF385E42) else Color(0xFFD7D3CA))
                    .clickable {
                      activeHeroIndex = index
                      isPaused = true
                    }
                )
              }
            }

            Spacer(modifier = Modifier.width(10.dp))

            IconButton(
              onClick = {
                activeHeroIndex = (activeHeroIndex + 1) % heroes.size
                isPaused = true
              },
              modifier = Modifier
                .size(28.dp)
                .border(1.dp, Color(0xFFD7D3CA), CircleShape)
            ) {
              Icon(Icons.Default.ChevronRight, contentDescription = "Next slide", modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }
  }
}
