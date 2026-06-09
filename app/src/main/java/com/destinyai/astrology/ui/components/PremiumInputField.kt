package com.destinyai.astrology.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyInput
import com.destinyai.astrology.ui.theme.TextTertiary

/**
 * iOS parity (PremiumTextField.swift).
 *
 * Renders a label above the field plus a placeholder shown inside the input
 * row when empty. The input row is a filled rounded rectangle (12.dp radius)
 * with a fixed 52.dp height, a leading 16.dp icon that animates between Gold
 * (focused) and TextTertiary (unfocused), and a gold border that thickens on
 * focus (1.dp @ 15% → 1.5.dp @ 50%).
 */
@Composable
fun PremiumInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    icon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isSecure: Boolean = false,
    enabled: Boolean = true,
    accessibilityId: String? = null,
) {
    val effectiveVisualTransformation =
        if (isSecure) PasswordVisualTransformation() else visualTransformation
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Gold.copy(alpha = 0.5f) else Gold.copy(alpha = 0.15f),
        animationSpec = tween(durationMillis = 200),
        label = "borderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 200),
        label = "borderWidth",
    )
    val iconTint by animateColorAsState(
        targetValue = if (isFocused) Gold else TextTertiary,
        animationSpec = tween(durationMillis = 200),
        label = "iconTint",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = CreamDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(NavyInput, RoundedCornerShape(12.dp))
                .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (accessibilityId != null) it.testTag(accessibilityId) else it },
                    textStyle = TextStyle(
                        color = if (enabled) CreamText else CreamDim,
                        fontSize = 16.sp,
                    ),
                    interactionSource = interactionSource,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = effectiveVisualTransformation,
                    enabled = enabled,
                    cursorBrush = SolidColor(Gold),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                color = TextTertiary,
                                fontSize = 16.sp,
                            )
                        }
                        innerTextField()
                    },
                )
            }
        }
    }
}
