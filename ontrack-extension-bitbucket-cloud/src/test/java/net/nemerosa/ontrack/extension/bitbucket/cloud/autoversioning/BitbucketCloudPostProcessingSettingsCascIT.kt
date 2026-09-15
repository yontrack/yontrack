package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

internal class BitbucketCloudPostProcessingSettingsCascIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var bitbucketCloudPostProcessingSettingsCasc: BitbucketCloudPostProcessingSettingsCasc

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @Test
    fun `CasC schema type`() {
        val type = bitbucketCloudPostProcessingSettingsCasc.jsonType(jsonTypeBuilder)
        assertEquals(
            """
                {
                  "title": "BitbucketCloudPostProcessingSettings",
                  "properties": {
                    "branch": {
                      "description": "Branch to run the pipeline on",
                      "type": "string"
                    },
                    "config": {
                      "description": "Default Bitbucket Cloud configuration to use for the connection",
                      "type": "string"
                    },
                    "pipeline": {
                      "description": "Name of the custom pipeline containing the post-processing (like `yontrack-auto-versioning`)",
                      "type": "string"
                    },
                    "repository": {
                      "description": "Default repository containing the pipeline",
                      "type": "string"
                    },
                    "retries": {
                      "description": "The amount of times we check for the completion of the post-processing pipeline",
                      "type": "integer"
                    },
                    "retriesDelaySeconds": {
                      "description": "The time (in seconds) between two checks for the completion of the post-processing pipeline, never less than 10 seconds",
                      "type": "integer"
                    },
                    "workspace": {
                      "description": "Default workspace of the repository containing the pipeline",
                      "type": "string"
                    }
                  },
                  "required": [],
                  "additionalProperties": false,
                  "type": "object"
                }
            """.trimIndent().parseAsJson(),
            type.asJson()
        )
    }

    @Test
    fun `Setting the settings through CasC`() {
        asAdmin {
            withCleanSettings<BitbucketCloudPostProcessingSettings> {
                casc(
                    """
                    ontrack:
                        config:
                            settings:
                                bitbucket-cloud-av-post-processing:
                                    config: my-config
                                    workspace: my-ws
                                    repository: my-repo
                                    pipeline: yontrack-auto-versioning
                                    branch: develop
                                    retries: 30
                                    retriesDelaySeconds: 10
                """.trimIndent()
                )
                val saved = cachedSettingsService.getCachedSettings(BitbucketCloudPostProcessingSettings::class.java)
                assertEquals("my-config", saved.config)
                assertEquals("my-ws", saved.workspace)
                assertEquals("my-repo", saved.repository)
                assertEquals("yontrack-auto-versioning", saved.pipeline)
                assertEquals("develop", saved.branch)
                assertEquals(30, saved.retries)
                assertEquals(10, saved.retriesDelaySeconds)
            }
        }
    }

}
