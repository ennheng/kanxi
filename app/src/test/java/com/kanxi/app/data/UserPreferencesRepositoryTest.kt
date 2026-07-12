package com.kanxi.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesRepositoryTest {
    @Test
    fun textSizeLevel_unknownOrMissingValueFallsBackToLarge() {
        assertEquals(TextSizeLevel.LARGE, TextSizeLevel.fromStorage(null))
        assertEquals(TextSizeLevel.LARGE, TextSizeLevel.fromStorage("future_value"))
        assertEquals(TextSizeLevel.LARGE, TextSizeLevel.fromStorage("large"))
        assertEquals(TextSizeLevel.EXTRA_LARGE, TextSizeLevel.fromStorage("extra_large"))
    }
}
