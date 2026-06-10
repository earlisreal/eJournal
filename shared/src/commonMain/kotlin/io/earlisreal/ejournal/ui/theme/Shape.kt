package io.earlisreal.ejournal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(14.dp)
val ControlShape = RoundedCornerShape(10.dp)
val PillShape = RoundedCornerShape(8.dp)

val AppShapes = Shapes(
    small = PillShape,
    medium = ControlShape,
    large = CardShape,
)
