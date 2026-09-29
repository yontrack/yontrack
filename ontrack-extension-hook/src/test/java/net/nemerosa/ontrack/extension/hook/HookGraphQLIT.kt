package net.nemerosa.ontrack.extension.hook

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.test.assertJsonNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class HookGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var hookTestSupport: HookTestSupport

    @Test
    fun `Accessing the hook records using GraphQL`() {
        asAdmin {
            // Calling the test hook
            hookTestSupport.testHook(
                body = "Testing GraphQL",
                parameters = mapOf(
                    "test" to "xxx",
                )
            )
            // Getting the last record for this hook
            asAdmin {
                run(
                    """
                    {
                        hookRecordings(filter: {hook: "test"}, size: 1) {
                            pageItems {
                                id
                                hook
                                request {
                                    body
                                    parameters {
                                        name
                                        value
                                    }
                                }
                                startTime
                                state
                                message
                                exception
                                endTime
                                response {
                                    type
                                }
                            }
                        }
                    }
                """
                ) { data ->
                    val record = data.path("hookRecordings").path("pageItems").firstOrNull()
                    assertNotNull(record, "At least one record") {
                        assertEquals("test", it.getRequiredTextField("hook"))
                        it.path("request").let { request ->
                            assertEquals("\"Testing GraphQL\"", request.getRequiredTextField("body"))
                            request.path("parameters").let { parameters ->
                                assertEquals(1, parameters.size())
                                val parameter = parameters.path(0)
                                assertEquals("test", parameter.getRequiredTextField("name"))
                                assertEquals("xxx", parameter.getRequiredTextField("value"))
                            }
                        }
                        assertEquals("SUCCESS", it.getRequiredTextField("state"))
                        assertEquals("PROCESSED", it.path("response").path("type").asText())
                    }
                }
            }
        }
    }

    @Test
    fun `Accessing the hook records info links using GraphQL`() {
        asAdmin {
            // Calling the test hook
            hookTestSupport.testHook(
                body = "Testing GraphQL",
                parameters = mapOf(
                    "test" to "xxx",
                )
            )
            // Getting the last record for this hook
            asAdmin {
                run(
                    """
                    {
                        hookRecordings(filter: {hook: "test"}, size: 1) {
                            pageItems {
                                id
                                hook
                                request {
                                    body
                                    parameters {
                                        name
                                        value
                                    }
                                }
                                startTime
                                state
                                message
                                exception
                                endTime
                                response {
                                    type
                                    infoLink {
                                        feature
                                        id
                                        data
                                    }
                                }
                            }
                        }
                    }
                """
                ) { data ->
                    val record = data.path("hookRecordings").path("pageItems").firstOrNull()
                    assertNotNull(record, "At least one record") {
                        assertEquals("test", it.getRequiredTextField("hook"))
                        val infoLink = it.path("response").path("infoLink")
                        assertJsonNotNull(infoLink, "Info link is present") {
                            assertEquals("test", getRequiredTextField("feature"))
                            assertEquals("test", getRequiredTextField("id"))
                            assertEquals("""Processing: "Testing GraphQL"""", getRequiredTextField("data"))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Filtering the hook records using GraphQL`() {
        asAdmin {
            hookTestSupport.clearRecords()
            // Calling the test hook twice
            hookTestSupport.testHook(
                body = "Testing GraphQL",
                parameters = mapOf(
                    "test" to "xxx",
                )
            )
            assertFailsWith<RuntimeException> {
                hookTestSupport.testHook(
                    body = "Testing GraphQL with an error",
                    error = true,
                    parameters = mapOf(
                        "test" to "xxx",
                    )
                )
            }
            // Getting the last record for this hook
            asAdmin {
                run(
                    """
                    {
                        hookRecordings(filter: {hook: "test", state: ERROR}, size: 2) {
                            pageItems {
                                request {
                                    body
                                }
                                state
                            }
                        }
                    }
                """
                ) { data ->
                    val records = data.path("hookRecordings").path("pageItems")
                    assertEquals(1, records.size(), "Only one record returned")
                    val record = records.first()
                    assertEquals(
                        "\"Testing GraphQL with an error\"",
                        record.path("request").getRequiredTextField("body")
                    )
                    assertEquals("ERROR", record.getRequiredTextField("state"))
                }
            }
        }
    }
}
