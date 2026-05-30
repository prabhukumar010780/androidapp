package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyInput

/**
 * STUB — TextField wrapper with a floating gold label and underline focus animation.
 *
 * TODO (R3): Animate label upward when the field has content or focus (scale + translate).
 */
@Composable
fun PremiumInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val underlineAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.4f,
        animationSpec = tween(durationMillis = 200),
        label = "underlineAlpha",
    )
    val underlineColor = Gold.copy(alpha = underlineAlpha)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (isFocused) Gold else CreamDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val y = size.height
                    drawLine(
                        color = underlineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
                .padding(bottom = 6.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = if (enabled) CreamText else CreamDim,
                    fontSize = 16.sp,
                ),
                interactionSource = interactionSource,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                enabled = enabled,
                cursorBrush = SolidColor(Gold),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            color = CreamDim.copy(alpha = 0.5f),
                            fontSize = 16.sp,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}
