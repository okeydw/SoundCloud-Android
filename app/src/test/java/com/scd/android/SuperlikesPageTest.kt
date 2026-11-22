package com.scd.android

import java.io.IOException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuperlikesPageTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun followsServerOffsetEvenWhenSomeTrackDetailsAreMissing() {
        val first = json.decodeFromString<SuperlikesPage>(
            """{"collection":[{"urn":"soundcloud:tracks:7","user_superlike":true}],"next_href":"?limit=200&offset=200"}""",
        )
        assertEquals(listOf("soundcloud:tracks:7"), first.collection.map { it.urn })
        assertEquals(200, first.nextOffset(0))
        val last = json.decodeFromString<SuperlikesPage>("""{"collection":[],"next_href":null}""")
        assertNull(last.nextOffset(200))
    }

    @Test(expected = IOException::class)
    fun rejectsRepeatedPageInsteadOfLooping() {
        SuperlikesPage(emptyList(), "?limit=200&offset=200").nextOffset(200)
    }
}
