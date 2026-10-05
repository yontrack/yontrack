package net.nemerosa.ontrack.extension.audittrail.ui

import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationRun
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.graphql.support.toNotNull
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class GQLTypeEvidence(
    private val structureService: StructureService,
) : GQLType {

    override fun getTypeName(): String = getAPITypeName(EvidenceView::class)

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLBeanConverter.asObjectType(EvidenceView::class, cache) {
            field {
                it.name("validationRun")
                    .description("Validation run the evidence is attached to")
                    .type(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN).toNotNull())
                    .dataFetcher { env ->
                        val evidence: EvidenceView = env.getSource()!!
                        // Loaded once per request: the evidence of a build share a few runs
                        val runs = env.graphQlContext.computeIfAbsent(CONTEXT_VALIDATION_RUNS) {
                            ConcurrentHashMap<Int, ValidationRun>()
                        }
                        runs.computeIfAbsent(evidence.validationRunId) { id ->
                            structureService.getValidationRun(ID.of(id))
                        }
                    }
            }
        }

    companion object {
        /**
         * Key, in the context of a GraphQL request, of the validation runs of its evidence, by ID
         */
        private const val CONTEXT_VALIDATION_RUNS = "audit-trail.evidence.validationRuns"
    }
}
