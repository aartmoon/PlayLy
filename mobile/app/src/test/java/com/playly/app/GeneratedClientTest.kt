package com.playly.app

import com.playly.app.generated.models.GamePage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeneratedClientTest {
    @Test
    fun decodesEmptyCatalogPage() {
        val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            .adapter(GamePage::class.java)
        assertNotNull(adapter.fromJson("""{"items":[],"nextCursor":null}"""))
    }
}
