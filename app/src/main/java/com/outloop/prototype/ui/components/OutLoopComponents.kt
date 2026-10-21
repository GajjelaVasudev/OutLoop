package com.outloop.prototype.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outloop.prototype.ui.theme.LabelMonoMedium
import com.outloop.prototype.ui.theme.OutLoopTheme

// ============================================================================
// REUSABLE STITCH UI BUTTONS
// ============================================================================

enum class OutLoopButtonStyle {
    PrimaryKinetic, // Electric Lime background (#B9F45A), dark text (#111318)
    Secondary,      // Surface Card background with subtle hairline border
    Outlined,       // Transparent background with 1px border
    Destructive     // Subdued red container with error text
}

@Composable
fun OutLoopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OutLoopButtonStyle = OutLoopButtonStyle.PrimaryKinetic,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    height: Dp = 50.dp
) {
    val colors = OutLoopTheme.colors

    val containerColor = when (style) {
        OutLoopButtonStyle.PrimaryKinetic -> colors.accentLime
        OutLoopButtonStyle.Secondary -> colors.surfaceCard
        OutLoopButtonStyle.Outlined -> Color.Transparent
        OutLoopButtonStyle.Destructive -> colors.statusError.copy(alpha = 0.15f)
    }

    val contentColor = when (style) {
        OutLoopButtonStyle.PrimaryKinetic -> colors.onAccentLime
        OutLoopButtonStyle.Secondary -> colors.textPrimary
        OutLoopButtonStyle.Outlined -> colors.textPrimary
        OutLoopButtonStyle.Destructive -> colors.statusError
    }

    val border = when (style) {
        OutLoopButtonStyle.Secondary -> BorderStroke(1.dp, colors.surfaceBorder)
        OutLoopButtonStyle.Outlined -> BorderStroke(1.dp, colors.surfaceBorder)
        else -> null
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = OutLoopTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ============================================================================
// REUSABLE STITCH CARD CONTAINER
// ============================================================================

@Composable
fun OutLoopCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = OutLoopTheme.colors.surfaceCard,
    borderColor: Color = OutLoopTheme.colors.surfaceBorder,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

// ============================================================================
// STATUS BADGES & LIVE INDICATORS
// ============================================================================

@Composable
fun OutLoopStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = OutLoopTheme.colors.accentLime.copy(alpha = 0.15f),
    contentColor: Color = OutLoopTheme.colors.accentLime,
    pulse: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (pulse) {
                PulsingDot(color = contentColor)
            }
            Text(
                text = text,
                color = contentColor,
                style = OutLoopTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PulsingDot(
    modifier: Modifier = Modifier,
    color: Color = OutLoopTheme.colors.accentLime,
    size: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

// ============================================================================
// FILTER CHIP (For Diagnostic telemetry stream filters)
// ============================================================================

@Composable
fun OutLoopFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null
) {
    val colors = OutLoopTheme.colors
    val containerColor = if (selected) colors.accentLime else colors.surfaceCard
    val contentColor = if (selected) colors.onAccentLime else colors.textSecondary
    val border = if (selected) null else BorderStroke(1.dp, colors.surfaceBorder)

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = containerColor,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = contentColor,
                style = LabelMonoMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            if (count != null) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) colors.onAccentLime.copy(alpha = 0.15f) else colors.surfaceContainer
                ) {
                    Text(
                        text = count.toString(),
                        color = contentColor,
                        style = OutLoopTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// TOP APP BAR (Edge-to-edge support with Stitch styling)
// ============================================================================

@Composable
fun OutLoopTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    val colors = OutLoopTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.canvasRoot.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, colors.surfaceBorder.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onBackClick != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(38.dp)
                            .background(colors.surfaceContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = OutLoopTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions
                )
            }
        }
    }
}

// ============================================================================
// BOTTOM NAVIGATION BAR (Matches Screen 4 specifications)
// ============================================================================

data class BottomNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun OutLoopBottomNavBar(
    items: List<BottomNavItem>,
    selectedItemId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutLoopTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.canvasRoot.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, colors.surfaceBorder.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = item.id == selectedItemId
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onItemSelected(item.id) }
                            .padding(vertical = 4.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) colors.accentLime else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (isSelected) colors.onAccentLime else colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            style = OutLoopTheme.typography.labelSmall,
                            color = if (isSelected) colors.textPrimary else colors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
