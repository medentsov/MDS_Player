package com.mds.player.data

/**
 * Одна запись каталога «Модель для сборки».
 *
 * [id] — идентификатор записи на сайте (параметр `user`). У нескольких старых
 * записей ссылки на страницу нет — тогда используется синтетический id `xNNN`,
 * а [detailAvailable] возвращает false.
 */
data class CatalogItem(
    val id: String,
    val number: String,
    val order: String = "",
    val author: String,
    val title: String,
    val date: String,
    val station: String = "",
    val hasReview: Boolean = false
) {
    val detailAvailable: Boolean get() = id.isNotEmpty() && id.all { it.isDigit() }

    /** «001» -> «1», «1601» -> «1601». */
    val displayNumber: String get() = number.trimStart('0').ifEmpty { "0" }

    /** Ключ для сортировки по дате: 28.11.2021 -> 20211128. */
    val dateKey: String? get() = parseDateKey(date)

    val hasDate: Boolean get() = date.isNotBlank() && date != "-"

    companion object {
        private val DATE_REGEX = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")

        fun parseDateKey(value: String): String? {
            val match = DATE_REGEX.find(value) ?: return null
            val (day, month, year) = match.destructured
            return year + month.padStart(2, '0') + day.padStart(2, '0')
        }
    }
}

/** Один аудиофайл записи (зеркало). */
data class AudioSource(
    val url: String,
    val server: String,
    val fileName: String,
    val size: String? = null
) {
    val host: String get() = url.substringAfter("://").substringBefore('/')
}

/** Подробности, которые можно получить только со страницы записи. */
data class ItemDetail(
    val author: String = "",
    val title: String = "",
    val date: String = "",
    val station: String = "",
    val orderLabel: String = "",
    val review: String? = null,
    val reviewAuthor: String? = null,
    val audio: List<AudioSource> = emptyList()
) {
    val hasReview: Boolean get() = !review.isNullOrBlank()
}

data class CatalogPage(
    val items: List<CatalogItem>,
    val total: Int
)

/** Состояние синхронизации каталога. */
data class SyncState(
    val isSyncing: Boolean = false,
    val loaded: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val lastUpdated: Long = 0L
)

enum class CatalogSort(val label: String) {
    NEWEST("Сначала новые"),
    AUTHOR("По автору"),
    TITLE("По названию"),
    STATION("По радиостанции"),
    NUMBER("По каталогу")
}

/** Чистые функции поиска и сортировки — их удобно покрывать тестами. */
object CatalogQuery {

    private fun collator(): java.text.Collator =
        java.text.Collator.getInstance(java.util.Locale.forLanguageTag("ru")).apply {
            strength = java.text.Collator.PRIMARY
        }

    fun sort(items: List<CatalogItem>, mode: CatalogSort): List<CatalogItem> {
        val dateDesc = compareByDescending<CatalogItem> { it.dateKey ?: "" }
            .thenBy { it.number.padStart(6, '0') }
        return when (mode) {
            CatalogSort.NEWEST -> items.sortedWith(dateDesc)
            CatalogSort.NUMBER -> items.sortedBy { it.number.padStart(6, '0') }
            CatalogSort.AUTHOR -> {
                val c = collator()
                items.sortedWith(compareBy<CatalogItem, String>(c) { it.author }
                    .thenBy(c) { it.title })
            }
            CatalogSort.TITLE -> {
                val c = collator()
                items.sortedWith(compareBy<CatalogItem, String>(c) { it.title }
                    .thenBy(c) { it.author })
            }
            CatalogSort.STATION -> {
                val c = collator()
                items.sortedWith(compareBy<CatalogItem, String>(c) { it.station }
                    .thenByDescending { it.dateKey ?: "" })
            }
        }
    }

    fun search(items: List<CatalogItem>, rawQuery: String): List<CatalogItem> {
        val query = normalize(rawQuery)
        if (query.isBlank()) return emptyList()
        val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        return items.asSequence()
            .filter { item ->
                val haystack = normalize(
                    listOf(
                        item.author, item.title, item.station, item.date,
                        item.number, item.order
                    ).joinToString(" ")
                )
                tokens.all { haystack.contains(it) }
            }
            .sortedWith(
                compareBy<CatalogItem> { relevance(it, query) }
                    .thenByDescending { it.dateKey ?: "" }
                    .thenBy { it.number.padStart(6, '0') }
            )
            .toList()
    }

    private fun relevance(item: CatalogItem, query: String): Int {
        val title = normalize(item.title)
        val author = normalize(item.author)
        return when {
            title.startsWith(query) -> 0
            author.startsWith(query) -> 1
            title.contains(query) -> 2
            author.contains(query) -> 3
            else -> 4
        }
    }

    fun normalize(value: String): String = value
        .replace('\u00A0', ' ')
        .replace('ё', 'е')
        .replace('Ё', 'Е')
        .lowercase(java.util.Locale.forLanguageTag("ru"))
        .trim()
}
