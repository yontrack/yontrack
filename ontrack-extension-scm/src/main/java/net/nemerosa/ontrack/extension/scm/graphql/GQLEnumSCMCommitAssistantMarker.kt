package net.nemerosa.ontrack.extension.scm.graphql

import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantMarker
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumSCMCommitAssistantMarker : AbstractGQLEnum<SCMCommitAssistantMarker>(
    type = SCMCommitAssistantMarker::class,
    values = SCMCommitAssistantMarker.entries.toTypedArray(),
    description = "Kind of marker naming an assistant on a commit: CO_AUTHOR (a Co-Authored-By trailer with an agent's email), ASSISTED_BY (an Assisted-by trailer), SESSION_TRAILER (a session trailer such as Claude-Session), TRAILER (a trailer named by an agent marker pattern), AUTHOR (the email or login of the author), COMMITTER (the login of the committer)."
)
