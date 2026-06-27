package com.oasismall.oasisai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AtomicJsonWriterTest {

    @Test
    fun writeTextAtomic_replacesContent() {
        val dir = createTempDir("atomic-json")
        val file = File(dir, "state.json")
        file.writeText("{\"v\":1}")
        file.writeTextAtomic("{\"v\":2}")
        assertEquals("{\"v\":2}", file.readText())
    }

    @Test
    fun writeTextAtomic_neverLeavesPartialFile() {
        val dir = createTempDir("atomic-json")
        val file = File(dir, "state.json")
        file.writeTextAtomic("{\"ok\":true}")
        assertTrue(file.exists())
        assertEquals("{\"ok\":true}", file.readText())
        dir.listFiles()?.none { it.name.endsWith(".tmp") }?.let { assertTrue(it) }
    }
}
