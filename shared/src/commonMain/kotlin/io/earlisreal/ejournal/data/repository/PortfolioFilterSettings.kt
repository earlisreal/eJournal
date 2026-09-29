package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.domain.model.Portfolio
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal const val PORTFOLIO_FILTER_STATE_KEY = "filter.state"

fun encodePortfolioFilterPrefs(prefs: PortfolioFilterPrefs): String {
    val normalized = normalizePortfolioFilterPrefs(prefs)
    return buildJsonObject {
        put("preset", normalized.preset.name)
        put("customFrom", normalized.customFrom?.toString()?.let(::JsonPrimitive) ?: JsonNull)
        put("customTo", normalized.customTo?.toString()?.let(::JsonPrimitive) ?: JsonNull)
        put("segment", normalized.segment.name)
        put("selectedTagIds", buildJsonArray {
            normalized.selectedTagIds.sorted().forEach { add(JsonPrimitive(it)) }
        })
        put("tagMatch", normalized.tagMatch.name)
    }.toString()
}

fun decodePortfolioFilterPrefs(value: String?): PortfolioFilterPrefs {
    if (value == null) return PortfolioFilterPrefs()
    val fields = runCatching { Json.parseToJsonElement(value) as? JsonObject }.getOrNull()
        ?: return PortfolioFilterPrefs()

    val preset = fields.enumValue("preset", DateRangePreset.ALL_TIME)
    val filters = PortfolioFilterPrefs(
        preset = preset,
        customFrom = fields.dateValue("customFrom"),
        customTo = fields.dateValue("customTo"),
        segment = fields.enumValue("segment", Segment.ALL),
        selectedTagIds = fields.tagIds(),
        tagMatch = fields.enumValue("tagMatch", TagMatch.ANY),
    )
    return normalizePortfolioFilterPrefs(filters)
}

fun normalizePortfolioFilterPrefs(prefs: PortfolioFilterPrefs): PortfolioFilterPrefs = when {
    prefs.preset != DateRangePreset.CUSTOM -> prefs.copy(customFrom = null, customTo = null)
    prefs.customFrom == null || prefs.customTo == null || prefs.customFrom > prefs.customTo ->
        PortfolioFilterPrefs(segment = prefs.segment, selectedTagIds = prefs.selectedTagIds, tagMatch = prefs.tagMatch)
    else -> prefs
}

suspend fun PortfolioSettingsRepository.putPortfolioFilterPrefs(portfolioId: Long, prefs: PortfolioFilterPrefs) {
    putString(portfolioId, PORTFOLIO_FILTER_STATE_KEY, encodePortfolioFilterPrefs(prefs))
}

suspend fun PortfolioSettingsRepository.loadPortfolioFilterPrefs(
    portfolioId: Long,
    tagRepository: TagRepository,
): PortfolioFilterPrefs {
    val encoded = getString(portfolioId, PORTFOLIO_FILTER_STATE_KEY)
    val stored = decodePortfolioFilterPrefs(encoded)
    val sanitized = stored.withOwnedTags(portfolioId, tagRepository)
    if (encoded != null && sanitized != stored) putPortfolioFilterPrefs(portfolioId, sanitized)
    return sanitized
}

data class InitialPortfolioFilterState(
    val portfolio: Portfolio?,
    val filters: PortfolioFilterPrefs,
)

suspend fun loadInitialPortfolioFilterState(
    portfolios: List<Portfolio>,
    settingsRepository: SettingsRepository,
    portfolioSettings: PortfolioSettingsRepository,
    tagRepository: TagRepository,
): InitialPortfolioFilterState {
    val savedPortfolioId = settingsRepository.getSelectedPortfolioId()
    val legacy = settingsRepository.getLegacyFilterPrefs()
    if (legacy != null) {
        val legacyPortfolio = legacy.portfolioId?.let { id -> portfolios.firstOrNull { it.id == id } }
        if (legacyPortfolio != null && portfolioSettings.getString(legacyPortfolio.id, PORTFOLIO_FILTER_STATE_KEY) == null) {
            val filters = legacy.filters.withOwnedTags(legacyPortfolio.id, tagRepository)
            portfolioSettings.putPortfolioFilterPrefs(legacyPortfolio.id, filters)
        }
        // Clear only after migration succeeds; if storage fails, the next startup can retry.
        settingsRepository.clearLegacyFilterPrefs()
    }

    val portfolio = savedPortfolioId
        ?.let { id -> portfolios.firstOrNull { it.id == id } }
        ?: portfolios.firstOrNull()
    settingsRepository.setSelectedPortfolioId(portfolio?.id)

    val filters = portfolio?.let { selected -> portfolioSettings.loadPortfolioFilterPrefs(selected.id, tagRepository) }
        ?: PortfolioFilterPrefs()

    return InitialPortfolioFilterState(portfolio, filters)
}

suspend fun PortfolioFilterPrefs.withOwnedTags(portfolioId: Long, tagRepository: TagRepository): PortfolioFilterPrefs {
    if (selectedTagIds.isEmpty()) return this
    val ownedIds = tagRepository.getAll(portfolioId).mapTo(mutableSetOf()) { it.id }
    return copy(selectedTagIds = selectedTagIds intersect ownedIds)
}

private inline fun <reified T : Enum<T>> JsonObject.enumValue(key: String, default: T): T =
    (this[key] as? JsonPrimitive)?.contentOrNull?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

private fun JsonObject.dateValue(key: String): LocalDate? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun JsonObject.tagIds(): Set<Long> =
    (this["selectedTagIds"] as? JsonArray)?.mapNotNull { element ->
        (element as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it > 0L }
    }?.toSet().orEmpty()
