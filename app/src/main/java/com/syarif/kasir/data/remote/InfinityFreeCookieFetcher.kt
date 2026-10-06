package com.syarif.kasir.data.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object InfinityFreeCookieFetcher {
    private const val TAG = "InfinityFreeCookie"
    private const val TARGET_URL = "https://achsyarif.rf.gd/kasir/api/barang.php"
    private const val BASE_URL = "https://achsyarif.rf.gd/"

    fun fetchCookie(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val webView = WebView(context.applicationContext)
                val settings = webView.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) KasirApp/1.0"

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        try {
                            val cookiesString = cookieManager.getCookie(BASE_URL)
                            try {
                                Log.d(TAG, "Cookies fetched from WebView: $cookiesString")
                            } catch (_: RuntimeException) {
                                println("DEBUG: $TAG: Cookies fetched from WebView: $cookiesString")
                            }
                            var injected = false
                            if (!cookiesString.isNullOrBlank()) {
                                injected = extractAndInjectCookies(cookiesString)
                            }
                            onComplete?.invoke(injected)
                        } catch (e: Exception) {
                            try {
                                Log.e(TAG, "Error extracting cookies in onPageFinished", e)
                            } catch (_: RuntimeException) {
                                println("ERROR: $TAG: Error extracting cookies in onPageFinished - ${e.message}")
                            }
                            onComplete?.invoke(false)
                        } finally {
                            webView.destroy()
                        }
                    }
                }

                webView.loadUrl(TARGET_URL)
            } catch (e: Exception) {
                try {
                    Log.e(TAG, "Error initializing WebView for cookie fetching", e)
                } catch (_: RuntimeException) {
                    println("ERROR: $TAG: Error initializing WebView for cookie fetching - ${e.message}")
                }
                onComplete?.invoke(false)
            }
        }
    }

    fun extractAndInjectCookies(cookiesString: String): Boolean {
        var testCookieFound = false
        val httpUrl = BASE_URL.toHttpUrlOrNull() ?: return false
        val pairs = cookiesString.split(";")
        val cookieList = mutableListOf<Cookie>()

        for (pair in pairs) {
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                if (name == "__test") {
                    testCookieFound = true
                }
                val cookie = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain("achsyarif.rf.gd")
                    .path("/")
                    .build()
                cookieList.add(cookie)
            }
        }

        if (cookieList.isNotEmpty()) {
            NetworkClient.cookieJar.saveFromResponse(httpUrl, cookieList)
            try {
                Log.d(TAG, "Injected ${cookieList.size} cookies into NetworkClient CookieJar (has __test: $testCookieFound)")
            } catch (_: RuntimeException) {
                println("DEBUG: $TAG: Injected ${cookieList.size} cookies into NetworkClient CookieJar (has __test: $testCookieFound)")
            }
        }

        return testCookieFound
    }
}
