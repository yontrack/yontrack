package net.nemerosa.ontrack.service.search.perf

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.nemerosa.ontrack.extension.support.CoreExtensionFeature
import net.nemerosa.ontrack.json.ObjectMapperFactory
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.search.SearchDocumentIdentifiers
import net.nemerosa.ontrack.repository.search.SearchDocumentJdbcRepository
import net.nemerosa.ontrack.repository.search.SearchDocumentScope
import net.nemerosa.ontrack.service.search.SearchDocumentServiceImpl
import net.nemerosa.ontrack.service.search.SearchIndexMetrics
import net.nemerosa.ontrack.service.search.perf.SearchPerfDataset.Companion.TYPES
import net.nemerosa.ontrack.service.search.perf.SearchPerfDataset.Companion.TYPE_BUILD
import net.nemerosa.ontrack.service.search.perf.SearchPerfDataset.Companion.TYPE_FINDING
import net.nemerosa.ontrack.service.search.perf.SearchPerfDataset.Companion.TYPE_SCM_CATALOG
import net.nemerosa.ontrack.service.search.perf.SearchPerfPlans.IX_IDENTIFIERS_TRGM
import net.nemerosa.ontrack.service.search.perf.SearchPerfPlans.TIER_INDEXES
import net.nemerosa.ontrack.service.search.perf.SearchPerfPlans.TRIGRAM_INDEXES
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.lang.reflect.Proxy
import java.sql.DriverManager
import java.time.LocalDateTime
import kotlin.math.roundToLong
import kotlin.system.exitProcess

/**
 * The `searchPerfTest` (`./gradlew searchPerfTest`): search on a large dataset, on the Postgres
 * of the integration test stack, in a database of its own.
 *
 * 1. bulk-loads the [dataset][SearchPerfDataset];
 * 2. checks the plan of each query shape with `EXPLAIN`: it must use its index, and never scan
 *    the whole `SEARCH_DOCUMENTS` table;
 * 3. measures the p95 latency of each query shape;
 * 4. measures the full rebuild of all the search documents.
 *
 * The results are written into a JSON report. The run fails on a failed `EXPLAIN` assertion, on a
 * p95 past its [ceiling][CEILINGS] or on an error of the rebuild — never on a p95 which is only
 * past its budget, the design target, measured on a laptop.
 *
 * The searches go through [SearchDocumentServiceImpl.search], as the service runs them: in a
 * read-only transaction, with its `work_mem` and its cap of the counts.
 *
 * See `doc/dev-guide/search-perf-test.md`.
 */
object SearchPerf {

    /**
     * Budgets of the p95 latencies, in milliseconds: the design target, the same for a user
     * seeing a tenth of the projects as for an administrator. The exact build and commit lookups
     * go through the palette.
     */
    private val BUDGETS = mapOf(
        "palette_p95" to 150.0,
        "results_p95" to 500.0,
        "palette_restricted_p95" to 150.0,
        "results_restricted_p95" to 500.0,
        "exact_build_p95" to 150.0,
        "commit_lookup_p95" to 150.0,
    )

    /**
     * Ceilings of the p95 latencies, in milliseconds: a p95 past its ceiling fails the run, and
     * the nightly `SEARCH.PERFORMANCE` stamp. The nightly runs on a GitHub runner, slower than
     * the machine of the budget: each ceiling is the p95 of a `workflow_dispatch` run of
     * `search-perf.yml`, times 1.5 (#1888).
     */
    private val CEILINGS = mapOf(
        "palette_p95" to 1500.0,
        "results_p95" to 5000.0,
        "palette_restricted_p95" to 1500.0,
        "results_restricted_p95" to 5000.0,
        "exact_build_p95" to 1500.0,
        "commit_lookup_p95" to 1500.0,
    )

    private const val PALETTE_SIZE = 20
    private const val PALETTE_PER_TYPE = 3
    private const val RESULTS_SIZE = 20
    private const val RESULTS_OFFSET = 20

    /**
     * Queries typed in the palette: short prefixes, words, several words, typos, names
     */
    private val GENERAL_QUERIES = listOf(
        "pa", "ma", "rel", "1.2", "feat", "develop",
        "payment", "checkout", "fix", "timeout", "cache",
        "payment service", "fix timeout", "flaky test",
        "paymnet", "chekout", "inventroy", "sheduler",
        "release-1.3", "build-4", "2025.03", "pay-10",
    )

    /**
     * A word matching tens of thousands of documents: every commit of the payment projects
     */
    private const val FREQUENT_WORD = "pay-10"

    private val config = Config()

    private fun log(message: String) = println("[search-perf] $message")

    @JvmStatic
    fun main(args: Array<String>) {
        val report = linkedMapOf<String, Any?>()
        val details = linkedMapOf<String, Any?>()
        val failures = mutableListOf<String>()
        try {
            run(report, details, failures)
        } catch (any: Throwable) {
            any.printStackTrace()
            failures += "Error: ${any.message ?: any::class.java.name}"
        }
        details["failures"] = failures
        report["details"] = details
        writeReport(report)
        if (failures.isEmpty()) {
            log("OK")
            exitProcess(0)
        } else {
            failures.forEach { log("FAILURE $it") }
            exitProcess(1)
        }
    }

    private fun run(report: MutableMap<String, Any?>, details: MutableMap<String, Any?>, failures: MutableList<String>) {
        val dataset = SearchPerfDataset(config.scale)

        // Database
        val (url, load) = prepareDatabase(dataset)
        val dataSource = SingleConnectionDataSource(url, config.username, config.password, true)
        val jdbc = JdbcTemplate(dataSource)
        details["postgres"] = jdbc.queryForObject("SELECT version()", String::class.java)
        details["dataset"] = linkedMapOf(
            "scale" to config.scale,
            "projects" to dataset.projects,
            "branches" to dataset.branches,
            "builds" to dataset.builds,
            "documents" to dataset.documentCounts(jdbc),
        ) + load
        jdbc.execute("ANALYZE SEARCH_DOCUMENTS")

        val repository = SearchDocumentJdbcRepository(dataSource)
        val transactionManager = DataSourceTransactionManager(dataSource)
        val searcher = Searcher(
            repository = repository,
            service = searchDocumentService(repository, transactionManager),
            transactionManager = transactionManager,
        )
        details["search_config"] = linkedMapOf(
            "count_cap" to searcher.config.countCap,
            "work_mem" to searcher.config.workMem,
        )
        val scenarios = Scenarios(
            buildNames = dataset.sampleBuildNames(jdbc, 20),
            commits = dataset.sampleCommits(20),
            restrictedProjectIds = (1..dataset.projects step 10).toList(),
        )

        // EXPLAIN assertions
        val explain = explain(searcher, scenarios)
        explain.filter { it["passed"] == false }.forEach { entry ->
            failures += "EXPLAIN ${entry["scenario"]} '${entry["query"]}' (${entry["statement"]}): ${entry["reason"]}"
        }

        // Latencies
        val measured = latencies(searcher, scenarios)
        val latencies = measured.summaries
        val p95s = linkedMapOf(
            "palette_p95" to latencies.getValue("palette").p95,
            "results_p95" to latencies.getValue("results").p95,
            "commit_lookup_p95" to latencies.getValue("commit_lookup").p95,
            "exact_build_p95" to latencies.getValue("exact_build").p95,
            "palette_restricted_p95" to latencies.getValue("palette_restricted").p95,
            "results_restricted_p95" to latencies.getValue("results_restricted").p95,
        )
        p95s.forEach { (key, p95) ->
            val ceiling = CEILINGS.getValue(key)
            if (p95 > ceiling) {
                failures += "$key = ${p95.round()} ms, past its ceiling of $ceiling ms"
            }
        }
        details["latencies"] = latencies.mapValues { (_, summary) ->
            linkedMapOf(
                "samples" to summary.samples,
                "p50" to summary.p50.round(),
                "p95" to summary.p95.round(),
                "max" to summary.max.round(),
            )
        }
        details["slowest_queries"] = measured.medians.sortedByDescending { it.third }.take(SLOWEST).map { (scenario, query, median) ->
            linkedMapOf("scenario" to scenario, "query" to query, "p50" to median.round())
        }
        details["budgets"] = BUDGETS
        details["ceilings"] = CEILINGS
        details["over_budget"] = p95s.filter { (key, p95) -> p95 > BUDGETS.getValue(key) }.keys.toList()

        // Rebuild
        val rebuild = rebuild(dataSource, url)
        if (rebuild.errors > 0) {
            failures += "Rebuild: ${rebuild.errors} batches could not be written"
        }
        details["rebuild"] = linkedMapOf(
            "documents" to rebuild.documents,
            "errors" to rebuild.errors,
            "per_type_seconds" to rebuild.perType.mapValues { it.value.round() },
        )

        report.putAll(p95s.mapValues { it.value.round() })
        report["rebuild_seconds"] = rebuild.seconds.round()
        report["explain"] = explain
        dataSource.destroy()
    }

    // ---------------------------------------------------------------------------------------------
    // Database
    // ---------------------------------------------------------------------------------------------

    /**
     * Creates the database of the test, migrated and loaded, unless reused.
     *
     * @return JDBC URL of the database of the test, and the timings of the load when there was one
     */
    private fun prepareDatabase(dataset: SearchPerfDataset): Pair<String, Map<String, Any?>> {
        val url = config.url.replace(Regex("/[^/?]+(\\?|$)"), "/${config.database}$1")
        val admin = connectWithRetries(config.url)
        val exists = admin.use { connection ->
            val exists = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?").use { ps ->
                ps.setString(1, config.database)
                ps.executeQuery().use { it.next() }
            }
            val reusable = exists && config.reuse && reusableScale(url) == config.scale
            if (!reusable) {
                connection.createStatement().use { st ->
                    st.execute("DROP DATABASE IF EXISTS ${config.database} WITH (FORCE)")
                    st.execute("CREATE DATABASE ${config.database}")
                }
            }
            reusable
        }
        if (exists) {
            log("Reusing the dataset of ${config.database} (scale ${config.scale})")
            return url to emptyMap()
        }
        log("Migrating ${config.database}")
        Flyway.configure()
            .dataSource(url, config.username, config.password)
            .table("schema_version")
            .load()
            .migrate()
        val dataSource = SingleConnectionDataSource(url, config.username, config.password, true)
        try {
            val jdbc = JdbcTemplate(dataSource)
            val timings = dataset.load(jdbc, ::log)
            jdbc.execute("CREATE TABLE SEARCH_PERF_DATASET (SCALE DOUBLE PRECISION NOT NULL)")
            jdbc.update("INSERT INTO SEARCH_PERF_DATASET (SCALE) VALUES (?)", config.scale)
            log("Dataset loaded in ${timings.totalSeconds.round()} s")
            return url to linkedMapOf(
                "load_seconds" to timings.totalSeconds.round(),
                "load_structure_seconds" to timings.structureSeconds.round(),
                "load_documents_seconds" to timings.documentsSeconds.round(),
                "load_indexes_seconds" to timings.indexesSeconds.round(),
                "load_vacuum_analyze_seconds" to timings.analyzeSeconds.round(),
            )
        } finally {
            dataSource.destroy()
        }
    }

    private fun reusableScale(url: String): Double? =
        try {
            DriverManager.getConnection(url, config.username, config.password).use { connection ->
                connection.createStatement().use { st ->
                    st.executeQuery("SELECT SCALE FROM SEARCH_PERF_DATASET").use { rs ->
                        if (rs.next()) rs.getDouble(1) else null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }

    /**
     * The port of the stack is open before Postgres accepts connections
     */
    private fun connectWithRetries(url: String): java.sql.Connection {
        val deadline = System.currentTimeMillis() + 120_000
        while (true) {
            try {
                return DriverManager.getConnection(url, config.username, config.password)
            } catch (any: Exception) {
                if (System.currentTimeMillis() > deadline) throw any
                log("Waiting for Postgres at $url: ${any.message}")
                Thread.sleep(2_000)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Scenarios
    // ---------------------------------------------------------------------------------------------

    private class Scenarios(
        val buildNames: List<String>,
        val commits: List<String>,
        restrictedProjectIds: List<Int>,
    ) {
        /**
         * An administrator: all the projects, all the types
         */
        val admin = SearchDocumentScope(
            types = TYPES,
            allProjects = true,
            projectIds = emptyList(),
            projectLessTypes = listOf(TYPE_SCM_CATALOG),
            nonFuzzyTypes = listOf(TYPE_FINDING),
        )

        /**
         * A user seeing one project out of ten, without the findings
         */
        val restricted = SearchDocumentScope(
            types = TYPES,
            allProjects = false,
            projectIds = restrictedProjectIds,
            projectLessTypes = emptyList(),
            restrictedTypes = mapOf(TYPE_FINDING to emptyList()),
            nonFuzzyTypes = listOf(TYPE_FINDING),
        )
    }

    /**
     * The search as the service runs it, and its statements, as they are `EXPLAIN`ed
     */
    private class Searcher(
        val repository: SearchDocumentJdbcRepository,
        val service: SearchDocumentServiceImpl,
        transactionManager: DataSourceTransactionManager,
    ) {
        val config = OntrackConfigProperties().search

        private val transaction = TransactionTemplate(transactionManager).apply { isReadOnly = true }

        /**
         * Runs some code in a transaction with the `work_mem` of the search, as the service does
         */
        fun <T> inSearchTransaction(code: () -> T): T = transaction.execute {
            if (config.workMem.isNotBlank()) {
                repository.setLocalWorkMem(config.workMem)
            }
            code()
        }!!
    }

    /**
     * The service of the search documents, for the searches and for the rebuild
     */
    private fun searchDocumentService(
        repository: SearchDocumentJdbcRepository,
        transactionManager: DataSourceTransactionManager,
        meterRegistry: SimpleMeterRegistry = SimpleMeterRegistry(),
    ): SearchDocumentServiceImpl {
        // The rebuild runs the indexers as administrator, and needs nothing else of the security
        val securityService = Proxy.newProxyInstance(
            SecurityService::class.java.classLoader,
            arrayOf(SecurityService::class.java),
        ) { _, method, args ->
            if (method.name == "asAdmin") {
                @Suppress("UNCHECKED_CAST")
                (args[0] as () -> Any?).invoke()
            } else {
                throw UnsupportedOperationException("SecurityService.${method.name}")
            }
        } as SecurityService
        return SearchDocumentServiceImpl(
            searchDocumentRepository = repository,
            securityService = securityService,
            meterRegistry = meterRegistry,
            ontrackConfigProperties = OntrackConfigProperties(),
            platformTransactionManager = transactionManager,
        )
    }

    /**
     * A search as run by the service
     */
    private data class Search(
        val statement: String,
        val scope: SearchDocumentScope,
        val offset: Int,
        val size: Int,
        val perType: Int?,
        val highlight: Boolean,
    ) {
        fun run(searcher: Searcher, query: String) =
            searcher.service.search(query, scope, offset, size, perType, highlight)
    }

    private fun palette(scope: SearchDocumentScope) =
        Search("palette", scope, offset = 0, size = PALETTE_SIZE, perType = PALETTE_PER_TYPE, highlight = false)

    /**
     * The results page runs two searches: the page of the selected type, and the facets of all
     * the types (the `all` alias)
     */
    private fun results(all: SearchDocumentScope) = listOf(
        Search("results page", all.copy(types = listOf(TYPE_BUILD)), RESULTS_OFFSET, RESULTS_SIZE, perType = null, highlight = true),
        Search("results facets", all, offset = 0, size = 0, perType = null, highlight = false),
    )

    // ---------------------------------------------------------------------------------------------
    // EXPLAIN
    // ---------------------------------------------------------------------------------------------

    private fun explain(searcher: Searcher, scenarios: Scenarios): List<Map<String, Any?>> {
        log("EXPLAIN")
        val repository = searcher.repository
        val entries = mutableListOf<Map<String, Any?>>()
        fun check(scenario: String, search: Search, query: String, expected: Set<String>) {
            val statements = repository.searchStatements(
                query, search.scope, search.offset, search.size, search.perType, search.highlight,
                countCap = searcher.config.countCap,
            ) ?: error("No statement for '$query'")
            listOfNotNull(statements.facets?.let { "facets" to it }, statements.rows?.let { "rows" to it })
                .forEach { (name, statement) ->
                    val plan = searcher.inSearchTransaction {
                        repository.namedParameterJdbcTemplate.queryForObject(
                            "EXPLAIN (FORMAT JSON) ${statement.sql}",
                            statement.params,
                            String::class.java,
                        )
                    } ?: error("No plan")
                    val check = SearchPerfPlans.check(plan, expected)
                    log("  ${if (check.passed) "PASS" else "FAIL"} $scenario '$query' ${search.statement} $name: ${check.indexes}${check.reason?.let { " — $it" } ?: ""}")
                    entries += linkedMapOf<String, Any?>(
                        "scenario" to scenario,
                        "query" to query,
                        "statement" to "${search.statement} $name",
                        "expected_indexes" to expected.sorted(),
                        "passed" to check.passed,
                        "indexes" to check.indexes,
                    ).apply {
                        if (!check.passed) {
                            put("reason", check.reason)
                            put("sql", statement.sql)
                            put("params", statement.params.values.mapValues { it.value.toString() })
                            put("plan", plan.parseAsJson())
                        }
                    }
                }
        }
        listOf("payment", "pa", "paymnet", "fix timeout").forEach { query ->
            check("palette", palette(scenarios.admin), query, TIER_INDEXES)
        }
        listOf("fix", "1.2").forEach { query ->
            results(scenarios.admin).forEach { search -> check("results", search, query, TIER_INDEXES) }
        }
        // A frequent word: its count is capped, and only the capped candidates are ranked
        results(scenarios.admin).forEach { search -> check("capped_count", search, FREQUENT_WORD, TIER_INDEXES) }
        check("capped_ranking", palette(scenarios.admin), FREQUENT_WORD, TIER_INDEXES)
        // A typo: the fuzzy types fall back on the trigram indexes
        check("trigram_fallback", palette(scenarios.admin), "paymnet", TRIGRAM_INDEXES)
        results(scenarios.admin).forEach { search -> check("trigram_fallback", search, "chekout", TRIGRAM_INDEXES) }
        // A user seeing a tenth of the projects
        listOf("payment", FREQUENT_WORD, "paymnet").forEach { query ->
            check("palette_restricted", palette(scenarios.restricted), query, TIER_INDEXES)
        }
        listOf("fix", FREQUENT_WORD).forEach { query ->
            results(scenarios.restricted).forEach { search -> check("results_restricted", search, query, TIER_INDEXES) }
        }
        scenarios.buildNames.take(4).forEach { name ->
            check("exact_build", palette(scenarios.admin), name, setOf(IX_IDENTIFIERS_TRGM))
        }
        scenarios.commits.take(2).forEach { commit ->
            check("commit_lookup", palette(scenarios.admin), commit, setOf(IX_IDENTIFIERS_TRGM))
        }
        return entries
    }

    // ---------------------------------------------------------------------------------------------
    // Latencies
    // ---------------------------------------------------------------------------------------------

    /**
     * Latencies of the scenarios, and the median of each query of each scenario
     */
    private class Latencies(
        val summaries: Map<String, SearchPerfStats.Summary>,
        val medians: List<Triple<String, String, Double>>,
    )

    private fun latencies(
        searcher: Searcher,
        scenarios: Scenarios,
    ): Latencies {
        val admin = palette(scenarios.admin)
        val restricted = palette(scenarios.restricted)
        val results = results(scenarios.admin)
        val resultsRestricted = results(scenarios.restricted)
        val runs: Map<String, Pair<List<String>, (String) -> Unit>> = linkedMapOf(
            "palette" to (GENERAL_QUERIES to { q -> admin.run(searcher, q) }),
            "palette_restricted" to (GENERAL_QUERIES to { q -> restricted.run(searcher, q) }),
            "results" to (GENERAL_QUERIES to { q -> results.forEach { it.run(searcher, q) } }),
            "results_restricted" to (GENERAL_QUERIES to { q -> resultsRestricted.forEach { it.run(searcher, q) } }),
            "exact_build" to (scenarios.buildNames to { q -> admin.run(searcher, q) }),
            "commit_lookup" to (scenarios.commits to { q -> admin.run(searcher, q) }),
        )
        log("Warming up")
        runs.values.forEach { (queries, search) ->
            repeat(WARM_UP_ROUNDS) { queries.forEach(search) }
        }
        val medians = mutableListOf<Triple<String, String, Double>>()
        val summaries = runs.mapValues { (scenario, run) ->
            val (queries, search) = run
            val samples = mutableListOf<Double>()
            val perQuery = queries.associateWith { mutableListOf<Double>() }
            repeat(config.rounds) {
                queries.forEach { query ->
                    val start = System.nanoTime()
                    search(query)
                    val sample = (System.nanoTime() - start) / 1e6
                    samples += sample
                    perQuery.getValue(query) += sample
                }
            }
            perQuery.forEach { (query, querySamples) ->
                medians += Triple(scenario, query, SearchPerfStats.percentile(querySamples, 50.0))
            }
            SearchPerfStats.summary(samples).also {
                log("  $scenario: ${it.samples} samples, p50 ${it.p50.round()} ms, p95 ${it.p95.round()} ms, max ${it.max.round()} ms")
            }
        }
        return Latencies(summaries, medians)
    }

    private const val WARM_UP_ROUNDS = 2

    /**
     * Number of the slowest queries in the report
     */
    private const val SLOWEST = 10

    // ---------------------------------------------------------------------------------------------
    // Rebuild
    // ---------------------------------------------------------------------------------------------

    private class Rebuild(
        val seconds: Double,
        val documents: Int,
        val errors: Int,
        val perType: Map<String, Double>,
    )

    /**
     * Full rebuild of all the documents, through [SearchDocumentServiceImpl.rebuild] — the batches,
     * their savepoints, the upserts and the deletion of the stale documents are the real ones.
     *
     * What is approximated is the source of the documents: the indexers read the entities through
     * the services, or scan the SCMs, where [ReadBackIndexer] reads the documents back from the
     * table. The time is the one of the writes, not of the reads of the indexers.
     */
    private fun rebuild(dataSource: SingleConnectionDataSource, url: String): Rebuild {
        log("Rebuilding all the documents")
        val meterRegistry = SimpleMeterRegistry()
        val service = searchDocumentService(
            SearchDocumentJdbcRepository(dataSource),
            DataSourceTransactionManager(dataSource),
            meterRegistry,
        )
        val reader = SingleConnectionDataSource(url, config.username, config.password, true)
        try {
            val readerJdbc = JdbcTemplate(reader)
            val perType = linkedMapOf<String, Double>()
            var documents = 0
            TYPES.forEach { type ->
                val indexer = ReadBackIndexer(type, readerJdbc)
                val start = System.nanoTime()
                service.rebuild(indexer)
                val seconds = (System.nanoTime() - start) / 1e9
                if (indexer.count > 0) {
                    perType[type] = seconds
                    documents += indexer.count
                    log("  $type: ${indexer.count} documents in ${seconds.round()} s")
                }
            }
            val errors = meterRegistry.find(SearchIndexMetrics.indexErrors).counters().sumOf { it.count() }.toInt()
            return Rebuild(perType.values.sum(), documents, errors, perType)
        } finally {
            reader.destroy()
        }
    }

    /**
     * Provides the documents of a type as they are in the table, by pages.
     */
    private class ReadBackIndexer(
        type: String,
        private val jdbc: JdbcTemplate,
    ) : SearchDocumentIndexer {

        var count = 0

        override val searchResultType = SearchResultType(
            feature = CoreExtensionFeature.INSTANCE.featureDescription,
            id = type,
            name = type,
            description = type,
            order = 0,
        )

        override fun indexAll(processor: (SearchDocument) -> Unit) {
            var last = 0
            while (true) {
                val page = jdbc.query(
                    """
                        SELECT ID, KEY, PROJECT_ID, ENTITY_TYPE, ENTITY_ID, TITLE, IDENTIFIERS, TEXT, DATA::TEXT AS DATA, UPDATED_AT
                        FROM SEARCH_DOCUMENTS
                        WHERE TYPE = ? AND ID > ?
                        ORDER BY ID
                        LIMIT 5000
                    """,
                    { rs, _ ->
                        rs.getInt("ID") to SearchDocument(
                            type = searchResultType.id,
                            key = rs.getString("KEY"),
                            projectId = rs.getInt("PROJECT_ID").takeIf { !rs.wasNull() },
                            entity = rs.getString("ENTITY_TYPE")?.let { entityType ->
                                ProjectEntityID(ProjectEntityType.valueOf(entityType), rs.getInt("ENTITY_ID"))
                            },
                            title = rs.getString("TITLE"),
                            identifiers = rs.getString("IDENTIFIERS")
                                .split(SearchDocumentIdentifiers.SEPARATOR)
                                .filter { it.isNotEmpty() },
                            text = rs.getString("TEXT"),
                            data = rs.getString("DATA").parseAsJson(),
                            updatedAt = rs.getObject("UPDATED_AT", LocalDateTime::class.java),
                        )
                    },
                    searchResultType.id,
                    last,
                )
                if (page.isEmpty()) return
                page.forEach { (_, document) -> processor(document) }
                count += page.size
                last = page.last().first
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Report
    // ---------------------------------------------------------------------------------------------

    private fun writeReport(report: Map<String, Any?>) {
        val file = File(config.report)
        file.absoluteFile.parentFile.mkdirs()
        file.writeText(ObjectMapperFactory.create().writerWithDefaultPrettyPrinter().writeValueAsString(report))
        log("Report written to ${file.absolutePath}")
    }

    private fun Double.round(): Double = (this * 10).roundToLong() / 10.0

    /**
     * Configuration, from the system properties passed by the Gradle task
     */
    private class Config {
        val url: String = System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/ontrack")
        val username: String = System.getProperty("spring.datasource.username", "ontrack")
        val password: String = System.getProperty("spring.datasource.password", "ontrack")
        val database: String = System.getProperty("searchPerf.database", "ontrack_search_perf")
        val scale: Double = System.getProperty("searchPerf.scale", "1").toDouble()
        val rounds: Int = System.getProperty("searchPerf.rounds", "10").toInt()
        val reuse: Boolean = System.getProperty("searchPerf.reuse", "false").toBoolean()
        val report: String = System.getProperty("searchPerf.report", "build/reports/search-perf/search-perf.json")
    }
}
