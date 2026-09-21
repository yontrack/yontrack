package net.nemerosa.ontrack.extension.indicators.model

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.structure.Project
import java.time.Duration
import java.time.LocalDateTime

interface IndicatorService {

    fun getProjectIndicators(project: Project, all: Boolean): List<Indicator<*>>

    fun <T> getProjectIndicator(project: Project, type: IndicatorType<T, *>, previous: Duration? = null): Indicator<T>

    /**
     * Gets the indicator which was previously entered for this [project] and this [type].
     *
     * The returned value is never `null` but its [value][Indicator.value] might be.
     */
    fun <T> getPreviousProjectIndicator(project: Project, type: IndicatorType<T, *>): Indicator<T>

    fun getProjectIndicator(project: Project, typeId: String): Indicator<*>

    fun <T> updateProjectIndicator(project: Project, typeId: String, input: JsonNode): Indicator<T>

    /**
     * @param time Time to set for the indicator (used only for tests)
     */
    fun <T> updateProjectIndicator(project: Project, type: IndicatorType<T, *>, value: T?, comment: String?, time: LocalDateTime? = null): Indicator<T>

    fun deleteProjectIndicator(project: Project, typeId: String): Ack

    /**
     * Gets all indicators with their history.
     *
     * @param project Project to get history for
     */
    fun getAllProjectIndicators(project: Project): List<Indicator<*>>

    /**
     * Gets the history of a given indicator.
     *
     * @param project Project to get history for
     * @param type Project indicator type to get history for
     * @param offset History offset
     * @param size History size
     * @return History of this indicator
     */
    fun <T> getProjectIndicatorHistory(project: Project, type: IndicatorType<T, *>, offset: Int, size: Int): IndicatorHistory<T>


}