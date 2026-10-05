package net.nemerosa.ontrack.extension.audittrail.ui

import graphql.Scalars.GraphQLBoolean
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceService
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationService
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.toNotNull
import org.springframework.stereotype.Component

/**
 * `BuildAuditTrail` — the trail of a build: its entries, their endorsements, the evidence of its
 * validation runs and its verification.
 */
@Component
class GQLTypeBuildAuditTrail(
    private val gqlTypeAuditTrailEntry: GQLTypeAuditTrailEntry,
    private val gqlTypeAuditTrailEndorsement: GQLTypeAuditTrailEndorsement,
    private val gqlTypeAuditTrailVerification: GQLTypeAuditTrailVerification,
    private val gqlTypeEvidence: GQLTypeEvidence,
    private val trailService: TrailService,
    private val evidenceService: EvidenceService,
    private val trailVerificationService: TrailVerificationService,
) : GQLType {

    override fun getTypeName(): String = BUILD_AUDIT_TRAIL

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Trail of a build: the append-only, hash-chained record of its story")
            .field {
                it.name("entries")
                    .description("Entries of the trail, by seq")
                    .type(listType(gqlTypeAuditTrailEntry.typeRef))
                    .dataFetcher { env ->
                        val trail: BuildAuditTrail = env.getSource()!!
                        trailService.getEntries(trail.build).map { entry -> AuditTrailEntryView.of(entry) }
                    }
            }
            .field {
                it.name("endorsements")
                    .description("Endorsements of the entries, by seq - none for the entries written while the instance key was not provisioned")
                    .type(listType(gqlTypeAuditTrailEndorsement.typeRef))
                    .dataFetcher { env ->
                        val trail: BuildAuditTrail = env.getSource()!!
                        val seqs = trailService.getEntries(trail.build).associate { entry -> entry.id to entry.seq }
                        trailService.getEndorsements(trail.build).map { endorsement ->
                            AuditTrailEndorsementView(
                                entryId = endorsement.entryId,
                                seq = seqs.getValue(endorsement.entryId),
                                keyId = endorsement.keyId,
                                signature = endorsement.signature,
                                time = TrailHashFormatV1.formatTime(endorsement.time),
                            )
                        }
                    }
            }
            .field {
                it.name("evidence")
                    .description("Evidences of every validation run of the build, deleted ones included, in the order of their upload")
                    .type(listType(gqlTypeEvidence.typeRef))
                    .dataFetcher { env ->
                        val trail: BuildAuditTrail = env.getSource()!!
                        evidenceService.getEvidences(trail.build).map { evidence -> EvidenceView.of(evidence) }
                    }
            }
            .field {
                it.name("verification")
                    .description("Verification of the trail: its chain, its endorsements and, on demand, its evidence")
                    .argument(
                        GraphQLArgument.newArgument()
                            .name(ARG_INCLUDE_EVIDENCE)
                            .description("Whether to check the evidence the trail references too")
                            .type(GraphQLBoolean)
                            .defaultValueProgrammatic(false)
                    )
                    .type(gqlTypeAuditTrailVerification.typeRef.toNotNull())
                    .dataFetcher { env ->
                        val trail: BuildAuditTrail = env.getSource()!!
                        val includeEvidence: Boolean = env.getArgument<Boolean>(ARG_INCLUDE_EVIDENCE) ?: false
                        trailVerificationService.verify(trail.build, includeEvidence)
                    }
            }
            .build()

    companion object {
        /**
         * Name of the type
         */
        const val BUILD_AUDIT_TRAIL = "BuildAuditTrail"

        /**
         * Argument of the verification which checks the evidence too
         */
        const val ARG_INCLUDE_EVIDENCE = "includeEvidence"
    }
}
