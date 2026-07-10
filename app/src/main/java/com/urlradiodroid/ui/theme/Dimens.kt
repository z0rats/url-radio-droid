package com.urlradiodroid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Spacing scale on Material's 4dp base grid. Only apply where a value already matches one of these exactly. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
}

/**
 * Corner-radius scale wired into [androidx.compose.material3.MaterialTheme.shapes].
 * extraSmall: icon-sized controls (e.g. play/pause). medium: buttons, fields, chips.
 * large: primary cards. extraLarge: pill-shaped mini-player cards.
 */
val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(40.dp),
    )
