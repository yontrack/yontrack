package net.nemerosa.ontrack.extension.scm.changelog

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.structure.Build

@APIDescription("Representation of a change log between two builds")
class SCMChangeLog(
    @APIDescription("Boundary for the change log")
    val from: Build,
    @APIDescription("Boundary for the change log")
    val to: Build,
    @APIDescription("Boundary commit for the change log")
    val fromCommit: String,
    @APIDescription("Boundary commit for the change log")
    val toCommit: String,
    @APIDescription("List of commits between the builds")
    val commits: List<SCMDecoratedCommit>,
    /**
     * Getting the issues of a change log usually means calling a remote issue service, which
     * can be slow. They are therefore resolved only when they are actually needed, and only once.
     */
    issuesProvider: () -> SCMChangeLogIssues?,
) {

    constructor(
        from: Build,
        to: Build,
        fromCommit: String,
        toCommit: String,
        commits: List<SCMDecoratedCommit>,
        issues: SCMChangeLogIssues?,
    ) : this(from, to, fromCommit, toCommit, commits, { issues })

    @APIDescription("List of issues between the builds")
    val issues: SCMChangeLogIssues? by lazy(issuesProvider)

    fun isEmpty() = fromCommit == toCommit
}
