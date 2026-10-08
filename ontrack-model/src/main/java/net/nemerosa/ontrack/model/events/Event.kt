package net.nemerosa.ontrack.model.events

import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.NameValue

/**
 * Definition of an event
 */
class Event(
    val id: Int = 0,
    val eventType: EventType,
    val signature: Signature?,
    val entities: Map<ProjectEntityType, ProjectEntity>,
    val extraEntities: Map<ProjectEntityType, ProjectEntity>,
    val ref: ProjectEntityType?,
    val values: Map<String, NameValue>,
) {

    fun getIntValue(name: String): Int {
        return getValue(name).toInt(10)
    }

    fun getValue(name: String): String =
        values[name]?.value ?: error("Missing value '$name' in the event.")

    @Suppress("UNCHECKED_CAST")
    fun <T : ProjectEntity> getEntity(entityType: ProjectEntityType): T =
        entities[entityType] as T? ?: error("Missing entity $entityType in the event.")

    fun withId(id: Int): Event = Event(
        id = id,
        eventType = eventType,
        signature = signature,
        entities = entities,
        extraEntities = extraEntities,
        ref = ref,
        values = values
    )

    fun withSignature(signature: Signature?): Event = Event(
        id = id,
        eventType = eventType,
        signature = signature,
        entities = entities,
        extraEntities = extraEntities,
        ref = ref,
        values = values
    )

    class EventBuilder(private val eventType: EventType) {

        private var signature: Signature? = null
        private val entities = mutableMapOf<ProjectEntityType, ProjectEntity>()
        private val extraEntities = mutableMapOf<ProjectEntityType, ProjectEntity>()
        private var ref: ProjectEntityType? = null
        private val values = mutableMapOf<String, NameValue>()

        fun with(signature: Signature?): EventBuilder {
            this.signature = signature
            return this
        }

        fun withNoSignature(): EventBuilder {
            signature = null
            return this
        }

        /**
         * Registers the build, its branch and its project - not the signature of the build: the event
         * is signed by the caller when posted, unless it says otherwise (#2043).
         */
        fun withBuild(build: Build): EventBuilder {
            return withBranch(build.branch).with(build)
        }

        fun withPromotionRun(promotionRun: PromotionRun): EventBuilder {
            return withBuild(promotionRun.build).with(promotionRun).with(promotionRun.promotionLevel)
                .with(promotionRun.signature)
        }

        fun withValidationRun(validationRun: ValidationRun): EventBuilder {
            return withBuild(validationRun.build).with(validationRun.validationStamp).with(validationRun)
                .with(validationRun.lastStatus.signature)
        }

        fun withPromotionLevel(promotionLevel: PromotionLevel): EventBuilder {
            return withBranch(promotionLevel.branch).with(promotionLevel)
        }

        fun withValidationStamp(validationStamp: ValidationStamp): EventBuilder {
            return withBranch(validationStamp.branch).with(validationStamp)
        }

        fun withBranch(branch: Branch): EventBuilder {
            return withProject(branch.project).with(branch)
        }

        fun withProject(project: Project): EventBuilder {
            return with(project)
        }

        fun withRef(entity: ProjectEntity): EventBuilder {
            ref = entity.projectEntityType
            return withProject(entity.project).with(entity)
        }

        fun with(entity: ProjectEntity): EventBuilder {
            entities[entity.projectEntityType] = entity
            return this
        }

        fun withExtra(entity: ProjectEntity): EventBuilder {
            extraEntities[entity.projectEntityType] = entity
            extraEntities[ProjectEntityType.PROJECT] = entity.project
            return this
        }

        fun withValidationRunStatus(statusID: ValidationRunStatusID): EventBuilder {
            return with("STATUS", NameValue(statusID.name, statusID.id))
        }

        fun with(name: String, value: NameValue): EventBuilder {
            values[name] = value
            return this
        }

        fun with(name: String, value: String?): EventBuilder =
            if (value == null) {
                this
            } else {
                with(name, NameValue(name, value))
            }

        fun build(): Event {
            // Creates the event
            val event = Event(
                id = 0,
                eventType = eventType,
                signature = signature,
                entities = entities,
                extraEntities = extraEntities,
                ref = ref,
                values = values
            )
            // OK
            return event
        }
    }

    companion object {

        @JvmStatic
        fun of(eventType: EventType): EventBuilder {
            return EventBuilder(eventType)
        }

    }
}