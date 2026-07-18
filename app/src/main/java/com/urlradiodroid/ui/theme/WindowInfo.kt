package com.urlradiodroid.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** True when the device is in landscape orientation, for screens that need a different layout shape. */
@Composable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * True when there's enough width for a list+detail two-pane layout — Material's "expanded" width
 * class breakpoint (840dp). Width-based rather than orientation-based on purpose: a folded phone
 * in landscape and an actual tablet/unfolded foldable can both report landscape orientation, but
 * only the latter has room for a second pane.
 */
@Composable
fun isWideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= 840
