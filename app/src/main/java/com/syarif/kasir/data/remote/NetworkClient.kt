package com.syarif.kasir.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.syarif.kasir.data.remote.adapter.FlexiblePrimitiveAdapterFactory
import com.syarif.kasir.data.remote.dto.HargaGrosirDto
import com.syarif.kasir.data.remote.dto.HargaGrosirListJsonAdapter
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val DEFAULT_BASE_URL = "https://achsyarif.rf.gd/kasir/api/"

    val cookieJar = InMemoryCookieJar()

    private val moshi: Moshi = Moshi.Builder()
        .add(FlexiblePrimitiveAdapterFactory)
        .add(Types.newParameterizedType(List::class.java, HargaGrosirDto::class.java), HargaGrosirListJsonAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun createApiService(
        baseUrl: String = DEFAULT_BASE_URL,
        userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) KasirApp/1.0"
    ): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(UserAgentInterceptor(userAgent))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
