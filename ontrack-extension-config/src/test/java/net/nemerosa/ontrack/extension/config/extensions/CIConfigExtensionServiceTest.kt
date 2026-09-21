package net.nemerosa.ontrack.extension.config.extensions

import tools.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.config.model.ExtensionConfiguration
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.merge
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CIConfigExtensionServiceTest {

    private lateinit var service: CIConfigExtensionService

    @BeforeEach
    fun before() {

        val av = mockCIConfigExtension(
            id = "autoVersioning"
        ) { a, b -> a.merge(b) }

        val notifications = mockCIConfigExtension(
            id = "notificationsConfig"
        ) { a, b ->
            val result = a.toMutableList()

            syncForward(
                from = b.toList(),
                to = a.toList(),
            ) {
                equality { i, j -> i.path("name").asText() == j.path("name").asText() }

                onDeletion { _ -> }

                onCreation { e -> result.add(e) }

                onModification { e, existing ->
                    result.removeIf { it.path("name").asText() == existing.path("name").asText() }
                    result.add(
                        mapOf(
                            "name" to existing.path("name"),
                            "channelConfig" to existing.path("channelConfig").merge(e.path("channelConfig")),
                        ).asJson()
                    )
                }
            }

            result.toList().asJson()
        }

        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.getExtensions(CIConfigExtension::class.java) } returns listOf(
            av, notifications
        )

        service = CIConfigExtensionService(
            extensionManager = extensionManager,
        )
    }

    private fun mockCIConfigExtension(
        id: String,
        merge: (a: JsonNode, b: JsonNode) -> JsonNode,
    ): CIConfigExtension<JsonNode> {
        val m = mockk<CIConfigExtension<JsonNode>>()

        every { m.id } returns id

        every { m.parseData(any()) } answers {
            firstArg()
        }

        every<JsonNode> { m.mergeConfig(any(), any()) } answers {
            merge(firstArg(), secondArg())
        }

        return m
    }

    @Test
    fun `Merging of extensions`() {
        val defaults = listOf(
            ExtensionConfiguration(
                "autoVersioning",
                mapOf("av" to 1).asJson(),
            ),
            ExtensionConfiguration(
                "notificationsConfig",
                listOf(
                    mapOf(
                        "name" to "Sub1",
                        "channelConfig" to mapOf(
                            "target" to "original"
                        )
                    ),
                    mapOf(
                        "name" to "Sub2",
                        "channelConfig" to mapOf(
                            "target" to "internal"
                        )
                    )
                ).asJson(),
            )
        )
        val custom = listOf(
            ExtensionConfiguration(
                "notificationsConfig",
                listOf(
                    mapOf(
                        "name" to "Sub2",
                        "channelConfig" to mapOf(
                            "target" to "release"
                        )
                    ),
                    mapOf(
                        "name" to "Sub3",
                        "channelConfig" to mapOf(
                            "target" to "three"
                        )
                    ),
                ).asJson()
            )
        )

        val extensions = service.merge(defaults, custom)

        assertEquals(
            listOf(
                ExtensionConfiguration(
                    "autoVersioning",
                    mapOf("av" to 1).asJson(),
                ),
                ExtensionConfiguration(
                    "notificationsConfig",
                    listOf(
                        mapOf(
                            "name" to "Sub1",
                            "channelConfig" to mapOf(
                                "target" to "original"
                            )
                        ),
                        mapOf(
                            "name" to "Sub2",
                            "channelConfig" to mapOf(
                                "target" to "release"
                            )
                        ),
                        mapOf(
                            "name" to "Sub3",
                            "channelConfig" to mapOf(
                                "target" to "three"
                            )
                        ),
                    ).asJson(),
                )
            ),
            extensions
        )
    }
}