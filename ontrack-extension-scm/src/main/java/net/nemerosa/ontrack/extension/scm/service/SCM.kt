package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project

/**
 * High-level interaction with the remote repository of a project.
 *
 * Any instance is specific to the project it was created for.
 */
interface SCM {

    /**
     * Type of SCM (git, etc.)
     */
    val type: String

    /**
     * Flavor/engine of the SCM (github, etc.)
     */
    val engine: String

    /**
     * Gets the (clone) URL of the project's repository
     */
    val repositoryURI: String

    /**
     * Gets the URL of the project's repository page
     */
    val repositoryHtmlURL: String

    /**
     * Gets the (short) path to the project's repository
     */
    val repository: String

    /**
     * Given a branch, returns its SCM branch if any.
     *
     * Throws an error if the branch does not belong to the SCM's project.
     */
    fun getSCMBranch(branch: Branch): String?

    /**
     * Deletes, if it exists, the given branch.
     *
     * @param branch Branch to delete
     */
    fun deleteBranch(branch: String)

    /**
     * Checking if a branch exists by returning its last commit or null if not existing
     *
     * @param branch Branch to check
     * @return Commit hash if the branch exists, null otherwise.
     */
    fun getBranchLastCommit(branch: String): String?

    /**
     * Creates a new branch in a project.
     *
     * @param sourceBranch Source branch
     * @param newBranch New branch
     * @return Commit of the new branch
     */
    fun createBranch(sourceBranch: String, newBranch: String): String

    /**
     * Downloads the content of a file.
     *
     * @param scmBranch Branch to download the file from (empty or null to use the default branch)
     * @param path Path to the file
     * @param retryOnNotFound If the file is not found, should we retry until a timeout is reached?
     * @return Binary content of the file or null if non existing
     */
    fun download(scmBranch: String?, path: String, retryOnNotFound: Boolean = false): ByteArray?

    /**
     * Uploads the content of a file to a branch.
     *
     * @param scmBranch Branch to upload the file to
     * @param commit Commit of the branch or empty for a new file
     * @param path Path to the file
     * @param content Binary content of the file
     * @param message Commit message
     */
    fun upload(scmBranch: String, commit: String, path: String, content: ByteArray, message: String)

    /**
     * Creates a pull request from the branch [from] to the branch [to], with a given [title] and
     * [description]. If [autoApproval] is `true`, the pull request will have a reviewer approving
     * it immediately.
     *
     * @param from Origin branch
     * @param to Target branch
     * @param title Title for the pull request
     * @param description Description (body) for the pull request
     * @param autoApproval Must the created pull request be auto-approved?
     * @param remoteAutoMerge If the SCM allows this, sets the PR in auto merge mode
     * @param message Commit message to set on auto merge
     * @param reviewers List of reviewers to set on the pull request (each entry is the ID of a user as supported by the remote SCM)
     * @return PR information
     */
    fun createPR(
        from: String,
        to: String,
        title: String,
        description: String,
        autoApproval: Boolean,
        remoteAutoMerge: Boolean,
        message: String,
        reviewers: List<String>,
    ): SCMPullRequest

    /**
     * Given the name of PR (#1234, PR-1234, etc. depending on the SCM), returns the corresponding
     * PR information or null if not found.
     */
    fun getPullRequestByName(prName: String): SCMPullRequest?

    /**
     * Gets a link to see the diff between two commits.
     */
    fun getDiffLink(commitFrom: String, commitTo: String): String?

    /**
     * Given the name of a branch in the SCM, returns the corresponding
     * branch in Yontrack.
     *
     * @param project The project the branch belongs to
     * @param scmBranch Name of the branch (simple name, like `main` and not `refs/heads/main`)
     * @return Yontrack branch if one has been found
     */
    fun findBranchFromScmBranchName(project: Project, scmBranch: String): Branch?

    /**
     * Gets the list of all branches which contain the given commit.
     *
     * @param project The project the commit belongs to
     * @param commit The commit SHA
     * @return List of branch names (simple names, like `main` and not `refs/heads/main`)
     */
    fun getBranchesForCommit(project: Project, commit: String): List<String>

    /**
     * Merges the branch [head] into the branch [base].
     *
     * @param head Branch to merge from (source, e.g. the auto-versioning upgrade branch)
     * @param base Branch to merge into (target, e.g. `main`)
     * @return The commit resulting from the merge
     */
    fun mergeBranch(
        head: String,
        base: String,
    ): SCMCommit

}