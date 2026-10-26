package com.mds.player.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLDecoder

/**
 * Разбор HTML сайта mds-club.ru.
 *
 * Сайт отдаёт страницы в windows-1251, поэтому перед разбором байты нужно
 * декодировать в строку (см. [MdsClient]). Jsoup получает уже готовый HTML.
 */
object CatalogParser {

    private val USER_ID_REGEX = Regex("""[?&]user=(\d+)""")
    private val TOTAL_REGEX = Regex("""Всего записей:\s*(\d+)""")
    private val PLAYER_SRC_REGEX = Regex("""[?&]src=([^&]+)""")

    fun parseCatalog(html: String, baseUrl: String? = null): CatalogPage {
        val doc = if (baseUrl != null) Jsoup.parse(html, baseUrl) else Jsoup.parse(html)

        val total = doc.selectFirst("#roller_num")
            ?.text()
            ?.let { TOTAL_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: 0

        val items = doc.select("#catalogtable tbody tr")
            .mapNotNull { row -> parseCatalogRow(row) }

        return CatalogPage(items = items, total = total)
    }

    private fun parseCatalogRow(row: Element): CatalogItem? {
        val cells = row.children().filter { it.tagName() == "td" }
        if (cells.size < 6) return null

        val numberCell = cells[0]
        val numberTokens = clean(numberCell.text()).split(Regex("\\s+")).filter { it.isNotBlank() }
        val number = numberTokens.firstOrNull() ?: return null
        val order = numberTokens.getOrNull(1).orEmpty()

        val href = numberCell.selectFirst("a[href]")?.attr("abs:href")
            ?: numberCell.selectFirst("a[href]")?.attr("href")
        val id = href?.let { USER_ID_REGEX.find(it)?.groupValues?.get(1) } ?: "x$number"

        val author = clean(cells[1].text())
        val title = clean(cells[2].text())
        if (author.isBlank() && title.isBlank()) return null

        val date = clean(cells[3].text()).ifBlank { "-" }
        val hasReview = cellContent(cells[4]).isNotBlank()
        val station = clean(cells[5].text())

        return CatalogItem(
            id = id,
            number = number,
            order = order,
            author = author,
            title = title,
            date = date,
            station = station,
            hasReview = hasReview
        )
    }

    fun parseDetail(html: String, baseUrl: String? = null): ItemDetail {
        val doc = if (baseUrl != null) Jsoup.parse(html, baseUrl) else Jsoup.parse(html)
        val titleBlock = doc.selectFirst("#attachtitle")
            ?: throw IOException("Страница записи не найдена")

        val lines = htmlLines(titleBlock)
        val author = field(lines, "Автор")
        val title = field(lines, "Название")
        val date = field(lines, "Дата выхода в эфир").ifBlank { "-" }
        val station = field(lines, "Радиостанция")
        val orderLabel = lines.firstOrNull {
            it.contains("в эфире", ignoreCase = true) && !it.startsWith("Дата")
        }?.takeIf { it != "-" }.orEmpty()

        val (reviewAuthor, reviewText) = parseReview(doc.selectFirst("#review"))

        return ItemDetail(
            author = author,
            title = title,
            date = date,
            station = station,
            orderLabel = orderLabel,
            review = reviewText,
            reviewAuthor = reviewAuthor,
            audio = parseAudio(doc, baseUrl)
        )
    }

    private fun parseAudio(doc: Element, baseUrl: String?): List<AudioSource> {
        val sources = LinkedHashMap<String, AudioSource>()

        val playerUrl = extractPlayerUrl(doc)
        if (playerUrl != null && isPlayable(playerUrl)) {
            sources[playerUrl] = AudioSource(
                url = playerUrl,
                server = hostOf(playerUrl),
                fileName = fileNameOf(playerUrl),
                size = null
            )
        }

        doc.select("#catalogtable tbody tr").forEach { row ->
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size < 4) return@forEach
            val link = cells[3].selectFirst("a[href]") ?: return@forEach
            val url = link.attr("abs:href").ifBlank { link.attr("href") }
            if (!isPlayable(url)) return@forEach

            val server = clean(cells[1].text()).ifBlank { hostOf(url) }
            val size = cells.getOrNull(4)?.text()?.trim()?.takeIf { it.isNotBlank() && it != "-" }
            val fileName = clean(link.text()).ifBlank { fileNameOf(url) }
            sources.putIfAbsent(
                url,
                AudioSource(url = url, server = server, fileName = fileName, size = size)
            )
        }

        return sources.values.toList()
    }

    private fun extractPlayerUrl(doc: Element): String? {
        val values = buildList {
            doc.selectFirst("#player object[data]")?.attr("data")?.let(::add)
            doc.selectFirst("#player param[name=movie]")?.attr("value")?.let(::add)
        }
        for (value in values) {
            val encoded = PLAYER_SRC_REGEX.find(value)?.groupValues?.get(1) ?: continue
            val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        return null
    }

    private fun parseReview(element: Element?): Pair<String?, String?> {
        if (element == null) return null to null
        val lines = htmlLines(element)
        var author: String? = null
        val body = mutableListOf<String>()
        for (line in lines) {
            when {
                line.equals("Рецензия", ignoreCase = true) -> Unit
                line.startsWith("Автор:", ignoreCase = true) && author == null ->
                    author = line.substringAfter(':').trim().ifBlank { null }
                else -> body += line
            }
        }
        val text = body.joinToString("\n\n").trim().ifBlank { null }
        return author to text
    }

    private fun htmlLines(element: Element): List<String> =
        element.html()
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .split('\n')
            .map { Jsoup.parse(it).text().let(::clean) }
            .filter { it.isNotBlank() }

    private fun field(lines: List<String>, name: String): String =
        lines.firstOrNull { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.let(::clean)
            ?.ifBlank { "" }
            ?: ""

    private fun cellContent(cell: Element): String =
        clean(cell.text()).ifBlank { cell.selectFirst("img")?.attr("alt").orEmpty().trim() }

    private fun clean(value: String): String = value
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isPlayable(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private fun hostOf(url: String): String =
        url.substringAfter("://", url).substringBefore('/')

    private fun fileNameOf(url: String): String =
        url.substringBefore('?').substringAfterLast('/').ifBlank { url }
}
