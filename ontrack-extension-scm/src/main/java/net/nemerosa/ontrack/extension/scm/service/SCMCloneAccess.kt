package net.nemerosa.ontrack.extension.scm.service

/**
 * What a process outside Yontrack needs to clone the repository of an [SCM] with Git over HTTP(S),
 * and to push to it.
 *
 * @property url Clone URL of the repository, **without** any credentials in it
 * @property credentials Credentials to use, `null` for an anonymous access
 */
data class SCMCloneAccess(
    val url: String,
    val credentials: SCMCloneCredentials?,
)

/**
 * Credentials of a Git HTTP(S) access, in the only shape Git knows: a username and a password. A
 * token is a password under the username its SCM expects.
 *
 * The password may be short-lived (a GitHub App installation token lasts one hour): it is read
 * when [SCM.getCloneAccess] is called, and must be used right away.
 */
class SCMCloneCredentials(
    val username: String,
    val password: String,
) {
    /** Never prints the password. */
    override fun toString(): String = "SCMCloneCredentials(username=$username, password=****)"
}
