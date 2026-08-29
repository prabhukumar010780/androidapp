package com.destinyai.astrology.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.data.remote.LocationResult
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyInput
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.TextTertiary

/**
 * Shared city type-ahead sheet — the single autocomplete component used by BOTH
 * the birth-data screen and the compatibility screen (iOS parity: both reuse
 * LocationSearchView). Debounced search is owned by the caller's ViewModel; this
 * renders the live suggestions list, loading, error, and empty states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchSheet(
    results: List<LocationResult>,
    isSearching: Boolean,
    errorRes: Int?,
    onQueryChange: (String) -> Unit,
    onSelect: (city: String, lat: Double, lng: Double, placeId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(40.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gold.copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                GoldGradientText(
                    text = stringResource(R.string.select_city_title),
                    fontSize = 20.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .testTag("location_search_cancel")
                        .testTag("location_search_cancel"),
                ) {
                    Text(text = stringResource(R.string.cancel_action), color = CreamText, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onQueryChange(it) },
                placeholder = { Text(stringResource(R.string.search_for_city), color = TextTertiary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Gold.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.cd_clear_search), tint = TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Gold.copy(alpha = 0.25f),
                    focusedTextColor = CreamText,
                    unfocusedTextColor = CreamText,
                    cursorColor = Gold,
                    unfocusedContainerColor = NavyInput,
                    focusedContainerColor = NavyInput,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            when {
                isSearching -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(text = stringResource(R.string.searching), fontSize = 15.sp, color = CreamDim)
                    }
                }
                errorRes != null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = stringResource(errorRes), fontSize = 14.sp, color = TextTertiary, textAlign = TextAlign.Center)
                    }
                }
                query.length >= 2 && results.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.LocationOff, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(40.dp))
                        Text(text = stringResource(R.string.no_cities_found), fontSize = 18.sp, fontWeight = FontWeight.Normal, color = CreamText, textAlign = TextAlign.Center)
                        Text(text = stringResource(R.string.try_different_search), fontSize = 15.sp, color = CreamDim, textAlign = TextAlign.Center)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                    ) {
                        items(results, key = { it.placeId ?: it.displayName }) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(result.displayName, result.latitude, result.longitude, result.placeId) }
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Gold.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(text = result.displayName, fontSize = 15.sp, color = CreamText)
                            }
                            HorizontalDivider(color = Gold.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }
}
