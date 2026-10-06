package com.syarif.kasir

import com.syarif.kasir.data.remote.UserAgentInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class UserAgentInterceptorTest {

    @Test
    fun testUserAgentHeaderIsAdded() {
        val customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CustomKasirApp/1.0"
        val interceptor = UserAgentInterceptor(customUserAgent)

        var interceptedRequest: Request? = null

        val mockChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder()
                .url("https://achsyarif.rf.gd/kasir/barang.php")
                .build()

            override fun proceed(request: Request): Response {
                interceptedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 10000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 10000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 10000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(mockChain)

        assertEquals(customUserAgent, interceptedRequest?.header("User-Agent"))
        assertEquals("application/json", interceptedRequest?.header("Accept"))
    }
}
