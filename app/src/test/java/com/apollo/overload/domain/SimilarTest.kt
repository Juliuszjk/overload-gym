package com.apollo.overload.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarTest {
    private val rdl = mapOf("hamstrings" to 1f, "glutes" to 0.8f, "lowerback" to 0.5f, "adductors" to 0.4f)
    private val legCurl = mapOf("hamstrings" to 1f, "calves" to 0.2f)
    private val legPress = mapOf("quads" to 1f, "glutes" to 0.6f)

    @Test fun legCurlFitsRdl() {
        assertTrue(Similar.fits(rdl, legCurl))
        assertFalse(Similar.fits(rdl, legPress))
    }

    @Test fun scoreOrdersByShape() {
        assertEquals(1f, Similar.score(rdl, rdl), 1e-4f)
        assertTrue(Similar.score(rdl, legCurl) > Similar.score(rdl, legPress))
        assertEquals(0f, Similar.score(rdl, emptyMap()), 0f)
    }
}
