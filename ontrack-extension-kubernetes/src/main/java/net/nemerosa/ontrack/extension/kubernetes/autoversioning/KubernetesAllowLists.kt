package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import net.nemerosa.ontrack.extension.kubernetes.KubernetesConfigProperties

/**
 * Allow-lists of the images and Secrets a post-processing may use.
 *
 * An empty list denies everything (decision 13 of #2171): an administrator opts in explicitly.
 */
object KubernetesAllowLists {

    /**
     * Checks an image against the patterns of [KubernetesConfigProperties.ALLOWED_IMAGES].
     *
     * @param image Rendered name of the image
     * @param patterns Allowed patterns, see [imageMatches]
     * @throws KubernetesPostProcessingConfigException When the image matches none of the patterns
     */
    fun checkImage(image: String, patterns: List<String>) {
        if (patterns.isEmpty()) {
            throw KubernetesPostProcessingConfigException(
                "Image `$image` is not allowed: no image is allowed for the Kubernetes post-processing. " +
                        "Set `${KubernetesConfigProperties.ALLOWED_IMAGES}` to the patterns of the allowed images."
            )
        } else if (patterns.none { imageMatches(image, it) }) {
            throw KubernetesPostProcessingConfigException(
                "Image `$image` is not allowed: it matches none of the patterns of " +
                        "`${KubernetesConfigProperties.ALLOWED_IMAGES}`."
            )
        }
    }

    /**
     * Checks a Secret name against [KubernetesConfigProperties.ALLOWED_SECRETS].
     *
     * @param secret Name of the Kubernetes Secret
     * @param allowed Allowed names, matched exactly
     * @throws KubernetesPostProcessingConfigException When the Secret is not in the list
     */
    fun checkSecret(secret: String, allowed: List<String>) {
        if (allowed.isEmpty()) {
            throw KubernetesPostProcessingConfigException(
                "Secret `$secret` is not allowed: no Secret is allowed for the Kubernetes post-processing. " +
                        "Set `${KubernetesConfigProperties.ALLOWED_SECRETS}` to the names of the allowed Secrets."
            )
        } else if (secret !in allowed) {
            throw KubernetesPostProcessingConfigException(
                "Secret `$secret` is not allowed: it is not listed in `${KubernetesConfigProperties.ALLOWED_SECRETS}`."
            )
        }
    }

    /**
     * Matches an image against a pattern: `*` stands for any sequence of characters (slashes
     * included), every other character is literal, and the whole image name must match.
     */
    fun imageMatches(image: String, pattern: String): Boolean =
        pattern.split('*')
            .joinToString(".*") { if (it.isEmpty()) "" else Regex.escape(it) }
            .toRegex()
            .matches(image)

}
