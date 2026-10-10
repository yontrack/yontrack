package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.support.ConfigurationProperty
import net.nemerosa.ontrack.model.support.ConfigurationPropertyType
import net.nemerosa.ontrack.repository.PropertyRepository
import net.nemerosa.ontrack.repository.TProperty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * Global listener to clean properties linked to removed configurations.
 */
@Component
class ConfigurationPropertyCleanupListener(
    private val propertyService: PropertyService,
    private val propertyRepository: PropertyRepository,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    transactionManager: PlatformTransactionManager,
) : EventListener {

    private val logger = LoggerFactory.getLogger(ConfigurationPropertyCleanupListener::class.java)

    /**
     * Decoding a property which fails in a transactional service (a configuration service, for example)
     * would otherwise mark the whole transaction of the configuration deletion as rollback-only.
     */
    private val nestedTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NESTED
    }

    override fun onEvent(event: Event) {
        if (event.eventType === EventFactory.DELETE_CONFIGURATION) {
            val configurationName = event.getValue("CONFIGURATION")
            val configurationType = event.getValue("CONFIGURATION_TYPE")
            cleanup(configurationName, configurationType)
        }
    }

    private fun cleanup(configurationName: String, configurationType: String) {
        securityService.asAdmin {
            propertyService.propertyTypes
                .filterIsInstance<ConfigurationPropertyType<*, *>>()
                .forEach { propertyType ->
                    cleanupType(propertyType, configurationName, configurationType)
                }
        }
    }

    private fun cleanupType(
        propertyType: ConfigurationPropertyType<*, *>,
        configurationName: String,
        configurationType: String
    ) {
        // Collects the entities first, the deletions not interfering with the loop over the stored properties
        val entities = mutableListOf<ProjectEntityID>()
        propertyRepository.forEachEntityWithProperty(propertyType.typeName) { t ->
            // A property which cannot be read is skipped, so that the other ones are still cleaned up
            val property = readProperty(propertyType, t) ?: return@forEachEntityWithProperty
            if (property is ConfigurationProperty<*>) {
                val configuration = property.configuration
                if (configuration::class.java.name == configurationType && configuration.name == configurationName) {
                    entities += ProjectEntityID(t.entityType, t.entityId.value)
                }
            }
        }
        entities.forEach { entityId ->
            val entity = entityId.type.getEntityFn(structureService).apply(ID.of(entityId.id))
            propertyService.deleteProperty(entity, propertyType.typeName)
        }
    }

    /**
     * Decodes a stored property, in a nested transaction, returning `null` when it cannot be read.
     */
    private fun readProperty(propertyType: ConfigurationPropertyType<*, *>, t: TProperty): Any? =
        try {
            nestedTransaction.execute { propertyType.fromStorage(t.json) }
        } catch (any: Exception) {
            // Never logging the stored JSON, which may contain secrets
            logger.warn(
                "Cannot read the ${propertyType.typeName} property of ${t.entityType} ${t.entityId.value}, " +
                        "skipping it in the cleanup of a deleted configuration: ${any.message}"
            )
            null
        }

}
