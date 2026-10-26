package com.mds.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Хранилище каталога: загружает весь каталог (34 страницы) один раз,
 * кэширует его на диск и отдаёт данные для списка, поиска и избранного.
 */
class MdsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val client = MdsClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val catalogFile = File(appContext.filesDir, "catalog.json")
    private val detailDir = File(appContext.filesDir, "details")
    private val favoritesPrefs =
        appContext.getSharedPreferences("mds_favorites", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow<List<CatalogItem>>(emptyList())
    val items: StateFlow<List<CatalogItem>> = _items.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _favorites = MutableStateFlow<List<CatalogItem>>(emptyList())
    val favorites: StateFlow<List<CatalogItem>> = _favorites.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private var syncJob: Job? = null

    @Volatile
    private var cacheComplete = false

    init {
        scope.launch {
            loadFavorites()
            loadCatalogCache()
            if (needsSync()) sync()
        }
    }

    fun sync(force: Boolean = false) {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch { runSync(force) }
    }

    fun refresh() = sync(force = true)

    fun findItem(id: String): CatalogItem? =
        _items.value.firstOrNull { it.id == id }
            ?: _favorites.value.firstOrNull { it.id == id }

    fun toggleFavorite(item: CatalogItem) {
        val current = _favorites.value
        val updated = if (current.any { it.id == item.id }) {
            current.filterNot { it.id == item.id }
        } else {
            listOf(item) + current
        }
        _favorites.value = updated
        _favoriteIds.value = updated.mapTo(mutableSetOf()) { it.id }
        favoritesPrefs.edit()
            .putString(FAVORITES_KEY, CatalogJson.itemsToJson(updated).toString())
            .apply()
    }

    suspend fun getDetail(id: String, force: Boolean = false): Result<ItemDetail> {
        if (!id.all { it.isDigit() }) {
            return Result.failure(IOException("У этой записи нет страницы с аудиофайлами"))
        }

        val cached = readDetailCache(id)
        if (cached != null && !force) return Result.success(cached)

        return try {
            val detail = client.fetchDetail(id)
            writeDetailCache(id, detail)
            Result.success(detail)
        } catch (error: Exception) {
            if (cached != null) Result.success(cached) else Result.failure(error)
        }
    }

    // ---------------------------------------------------------------- sync

    private suspend fun runSync(force: Boolean) {
        _syncState.update { it.copy(isSyncing = true, error = null) }

        val startingEmpty = _items.value.isEmpty()

        try {
            val firstPage = client.fetchCatalog(0)
            val total = firstPage.total
            val merged = LinkedHashMap<String, CatalogItem>()
            firstPage.items.forEach { merged[it.id] = it }

            if (startingEmpty) {
                _items.value = merged.values.toList()
                persistCatalog(merged.values.toList(), total, complete = false)
            }
            _syncState.update { it.copy(loaded = merged.size, total = total) }

            val failedPages = fetchRemainingPages(total, merged, startingEmpty)

            val result = merged.values.sortedBy { it.number.padStart(6, '0') }
            _items.value = result
            cacheComplete = failedPages == 0
            persistCatalog(result, total, complete = cacheComplete)

            _syncState.update {
                it.copy(
                    isSyncing = false,
                    loaded = result.size,
                    total = maxOf(total, result.size),
                    lastUpdated = System.currentTimeMillis(),
                    error = if (failedPages > 0) {
                        "Не удалось загрузить $failedPages стр. каталога — показаны доступные записи"
                    } else {
                        null
                    }
                )
            }
        } catch (error: Exception) {
            _syncState.update {
                it.copy(
                    isSyncing = false,
                    error = describeError(error)
                )
            }
        } finally {
            if (_syncState.value.isSyncing) {
                _syncState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private suspend fun fetchRemainingPages(
        total: Int,
        merged: LinkedHashMap<String, CatalogItem>,
        startingEmpty: Boolean
    ): Int {
        if (total <= 50) return 0

        var failedPages = 0
        val semaphore = Semaphore(6)
        val channel = Channel<Result<CatalogPage>>(Channel.UNLIMITED)

        supervisorScope {
            val jobs = (50 until total step 50).map { offset ->
                launch {
                    semaphore.withPermit {
                        val result = runCatching { client.fetchCatalog(offset) }
                        channel.send(result)
                    }
                }
            }
            launch {
                jobs.joinAll()
                channel.close()
            }

            for (result in channel) {
                result.onSuccess { page ->
                    page.items.forEach { merged[it.id] = it }
                    if (startingEmpty) {
                        _items.value = merged.values.toList()
                    }
                    _syncState.update {
                        it.copy(
                            loaded = merged.size,
                            total = maxOf(it.total, page.total)
                        )
                    }
                }.onFailure {
                    failedPages++
                }
            }
        }

        return failedPages
    }

    private fun needsSync(): Boolean {
        val state = _syncState.value
        if (_items.value.isEmpty()) return true
        if (!cacheComplete) return true
        val age = System.currentTimeMillis() - state.lastUpdated
        return age > CACHE_TTL_MS
    }

    // --------------------------------------------------------------- cache

    private fun loadCatalogCache() {
        if (!catalogFile.exists()) return
        runCatching {
            val root = JSONObject(catalogFile.readText())
            val items = CatalogJson.itemsFromJson(root.optJSONArray("items"))
            val total = root.optInt("total", items.size)
            val updatedAt = root.optLong("updatedAt", 0L)
            val complete = root.optBoolean("complete", false)

            if (items.isNotEmpty()) {
                _items.value = items.sortedBy { it.number.padStart(6, '0') }
                _syncState.value = SyncState(
                    isSyncing = false,
                    loaded = items.size,
                    total = maxOf(total, items.size),
                    error = null,
                    lastUpdated = updatedAt
                )
                cacheComplete = complete
            }
        }
    }

    private fun persistCatalog(items: List<CatalogItem>, total: Int, complete: Boolean) {
        runCatching {
            val root = JSONObject().apply {
                put("version", 1)
                put("updatedAt", System.currentTimeMillis())
                put("complete", complete)
                put("total", total)
                put("items", CatalogJson.itemsToJson(items))
            }
            val temp = File(catalogFile.parentFile, "${catalogFile.name}.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(catalogFile)) {
                catalogFile.writeText(root.toString())
                temp.delete()
            }
        }
    }

    private fun loadFavorites() {
        runCatching {
            val raw = favoritesPrefs.getString(FAVORITES_KEY, null) ?: return
            val list = CatalogJson.itemsFromJson(org.json.JSONArray(raw))
            _favorites.value = list
            _favoriteIds.value = list.mapTo(mutableSetOf()) { it.id }
        }
    }

    private fun readDetailCache(id: String): ItemDetail? {
        val file = File(detailDir, "$id.json")
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText())
            val fetchedAt = root.optLong("fetchedAt", 0L)
            if (System.currentTimeMillis() - fetchedAt > DETAIL_TTL_MS) return null
            root.optJSONObject("detail")?.let(CatalogJson::detailFromJson)
        }.getOrNull()
    }

    private fun writeDetailCache(id: String, detail: ItemDetail) {
        runCatching {
            detailDir.mkdirs()
            val root = JSONObject().apply {
                put("fetchedAt", System.currentTimeMillis())
                put("detail", CatalogJson.detailToJson(detail))
            }
            File(detailDir, "$id.json").writeText(root.toString())
        }
    }

    private fun describeError(error: Throwable): String = when (error) {
        is IOException -> "Не удалось связаться с mds-club.ru. Проверьте подключение к интернету."
        else -> "Ошибка загрузки каталога: ${error.message ?: "неизвестная ошибка"}"
    }

    companion object {
        private const val FAVORITES_KEY = "items_json"
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(12)
        private val DETAIL_TTL_MS = TimeUnit.DAYS.toMillis(30)
    }
}
