package net.nemerosa.ontrack.extension.gitlab.autoversioning

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

internal class GitLabPostProcessingSettingsCascIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var gitLabPostProcessingSettingsCasc: GitLabPostProcessingSettingsCasc

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @Test
    fun `CasC schema type`() {
        val type = gitLabPostProcessingSettingsCasc.jsonType(jsonTypeBuilder)
        assertEquals(
            """
                {
                  "title": "GitLabPostProcessingSettings",
                  "properties": {
                    "config": {
                      "description": "Default GitLab configuration to use for the connection",
                      "type": "string"
                    },
                    "project": {
                      "description": "Default full path of the GitLab project containing the pipeline, like `group/subgroup/project`",
                      "type": "string"
                    },
                    "ref": {
                      "description": "Branch or tag to run the pipeline on",
                      "type": "string"
                    },
                    "retries": {
                      "description": "The amount of times we check for the completion of the post-processing pipeline",
                      "type": "integer"
                    },
                    "retriesDelaySeconds": {
                      "description": "The time (in seconds) between two checks for the completion of the post-processing pipeline, never less than 10 seconds",
                      "type": "integer"
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
            withCleanSettings<GitLabPostProcessingSettings> {
                casc(
                    """
                    ontrack:
                        config:
                            settings:
                                gitlab-av-post-processing:
                                    config: my-config
                                    project: my-group/my-project
                                    ref: develop
                                    retries: 30
                                    retriesDelaySeconds: 10
                """.trimIndent()
                )
                val saved = cachedSettingsService.getCachedSettings(GitLabPostProcessingSettings::class.java)
                assertEquals("my-config", saved.config)
                assertEquals("my-group/my-project", saved.project)
                assertEquals("develop", saved.ref)
                assertEquals(30, saved.retries)
                assertEquals(10, saved.retriesDelaySeconds)
            }
        }
    }

    @Test
    fun `Default settings`() {
        asAdmin {
            withCleanSettings<GitLabPostProcessingSettings> {
                val settings = cachedSettingsService.getCachedSettings(GitLabPostProcessingSettings::class.java)
                assertEquals("", settings.config)
                assertEquals("", settings.project)
                assertEquals(GitLabPostProcessingSettings.DEFAULT_REF, settings.ref)
                assertEquals(GitLabPostProcessingSettings.DEFAULT_RETRIES, settings.retries)
                assertEquals(
                    GitLabPostProcessingSettings.DEFAULT_RETRIES_DELAY_SECONDS,
                    settings.retriesDelaySeconds
                )
            }
        }
    }

}
