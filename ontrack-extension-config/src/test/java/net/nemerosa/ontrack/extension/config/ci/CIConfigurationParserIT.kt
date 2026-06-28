package net.nemerosa.ontrack.extension.config.ci

import com.fasterxml.jackson.databind.node.TextNode
import net.nemerosa.ontrack.extension.config.model.*
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.PromotionLevelConfiguration
import net.nemerosa.ontrack.model.structure.PromotionLevelField
import net.nemerosa.ontrack.model.structure.PromotionLevelFieldType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class CIConfigurationParserIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var parser: CIConfigurationParser

    @Test
    fun `Parsing of the default configuration`() {
        val config = parser.parseConfig(
            """
                version: v1
                configuration: {}
            """.trimIndent()
        )
        assertEquals(
            ConfigurationInput(),
            config
        )
    }

    @Test
    fun `Parsing of configuration with properties and other attributes`() {
        val config = parser.parseConfig(
            """
                version: v1
                configuration:
                  defaults:
                    project:
                      properties:
                        useLabel: true
                        net.nemerosa.ontrack.extension.general.MessagePropertyType:
                            type: INFO
                            text: "This is a message"
                    branch:
                      validations:
                        KDSL.ACCEPTANCE:
                          tests:
                            warningIfSkipped: true
                      promotions:
                        BRONZE:
                          validations:
                            - BUILD
                            - UI_UNIT
                            - KDSL.ACCEPTANCE
                            - PLAYWRIGHT
                        RELEASE:
                          promotions:
                            - BRONZE
                          validations:
                            - GITHUB.RELEASE
                    build:
                      properties:
                        gitCommit: ${'$'}{env.GIT_COMMIT}
                        release: ${'$'}{env.VERSION}
            """.trimIndent()
        )
        assertEquals(
            ConfigurationInput(
                configuration = RootConfiguration(
                    defaults = Configuration(
                        project = ProjectConfiguration(
                            properties = listOf(
                                PropertyConfiguration(
                                    type = "net.nemerosa.ontrack.extension.general.BuildLinkDisplayPropertyType",
                                    data = mapOf(
                                        "useLabel" to true
                                    ).asJson()
                                ),
                                PropertyConfiguration(
                                    type = "net.nemerosa.ontrack.extension.general.MessagePropertyType",
                                    data = mapOf(
                                        "type" to "INFO",
                                        "text" to "This is a message",
                                    ).asJson()
                                ),
                            )
                        ),
                        branch = BranchConfiguration(
                            validations = listOf(
                                ValidationStampConfiguration(
                                    name = "KDSL.ACCEPTANCE",
                                    description = "",
                                    validationStampDataConfiguration = ValidationStampDataConfiguration(
                                        type = "net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType",
                                        data = mapOf(
                                            "warningIfSkipped" to true,
                                            "failWhenNoResults" to false,
                                        ).asJson()
                                    )
                                )
                            ),
                            promotions = listOf(
                                PromotionLevelConfiguration(
                                    name = "BRONZE",
                                    description = "",
                                    validations = listOf(
                                        "BUILD",
                                        "UI_UNIT",
                                        "KDSL.ACCEPTANCE",
                                        "PLAYWRIGHT",
                                    ),
                                    promotions = emptyList(),
                                ),
                                PromotionLevelConfiguration(
                                    name = "RELEASE",
                                    description = "",
                                    validations = listOf(
                                        "GITHUB.RELEASE",
                                    ),
                                    promotions = listOf(
                                        "BRONZE",
                                    ),
                                ),
                            )
                        ),
                        build = BuildConfiguration(
                            properties = listOf(
                                PropertyConfiguration(
                                    type = "net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType",
                                    data = mapOf(
                                        "commit" to "${'$'}{env.GIT_COMMIT}"
                                    ).asJson()
                                ),
                                PropertyConfiguration(
                                    type = "net.nemerosa.ontrack.extension.general.ReleasePropertyType",
                                    data = mapOf(
                                        "name" to "${'$'}{env.VERSION}"
                                    ).asJson()
                                ),
                            )
                        )
                    )
                )
            ),
            config
        )
    }

    @Test
    fun `Parsing of promotion with dependsOn`() {
        val config = parser.parseConfig(
            """
                version: v1
                configuration:
                  defaults:
                    branch:
                      promotions:
                        GOLD: {}
                        target-environment:
                          dependsOn:
                            - GOLD
            """.trimIndent()
        )
        assertEquals(
            ConfigurationInput(
                configuration = RootConfiguration(
                    defaults = Configuration(
                        branch = BranchConfiguration(
                            promotions = listOf(
                                PromotionLevelConfiguration(name = "GOLD"),
                                PromotionLevelConfiguration(
                                    name = "target-environment",
                                    dependencies = listOf("GOLD"),
                                ),
                            )
                        )
                    )
                )
            ),
            config
        )
    }

    @Test
    fun `Parsing of promotion with fields`() {
        val config = parser.parseConfig(
            """
                version: v1
                configuration:
                  defaults:
                    branch:
                      promotions:
                        BRONZE:
                          fields:
                            - name: ticket
                              displayName: Ticket
                              description: "JIRA ticket reference"
                              type: TEXT
                              required: true
                            - name: env
                              displayName: Environment
                              type: CHOICE
                              options:
                                - staging
                                - prod
                            - name: hotfix
                              displayName: Hotfix?
                              type: BOOLEAN
            """.trimIndent()
        )
        assertEquals(
            ConfigurationInput(
                configuration = RootConfiguration(
                    defaults = Configuration(
                        branch = BranchConfiguration(
                            promotions = listOf(
                                PromotionLevelConfiguration(
                                    name = "BRONZE",
                                    fields = listOf(
                                        PromotionLevelField(
                                            id = 0,
                                            name = "ticket",
                                            displayName = "Ticket",
                                            description = "JIRA ticket reference",
                                            type = PromotionLevelFieldType.TEXT,
                                            required = true,
                                            options = emptyList(),
                                            position = 0,
                                        ),
                                        PromotionLevelField(
                                            id = 0,
                                            name = "env",
                                            displayName = "Environment",
                                            description = null,
                                            type = PromotionLevelFieldType.CHOICE,
                                            required = false,
                                            options = listOf("staging", "prod"),
                                            position = 1,
                                        ),
                                        PromotionLevelField(
                                            id = 0,
                                            name = "hotfix",
                                            displayName = "Hotfix?",
                                            description = null,
                                            type = PromotionLevelFieldType.BOOLEAN,
                                            required = false,
                                            options = emptyList(),
                                            position = 2,
                                        ),
                                    ),
                                ),
                            )
                        )
                    )
                )
            ),
            config
        )
    }

    @Test
    fun `Parsing of configuration with custom conditions`() {
        val config = parser.parseConfig(
            """
                version: v1
                configuration:
                  defaults:
                    branch:
                      validations:
                        acceptance:
                          tests:
                            warningIfSkipped: true
                  custom:
                    configs:
                      - conditions:
                          - name: branch
                            config: "release.*"
                        branch:
                          validations:
                            deployment: {}
            """.trimIndent()
        )
        assertEquals(
            ConfigurationInput(
                configuration = RootConfiguration(
                    defaults = Configuration(
                        branch = BranchConfiguration(
                            validations = listOf(
                                ValidationStampConfiguration(
                                    name = "acceptance",
                                    description = "",
                                    validationStampDataConfiguration = ValidationStampDataConfiguration(
                                        type = "net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType",
                                        data = mapOf(
                                            "warningIfSkipped" to true,
                                            "failWhenNoResults" to false,
                                        ).asJson()
                                    )
                                )
                            ),
                        ),
                    ),
                    custom = Custom(
                        configs = listOf(
                            CustomConfig(
                                conditions = listOf(
                                    ConditionConfig(
                                        name = "branch",
                                        config = TextNode("release.*")
                                    )
                                ),
                                branch = BranchConfiguration(
                                    validations = listOf(
                                        ValidationStampConfiguration(
                                            name = "deployment",
                                            description = "",
                                            validationStampDataConfiguration = null,
                                        )
                                    ),
                                ),
                            )
                        )
                    )
                )
            ),
            config
        )
    }

}