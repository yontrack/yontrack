package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.ListRef

/**
 * Configuration of the `kubernetes` post-processing, as set in an auto-versioning configuration.
 *
 * It holds no secret value: [secrets] are names of Kubernetes Secrets only.
 */
data class KubernetesPostProcessingConfig(
    @APIDescription("Image running the post-processing command. It must be allowed by the Yontrack configuration, have a shell and run as a non-root user.")
    val dockerImage: String,
    @APIDescription("Command run through `sh -c` at the root of the working copy. No `git` command works in it.")
    val dockerCommand: String,
    @APIDescription("Commit message used to commit and push the result of the post-processing (optional)")
    val commitMessage: String? = null,
    @APIDescription("Environment variables of the command; their values are templates (optional, none by default)")
    @ListRef
    val env: List<KubernetesPostProcessingEnv> = emptyList(),
    @APIDescription("Keys of Kubernetes Secrets exposed to the command as environment variables. The Secrets must be allowed by the Yontrack configuration (optional, none by default)")
    @ListRef
    val secrets: List<KubernetesPostProcessingSecret> = emptyList(),
    @APIDescription("Time given to the Job, in seconds, capped by the Yontrack configuration (optional)")
    val timeoutSeconds: Int? = null,
)

data class KubernetesPostProcessingEnv(
    @APIDescription("Name of the environment variable")
    val name: String,
    @APIDescription("Value of the environment variable, as a template")
    val value: String,
)

data class KubernetesPostProcessingSecret(
    @APIDescription("Name of the Kubernetes Secret")
    val secret: String,
    @APIDescription("Key in the Secret")
    val key: String,
    @APIDescription("Name of the environment variable the value is exposed as")
    val env: String,
)
