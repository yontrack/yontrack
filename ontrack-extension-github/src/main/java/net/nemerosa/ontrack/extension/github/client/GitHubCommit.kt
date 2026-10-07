package net.nemerosa.ontrack.extension.github.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.nemerosa.ontrack.extension.github.model.GitHubUser

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCommit(
    val sha: String,
    @JsonProperty("html_url")
    val url: String,
    val commit: GitHubCommitInfo,
    val parents: List<GitHubCommitParentRef>?,
    /**
     * GitHub account of the author, `null` when GitHub does not link the commit to an account
     */
    val author: GitHubUser? = null,
    /**
     * GitHub account of the committer, `null` when GitHub does not link the commit to an account
     */
    val committer: GitHubUser? = null,
)
