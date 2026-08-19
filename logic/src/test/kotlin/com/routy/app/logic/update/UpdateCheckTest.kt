package com.routy.app.logic.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckTest {
    @Test
    fun `newer major version is newer`() {
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun `newer patch version is newer`() {
        assertTrue(isNewerVersion("1.0.2", "1.0.1"))
    }

    @Test
    fun `identical versions are not newer`() {
        assertFalse(isNewerVersion("1.2.3", "1.2.3"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(isNewerVersion("1.0.0", "1.1.0"))
    }

    @Test
    fun `leading v prefix is ignored`() {
        assertTrue(isNewerVersion("v1.1.0", "1.0.0"))
    }

    @Test
    fun `missing segments default to zero`() {
        assertTrue(isNewerVersion("1.1", "1.0.9"))
        assertFalse(isNewerVersion("1.0", "1.0.1"))
    }
}
