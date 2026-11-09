package top.madkarma.conduit.service.world

import kotlin.test.Test
import kotlin.test.assertEquals

class RegionLimitTest {
    @Test
    fun `a region over the draw limit says how to split it`() {
        assertEquals(
            "Region of 10800 blocks (108x1x100) exceeds the limit of 8192. Split it along x or z.",
            tooLarge(108, 1, 100, 8192),
        )
        assertEquals(
            "Region of 16384 blocks (16x64x16) exceeds the limit of 8192. Split it into 2 parts " +
                "of up to 32 blocks of height.",
            tooLarge(16, 64, 16, 8192),
        )
    }
}
