package net.nemerosa.ontrack.build

import java.io.File

/**
 * The development key of the stacks which run the `dev` profile (#2107): without it, their
 * Yontrack starts with no licensed feature.
 *
 * Looked up, in this order, in the [VARIABLE] environment variable, then in the [FILE_PATH] file
 * of the home directory - the same order as `scripts/dev-stack.sh` - and passed to the Compose
 * files in [VARIABLE], which they hand to Yontrack as `ONTRACK_CONFIG_LICENSE_KEY`.
 *
 * A variable of its own, rather than `ONTRACK_CONFIG_LICENSE_KEY`, so that a development key in a
 * shell never replaces the evaluation key of `compose/docker-compose.yml`, which a production
 * instance would then refuse.
 *
 * Never written to an `instance.env`: the key is a secret, and those files are read by anything.
 */
object DevLicenseKey {

    const val VARIABLE = "YONTRACK_DEV_LICENSE_KEY"

    /** Relative to the home directory. */
    const val FILE_PATH = ".yontrack/dev-license.key"

    fun resolve(
        env: (String) -> String? = System::getenv,
        home: File = File(System.getProperty("user.home")),
    ): String? =
        env(VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: File(home, FILE_PATH).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    /** What to pass to `docker compose`: the key, or nothing when there is none. */
    fun composeEnvironment(
        env: (String) -> String? = System::getenv,
        home: File = File(System.getProperty("user.home")),
    ): Map<String, String> =
        resolve(env, home)?.let { mapOf(VARIABLE to it) } ?: emptyMap()
}
