package net.nemerosa.ontrack.extension.indicators.model

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.indicators.acl.IndicatorTypeManagement
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class IndicatorTypeServiceImpl(
    private val indicatorCategoryService: IndicatorCategoryService,
    private val indicatorValueTypeService: IndicatorValueTypeService,
    private val storageService: StorageService,
    private val securityService: SecurityService
) : IndicatorTypeService, IndicatorCategoryListener {

    init {
        indicatorCategoryService.registerCategoryListener(this)
    }

    override fun onCategoryDeleted(category: IndicatorCategory) {
        findByCategory(category).forEach {
            deleteType(it.id)
        }
    }

    private val listeners = mutableListOf<IndicatorTypeListener>()

    override fun registerTypeListener(listener: IndicatorTypeListener) {
        listeners += listener
    }

    override fun findAll(): List<IndicatorType<*, *>> =
        storageService.getData(STORE, StoredIndicatorType::class.java)
            .values
            .mapNotNull {
                fromStorage<Any, Any>(it)
            }.sortedWith(
                compareBy(
                    { it.category.name },
                    { it.name }
                )
            )

    override fun findTypeById(typeId: String): IndicatorType<*, *>? =
        storageService.find(STORE, typeId, StoredIndicatorType::class)
            ?.let { fromStorage<Any, Any>(it) }

    override fun getTypeById(typeId: String): IndicatorType<*, *> =
        findTypeById(typeId) ?: throw IndicatorTypeNotFoundException(typeId)

    override fun findByCategory(category: IndicatorCategory): List<IndicatorType<*, *>> {
        return storageService.filter(
            store = STORE,
            type = StoredIndicatorType::class,
            query = "data->>'category' = :category",
            queryVariables = mapOf("category" to category.id),
        ).mapNotNull {
            fromStorage<Any, Any>(it)
        }.sortedWith(
            compareBy(
                { it.category.name },
                { it.name }
            )
        )
    }

    override fun findBySource(source: IndicatorSource): List<IndicatorType<*, *>> {
        return storageService.filter(
            store = STORE,
            type = StoredIndicatorType::class,
            query = """
                data->'source'->>'name' = :sourceName 
                and data->'source'->'provider'->>'id' = :sourceProviderId
            """,
            queryVariables = mapOf(
                "sourceName" to source.name,
                "sourceProviderId" to source.provider.id
            )
        ).mapNotNull {
            fromStorage<Any, Any>(it)
        }
    }

    private fun <T, C> fromStorage(stored: StoredIndicatorType): IndicatorType<T, C>? {
        val category = indicatorCategoryService.findCategoryById(stored.category)
        val valueType = indicatorValueTypeService.findValueTypeById<T, C>(stored.valueType)
        return if (category != null && valueType != null) {
            val valueConfig = valueType.fromConfigStoredJson(stored.valueConfig)
            IndicatorType(
                id = stored.id,
                category = category,
                name = stored.name,
                link = stored.link,
                valueType = valueType,
                valueConfig = valueConfig,
                source = stored.source,
                computed = stored.computed,
                deprecated = stored.deprecated
            )
        } else {
            null
        }
    }

    override fun createType(input: CreateTypeForm): IndicatorType<*, *> {
        securityService.checkGlobalFunction(IndicatorTypeManagement::class.java)
        val type = findTypeById(input.id)
        if (type != null) {
            throw IndicatorTypeIdAlreadyExistsException(input.id)
        } else {
            return updateType(input)
        }
    }

    override fun <T, C> createType(
        id: String,
        category: IndicatorCategory,
        name: String,
        link: String?,
        valueType: IndicatorValueType<T, C>,
        valueConfig: C,
        source: IndicatorSource?,
        computed: Boolean,
        deprecated: String?
    ): IndicatorType<T, C> {
        securityService.checkGlobalFunction(IndicatorTypeManagement::class.java)
        val type = findTypeById(id)
        if (type != null) {
            throw IndicatorTypeIdAlreadyExistsException(id)
        } else {
            return updateType(
                id = id,
                category = category,
                name = name,
                link = link,
                valueType = valueType,
                valueConfig = valueConfig,
                source = source,
                computed = computed,
                deprecated = deprecated
            )
        }
    }

    override fun deleteType(id: String, force: Boolean): Ack {
        securityService.checkGlobalFunction(IndicatorTypeManagement::class.java)
        if (force) securityService.checkGlobalFunction(GlobalSettings::class.java)
        val type = findTypeById(id)
        return if (type != null) {
            if (force || type.source == null || !type.deprecated.isNullOrBlank()) {
                listeners.forEach { it.onTypeDeleted(type) }
                storageService.delete(STORE, id)
                Ack.OK
            } else {
                Ack.NOK
            }
        } else {
            Ack.NOK
        }
    }

    override fun deprecateType(id: String, deprecated: String?) {
        securityService.checkGlobalFunction(IndicatorTypeManagement::class.java)
        val existing = storageService.find(STORE, id, StoredIndicatorType::class)
        if (existing != null) {
            val deprecatedEntry = existing.withDeprecated(deprecated)
            storageService.store(
                STORE,
                id,
                deprecatedEntry
            )
        }
    }

    override fun updateType(input: CreateTypeForm): IndicatorType<*, *> {
        securityService.checkGlobalFunction(IndicatorTypeManagement::class.java)
        val category = indicatorCategoryService.getCategory(input.category)
        val valueType = indicatorValueTypeService.getValueType<Any, Any>(input.valueType.id)
        val valueConfig = valueType.toConfigStoredJson(
            valueType.fromConfigForm(input.valueType.data ?: NullNode.instance)
        )
        val stored = StoredIndicatorType(
            id = input.id,
            category = category.id,
            name = input.name,
            link = input.link,
            valueType = valueType.id,
            valueConfig = valueConfig,
            source = null,
            computed = false,
            deprecated = input.deprecated?.takeIf { it.isNotBlank() }
        )
        storageService.store(
            STORE,
            input.id,
            stored
        )
        return getTypeById(input.id)
    }

    override fun <T, C> updateType(
        id: String,
        category: IndicatorCategory,
        name: String,
        link: String?,
        valueType: IndicatorValueType<T, C>,
        valueConfig: C,
        source: IndicatorSource?,
        computed: Boolean,
        deprecated: String?
    ): IndicatorType<T, C> {
        securityService.checkGlobalFunction(IndicatorTypeManagement::class.java)
        val stored = StoredIndicatorType(
            id = id,
            category = category.id,
            name = name,
            link = link,
            valueType = valueType.id,
            valueConfig = valueType.toConfigStoredJson(valueConfig),
            source = source,
            computed = computed,
            deprecated = deprecated?.takeIf { it.isNotBlank() }
        )
        storageService.store(
            STORE,
            id,
            stored
        )
        @Suppress("UNCHECKED_CAST")
        return getTypeById(id) as IndicatorType<T, C>
    }

    private class StoredIndicatorType(
        val id: String,
        val category: String,
        val name: String,
        val link: String?,
        val valueType: String,
        val valueConfig: JsonNode,
        val source: IndicatorSource?,
        val computed: Boolean,
        val deprecated: String?
    ) {
        fun withDeprecated(deprecated: String?) = StoredIndicatorType(
            id,
            category,
            name,
            link,
            valueType,
            valueConfig,
            source,
            computed,
            deprecated
        )
    }

    companion object {
        private val STORE: String get() = IndicatorType::class.java.name
    }

}