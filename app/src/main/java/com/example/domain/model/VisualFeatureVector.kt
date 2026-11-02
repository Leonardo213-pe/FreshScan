package com.example.domain.model

import kotlin.math.abs

data class VisualFeatureVector(
    val redPermille: Int,
    val yellowPermille: Int,
    val greenPermille: Int,
    val orangePermille: Int,
    val purplePermille: Int,
    val spotPermille: Int,
    val aspectRatioPermille: Int
) {
    fun matchesRange(rule: VisualForecastRule): Boolean {
        return redPermille in rule.redMinPermille..rule.redMaxPermille &&
            yellowPermille in rule.yellowMinPermille..rule.yellowMaxPermille &&
            greenPermille in rule.greenMinPermille..rule.greenMaxPermille &&
            orangePermille in rule.orangeMinPermille..rule.orangeMaxPermille &&
            purplePermille in rule.purpleMinPermille..rule.purpleMaxPermille &&
            spotPermille in rule.spotMinPermille..rule.spotMaxPermille &&
            aspectRatioPermille in rule.aspectRatioMinPermille..rule.aspectRatioMaxPermille
    }

    fun distanceToRuleCenter(rule: VisualForecastRule): Long {
        val redCenter = (rule.redMinPermille + rule.redMaxPermille) / 2L
        val yellowCenter = (rule.yellowMinPermille + rule.yellowMaxPermille) / 2L
        val greenCenter = (rule.greenMinPermille + rule.greenMaxPermille) / 2L
        val orangeCenter = (rule.orangeMinPermille + rule.orangeMaxPermille) / 2L
        val purpleCenter = (rule.purpleMinPermille + rule.purpleMaxPermille) / 2L
        val spotCenter = (rule.spotMinPermille + rule.spotMaxPermille) / 2L
        val aspectCenter = (rule.aspectRatioMinPermille + rule.aspectRatioMaxPermille) / 2L

        return abs(redPermille - redCenter) +
            abs(yellowPermille - yellowCenter) +
            abs(greenPermille - greenCenter) +
            abs(orangePermille - orangeCenter) +
            abs(purplePermille - purpleCenter) +
            abs(spotPermille - spotCenter) * 2L +
            abs(aspectRatioPermille - aspectCenter) / 2L
    }
}
