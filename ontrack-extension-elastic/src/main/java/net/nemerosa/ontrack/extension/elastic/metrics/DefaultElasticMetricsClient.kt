package net.nemerosa.ontrack.extension.elastic.metrics

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client
import com.fasterxml.jackson.databind.node.ObjectNode
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.toObject
import net.nemerosa.ontrack.model.metrics.increment
import net.nemerosa.ontrack.model.structure.SearchNodeResults
import net.nemerosa.ontrack.model.structure.SearchResultNode
import net.nemerosa.ontrack.model.support.time
import org.apache.hc.client5.http.auth.AuthScope
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@DelicateCoroutinesApi
@Component
@ConditionalOnProperty(
        prefix = ElasticMetricsConfigProperties.ELASTIC_METRICS_PREFIX,
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
)
class DefaultElasticMetricsClient(
        private val elasticMetricsConfigProperties: ElasticMetricsConfigProperties,
        private val defaultLowLevelClient: Rest5Client,
) : ElasticMetricsClient, MeterBinder {

    private val logger: Logger = LoggerFactory.getLogger(DefaultElasticMetricsClient::class.java)

    private var registry: MeterRegistry? = null

    private fun debug(message: String) {
        if (elasticMetricsConfigProperties.debug && logger.isDebugEnabled) {
            logger.debug(message)
        }
    }

    /**
     * Internal in-memory queue
     */
    private val queue = Channel<ECSEntry>(capacity = elasticMetricsConfigProperties.queue.capacity.toInt())

    /**
     * Channels do not have a measurable size, so keeping our own count.
     */
    private val queueSize = AtomicLong()

    /**
     * Buffer of entries
     */
    private val buffer = ConcurrentLinkedQueue<ECSEntry>()

    /**
     * Buffer lock
     */
    private val bufferLock = ReentrantLock()

    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry
        // Size of the queue
        registry.gauge(ElasticMetricsClientMetrics.queue, this) {
            queueSize.get().toDouble()
        }
        // Size of the buffer
        registry.gauge(ElasticMetricsClientMetrics.buffer, this) {
            buffer.size.toDouble()
        }
    }

    override fun saveMetrics(entries: Collection<ECSEntry>) {
        try {
            if (elasticMetricsConfigProperties.index.immediate) {
                // Direct registration
                val indexName = elasticMetricsConfigProperties.index.name
                entries.forEach { entry ->
                    val source = entry.asJson().toObject()
                    client.index {
                        it.index(indexName)
                                .id(entry.computeId())
                                .document(source)
                    }
                }
                // ... and flushing immediately
                client.indices().refresh {
                    it.index(indexName)
                }
            } else {
                runBlocking {
                    launch {
                        entries.forEach { entry ->
                            queueSize.incrementAndGet()
                            queue.send(entry)
                        }
                    }
                }
            }
        } catch (ex: Throwable) {
            logger.error("Cannot queue ${entries.size} metrics for export to ElasticSearch", ex)
        }
    }

    override fun saveMetric(entry: ECSEntry) {
        saveMetrics(listOf(entry))
    }

    init {
        /**
         * Launching the queue processing at startup
         */
        GlobalScope.launch {
            receiveEvents()
        }
        /**
         * Launching the queue regular purging
         */
        GlobalScope.launch {
            flushEvents()
        }
    }

    /**
     * Processing of the events
     */
    private suspend fun receiveEvents() {
        while (true) {
            try {
                val entry = queue.receive()
                queueSize.decrementAndGet()
                debug("Entry received: $entry")
                runBlocking {
                    launch(Job()) {
                        processEvent(entry)
                    }
                }
            } catch (any: Exception) {
                logger.error("Error on dequeuing an ECS entry", any)
            }
        }
    }

    private fun processEvent(entry: ECSEntry) {
        buffer.add(entry)
        debug("Entry buffered (${buffer.size}/${elasticMetricsConfigProperties.queue.buffer}) $entry")
        if (buffer.size >= elasticMetricsConfigProperties.queue.buffer.toInt()) {
            // Flushing the buffer
            debug("Buffer flushing")
            flushing()
        }
    }

    /**
     * Flushing the events regularly
     */
    private suspend fun flushEvents() {
        runBlocking {
            while (true) {
                try {
                    // Wait between each flushing session
                    delay(elasticMetricsConfigProperties.queue.flushing.toMillis())
                    // Flushing
                    debug("Timed flushing")
                    flushing()
                } catch (any: Exception) {
                    logger.error("Error on timed flushing", any)
                }
            }
        }
    }

    private fun flushing() {
        debug("Trying to get lock on buffer")
        bufferLock.withLock { }
        if (bufferLock.tryLock(1, TimeUnit.MINUTES)) {
            debug("Locked on the buffer")
            try {
                if (buffer.isNotEmpty()) {
                    debug("Flushing all entries (${buffer.size})")
                    // Copy of the elements
                    val entries = buffer.toTypedArray()
                    // Flushing the buffer
                    buffer.clear()
                    // Creating the bulk request
                    val indexName = elasticMetricsConfigProperties.index.name
                    val br = BulkRequest.Builder()
                    entries.forEach {
                        val source = it.asJson().toObject()
                        br.operations { op ->
                            op.index { idx ->
                                idx.index(indexName)
                                        .id(it.computeId())
                                        .document(source)
                            }
                        }
                    }
                    // Bulk request execution
                    val request = br.build()
                    registry?.time<Unit>(ElasticMetricsClientMetrics.time) {
                        try {
                            debug("Sending the bulk request (${request.operations().size})")
                            val response = client.bulk(request)
                            if (response.errors()) {
                                val errorMessage = response.items()
                                        .mapNotNull { it.error()?.reason() }
                                        .joinToString("\n")
                                logger.error("Errors while exporting metrics to ElasticSearch. $errorMessage")
                                registry?.increment(ElasticMetricsClientMetrics.errors)
                            }
                        } catch (any: Exception) {
                            logger.error("Cannot export ${entries.size} metrics to ElasticSearch", any)
                            registry?.increment(ElasticMetricsClientMetrics.errors)
                        }
                    }
                } else {
                    debug("Nothing to flush")
                }
            } catch (any: Throwable) {
                logger.error("Uncaught error on flushing events", any)
            } finally {
                bufferLock.unlock()
                debug("Unlocking the buffer")
            }
        } else {
            debug("Could not acquire lock. Skipping.")
        }
    }

    override fun dropIndex() {
        if (elasticMetricsConfigProperties.allowDrop) {
            val indexExists = client.indices().exists {
                it.index(elasticMetricsConfigProperties.index.name)
            }.value()
            if (indexExists) {
                logger.info("Dropping the ${elasticMetricsConfigProperties.index.name} index before re-export")
                client.indices().delete {
                    it.index(elasticMetricsConfigProperties.index.name)
                }
            }
        }
    }

    override fun rawSearch(
            token: String,
            offset: Int,
            size: Int,
    ): SearchNodeResults {
        // Getting the result of the search
        val response = client.search({ s ->
            s.index(elasticMetricsConfigProperties.index.name)
                    .query { q ->
                        q.multiMatch { mm -> mm.query(token).type(TextQueryType.BestFields) }
                    }.from(offset).size(size)
        }, ObjectNode::class.java)

        // Pagination information
        val responseHits = response.hits()
        val totalHits = responseHits.total()?.value() ?: 0

        // Hits as JSON nodes
        val hits = responseHits.hits().mapNotNull { hit ->
            hit.id()?.let {
                @Suppress("UNCHECKED_CAST")
                SearchResultNode(
                    hit.index(),
                    it,
                    hit.score() ?: 0.0,
                    hit.source()?.toObject() as? Map<String,Any>? ?: emptyMap(),
                )
            }
        }

        // Transforming into search results
        return SearchNodeResults(
                items = hits,
                offset = offset,
                total = totalHits.toInt(),
                message = when {
                    totalHits <= 0 -> "The number of total matches is not known and pagination is not possible."
                    else -> null
                }
        )
    }

    private val client: ElasticsearchClient by lazy {
        val restClient = when (elasticMetricsConfigProperties.target) {
            // Using the main ES instance
            ElasticMetricsTarget.MAIN -> defaultLowLevelClient
            // Using the custom ES instance
            ElasticMetricsTarget.CUSTOM -> customClient()
        }
        val transport = Rest5ClientTransport(restClient, JacksonJsonpMapper())
        ElasticsearchClient(transport)
    }

    private fun customClient(): Rest5Client {
        val hosts = elasticMetricsConfigProperties.custom.uris.map { value ->
            val uri = URI(value)
            URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, uri.fragment)
        }
        val builder = Rest5Client.builder(hosts)
        if (!elasticMetricsConfigProperties.custom.username.isNullOrBlank()) {
            builder.setHttpClientConfigCallback {
                it.setDefaultCredentialsProvider(
                        BasicCredentialsProvider().apply {
                            setCredentials(
                                    AuthScope(null, -1),
                                    UsernamePasswordCredentials(
                                            elasticMetricsConfigProperties.custom.username,
                                            elasticMetricsConfigProperties.custom.password?.toCharArray(),
                                    )
                            )
                        }
                )
            }
        }
        if (elasticMetricsConfigProperties.custom.pathPrefix != null) {
            builder.setPathPrefix(elasticMetricsConfigProperties.custom.pathPrefix)
        }
        return builder.build()
    }
}
