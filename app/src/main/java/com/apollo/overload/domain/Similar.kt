package com.apollo.overload.domain

import kotlin.math.sqrt

/** How alike two exercises are by the muscles they work (muscle key -> activation 0..1). */
object Similar {
    /** Cosine similarity of the two muscle maps, 0..1. */
    fun score(a: Map<String, Float>, b: Map<String, Float>): Float {
        val dot = a.entries.sumOf { (k, v) -> (v * (b[k] ?: 0f)).toDouble() }
        val na = sqrt(a.values.sumOf { (it * it).toDouble() })
        val nb = sqrt(b.values.sumOf { (it * it).toDouble() })
        return if (na == 0.0 || nb == 0.0) 0f else (dot / (na * nb)).toFloat()
    }

    /** True when [candidate] works [original]'s main muscle as one of its own main ones (activation 0.5 or more). */
    fun fits(original: Map<String, Float>, candidate: Map<String, Float>): Boolean {
        val main = original.maxByOrNull { it.value }?.key ?: return false
        return (candidate[main] ?: 0f) >= 0.5f
    }
}
