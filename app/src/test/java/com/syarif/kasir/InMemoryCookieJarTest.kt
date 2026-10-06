package com.syarif.kasir

import com.syarif.kasir.data.remote.InMemoryCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryCookieJarTest {

    @Test
    fun testSaveAndLoadCookie() {
        val cookieJar = InMemoryCookieJar()
        val url = "https://achsyarif.rf.gd/kasir/api/barang.php".toHttpUrl()
        val testCookie = Cookie.Builder()
            .name("__test")
            .value("abc123xyz")
            .domain("achsyarif.rf.gd")
            .path("/")
            .build()

        cookieJar.saveFromResponse(url, listOf(testCookie))

        val loadedCookies = cookieJar.loadForRequest(url)
        assertEquals(1, loadedCookies.size)
        assertEquals("__test", loadedCookies[0].name)
        assertEquals("abc123xyz", loadedCookies[0].value)
    }

    @Test
    fun testExpiredCookieIsIgnored() {
        val cookieJar = InMemoryCookieJar()
        val url = "https://achsyarif.rf.gd/kasir/api/barang.php".toHttpUrl()
        val expiredCookie = Cookie.Builder()
            .name("__test")
            .value("expired")
            .domain("achsyarif.rf.gd")
            .path("/")
            .expiresAt(System.currentTimeMillis() - 1000)
            .build()

        cookieJar.saveFromResponse(url, listOf(expiredCookie))

        val loadedCookies = cookieJar.loadForRequest(url)
        assertTrue(loadedCookies.isEmpty())
    }
}
