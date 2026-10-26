package com.mds.player.data

import org.json.JSONArray
import org.json.JSONObject

/** Ручная сериализация в JSON — без лишних плагинов и зависимостей. */
internal object CatalogJson {

    fun itemToJson(item: CatalogItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("number", item.number)
        put("order", item.order)
        put("author", item.author)
        put("title", item.title)
        put("date", item.date)
        put("station", item.station)
        put("hasReview", item.hasReview)
    }

    fun itemFromJson(json: JSONObject): CatalogItem? = runCatching {
        val id = json.optString("id")
        val number = json.optString("number")
        val title = json.optString("title")
        if (id.isBlank() || number.isBlank() || title.isBlank()) return@runCatching null
        CatalogItem(
            id = id,
            number = number,
            order = json.optString("order"),
            author = json.optString("author"),
            title = title,
            date = json.optString("date").ifBlank { "-" },
            station = json.optString("station"),
            hasReview = json.optBoolean("hasReview", false)
        )
    }.getOrNull()

    fun itemsToJson(items: Collection<CatalogItem>): JSONArray =
        JSONArray().apply { items.forEach { put(itemToJson(it)) } }

    fun itemsFromJson(array: JSONArray?): List<CatalogItem> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)?.let(::itemFromJson) ?: continue
                add(item)
            }
        }
    }

    fun detailToJson(detail: ItemDetail): JSONObject = JSONObject().apply {
        put("author", detail.author)
        put("title", detail.title)
        put("date", detail.date)
        put("station", detail.station)
        put("orderLabel", detail.orderLabel)
        detail.review?.let { put("review", it) }
        detail.reviewAuthor?.let { put("reviewAuthor", it) }
        put("audio", JSONArray().apply {
            detail.audio.forEach { source ->
                put(JSONObject().apply {
                    put("url", source.url)
                    put("server", source.server)
                    put("fileName", source.fileName)
                    source.size?.let { put("size", it) }
                })
            }
        })
    }

    fun detailFromJson(json: JSONObject): ItemDetail {
        val audioArray = json.optJSONArray("audio")
        val audio = buildList {
            if (audioArray != null) {
                for (index in 0 until audioArray.length()) {
                    val source = audioArray.optJSONObject(index) ?: continue
                    val url = source.optString("url")
                    if (url.isBlank()) continue
                    add(
                        AudioSource(
                            url = url,
                            server = source.optString("server"),
                            fileName = source.optString("fileName"),
                            size = source.optString("size").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }
        return ItemDetail(
            author = json.optString("author"),
            title = json.optString("title"),
            date = json.optString("date"),
            station = json.optString("station"),
            orderLabel = json.optString("orderLabel"),
            review = json.optString("review").takeIf { it.isNotBlank() },
            reviewAuthor = json.optString("reviewAuthor").takeIf { it.isNotBlank() },
            audio = audio
        )
    }
}
