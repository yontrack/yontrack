package net.nemerosa.ontrack.graphql.prefetch

import graphql.execution.DataFetcherResult
import graphql.execution.instrumentation.FieldFetchingInstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.GraphQLList
import graphql.schema.GraphQLTypeUtil
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.support.ReadMemo
import org.springframework.stereotype.Component

/**
 * The entities returned by a list field - the builds of a branch, the branches of a project, the
 * projects, the promotion levels and validation stamps of a branch, the runs of a build... - have
 * their properties [prefetched][PropertyService.prefetchProperties] together (#2081): the first
 * property read for one of them, by a field of the query or by a service it calls - a decoration,
 * a display name - loads the properties of all of them in one query, into the read memo of the
 * request.
 *
 * Nothing is loaded when no property of the listed entities is read, nor outside of a unit of work.
 * No `DataLoader`: the readers of the properties are synchronous services, which read the memo.
 */
@Component
class PropertiesPrefetchInstrumentation(
    private val propertyService: PropertyService,
) : SimplePerformantInstrumentation() {

    override fun beginFieldFetching(
        parameters: InstrumentationFieldFetchParameters,
        state: InstrumentationState?,
    ): FieldFetchingInstrumentationContext? {
        val listField = GraphQLTypeUtil.unwrapNonNull(parameters.environment.fieldType) is GraphQLList
        return if (listField && ReadMemo.current() != null) {
            PrefetchContext()
        } else {
            super.beginFieldFetching(parameters, state)
        }
    }

    private inner class PrefetchContext : FieldFetchingInstrumentationContext {

        override fun onDispatched() {
        }

        override fun onCompleted(result: Any?, t: Throwable?) {
            val value = if (result is DataFetcherResult<*>) result.data else result
            if (value is Iterable<*>) {
                val entities = value.filterIsInstance<ProjectEntity>()
                if (entities.size > 1) {
                    propertyService.prefetchProperties(entities)
                }
            }
        }
    }
}
