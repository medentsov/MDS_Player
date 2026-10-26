package com.mds.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/** HTTP-клиент каталога mds-club.ru. */
class MdsClient(
    private val client: OkHttpClient = defaultClient()
) {
    suspend fun fetchCatalog(offset: Int): CatalogPage = withContext(Dispatchers.IO) {
        val url = catalogUrl(offset)
        CatalogParser.parseCatalog(download(url), url.toString())
    }

    suspend fun fetchDetail(id: String): ItemDetail = withContext(Dispatchers.IO) {
        require(id.all { it.isDigit() }) { "Некорректный id записи: $id" }
        val url = detailUrl(id)
        CatalogParser.parseDetail(download(url), url.toString())
    }

    private fun download(url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Сервер ответил ${response.code}")
            }
            val bytes = response.body?.bytes() ?: throw IOException("Пустой ответ сервера")
            return String(bytes, WINDOWS_1251)
        }
    }

    companion object {
        private const val BASE_URL = "http://mds-club.ru/cgi-bin/index.cgi"
        private const val USER_AGENT = "MDSPlayer/1.0 (Android)"
        private val WINDOWS_1251: Charset = Charset.forName("windows-1251")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun catalogUrl(offset: Int, sortBy: Int = 20): HttpUrl = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("r", "84")
            .addQueryParameter("lang", "rus")
            .addQueryParameter("filter", "0")
            .addQueryParameter("article", "0")
            .addQueryParameter("sortby", sortBy.toString())
            .addQueryParameter("posits", offset.coerceAtLeast(0).toString())
            .addQueryParameter("search", "")
            .build()

        fun detailUrl(id: String): HttpUrl = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("r", "84")
            .addQueryParameter("lang", "rus")
            .addQueryParameter("sbr", "2")
            .addQueryParameter("user", id)
            .addQueryParameter("filter", "0")
            .addQueryParameter("article", "0")
            .addQueryParameter("posits", "0")
            .addQueryParameter("sortby", "20")
            .addQueryParameter("search", "")
            .build()

        fun siteUrl(): String = "http://mds-club.ru/"
    }
}
