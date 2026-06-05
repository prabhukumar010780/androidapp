package com.destinyai.astrology.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

private const val DELETE_CONFIRM_WORD = "DELETE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountSheet(
    hasActiveSubscription: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    isDeleting: Boolean = false,
    errorMessage: String? = null,
) {
    var inputText by remember { mutableStateOf("") }
    val inputMatches = inputText == DELETE_CONFIRM_WORD
    val canDelete = inputMatches && !hasActiveSubscription && !isDeleting
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

    // iOS parity: DeleteAccountSheet.swift:164 .interactiveDismissDisabled(isDeleting).
    // confirmValueChange returns false while deleting so swipe-down/scrim taps cannot
    // dismiss the sheet — the user has to wait for the delete RPC to finish.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !isDeleting },
    )

    ModalBottomSheet(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        sheetState = sheetState,
        containerColor = NavySurface,
        // iOS .interactiveDismissDisabled(isDeleting) parity — block back-press
        // dismissal while the delete RPC is in flight. Drag/scrim is blocked via
        // the sheetState.confirmValueChange callback above.
        properties = ModalBottomSheetProperties(
            securePolicy = androidx.compose.ui.window.SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = !isDeleting,
        ),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gold.copy(alpha = 0.3f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
                .testTag("delete_account_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Warning icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.delete_account_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = CreamText,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.delete_account_subtitle),
                fontSize = 13.sp,
                color = CreamDim,
            )

            Spacer(Modifier.height(20.dp))

            // Bullet rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DeleteBulletRow(
                    icon = Icons.Filled.Cancel,
                    text = stringResource(R.string.delete_account_bullet_remove),
                    iconTint = CreamDim,
                )
                DeleteBulletRow(
                    icon = Icons.Filled.Email,
                    text = stringResource(R.string.delete_account_bullet_history),
                    iconTint = CreamDim,
                )
                DeleteBulletRow(
                    icon = Icons.Filled.Delete,
                    text = stringResource(R.string.delete_account_bullet_irreversible),
                    iconTint = Color(0xFFFF5252),
                )
            }

            if (hasActiveSubscription) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF8C00).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFFF8C00).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.delete_account_active_subscription_warning),
                        fontSize = 13.sp,
                        color = Color(0xFFFF8C00),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text(stringResource(R.string.type_delete_to_confirm), color = CreamDim) },
                enabled = !isDeleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_account_confirm_input"),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (inputMatches) Gold else Color(0xFFFF5252).copy(alpha = 0.6f),
                    unfocusedBorderColor = if (inputMatches) Gold.copy(alpha = 0.6f) else CreamDim.copy(alpha = 0.3f),
                    focusedTextColor = CreamText,
                    unfocusedTextColor = CreamText,
                    cursorColor = Gold,
                    focusedLabelColor = if (inputMatches) Gold else CreamDim,
                    unfocusedContainerColor = NavySurface,
                    focusedContainerColor = NavySurface,
                ),
            )

            Spacer(Modifier.height(16.dp))

            // Inline error — mirrors iOS DeleteAccountSheet errorMessage at line 112-117.
            if (!errorMessage.isNullOrEmpty()) {
                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    color = Color(0xFFFF5252),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("delete_account_error_message"),
                )
            }

            Button(
                onClick = {
                    haptic.heavy()
                    // Do NOT dismiss here — sheet must stay open while delete is in flight.
                    // ProfileViewModel will dismiss on success (isDeleted->true) or surface
                    // an inline error and keep the sheet visible. Mirrors iOS behavior at
                    // ProfileView.swift:844-872.
                    onConfirmDelete()
                },
                enabled = canDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("delete_account_confirm_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5252),
                    contentColor = Color.White,
                    disabledContainerColor = NavyVariant,
                    disabledContentColor = CreamDim,
                ),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(stringResource(R.string.delete_account_button), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = {
                    if (!isDeleting) {
                        haptic.light()
                        onDismiss()
                    }
                },
                enabled = !isDeleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_account_cancel_button"),
            ) {
                Text(stringResource(R.string.delete_account_cancel), color = CreamDim, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun DeleteBulletRow(icon: ImageVector, text: String, iconTint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, fontSize = 13.sp, color = CreamDim, modifier = Modifier.weight(1f))
    }
}
