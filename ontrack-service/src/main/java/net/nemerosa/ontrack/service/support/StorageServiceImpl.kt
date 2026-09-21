package net.nemerosa.ontrack.service.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.support.StorageService
import net.nemerosa.ontrack.repository.StorageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KClass

@Service
@Transactional
class StorageServiceImpl(
    private val repository: StorageRepository,
) : StorageService {

    override fun storeJson(store: String, key: String, node: JsonNode?) {
        if (node != null) {
            repository.storeJson(store, key, node)
        } else {
            repository.delete(store, key)
        }
    }

    override fun findJson(store: String, key: String): JsonNode? = repository.retrieveJson(store, key)

    override fun getKeys(store: String): List<String> = repository.getKeys(store)

    override fun getData(store: String): Map<String, JsonNode> = repository.getData(store)

    override fun delete(store: String, key: String) {
        repository.delete(store, key)
    }

    override fun clear(store: String) {
        repository.clear(store)
    }

    override fun exists(store: String, key: String): Boolean = repository.exists(store, key)

    override fun count(store: String, context: String, query: String?, queryVariables: Map<String, *>?): Int =
        repository.count(store, context, query, queryVariables)

    override fun <T : Any> paginatedFilter(
        store: String,
        type: KClass<T>,
        offset: Int,
        size: Int,
        context: String,
        query: String?,
        queryVariables: Map<String, *>?,
        orderQuery: String?
    ): PaginatedList<T> {
        val items = filter(
            store = store,
            type = type,
            offset = offset,
            size = size,
            context = context,
            query = query,
            queryVariables = queryVariables,
            orderQuery = orderQuery
        )
        val total = repository.count(
            store = store,
            context = context,
            query = query,
            queryVariables = queryVariables,
        )
        return PaginatedList.create(
            items = items,
            offset = offset,
            pageSize = size,
            total = total,
        )
    }

    override fun <T : Any> filter(
        store: String,
        type: KClass<T>,
        offset: Int,
        size: Int,
        context: String,
        query: String?,
        queryVariables: Map<String, *>?,
        orderQuery: String?,
    ): List<T> =
        repository.filter(
            store = store,
            offset = offset,
            size = size,
            context = context,
            query = query,
            queryVariables = queryVariables,
            orderQuery = orderQuery
        ).map {
            it.parseInto(type)
        }

    override fun <T : Any> filterRecords(
        store: String,
        type: KClass<T>,
        offset: Int,
        size: Int,
        context: String,
        query: String?,
        queryVariables: Map<String, *>?,
        orderQuery: String?,
    ): Map<String, T> =
        repository.filterRecords(store, offset, size, context, query, queryVariables, orderQuery)
            .mapValues { (_, data) ->
                data.parseInto(type)
            }

    override fun <T : Any> forEach(
        store: String,
        type: KClass<T>,
        context: String,
        query: String?,
        queryVariables: Map<String, *>?,
        orderQuery: String?,
        code: (key: String, item: T) -> Unit,
    ) {
        repository.forEach(store, context, query, queryVariables, orderQuery) { key, node ->
            val item = node.parseInto(type)
            code(key, item)
        }
    }

    override fun <T : Any> updateAll(store: String, type: KClass<T>, code: (key: String, item: T) -> T?) {
        repository.forEach(store) { key, node ->
            val item = node.parseInto(type)
            val newVersion = code(key, item)
            if (newVersion != null) {
                store(store, key, newVersion)
            }
        }
    }

    override fun deleteWithFilter(store: String, query: String?, queryVariables: Map<String, *>?): Int =
        repository.deleteWithFilter(store, query, queryVariables)
}
