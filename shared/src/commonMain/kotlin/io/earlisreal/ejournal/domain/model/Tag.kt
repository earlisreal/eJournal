package io.earlisreal.ejournal.domain.model

/**
 * A user-defined label attachable to closed positions. The vocabulary is global across portfolios;
 * [color] is a hex string (e.g. "#4CAF50"). Assignments are anchored to a position's opening
 * transaction id — see PositionTag / PositionTagService.
 */
data class Tag(val id: Long, val name: String, val color: String)

/**
 * Default color choices offered when creating a tag (hex "#RRGGBB"). Medium-saturation hues picked to
 * stay legible on both the light and dark surfaces. Quick-create cycles through these.
 */
val defaultTagColors: List<String> = listOf(
    "#4C8DF6", // blue
    "#22B8A6", // teal
    "#16A34A", // green
    "#E0A340", // amber
    "#EF6C3B", // orange
    "#DC2626", // red
    "#A855F7", // purple
    "#EC4899", // pink
    "#64748B", // slate
)
