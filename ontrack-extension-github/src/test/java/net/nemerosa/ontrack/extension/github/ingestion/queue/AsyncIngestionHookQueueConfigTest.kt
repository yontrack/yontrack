package net.nemerosa.ontrack.extension.github.ingestion.queue

import net.nemerosa.ontrack.extension.github.ingestion.IngestionConfigProperties
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Owner
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Repository
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import kotlin.test.assertEquals

internal class AsyncIngestionHookQueueConfigTest {

    private lateinit var ingestionConfigProperties: IngestionConfigProperties

    @BeforeEach
    fun before() {
        ingestionConfigProperties = IngestionConfigProperties()
    }

    @Test
    fun `Computing the routing key for a default queue without scaling`() {
        val repository = repository()
        val key = AsyncIngestionHookQueueConfig.getRoutingKey(ingestionConfigProperties, repository)
        assertEquals("default", key)
    }

    @Test
    fun `Computing the routing key for a default queue with scaling`() {
        val repository = repository(name = "ontrack", login = "test")
        ingestionConfigProperties.processing.scale = 10
        val key = AsyncIngestionHookQueueConfig.getRoutingKey(ingestionConfigProperties, repository)
        assertEquals("default.9", key)
    }

    @Test
    fun `Computing the routing key for a specific queue without scaling`() {
        val repository = repository()
        ingestionConfigProperties.processing.repositories = mapOf(
            "specific" to IngestionConfigProperties.RepositoryQueueConfig(
                owner = repository.owner.login,
            )
        )
        val key = AsyncIngestionHookQueueConfig.getRoutingKey(ingestionConfigProperties, repository)
        assertEquals("specific", key)
    }

    @Test
    fun `Computing the routing key for a specific queue with scaling`() {
        val repository = repository()
        ingestionConfigProperties.processing.scale = 10
        ingestionConfigProperties.processing.repositories = mapOf(
            "specific" to IngestionConfigProperties.RepositoryQueueConfig(
                owner = repository.owner.login,
            )
        )
        val key = AsyncIngestionHookQueueConfig.getRoutingKey(ingestionConfigProperties, repository)
        assertEquals("specific", key)
    }

    @Test
    fun `Computing the routing key for a non matching specific queue without scaling`() {
        val repository = repository()
        ingestionConfigProperties.processing.repositories = mapOf(
            "specific" to IngestionConfigProperties.RepositoryQueueConfig(
                owner = "other",
            )
        )
        val key = AsyncIngestionHookQueueConfig.getRoutingKey(ingestionConfigProperties, repository)
        assertEquals("default", key)
    }

    @Test
    fun `Computing the routing key for a non matching specific queue with scaling`() {
        val repository = repository(name = "ontrack", login = "test")
        ingestionConfigProperties.processing.scale = 10
        ingestionConfigProperties.processing.repositories = mapOf(
            "specific" to IngestionConfigProperties.RepositoryQueueConfig(
                owner = "other",
            )
        )
        val key = AsyncIngestionHookQueueConfig.getRoutingKey(ingestionConfigProperties, repository)
        assertEquals("default.9", key)
    }

    @Test
    fun `Computing the routing key for a null repository without scaling`() {
        ingestionConfigProperties.processing.repositories = mapOf(
            "specific" to IngestionConfigProperties.RepositoryQueueConfig(
                owner = "other",
            )
        )
        val key = AsyncIngestionHookQueueConfig.getRoutingKey(ingestionConfigProperties, null)
        assertEquals("default", key)
    }

    @Test
    fun `Computing the routing key for a null repository with scaling`() {
        ingestionConfigProperties.processing.scale = 10
        ingestionConfigProperties.processing.repositories = mapOf(
            "specific" to IngestionConfigProperties.RepositoryQueueConfig(
                owner = "other",
            )
        )
        val key = AsyncIngestionHookQueueConfig.getRoutingKey(ingestionConfigProperties, null)
        assertEquals("default.0", key)
    }

    private fun declarables(): Declarables =
        AsyncIngestionHookQueueConfig(ingestionConfigProperties).gitHubIngestionTopicBindings()

    private fun Declarables.assertDeclared(vararg queuesByRoutingKey: Pair<String, String>) {
        assertEquals(
            listOf("github.ingestion.v2"),
            getDeclarablesByType(DirectExchange::class.java).map { it.name },
            "New exchange"
        )
        val queues = getDeclarablesByType(Queue::class.java)
        assertEquals(queuesByRoutingKey.map { it.second }, queues.map { it.name }, "New queues")
        queues.forEach { queue ->
            assertEquals(true, queue.isDurable, "Durable queue")
            assertEquals(true, queue.arguments["x-single-active-consumer"], "Single active consumer")
        }
        val bindings = getDeclarablesByType(Binding::class.java)
        assertEquals(queuesByRoutingKey.toList(), bindings.map { it.routingKey to it.destination }, "Unchanged routing keys")
        bindings.forEach { assertEquals("github.ingestion.v2", it.exchange) }
    }

    @Test
    fun `Declaring the default queue without scaling`() {
        declarables().assertDeclared(
            "default" to "github.ingestion.v2.default",
        )
    }

    @Test
    fun `Declaring the default queues with scaling`() {
        ingestionConfigProperties.processing.scale = 3
        declarables().assertDeclared(
            "default.0" to "github.ingestion.v2.default.0",
            "default.1" to "github.ingestion.v2.default.1",
            "default.2" to "github.ingestion.v2.default.2",
        )
    }

    @Test
    fun `Declaring the specific queues`() {
        ingestionConfigProperties.processing.repositories = mapOf(
            "specific" to IngestionConfigProperties.RepositoryQueueConfig(owner = "owner"),
        )
        declarables().assertDeclared(
            "specific" to "github.ingestion.v2.specific",
            "default" to "github.ingestion.v2.default",
        )
    }

    private fun repository(
        name: String = uid("r"),
        login: String = uid("u"),
    ) = Repository(
        name = name,
        description = null,
        owner = Owner(login = login),
        htmlUrl = "uri",
    )

}