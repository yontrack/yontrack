package net.nemerosa.ontrack.extension.indicators.model

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.extension.indicators.acl.IndicatorEdit
import net.nemerosa.ontrack.extension.indicators.store.IndicatorStore
import net.nemerosa.ontrack.extension.indicators.store.StoredIndicator
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.Signature
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
@Transactional
class IndicatorServiceImpl(
        private val securityService: SecurityService,
        private val indicatorStore: IndicatorStore,
        private val indicatorTypeService: IndicatorTypeService,
) : IndicatorService, IndicatorTypeListener {

    init {
        indicatorTypeService.registerTypeListener(this)
    }

    override fun onTypeDeleted(type: IndicatorType<*, *>) {
        indicatorStore.deleteIndicatorByType(type.id)
    }

    override fun getProjectIndicators(project: Project, all: Boolean): List<Indicator<*>> {
        // Gets all the types
        val types = indicatorTypeService.findAll()
        // Gets the values
        return types.map {
            loadIndicator(project, it)
        }.filter {
            all || it.value != null
        }
    }

    override fun getAllProjectIndicators(project: Project): List<Indicator<*>> {
        // Gets all the types
        val types = indicatorTypeService.findAll()
        // Gets the values
        return types.flatMap { type ->
            indicatorStore.loadIndicatorHistory(project, type.id).map { stored ->
                toIndicator(stored, type)
            }
        }
    }

    override fun <T> getProjectIndicatorHistory(project: Project, type: IndicatorType<T, *>, offset: Int, size: Int): IndicatorHistory<T> {
        val indicators = indicatorStore.loadIndicatorHistory(project, type.id, offset, size).map { stored ->
            toIndicator(stored, type)
        }
        val total = indicatorStore.getCountIndicatorHistory(project, type.id)
        return IndicatorHistory(
                items = indicators,
                offset = offset,
                total = total
        )
    }

    override fun getProjectIndicator(project: Project, typeId: String): Indicator<*> {
        val type = indicatorTypeService.getTypeById(typeId)
        return loadIndicator(project, type)
    }

    override fun <T> getProjectIndicator(project: Project, type: IndicatorType<T, *>, previous: Duration?): Indicator<T> {
        return loadIndicator(project, type, previous)
    }

    override fun <T> updateProjectIndicator(project: Project, typeId: String, input: JsonNode): Indicator<T> {
        securityService.checkProjectFunction(project, IndicatorEdit::class.java)
        @Suppress("UNCHECKED_CAST")
        val type = indicatorTypeService.getTypeById(typeId) as IndicatorType<T, *>
        // Parsing
        val value = type.fromClientJson(input)
        // Comment extraction
        val comment = if (input is ObjectNode && input.has(FIELD_COMMENT)) {
            val commentNode = input.get(FIELD_COMMENT)
            input.remove(FIELD_COMMENT)
            if (commentNode.isNull) {
                null
            } else {
                val comment = commentNode.asText()
                comment
            }
        } else {
            null
        }
        // OK
        return updateProjectIndicator(project, type, value, comment)
    }

    override fun <T> updateProjectIndicator(project: Project, type: IndicatorType<T, *>, value: T?, comment: String?, time: LocalDateTime?): Indicator<T> {
        // Signature
        val signature = if (time != null) {
            securityService.currentSignature.withTime(time)
        } else {
            securityService.currentSignature
        }
        // Storing the indicator
        indicatorStore.storeIndicator(project, type.id, StoredIndicator(
                value = value?.run { type.toStoredJson(this) } ?: NullNode.instance,
                comment = comment,
                signature = signature
        ))
        val indicator = loadIndicator(project, type)
        // OK
        return indicator
    }

    override fun deleteProjectIndicator(project: Project, typeId: String): Ack {
        return indicatorStore.deleteIndicator(project, typeId)
    }

    override fun <T> getPreviousProjectIndicator(project: Project, type: IndicatorType<T, *>): Indicator<T> {
        val stored = indicatorStore.loadPreviousIndicator(project, type.id)
        return toIndicator(stored, type)
    }

    private fun <T, C> loadIndicator(project: Project, type: IndicatorType<T, C>, previous: Duration? = null): Indicator<T> {
        val stored = indicatorStore.loadIndicator(project, type.id, previous)
        return toIndicator(stored, type)
    }

    private fun <C, T> toIndicator(stored: StoredIndicator?, type: IndicatorType<T, C>): Indicator<T> {
        return if (stored != null) {
            if (stored.value != null && !stored.value.isNull) {
                val value = type.fromStoredJson(stored.value)
                Indicator(
                        type = type,
                        value = value,
                        compliance = value?.let { type.getStatus(it) },
                        comment = stored.comment,
                        signature = stored.signature
                )
            } else {
                Indicator(
                        type = type,
                        value = null,
                        compliance = null,
                        comment = stored.comment,
                        signature = stored.signature
                )
            }
        } else {
            Indicator(
                    type = type,
                    value = null,
                    compliance = null,
                    comment = null,
                    signature = Signature.anonymous()
            )
        }
    }

    companion object {
        private const val FIELD_COMMENT = "comment"
    }
}