package com.destinyai.astrology

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.destinyai.astrology.ui.nav.AppNav
import com.destinyai.astrology.ui.theme.DestinyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DestinyTheme {
                AppNav()
            }
        }
    }
}
