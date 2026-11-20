package org.avitrack.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ControlShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(16.dp)
private val BorderWidth = 1.dp
private val ButtonShadow = 2.dp
private val CardShadow = 2.dp
private val HoveredCardShadow = 5.dp
private val CardPadding = 16.dp
private val BadgeHorizontalPadding = 10.dp
private val BadgeVerticalPadding = 4.dp
private val BadgeDotSize = 8.dp
private val BadgeItemSpacing = 6.dp
private val ButtonIconSize = 18.dp
private val ButtonItemSpacing = 8.dp
private val DangerHover = Color(0xFFB91C1C)
private const val SECONDARY_BORDER_ALPHA = 0.3f
private const val BADGE_BACKGROUND_ALPHA = 0.2f
private const val BADGE_BORDER_ALPHA = 0.4f

@Composable
fun AviTrackPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalAviTrackColors.current
    AviTrackButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        backgroundColor = colors.yolkPrimary,
        hoveredColor = colors.yolkHover,
        textColor = colors.mahoganyPrimary,
        fontWeight = FontWeight.Bold,
        hasShadow = true,
    )
}

@Composable
fun AviTrackSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalAviTrackColors.current
    AviTrackButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        backgroundColor = colors.cinnamonBorder,
        hoveredColor = colors.mahoganyPrimary,
        textColor = colors.eggshellCanvas,
        fontWeight = FontWeight.Medium,
        border = BorderStroke(BorderWidth, colors.ochreAccent.copy(alpha = SECONDARY_BORDER_ALPHA)),
    )
}

@Composable
fun AviTrackDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    AviTrackButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        backgroundColor = LocalAviTrackColors.current.combCrimson,
        hoveredColor = DangerHover,
        textColor = Color.White,
        fontWeight = FontWeight.Bold,
        hasShadow = true,
    )
}

@Composable
fun AviTrackCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shadowElevation by animateDpAsState(
        targetValue = if (hovered) HoveredCardShadow else CardShadow,
        label = "AviTrack card shadow",
    )
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .shadow(shadowElevation, CardShape, clip = false)
            .hoverable(interactionSource),
        shape = CardShape,
        color = scheme.surface,
        contentColor = scheme.onSurface,
        border = BorderStroke(BorderWidth, scheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(CardPadding), content = content)
    }
}

@Composable
fun AviTrackStatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = BADGE_BACKGROUND_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(BorderWidth, color.copy(alpha = BADGE_BORDER_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = BadgeHorizontalPadding,
                vertical = BadgeVerticalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(BadgeItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(BadgeDotSize).background(color, CircleShape))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun AviTrackTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = LocalAviTrackColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        enabled = enabled,
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.yolkPrimary,
            unfocusedBorderColor = colors.cinnamonBorder,
            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = colors.yolkPrimary,
        ),
    )
}

@Composable
private fun AviTrackButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    backgroundColor: Color,
    hoveredColor: Color,
    textColor: Color,
    fontWeight: FontWeight,
    border: BorderStroke? = null,
    hasShadow: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = if (enabled && hovered) hoveredColor else backgroundColor,
        label = "AviTrack button color",
    )
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = textColor,
        ),
        elevation = if (hasShadow) {
            ButtonDefaults.buttonElevation(
                defaultElevation = ButtonShadow,
                pressedElevation = ButtonShadow,
                focusedElevation = ButtonShadow,
                hoveredElevation = ButtonShadow,
            )
        } else {
            null
        },
        border = border,
        interactionSource = interactionSource,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ButtonIconSize),
            )
        }
        Text(
            text = text,
            modifier = if (icon != null) Modifier.padding(start = ButtonItemSpacing) else Modifier,
            fontWeight = fontWeight,
        )
    }
}
