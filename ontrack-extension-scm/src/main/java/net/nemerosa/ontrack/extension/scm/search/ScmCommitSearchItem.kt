package net.nemerosa.ontrack.extension.scm.search

import net.nemerosa.ontrack.common.asMap
import net.nemerosa.ontrack.model.structure.SearchItem

data class ScmCommitSearchItem(
    val projectName: String,
    override val id: String,
    val shortId: String,
    val author: String,
    val message: String,
) : SearchItem {

    /**
     * A commit ID is only unique within its repository, and the same repository - or a fork, or
     * a mirror - can be registered in several projects. The indexed [id] field stays the bare
     * commit ID, which is what is searched for and what the result links to.
     */
    override val documentId: String get() = "$projectName::$id"

    override val fields: Map<String, Any?> = asMap(
        this::projectName,
        this::id,
        this::shortId,
        this::author,
        this::message,
    )

}
