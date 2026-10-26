package com.mds.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogJsonTest {

    @Test
    fun `catalog item survives json roundtrip`() {
        val item = CatalogItem(
            id = "1694",
            number = "001",
            order = "1",
            author = "Алан Кубатиев",
            title = "Штрудель по-венски",
            date = "28.11.2021",
            station = "Гастропаб",
            hasReview = true
        )

        val restored = CatalogJson.itemFromJson(CatalogJson.itemToJson(item))
        assertEquals(item, restored)
    }

    @Test
    fun `detail with audio survives json roundtrip`() {
        val detail = ItemDetail(
            author = "Стивен Кинг",
            title = "Долгий джонт",
            date = "-",
            station = "Станция",
            orderLabel = "Второй в эфире",
            review = "Текст рецензии",
            reviewAuthor = "Сергей",
            audio = listOf(
                AudioSource(
                    url = "http://mds.kallisto.ru/file.mp3",
                    server = "mds.kallisto.ru",
                    fileName = "file.mp3",
                    size = "19.680 Mb"
                )
            )
        )

        val restored = CatalogJson.detailFromJson(CatalogJson.detailToJson(detail))
        assertEquals(detail, restored)
    }

    @Test
    fun `json array skips broken entries`() {
        val array = org.json.JSONArray()
        array.put(CatalogJson.itemToJson(
            CatalogItem(
                id = "1", number = "001", author = "Автор", title = "Название", date = "01.01.2000"
            )
        ))
        array.put(org.json.JSONObject().put("broken", true))

        val restored = CatalogJson.itemsFromJson(array)
        assertEquals(1, restored.size)
        assertTrue(restored.first().detailAvailable)
    }
}
