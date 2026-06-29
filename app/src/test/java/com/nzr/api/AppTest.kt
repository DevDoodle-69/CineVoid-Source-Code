package com.nzr.api
import kotlinx.coroutines.runBlocking
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request

class AppTest {
    @Test
    fun testApi() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://movie-box-api-v1.onrender.com/api/test-live/item?id=8906247916759695608")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: ""
            println("TRENDING_PREVIEW:\n" + json.take(1500))
        }
    }
}
