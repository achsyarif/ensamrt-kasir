package com.syarif.kasir.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor(
    private val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) KasirApp/1.0"
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestWithHeaders = originalRequest.newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        return chain.proceed(requestWithHeaders)
    }
}
