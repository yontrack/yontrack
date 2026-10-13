package net.nemerosa.ontrack.service

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.exceptions.PropertyTypeNotFoundException
import net.nemerosa.ontrack.model.exceptions.PropertyUnsupportedEntityTypeException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.PropertyRepository
import net.nemerosa.ontrack.repository.TProperty
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.function.BiFunction
import java.util.function.Predicate
import kotlin.reflect.KClass

/**
 * The properties are read through the read memo of the unit of work, never cached across units
 * (#2081): see [PropertyReadMemo].
 *
 * The methods which only read properties never mark the transaction as rollback-only when they fail, for
 * example on a stored value which cannot be decoded anymore. Their callers can then catch the failure and go on,
 * like when a project's SCM configuration cannot be read, without failing the whole request when it commits.
 */
@Service
@Transactional
class PropertyServiceImpl(
        private val eventPostService: EventPostService,
        private val eventFactory: EventFactory,
        private val propertyRepository: PropertyRepository,
        private val securityService: SecurityService,
        private val extensionManager: ExtensionManager,
        transactionManager: PlatformTransactionManager,
) : PropertyService {

    private val nestedTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NESTED
    }

    private val logger = LoggerFactory.getLogger(PropertyServiceImpl::class.java)

    private val memo = PropertyReadMemo(propertyRepository)

    override val propertyTypes: List<PropertyType<*>> by lazy {
        val types = extensionManager.getExtensions(PropertyType::class.java)
        val result = mutableListOf<PropertyType<*>>()
        types.forEach { result.add(it) }
        result
    }

    /**
     * The number of available property types is fairly limited so a static cache is enough.
     */
    private val cache: Map<String, PropertyType<*>?> by lazy {
        propertyTypes.associateBy { it.typeName }
    }

    override fun <T> getPropertyTypeByName(propertyTypeName: String): PropertyType<T> {
        @Suppress("UNCHECKED_CAST")
        return cache[propertyTypeName] as PropertyType<T>? ?: throw PropertyTypeNotFoundException(propertyTypeName)
    }

    @Transactional(noRollbackFor = [Exception::class])
    override fun getProperties(entity: ProjectEntity): List<Property<*>> =
        getProperties(entity, propertyTypes)

    @Transactional(noRollbackFor = [Exception::class])
    override fun getProperties(entity: ProjectEntity, propertyTypeName: String): List<Property<*>> =
        getProperties(entity, listOfNotNull(cache[propertyTypeName]))

    private fun getProperties(entity: ProjectEntity, types: List<PropertyType<*>>): List<Property<*>> {
        // With the given property types...
        val visibleTypes = types
                // ... filters them by entity
                .filter { type -> type.supportedEntityTypes.contains(entity.projectEntityType) }
                // ... filters them by access right
                .filter { type -> type.canView(entity, securityService) }
        if (visibleTypes.isEmpty()) return emptyList()
        // All the stored properties of the entity, in one query
        val stored = memo.stored(entity.projectEntityType, entity.id)
        return visibleTypes
                // ... decodes them, an unreadable value not failing the whole list
                .map { type -> decodeOrError(type, entity, stored[type.javaClass.name]) }
                // .. flags with edition rights
                .map { prop -> prop.editable(prop.type.canEdit(entity, securityService)) }
    }

    override fun prefetchProperties(entities: Collection<ProjectEntity>) {
        memo.prefetch(entities)
    }

    @Transactional(noRollbackFor = [Exception::class])
    override fun <T> getProperty(entity: ProjectEntity, propertyTypeName: String): Property<T> {
        // Gets the property using its fully qualified type name
        val propertyType: PropertyType<T> = getPropertyTypeByName(propertyTypeName)
        // Access, an unreadable value being returned as an error
        return getPropertyOrError(propertyType, entity)
    }

    @Transactional(noRollbackFor = [Exception::class])
    override fun <T> getProperty(entity: ProjectEntity, propertyTypeClass: Class<out PropertyType<T>>): Property<T> {
        // The caller asks for the value: an unreadable value must fail, not look like a missing one
        val propertyType: PropertyType<T> = getPropertyTypeByName(propertyTypeClass.name)
        return getProperty(propertyType, entity)
    }

    @Transactional(noRollbackFor = [Exception::class])
    override fun <T> getPropertyValue(entity: ProjectEntity, propertyTypeClass: Class<out PropertyType<T>>): T? {
        return getProperty(entity, propertyTypeClass).value
    }

    @Transactional(noRollbackFor = [Exception::class])
    override fun <T> hasProperty(entity: ProjectEntity, propertyTypeClass: Class<out PropertyType<T>>): Boolean {
        return memo.stored(propertyTypeClass.name, entity.projectEntityType, entity.id) != null
    }

    override fun editProperty(entity: ProjectEntity, propertyTypeName: String, data: JsonNode): Ack {
        // Gets the property using its fully qualified type name
        val propertyType: PropertyType<*> = getPropertyTypeByName<Any>(propertyTypeName)
        // Edits the property
        return editProperty(entity, propertyType, data)
    }

    override fun deleteProperty(entity: ProjectEntity, propertyTypeName: String): Ack {
        // Gets the property using its fully qualified type name
        val propertyType: PropertyType<*> = getPropertyTypeByName<Any>(propertyTypeName)
        // Deletes the property
        return deleteProperty(entity, propertyType)
    }

    private fun <T> deleteProperty(entity: ProjectEntity, propertyType: PropertyType<T>): Ack {
        // Checks for edition
        if (!propertyType.canEdit(entity, securityService)) {
            throw AccessDeniedException("Property is not opened for viewing.")
        }
        // Checks the existence without decoding, so that a property whose value cannot be read can be deleted
        val typeName = propertyType.javaClass.name
        return if (propertyRepository.hasProperty(typeName, entity.projectEntityType, entity.id)) {
            // Existing value, for the listener, null when it cannot be read
            val value = getPropertyOrError(propertyType, entity).value
            val ack = propertyRepository.deleteProperty(typeName, entity.projectEntityType, entity.id)
            memo.written(typeName, entity.projectEntityType, entity.id)
            if (ack.success) {
                // Property deletion event
                eventPostService.post(eventFactory.propertyDelete(entity, propertyType))
                // Listener, skipped when the stored value could not be read, as there is no value to pass
                if (value != null) {
                    propertyType.onPropertyDeleted(entity, value)
                }
            }
            // OK
            ack
        } else {
            Ack.NOK
        }
    }

    override fun <T> editProperty(entity: ProjectEntity, propertyType: Class<out PropertyType<T>>, data: T): Ack {
        // Gets the property type by name
        val actualPropertyType: PropertyType<T> = getPropertyTypeByName(propertyType.name)
        // Actual edition
        return editProperty(entity, actualPropertyType, data)
    }

    private fun <T> editProperty(entity: ProjectEntity, propertyType: PropertyType<T>, data: JsonNode): Ack {
        // Gets the value and validates it
        val value = propertyType.fromClient(data)
        // Actual edition
        return editProperty(entity, propertyType, value)
    }

    private fun <T> editProperty(entity: ProjectEntity, propertyType: PropertyType<T>, value: T): Ack {
        // Checks for edition
        if (!propertyType.canEdit(entity, securityService)) {
            throw AccessDeniedException("Property is not opened for edition.")
        }
        // Gets the JSON for the storage
        val storage = propertyType.forStorage(value)
        // Stores the property
        propertyRepository.saveProperty(
                propertyType.javaClass.name,
                entity.projectEntityType,
                entity.id,
                storage
        )
        memo.written(propertyType.javaClass.name, entity.projectEntityType, entity.id)
        // Property change event
        eventPostService.post(eventFactory.propertyChange(entity, propertyType))
        // Listener
        propertyType.onPropertyChanged(entity, value)
        // OK
        return Ack.OK
    }

    protected fun <T> getProperty(type: PropertyType<T>, entity: ProjectEntity): Property<T> {
        val value = getPropertyValue(type, entity)
        return if (value != null) Property.of(type, value) else Property.empty(type)
    }

    /**
     * Same as [getProperty] but returns a property with an [error][Property.error] when its stored value
     * cannot be decoded, instead of failing.
     */
    private fun <T> getPropertyOrError(type: PropertyType<T>, entity: ProjectEntity): Property<T> {
        // Checks done outside the decoding, so that they are not turned into errors
        checkPropertyAccess(type, entity)
        val t = memo.stored(type.javaClass.name, entity.projectEntityType, entity.id)
        return decodeOrError(type, entity, t)
    }

    /**
     * Decodes the stored property [t] - none meaning an empty property - into a property, with an
     * [error][Property.error] when it cannot be decoded.
     */
    private fun <T> decodeOrError(type: PropertyType<T>, entity: ProjectEntity, t: TProperty?): Property<T> {
        if (t == null) return Property.empty(type)
        val decoded = memo.decoded(type.javaClass.name, entity.projectEntityType, entity.id) {
            // Decoding in a nested transaction: a transactional service failing during the decoding (a
            // configuration service, for example) would otherwise mark the whole transaction as rollback-only
            nestedTransaction.execute { type.fromStorage(t.json) }
        }
        return decoded.fold(
            onSuccess = { value ->
                @Suppress("UNCHECKED_CAST")
                if (value != null) Property.of(type, value as T) else Property.empty(type)
            },
            onFailure = { any ->
                logUnreadableProperty(type, entity, any)
                Property.error(type, unreadablePropertyMessage(any))
            },
        )
    }

    private fun logUnreadableProperty(type: PropertyType<*>, entity: ProjectEntity, any: Throwable) {
        // No stack trace, as this would be logged at every page load
        logger.warn(
            "Cannot read the ${type.javaClass.name} property of ${entity.entityDisplayName}: ${unreadablePropertyMessage(any)}"
        )
    }

    private fun checkPropertyAccess(type: PropertyType<*>, entity: ProjectEntity) {
        // Supported entity?
        if (!type.supportedEntityTypes.contains(entity.projectEntityType)) {
            throw PropertyUnsupportedEntityTypeException(type.javaClass.name, entity.projectEntityType)
        }
        // Checks for viewing
        if (!type.canView(entity, securityService)) {
            throw AccessDeniedException("Property is not opened for viewing.")
        }
    }

    protected fun <T> getPropertyValue(type: PropertyType<T>, entity: ProjectEntity): T? {
        val typeName = type.javaClass.name
        checkPropertyAccess(type, entity)
        // Gets the raw information from the repository
        val t = memo.stored(typeName, entity.projectEntityType, entity.id)
                ?: return null
        // Converts the stored value into an actual value, failing if it cannot be decoded
        @Suppress("UNCHECKED_CAST")
        return memo.decoded(typeName, entity.projectEntityType, entity.id) {
            type.fromStorage(t.json)
        }.getOrThrow() as T?
    }

    @Transactional(noRollbackFor = [Exception::class])
    override fun <T> searchWithPropertyValue(
            propertyTypeClass: Class<out PropertyType<T>>,
            entityLoader: BiFunction<ProjectEntityType, ID, ProjectEntity>,
            predicate: Predicate<T>): Collection<ProjectEntity> {
        // Gets the property type
        val propertyTypeName = propertyTypeClass.name
        val propertyType: PropertyType<T> = getPropertyTypeByName(propertyTypeName)
        // Search
        return propertyRepository.searchByProperty(
                propertyTypeName,
                entityLoader
        ) { t: TProperty ->
            predicate.test(propertyType.fromStorage(t.json))
        }
    }

    override fun <T> forEachEntityWithProperty(propertyTypeClass: KClass<out PropertyType<T>>, consumer: (ProjectEntityID, T) -> Unit) {
        // Gets the property type name
        val propertyTypeName = propertyTypeClass.java.name
        // Gets the property type by name
        val actualPropertyType: PropertyType<T> = getPropertyTypeByName(propertyTypeName)
        // Loops over the properties
        propertyRepository.forEachEntityWithProperty(
                propertyTypeName
        ) { t: TProperty ->
            consumer(
                    ProjectEntityID(t.entityType, t.entityId.value),
                    actualPropertyType.fromStorage(t.json)
            )
        }
    }

    override fun <T> forEachEntityWithProperty(propertyType: PropertyType<T>, consumer: (ProjectEntityID, T) -> Unit) {
        propertyRepository.forEachEntityWithProperty(
                propertyType.typeName
        ) { t: TProperty ->
            consumer(
                    ProjectEntityID(t.entityType, t.entityId.value),
                    propertyType.fromStorage(t.json)
            )
        }
    }

    override fun <T> findBuildByBranchAndSearchkey(branchId: ID, propertyType: Class<out PropertyType<T>>, searchKey: String): ID? {
        // Gets the property type by name
        val actualPropertyType: PropertyType<T> = getPropertyTypeByName(propertyType.name)
        // Gets the search arguments
        val searchArguments = actualPropertyType.getSearchArguments(searchKey)
        return if (searchArguments != null) {
            propertyRepository.findBuildByBranchAndSearchkey(branchId, propertyType.name, searchArguments)
        } else {
            null
        }
    }

    override fun <T> findByEntityTypeAndSearchkey(entityType: ProjectEntityType, propertyType: Class<out PropertyType<T>>, searchKey: String): List<ID> {
        // Gets the property type by name
        val actualPropertyType: PropertyType<T> = getPropertyTypeByName(propertyType.name)
        // Gets the search arguments
        val searchArguments = actualPropertyType.getSearchArguments(searchKey)
        return if (searchArguments != null) {
            propertyRepository.findByEntityTypeAndSearchArguments(entityType, propertyType.name, searchArguments)
        } else {
            emptyList()
        }
    }

    override fun <T> findByEntityTypeAndSearchArguments(
        entityType: ProjectEntityType,
        propertyType: KClass<out PropertyType<T>>,
        searchArguments: PropertySearchArguments?
    ): List<ID> {
        return propertyRepository.findByEntityTypeAndSearchArguments(
            entityType,
            propertyType.java.name,
            searchArguments
        )
    }

    override fun <T> copyProperty(sourceEntity: ProjectEntity, property: Property<T>, targetEntity: ProjectEntity, replacementFn: (String) -> String) {
        property.value?.let {
            // Property copy
            val data = property.type.copy(sourceEntity, it, targetEntity, replacementFn)
            // Direct edition
            editProperty(targetEntity, property.type, data)
        }
    }

    companion object {

        /**
         * Location of a JSON parsing error, which may embed the source being parsed.
         */
        private val jsonSourceLocation = Regex("""\s*at \[Source: .*?; line: \d+, column: \d+]""", RegexOption.DOT_MATCHES_ALL)

        /**
         * Message for a property whose stored value cannot be read: the message of the root cause, without
         * the stored value, which may hold secrets.
         */
        internal fun unreadablePropertyMessage(any: Throwable): String {
            var root = any
            while (root.cause != null && root.cause !== root) {
                root = root.cause!!
            }
            val message = root.message
                ?.replace(jsonSourceLocation, "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            return message ?: root.javaClass.simpleName
        }
    }

}
