package net.nemerosa.ontrack.extension.scm.changelog

import org.springframework.stereotype.Component

@Component
class SemanticChangelogServiceImpl : SemanticChangelogService {

    // Regex: type(scope)?: subject
    private val regex = Regex("""^(\w+)(?:\(([^)]+)\))?!?: (.+)$""")

    override fun parseSemanticCommit(message: String): SemanticCommit {
        // The subject of a commit is its first line - the body never belongs to a change log
        val subject = message.lineSequence().firstOrNull() ?: ""
        val match = regex.find(subject) ?: return SemanticCommit.subjectOnly(subject)

        return SemanticCommit(
            type = match.groupValues[1],
            scope = match.groupValues[2].ifEmpty { null },
            subject = match.groupValues[3],
        )
    }
}