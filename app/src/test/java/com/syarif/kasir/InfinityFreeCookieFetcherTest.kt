package com.syarif.kasir

import com.syarif.kasir.data.remote.InfinityFreeCookieFetcher
import com.syarif.kasir.data.remote.NetworkClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityFreeCookieFetcherTest {

    @Test
    fun testExtractAndInjectCookies() {
        val cookiesHeader = "__test=1234567890abcdef; path=/; expires=Thu, 31-Dec-2037 23:59:59 GMT"
        val hasTest = InfinityFreeCookieFetcher.extractAndInjectCookies(cookiesHeader)
        assertTrue(hasTest)

        val targetUrl = "https://achsyarif.rf.gd/kasir/api/barang.php".toHttpUrl()
        val loadedCookies = NetworkClient.cookieJar.loadForRequest(targetUrl)

        val testCookie = loadedCookies.find { it.name == "__test" }
        assertTrue(testCookie != null)
        assertEquals("1234567890abcdef", testCookie?.value)
    }
}
