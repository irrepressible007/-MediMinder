package com.example.mediminder.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The cloud copy is a gzipped backup in parts that fit Firestore's 1 MiB documents. */
class CloudPackTest {
    @Test fun `a small backup is one part and comes back the same`() {
        val text = """{"medications":[{"name":"নাপা","strength":"500 mg"}]}""".repeat(50)
        val parts = CloudBackup.pack(text)
        assertEquals(1, parts.size)
        assertEquals(text, CloudBackup.unpack(parts))
    }

    @Test fun `a large backup is split, every part fits, and it joins back`() {
        val random = Random(7)
        val text = buildString { repeat(2_000_000) { append(('a'..'z').random(random)) } }
        val parts = CloudBackup.pack(text)
        assertTrue("several parts", parts.size > 1)
        assertTrue("each fits a Firestore document", parts.all { it.size <= CloudBackup.PART_BYTES })
        assertEquals(text, CloudBackup.unpack(parts))
    }
}
