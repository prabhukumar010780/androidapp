package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerPickerSheet(
    viewModel: CompatibilityViewModel,
    onDismiss: () -> Unit,
) {
    val savedPartners by viewModel.savedPartners.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadSavedPartners() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = "Select Saved Partner",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold,
            )
            Spacer(Modifier.height(16.dp))

            if (savedPartners.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No saved partners yet",
                        color = CreamDim,
                        fontSize = 15.sp,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(savedPartners) { partner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NavySurface)
                                .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.selectSavedPartner(partner)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partner.name.ifEmpty { "Partner" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreamText,
                                )
                                if (partner.cityOfBirth.isNotEmpty()) {
                                    Text(
                                        text = partner.cityOfBirth,
                                        fontSize = 13.sp,
                                        color = CreamDim,
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
