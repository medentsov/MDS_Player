package com.mds.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogQueryTest {

    private fun item(
        id: String,
        author: String,
        title: String,
        date: String,
        station: String = ""
    ) = CatalogItem(
        id = id,
        number = id.padStart(4, '0'),
        order = "1",
        author = author,
        title = title,
        date = date,
        station = station
    )

    private val items = listOf(
        item("1", "Стивен Кинг", "Долгий джонт", "01.02.1996", "Станция"),
        item("2", "Аркадий и Борис Стругацкие", "Пикник на обочине", "10.07.2021"),
        item("3", "Стивен Кинг", "Оно", "05.05.2020", "Энергия"),
        item("4", "Виктор Пелевин", "Generation П", "12.12.1999"),
        item("5", "Стивен Кинг", "Ёж", "-", "Станция")
    )

    @Test
    fun `search matches several tokens across fields`() {
        val result = CatalogQuery.search(items, "кинг 1996")
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `search matches year and sorts newest first`() {
        val result = CatalogQuery.search(items, "1996")
        assertEquals(listOf("1"), result.map { it.id })

        val year = CatalogQuery.search(items, "2021")
        assertEquals(listOf("2"), year.map { it.id })
    }

    @Test
    fun `search ignores case and yo`() {
        val result = CatalogQuery.search(items, "еж")
        assertEquals(listOf("5"), result.map { it.id })
    }

    @Test
    fun `search finds by station`() {
        val result = CatalogQuery.search(items, "энергия")
        assertEquals(listOf("3"), result.map { it.id })
    }

    @Test
    fun `search prefers title prefix`() {
        val result = CatalogQuery.search(items, "о")
        assertEquals("3", result.first().id)
    }

    @Test
    fun `newest sort puts known dates first`() {
        val sorted = CatalogQuery.sort(items, CatalogSort.NEWEST)
        assertEquals(listOf("2", "3", "4", "1", "5"), sorted.map { it.id })
    }

    @Test
    fun `author sort is alphabetical`() {
        val sorted = CatalogQuery.sort(items, CatalogSort.AUTHOR)
        assertEquals("2", sorted.first().id)
        assertEquals("4", sorted[1].id)
        assertEquals(setOf("1", "3", "5"), sorted.takeLast(3).map { it.id }.toSet())
    }

    @Test
    fun `number sort is numeric`() {
        val sorted = CatalogQuery.sort(items, CatalogSort.NUMBER)
        assertEquals(listOf("1", "2", "3", "4", "5"), sorted.map { it.id })
    }
}
