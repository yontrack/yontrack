package net.nemerosa.ontrack.extension.scm.graphql

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.scm.changelog.COMMIT_MESSAGE_DEFAULT_MAX_LENGTH
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMDecoratedCommit
import net.nemerosa.ontrack.extension.scm.changelog.shortCommitMessage
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeBuild
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.field
import net.nemerosa.ontrack.graphql.support.getTypeDescription
import net.nemerosa.ontrack.graphql.support.toNotNull
import net.nemerosa.ontrack.model.support.MessageAnnotationUtils
import org.springframework.stereotype.Component

@Component
class GQLTypeSCMDecoratedCommit(
    private val gqlTypeSCMCommit: GQLTypeSCMCommit,
    private val scmDetector: SCMDetector,
) : GQLType {

    override fun getTypeName(): String = SCMDecoratedCommit::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(SCMDecoratedCommit::class))
            .field(SCMDecoratedCommit::commit, gqlTypeSCMCommit)

            .field {
                it.name("annotatedMessage")
                    .description("Annotated message with links")
                    .argument { arg ->
                        arg.name(ARG_MAX_LENGTH)
                            .description("Maximum length of the message, ellipsis included. Only the first line of the commit message is returned; set this to 0 to get the whole message.")
                            .type(GraphQLInt)
                            .defaultValueProgrammatic(COMMIT_MESSAGE_DEFAULT_MAX_LENGTH)
                    }
                    .type(GraphQLString.toNotNull())
                    .dataFetcher { env ->
                        val (project, commit) = env.getSource<SCMDecoratedCommit>()!!
                        val maxLength = env.getArgument<Int>(ARG_MAX_LENGTH) ?: COMMIT_MESSAGE_DEFAULT_MAX_LENGTH
                        // Shortening _before_ annotating: the annotated message is HTML, and cutting
                        // it as a string would break the links it contains.
                        //
                        // Note that 0 means more here than it does for the `commitsMaxLength` of a
                        // change log: a change log is a list of subjects, so 0 only lifts the length
                        // limit there, whereas a caller asking a single commit for its message
                        // without any limit wants the whole of it, body included. This is what the
                        // commit page does.
                        val message = if (maxLength <= 0) {
                            commit.message
                        } else {
                            shortCommitMessage(commit.message, maxLength)
                        }
                        val scm = scmDetector.getSCM(project)
                        if (scm != null && scm is SCMChangeLogEnabled) {
                            val annotator = scm.getConfiguredIssueService()?.messageAnnotator
                            if (annotator != null) {
                                MessageAnnotationUtils.annotate(message, listOf(annotator))
                            } else {
                                message
                            }
                        } else {
                            message
                        }
                    }
            }

            .field {
                it.name("build")
                    .description("Any build linked to this commit")
                    .type(GraphQLTypeReference(GQLTypeBuild.BUILD))
                    .dataFetcher { env ->
                        val (project, commit) = env.getSource<SCMDecoratedCommit>()!!
                        val scm = scmDetector.getSCM(project)
                        if (scm != null && scm is SCMChangeLogEnabled) {
                            val build = scm.findBuildByCommit(project, commit.id)
                            build
                        } else {
                            null
                        }
                    }
            }

            .build()

    companion object {
        const val ARG_MAX_LENGTH = "maxLength"
    }

}
