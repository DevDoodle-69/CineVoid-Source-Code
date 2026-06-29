package com.nzr

import com.nzr.api.ApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ApiTest {
    @Test
    fun testSeriesDetails() = runBlocking {
        try {
            val resNoParams = ApiClient.service.getTestLiveItem("3685")
            val resSeason3 = ApiClient.service.getTestLiveItem("3685", 3)
            java.io.File("./tester.log").writeText("MY_RESULTS => NoParam:${resNoParams.streams.size}, Season3:${resSeason3.streams.size}")
        } catch(e: Exception) {
            e.printStackTrace()
            java.io.File("./tester.log").writeText("MY_RESULTS_ERR => Error: ${e.message}")
        }
    }
}
