package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.DestinyTileType
import com.destinyai.astrology.domain.model.YogaDoshaData
import com.destinyai.astrology.domain.model.YogaItem
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

@Composable
fun AdditionalYogasScreen(
    boyYogaData: YogaDoshaData?,
    girlYogaData: YogaDoshaData?,
    boyName: String,
    girlName: String,
    onBack: () -> Unit,
) {
    var selectedPartner by remember { mutableIntStateOf(0) }
    var selectedTile by remember { mutableStateOf(DestinyTileType.WEALTH) }

    val currentData = if (selectedPartner == 0) boyYogaData else girlYogaData
    val currentName = if (selectedPartner == 0) boyName else girlName

    val topicItems = remember(currentData, selectedTile) {
        yogasSortedAlphabetically(currentData?.items(selectedTile) ?: emptyList())
    }

    val tileCounts = remember(currentData) {
        DestinyTileType.topicTiles.associateWith { tile ->
            currentData?.items(tile)?.size ?: 0
        } + mapOf(DestinyTileType.DOSHA to (currentData?.activeDoshas?.size ?: 0))
    }

    val allTiles = DestinyTileType.topicTiles + listOf(DestinyTileType.DOSHA)

    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Gold)
                }
                Text(
                    stringResource(R.string.yogas_analysis),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText,
                )
            }

            // Topic tab row with counts (iOS: MagicTabbar is Tier 1, on top)
            MagicTabbar(
                tabs = allTiles,
                selectedTab = selectedTile,
                tileCounts = tileCounts,
                onSelect = { selectedTile = it },
            )

            Spacer(Modifier.height(4.dp))

            // Partner tab selector (iOS: ProfileSwitcher is Tier 2, below tabbar)
            ProfileSwitcher(
                selectedIndex = selectedPartner,
                names = listOf(boyName, girlName),
                onSelect = { selectedPartner = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            if (currentData == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "yoga_empty_state" },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = CreamDim,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            stringResource(R.string.yoga_no_data),
                            fontSize = 14.sp,
                            color = CreamDim,
                        )
                    }
                }
            } else {
                AnimatedContent(
                    targetState = "${selectedTile.name}_${selectedPartner}",
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) +
                            slideInHorizontally(animationSpec = tween(220)) { it / 8 }) togetherWith
                            (fadeOut(animationSpec = tween(160)) +
                                slideOutHorizontally(animationSpec = tween(160)) { -it / 8 })
                    },
                    label = "yogas_content_swap",
                ) { contentKey ->
                    TopicListView(
                        tile = selectedTile,
                        items = topicItems,
                        personName = currentName,
                        modifier = Modifier.fillMaxSize(),
                        key = contentKey,
                    )
                }
            }
        }
    }
}

// MagicTabbar lives in MagicTabbar.kt

// Pure helper — unit testable
internal fun yogasSortedAlphabetically(items: List<com.destinyai.astrology.domain.model.YogaItem>): List<com.destinyai.astrology.domain.model.YogaItem> =
    items.sortedBy { it.name.lowercase() }

internal fun activeDoshaTileCount(doshas: List<com.destinyai.astrology.domain.model.YogaItem>): Int =
    doshas.count { it.isActive }

internal fun allDoshaItems(data: YogaDoshaData?): List<YogaItem> =
    data?.doshas ?: emptyList()

internal fun toYogaDoshaData(map: Map<String, Any>): YogaDoshaData = map.yogaDoshaDataFrom()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any>.yogaDoshaDataFrom(): YogaDoshaData {
    val yogas = entries.mapNotNull { (key, value) ->
        val map = value as? Map<*, *> ?: return@mapNotNull null
        val isDosha = map["is_dosha"] as? Boolean ?: false
        val isActive = map["yoga_present"] == true || map["is_active"] == true || map["active"] == true
        val description = (map["description"] as? String) ?: (map["effect"] as? String) ?: ""
        val strengthRaw = (map["strength"] as? Number)?.toDouble() ?: if (isActive) 100.0 else 0.0
        val category = (map["category"] as? String)
        YogaItem(
            id = key,
            name = key.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
            yogaKey = key,
            status = if (!isActive) "C" else "A",
            strengthRaw = strengthRaw,
            description = description,
            category = category,
            isDosha = isDosha,
            outcome = map["outcome"] as? String,
            formation = map["formation"] as? String,
            reason = map["reason"] as? String,
            planets = map["planets"] as? String,
            houses = map["houses"] as? String,
        )
    }
    val (doshas, normalYogas) = yogas.partition { it.isDosha == true }
    return YogaDoshaData(yogas = normalYogas, doshas = doshas)
}
