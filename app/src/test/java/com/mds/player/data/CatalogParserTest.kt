package com.mds.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogParserTest {

    private val listHtml = """
        <!DOCTYPE html><html><body>
        <div id="roller_num">Всего записей: 1665&nbsp;</div>
        <div id="catalogtable"><center><table>
          <thead><tr><th>№</th><th>Автор</th><th>Название</th><th>Дата</th><th>R</th><th>Радио</th></tr></thead>
          <tbody>
            <tr class="w">
              <td><a href="http://mds-club.ru/cgi-bin/index.cgi?r=84&amp;lang=rus&amp;sbr=2&amp;user=1694&amp;filter=0">001</a>&nbsp;<a href="http://mds-club.ru/cgi-bin/index.cgi?r=84&amp;lang=rus&amp;sbr=2&amp;user=1694">1</a></td>
              <td class="l" nowrap><a title="Автор рассказа" href="#">Алан Кубатиев</a></td>
              <td class="l"><a title="Название рассказа" href="#">Штрудель по-венски</a></td>
              <td><a title="Дата" href="#">28.11.2021</a></td>
              <td><a href="#">Y</a></td>
              <td><a title="Радиостанция" href="#">Гастропаб &#34;Гаражане&#34;</a></td>
            </tr>
            <tr class="w">
              <td>1633&nbsp;&nbsp;</td>
              <td>Айзек Азимов</td>
              <td>Вставьте шплинт А в гнездо Б</td>
              <td>-</td>
              <td></td>
              <td></td>
            </tr>
          </tbody>
        </table></center></div>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <!DOCTYPE html><html><body>
        <div id="attachtitle"> Автор: Стивен Кинг<br>
            Название: Долгий джонт<br>
            Дата выхода в эфир: -<br>
            -<br>
            Радиостанция: Станция<br><br>
            <a href="http://mds-club.ru/cgi-bin/index.cgi?r=84">Вернуться в каталог</a></div>
        <div id="player">Прослушать<br>
            <object type="application/x-shockwave-flash" data="/player.swf?src=%68%74%74%70%3a%2f%2f%6d%64%73%2e%64%61%74%61%67%72%61%64%2e%72%75%2f%64%6f%77%6e%6c%6f%61%64%2f%53%74%69%76%65%6e%5f%4b%69%6e%67%5f%2d%5f%44%6f%6c%67%69%6a%5f%64%7a%68%6f%6e%74%2e%6d%70%33">
            </object>
        </div>
        <div id="catalogtable"><center><table><tbody>
            <tr class="w"><td>1</td><td class="l" nowrap>ftp://mds.kallisto.ru</td><td></td>
                <td class="l"><a href="ftp://mds:mds@mds.kallisto.ru/Stiven_King_-_Dolgij_dzhont.mp3"><u>Stiven_King_-_Dolgij_dzhont.mp3</u></a></td><td>19.680 Mb</td></tr>
            <tr class="w"><td>2</td><td class="l" nowrap>http://mds.kallisto.ru</td><td></td>
                <td class="l"><a href="http://mds.kallisto.ru/Stiven_King_-_Dolgij_dzhont.mp3"><u>Stiven_King_-_Dolgij_dzhont.mp3</u></a></td><td>19.680 Mb</td></tr>
            <tr class="w"><td>3</td><td class="l" nowrap>http://mds.datagrad.ru/download</td><td></td>
                <td class="l"><a href="http://mds.datagrad.ru/download/Stiven_King_-_Dolgij_dzhont.mp3"><u>Stiven_King_-_Dolgij_dzhont.mp3</u></a></td><td>19.680 Mb</td></tr>
        </tbody></table></center></div>
        <div id="review"><b>Рецензия</b><br><br><b>Автор:</b> Сергей<br>
            Будущее, новые технологии, наука постигла все секреты телепортации.<br>
            <br>Гениально. Стивен Кинг.</div>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses catalog rows and total`() {
        val page = CatalogParser.parseCatalog(listHtml, "http://mds-club.ru/cgi-bin/index.cgi")

        assertEquals(1665, page.total)
        assertEquals(2, page.items.size)

        val first = page.items[0]
        assertEquals("1694", first.id)
        assertEquals("001", first.number)
        assertEquals("1", first.order)
        assertEquals("Алан Кубатиев", first.author)
        assertEquals("Штрудель по-венски", first.title)
        assertEquals("28.11.2021", first.date)
        assertEquals("Гастропаб \"Гаражане\"", first.station)
        assertTrue(first.hasReview)
        assertTrue(first.detailAvailable)
        assertEquals("20211128", first.dateKey)

        val second = page.items[1]
        assertEquals("x1633", second.id)
        assertFalse(second.detailAvailable)
        assertFalse(second.hasReview)
        assertNull(second.dateKey)
    }

    @Test
    fun `parses detail page with mirrors and review`() {
        val detail = CatalogParser.parseDetail(
            detailHtml,
            "http://mds-club.ru/cgi-bin/index.cgi?r=84&sbr=2&user=37"
        )

        assertEquals("Стивен Кинг", detail.author)
        assertEquals("Долгий джонт", detail.title)
        assertEquals("-", detail.date)
        assertEquals("Станция", detail.station)
        assertEquals("Сергей", detail.reviewAuthor)
        assertTrue(detail.review.orEmpty().contains("Будущее"))
        assertFalse(detail.review.orEmpty().contains("Рецензия"))
        assertFalse(detail.review.orEmpty().contains("Автор:"))

        // Плеер + http-зеркало, ftp отброшен, дубль datagrad схлопнут.
        assertEquals(2, detail.audio.size)
        assertEquals(
            "http://mds.datagrad.ru/download/Stiven_King_-_Dolgij_dzhont.mp3",
            detail.audio[0].url
        )
        assertEquals("mds.datagrad.ru", detail.audio[0].host)
        assertEquals("http://mds.kallisto.ru/Stiven_King_-_Dolgij_dzhont.mp3", detail.audio[1].url)
        assertEquals("19.680 Mb", detail.audio[1].size)
    }

    @Test
    fun `invalid detail page fails`() {
        val result = runCatching { CatalogParser.parseDetail("<html><body>404</body></html>") }
        assertTrue(result.isFailure)
    }
}
