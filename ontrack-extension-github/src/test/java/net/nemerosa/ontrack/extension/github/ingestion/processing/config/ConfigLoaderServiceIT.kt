package net.nemerosa.ontrack.extension.github.ingestion.processing.config

import net.nemerosa.ontrack.extension.git.WithGitPullRequestEnabled
import net.nemerosa.ontrack.extension.github.TestOnGitHub
import net.nemerosa.ontrack.extension.github.githubTestEnv
import net.nemerosa.ontrack.extension.github.ingestion.AbstractIngestionTestSupport
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfigVSNameNormalization
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfigWorkflows
import net.nemerosa.ontrack.extension.github.ingestion.config.model.support.FilterConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Reads the `.github/ontrack/ingestion.yml` file of the `v1` and `v1-test` branches of the GitHub
 * test repository, which must be in the `v2` format, with `vs-name-normalization: LEGACY` (#1928).
 */
@TestOnGitHub
@WithGitPullRequestEnabled
class ConfigLoaderServiceIT : AbstractIngestionTestSupport() {

    @Autowired
    private lateinit var configLoaderService: ConfigLoaderService

    @Test
    fun `Getting the ingestion configuration from a real project`() {
        project {
            gitHubRealConfig()
            branch {
                gitRealConfig()
                val config = configLoaderService.loadConfig(this, INGESTION_CONFIG_FILE_PATH)
                assertNotNull(config, "Ingestion configuration was loaded") {
                    assertEquals(
                        IngestionConfig(
                            version = "v2",
                            workflows = IngestionConfigWorkflows(
                                filter = FilterConfig(includes = "build")
                            ),
                            vsNameNormalization = IngestionConfigVSNameNormalization.LEGACY,
                        ),
                        it
                    )
                }
            }
        }
    }

    @Test
    fun `Getting the ingestion configuration for a pull request`() {
        project {
            gitHubRealConfig()
            val prName = "PR-${githubTestEnv.pr}"
            branch(name = prName) {
                gitRealConfig(branch = prName)
                val config = configLoaderService.loadConfig(this, INGESTION_CONFIG_FILE_PATH)
                assertNotNull(config, "Ingestion configuration was loaded") {
                    assertEquals(
                        IngestionConfig(
                            version = "v2",
                            workflows = IngestionConfigWorkflows(
                                filter = FilterConfig(includes = "build")
                            ),
                            vsNameNormalization = IngestionConfigVSNameNormalization.LEGACY,
                        ),
                        it
                    )
                }
            }
        }
    }
}