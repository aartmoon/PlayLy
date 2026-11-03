package com.playly.app

import com.playly.app.generated.infrastructure.Serializer
import com.playly.app.generated.models.GamePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneratedClientTest {
    @Test
    fun decodesEmptyCatalogPage() {
        val adapter = Serializer.moshi.adapter(GamePage::class.java)
        val page = requireNotNull(adapter.fromJson("""{"items":[],"nextCursor":null}"""))
        assertEquals(emptyList<Any>(), page.items)
        assertNull(page.nextCursor)
    }
}
