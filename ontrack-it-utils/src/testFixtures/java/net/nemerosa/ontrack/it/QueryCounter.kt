package net.nemerosa.ontrack.it

import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.QueryExecutionListener
import net.ttddyy.dsproxy.proxy.ParameterSetOperation
import net.ttddyy.dsproxy.support.ProxyDataSource
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import javax.sql.DataSource

/**
 * Records the SQL queries run against the database of the integration tests, to check how many a
 * piece of code runs - a budget of queries per request, for example:
 *
 * ```kotlin
 * @Autowired
 * private lateinit var queryCounter: QueryCounter
 *
 * val queries = queryCounter.record { callTheApi() }
 * assertEquals(1, queries.on("SETTINGS").withParameter(SecuritySettings::class.java.name).size)
 * ```
 *
 * The `dataSource` of the integration tests goes through `datasource-proxy`, which reports each
 * query to this counter (see [QueryCounterDataSourceWrapper]). A recording takes the queries of
 * **every thread** while its code runs, so that a call to the HTTP server of a `RANDOM_PORT` test,
 * which runs on a thread of the server, is counted: a test which may meet the queries of other
 * threads - a job, a queue listener - narrows them with [RecordedQueries.filter] on
 * [RecordedQuery.thread].
 */
@Component
class QueryCounter {

    private val recordings = CopyOnWriteArrayList<MutableList<RecordedQuery>>()

    /**
     * Runs the [code] and returns the queries run meanwhile, by any thread.
     */
    fun record(code: () -> Unit): RecordedQueries {
        val recording: MutableList<RecordedQuery> = Collections.synchronizedList(mutableListOf())
        recordings += recording
        try {
            code()
        } finally {
            recordings -= recording
        }
        return RecordedQueries(synchronized(recording) { recording.toList() })
    }

    /**
     * Whether a recording is running, before the queries are converted for it.
     */
    internal val recording: Boolean get() = recordings.isNotEmpty()

    /**
     * Some [queries] have been run.
     */
    internal fun executed(queries: List<RecordedQuery>) {
        recordings.forEach { it.addAll(queries) }
    }
}

/**
 * A query run against the database.
 *
 * @property sql SQL of the query, with its `?` placeholders
 * @property parameters Values of its parameters, in the order of the placeholders
 * @property thread Name of the thread which ran it
 */
data class RecordedQuery(
    val sql: String,
    val parameters: List<Any?>,
    val thread: String,
)

/**
 * Queries recorded by the [QueryCounter], in the order they were run.
 */
class RecordedQueries(val queries: List<RecordedQuery>) : List<RecordedQuery> by queries {

    /**
     * The queries reading or writing the [table]: whose SQL names it after `FROM`, `JOIN`, `INTO` or
     * `UPDATE`, whatever its case.
     */
    fun on(table: String): RecordedQueries {
        val regex = Regex("""\b(FROM|JOIN|INTO|UPDATE)\s+${Regex.escape(table)}\b""", RegexOption.IGNORE_CASE)
        return filter { regex.containsMatchIn(it.sql) }
    }

    /**
     * The queries one of whose parameters is the [value].
     */
    fun withParameter(value: Any?): RecordedQueries = filter { value in it.parameters }

    /**
     * The queries matching the [predicate].
     */
    fun filter(predicate: (RecordedQuery) -> Boolean): RecordedQueries =
        RecordedQueries(queries.filter(predicate))

    override fun toString(): String = queries.joinToString(separator = "\n") { "[${it.thread}] ${it.sql} ${it.parameters}" }
}

/**
 * Makes the queries of the `dataSource` of the integration tests go through the [QueryCounter].
 */
@Component
class QueryCounterDataSourceWrapper(
    private val queryCounter: QueryCounter,
) : BeanPostProcessor {

    private val listener = object : QueryExecutionListener {

        override fun beforeQuery(execInfo: ExecutionInfo, queryInfoList: List<QueryInfo>) {
        }

        override fun afterQuery(execInfo: ExecutionInfo, queryInfoList: List<QueryInfo>) {
            if (!queryCounter.recording) return
            val thread = Thread.currentThread().name
            queryCounter.executed(
                queryInfoList.flatMap { info ->
                    val parameterSets = info.parametersList.takeIf { it.isNotEmpty() } ?: listOf(emptyList())
                    parameterSets.map { operations ->
                        RecordedQuery(
                            sql = info.query,
                            // The first argument of a parameter setter is its index, the second its
                            // value - or its SQL type, for `setNull`
                            parameters = operations
                                .filter { (it.args?.size ?: 0) >= 2 }
                                .sortedBy { (it.args[0] as? Int) ?: 0 }
                                .map { if (ParameterSetOperation.isSetNullParameterOperation(it)) null else it.args[1] },
                            thread = thread,
                        )
                    }
                }
            )
        }
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any =
        if (bean is DataSource && bean !is ProxyDataSource && beanName == "dataSource") {
            ProxyDataSourceBuilder.create(bean)
                .name("query-counter")
                .listener(listener)
                .build()
        } else {
            bean
        }
}
