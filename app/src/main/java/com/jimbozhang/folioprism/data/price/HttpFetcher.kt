package com.jimbozhang.folioprism.data.price

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface HttpFetcher {
    suspend fun get(url: String): String
}

class OkHttpFetcher(
    private val client: OkHttpClient = defaultClient(),
) : HttpFetcher {

    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120 Mobile Safari/537.36",
            )
            .header("Accept", "application/json, text/plain, */*")
            .apply {
                refererFor(url)?.let { header("Referer", it) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} for $url")
            }
            response.body?.string() ?: error("Empty response for $url")
        }
    }

    private fun refererFor(url: String): String? = when {
        url.contains("eastmoney.com") || url.contains("1234567.com.cn") ->
            "https://quote.eastmoney.com/"
        url.contains("gtimg.cn") -> "https://gu.qq.com/"
        else -> null
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}
