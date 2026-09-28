package net.nemerosa.ontrack.extension.av.graphql

import graphql.Scalars
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntry
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntryService
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningRetryService
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequest
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypePromotionRun
import net.nemerosa.ontrack.graphql.support.*
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

@Component
class GQLTypeAutoVersioningAuditEntry(
    private val gqlTypeAutoVersioningOrder: GQLTypeAutoVersioningOrder,
    private val gqlTypeAutoVersioningAuditEntryState: GQLTypeAutoVersioningAuditEntryState,
    private val structureService: StructureService,
    private val autoVersioningAuditEntryService: AutoVersioningAuditEntryService,
    private val gqlTypeAutoVersioningAuditEntryLineage: GQLTypeAutoVersioningAuditEntryLineage,
    private val autoVersioningRetryService: AutoVersioningRetryService,
) : GQLType {

    override fun getTypeName(): String = AutoVersioningAuditEntry::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Audit entry for an auto versioning processing order.")
            .field(AutoVersioningAuditEntry::order, gqlTypeAutoVersioningOrder, "Associated auto versioning order")
            // Audit list
            .field {
                it.name("audit")
                    .description("History of the different states")
                    .type(listType(gqlTypeAutoVersioningAuditEntryState.typeRef))
            }
            // Most recent state
            .field {
                it.name("mostRecentState")
                    .description("Most recent state")
                    .type(gqlTypeAutoVersioningAuditEntryState.typeRef.toNotNull())
            }
            // Running flag
            .field {
                it.name("running")
                    .description("Running state")
                    .type(Scalars.GraphQLBoolean)
            }
            // Duration
            .field {
                it.name("duration")
                    .description("Elapsed time between the creation of this process and its last state (in ms)")
                    .type(Scalars.GraphQLInt)
            }
            // Link to any associated pull request
            .fieldGetter<AutoVersioningAuditEntry, SCMPullRequest>(
                name = "pullRequest",
                description = "Associated pull request if any",
                nullable = true,
            ) { source, _ ->
                autoVersioningAuditEntryService.getPullRequest(source)
            }
            // Upgrade branch
            .stringField(AutoVersioningAuditEntry::upgradeBranch)
            // Routing & queue
            .stringField(AutoVersioningAuditEntry::routing)
            .stringField(AutoVersioningAuditEntry::queue)
            // Promotion run which triggered the auto-versioning
            .field {
                it.name("promotionRun")
                    .description("Promotion which triggered the auto-versioning")
                    .type(GraphQLTypeReference(GQLTypePromotionRun.PROMOTION_RUN))
                    .dataFetcher { env ->
                        val entry: AutoVersioningAuditEntry = env.getSource()!!
                        val promotionRunId = entry.order.sourcePromotionRunId
                        promotionRunId?.let {
                            structureService.findPromotionRunByID(ID.of(it))
                        }
                    }
            }
            // Automatic retries & manual reschedules
            .field {
                it.name("lineage")
                    .description("Automatic retries & manual reschedules this entry is part of")
                    .type(gqlTypeAutoVersioningAuditEntryLineage.typeRef.toNotNull())
                    .dataFetcher { env ->
                        val entry: AutoVersioningAuditEntry = env.getSource()!!
                        autoVersioningRetryService.getLineage(entry)
                    }
            }
            // OK
            .build()
}