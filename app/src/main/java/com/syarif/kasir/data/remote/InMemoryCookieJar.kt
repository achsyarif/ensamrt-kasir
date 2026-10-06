package com.syarif.kasir.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class InMemoryCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val domainCookies = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(domainCookies) {
            for (cookie in cookies) {
                domainCookies.removeIf { it.name == cookie.name }
                domainCookies.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val validCookies = mutableListOf<Cookie>()
        val currentTime = System.currentTimeMillis()

        cookieStore.forEach { (domain, cookies) ->
            if ((host.endsWith(domain) || domain == host)) {
                synchronized(cookies) {
                    val iterator = cookies.iterator()
                    while (iterator.hasNext()) {
                        val cookie = iterator.next()
                        if (cookie.expiresAt <= currentTime) {
                            iterator.remove()
                        } else if (cookie.matches(url)) {
                            validCookies.add(cookie)
                        }
                    }
                }
            }
        }
        return validCookies
    }
}
