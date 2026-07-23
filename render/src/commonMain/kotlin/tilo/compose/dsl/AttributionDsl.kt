@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import tilo.compose.core.layers.Attribution

/**
 * Creates attribution metadata for a map layer.
 */
@ExperimentalTiloApi
fun attribution(
    label: String,
    url: String? = null,
): Attribution = Attribution(label = label, url = url)

internal fun List<Attribution>.withSingle(attribution: Attribution?): List<Attribution> =
    if (attribution == null) this else this + attribution
