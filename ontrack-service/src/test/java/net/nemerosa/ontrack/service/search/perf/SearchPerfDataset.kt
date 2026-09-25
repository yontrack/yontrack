package net.nemerosa.ontrack.service.search.perf

import org.springframework.jdbc.core.JdbcTemplate
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The dataset of the `searchPerfTest`, bulk-loaded straight into Postgres with `INSERT … SELECT`
 * over `generate_series`: no row goes through the JVM, and every value derives from its ID, so
 * that two loads at the same [scale] are identical.
 *
 * At scale 1: 500 projects, 5,000 branches, 100,000 builds, 1,000,000 commits, plus in proportion
 * 100,000 build links, 10,000 releases, 50,000 issues and 5,000 Git branches — and their search
 * documents, whose `TITLE`, `IDENTIFIERS` and `TEXT` are those the indexers would write. Their
 * `DATA` only has the shape and the size of the real one: no query reads into it.
 *
 * The indexes of `SEARCH_DOCUMENTS` other than its keys are dropped before the load and created
 * again after it, from their definitions as read in the database — the ones of the migrations.
 */
class SearchPerfDataset(
    val scale: Double,
) {

    val projects = max(10, (500 * scale).roundToInt())
    val branches = projects * BRANCHES_PER_PROJECT
    val builds = branches * BUILDS_PER_BRANCH
    val commits = projects * COMMITS_PER_PROJECT
    val issues = projects * ISSUES_PER_PROJECT
    val releases = builds / RELEASE_EVERY
    val buildLinks = builds
    val gitBranches = branches

    companion object {
        const val BRANCHES_PER_PROJECT = 10
        const val BUILDS_PER_BRANCH = 20
        const val COMMITS_PER_PROJECT = 2_000
        const val ISSUES_PER_PROJECT = 100
        const val RELEASE_EVERY = 10

        // Search result types, as declared by their indexers
        const val TYPE_PROJECT = "project"
        const val TYPE_BRANCH = "branch"
        const val TYPE_BUILD = "build"
        const val TYPE_RELEASE = "build-release"
        const val TYPE_BUILD_LINK = "build-link"
        const val TYPE_ISSUE = "scm-issue"
        const val TYPE_GIT_BRANCH = "git-branch"
        const val TYPE_COMMIT = "scm-commit"
        const val TYPE_FINDING = "finding"
        const val TYPE_SCM_CATALOG = "scm-catalog"

        /**
         * All the types, in their display order (`SearchResultType.order`)
         */
        val TYPES = listOf(
            TYPE_PROJECT, TYPE_BRANCH, TYPE_BUILD, TYPE_RELEASE, TYPE_BUILD_LINK, TYPE_ISSUE,
            TYPE_GIT_BRANCH, TYPE_COMMIT, TYPE_FINDING, TYPE_SCM_CATALOG,
        )

        private val PROJECT_WORDS = listOf(
            "payment", "billing", "catalog", "search", "inventory", "gateway", "auth", "orders", "shipping",
            "notification", "analytics", "reporting", "ledger", "pricing", "checkout", "profile", "identity",
            "routing", "storage", "scheduler",
        )
        private val PROJECT_KINDS = listOf(
            "service", "api", "ui", "worker", "lib", "sdk", "core", "adapter", "client", "agent", "batch",
            "portal", "engine", "connector", "web",
        )
        private val TOPICS = listOf(
            "login", "cache", "retry", "export", "import", "cleanup", "upgrade", "metrics", "audit", "search",
        )
        private val VERBS = listOf(
            "Fix", "Add", "Update", "Remove", "Refactor", "Improve", "Rename", "Document", "Test", "Bump",
            "Revert", "Handle", "Support", "Optimise", "Clean up",
        )
        private val NOUNS = listOf(
            "null pointer", "timeout", "retry logic", "cache", "validation", "logging", "metrics", "configuration",
            "dependency", "migration", "endpoint", "serializer", "permissions", "pagination", "error handling",
            "race condition", "memory leak", "build script", "flaky test", "documentation",
        )
        private val PREPOSITIONS = listOf("in", "for", "of", "around", "on")
        private val MODULES = listOf(
            "payment", "billing", "catalog", "search", "inventory", "gateway", "auth", "orders", "shipping",
            "notification", "scheduler", "storage", "api", "ui", "worker", "database", "queue", "client",
        )
        private val AUTHORS = listOf("alice", "bob", "carol", "dave", "erin", "frank", "grace", "heidi")
        private val BUILD_DESCRIPTIONS = listOf(
            "Nightly build", "Release candidate", "Hotfix build", "Snapshot build", "Continuous integration build",
        )

        private fun array(values: List<String>) =
            values.joinToString(prefix = "(ARRAY[", separator = ",", postfix = "]::TEXT[])") { "'$it'" }

        /**
         * Picks a value of an array for an integer SQL expression
         */
        private fun pick(values: List<String>, expression: String) =
            "${array(values)}[1 + (($expression) % ${values.size})]"

        /**
         * Picks a value of an array for an integer SQL expression, pseudo-randomly: the picks of
         * two [salt]s are not correlated, where a modulo of the ID would tie the words of a commit
         * message to its project
         */
        private fun pickRandom(values: List<String>, salt: Int, expression: String) =
            "${array(values)}[1 + (${hash(salt, expression)} % ${values.size})]"

        /**
         * Pseudo-random positive integer for an integer SQL expression
         */
        private fun hash(salt: Int, expression: String) = "abs(hashint4(($expression) # $salt)::BIGINT)"

        private fun md5(value: String): String =
            MessageDigest.getInstance("MD5").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }

        /**
         * Full ID of the commit number [c], as computed by the SQL of the load
         */
        fun commitId(c: Int): String = md5("c$c") + md5("d$c").take(8)
    }

    /**
     * Timings of a load, in seconds
     */
    data class LoadTimings(
        val structureSeconds: Double,
        val documentsSeconds: Double,
        val indexesSeconds: Double,
        val analyzeSeconds: Double,
    ) {
        val totalSeconds: Double get() = structureSeconds + documentsSeconds + indexesSeconds + analyzeSeconds
    }

    // SQL expressions, for a project ID `p`, a branch ID `b` and a build ID `n`

    private fun projectName(p: String) =
        "${pick(PROJECT_WORDS, "($p) - 1")} || '-' || ${pick(PROJECT_KINDS, "(($p) - 1) / ${PROJECT_WORDS.size}")}" +
                " || CASE WHEN ($p) > ${PROJECT_WORDS.size * PROJECT_KINDS.size}" +
                " THEN '-' || ((($p) - 1) / ${PROJECT_WORDS.size * PROJECT_KINDS.size} + 1) ELSE '' END"

    private fun issuePrefix(p: String) = "upper(left(${pick(PROJECT_WORDS, "($p) - 1")}, 3))"

    /** Index of a branch in its project */
    private fun branchIndex(b: String) = "((($b) - 1) % $BRANCHES_PER_PROJECT)"

    private fun branchProject(b: String) = "(1 + (($b) - 1) / $BRANCHES_PER_PROJECT)"

    /** Index of a build in its branch */
    private fun buildIndex(n: String) = "((($n) - 1) % $BUILDS_PER_BRANCH)"

    private fun buildBranch(n: String) = "(1 + (($n) - 1) / $BUILDS_PER_BRANCH)"

    private fun buildProject(n: String) = branchProject(buildBranch(n))

    /**
     * Four styles of build names, one per project: semantic versions (the same names in many
     * branches and projects), build numbers with a display name, dated builds and prefixed numbers.
     */
    private fun buildName(n: String): String {
        val k = branchIndex(buildBranch(n))
        val j = buildIndex(n)
        return """
            CASE ${buildProject(n)} % 4
                WHEN 0 THEN '1.' || $k || '.' || $j
                WHEN 1 THEN (1000 + ($n))::TEXT
                WHEN 2 THEN to_char(DATE '2025-01-01' + (($n) % 365), 'YYYY.MM.DD') || '-' || left(md5('b' || ($n)), 7)
                ELSE 'build-' || ($n)
            END
        """.trimIndent()
    }

    private fun buildDisplayName(n: String) =
        "CASE WHEN ${buildProject(n)} % 4 = 1 THEN '2.' || ${branchIndex(buildBranch(n))} || '.' || ${buildIndex(n)} END"

    private fun gitBranchName(branchName: String) = "regexp_replace($branchName, '^(release|feature)-', '\\1/')"

    private fun identifiers(vararg values: String) =
        "E'\\n' || " + values.joinToString(" || ") { "coalesce(lower($it) || E'\\n', '')" }

    private fun time(id: String, step: String) = "(TIMESTAMP '2024-01-01 00:00:00' + ($id) * INTERVAL '$step')"

    /**
     * Loads the whole dataset into an empty database.
     */
    fun load(jdbc: JdbcTemplate, log: (String) -> Unit): LoadTimings {
        val structure = timed {
            log("Loading $projects projects, $branches branches, $builds builds")
            loadStructure(jdbc)
        }
        // Indexes of the documents, but for their keys
        val indexes = jdbc.queryForList(
            """
                SELECT indexname, indexdef FROM pg_indexes
                WHERE schemaname = current_schema() AND tablename = 'search_documents'
                AND indexname NOT IN ('search_documents_pkey', 'search_documents_uq_type_key')
                ORDER BY indexname
            """
        ).map { it["indexname"] as String to it["indexdef"] as String }
        indexes.forEach { (name, _) -> jdbc.execute("DROP INDEX $name") }
        val documents = timed {
            log("Loading the search documents ($commits commits)")
            loadDocuments(jdbc)
        }
        val indexing = timed {
            log("Creating ${indexes.size} indexes: ${indexes.joinToString { it.first }}")
            jdbc.execute("SET maintenance_work_mem = '512MB'")
            indexes.forEach { (_, definition) -> jdbc.execute(definition) }
        }
        val analyze = timed {
            log("VACUUM ANALYZE")
            jdbc.execute("VACUUM ANALYZE")
        }
        return LoadTimings(structure, documents, indexing, analyze)
    }

    private fun loadStructure(jdbc: JdbcTemplate) {
        jdbc.execute(
            """
                INSERT INTO PROJECTS (ID, NAME, DESCRIPTION, DISABLED, CREATION, CREATOR)
                SELECT p,
                       ${projectName("p")},
                       'The ' || ${pick(PROJECT_WORDS, "p - 1")} || ' ' || ${pick(PROJECT_KINDS, "(p - 1) / ${PROJECT_WORDS.size}")} || ' of the platform',
                       FALSE,
                       '2024-01-01T00:00:00',
                       'perf'
                FROM generate_series(1, $projects) p
            """
        )
        jdbc.execute(
            """
                INSERT INTO BRANCHES (ID, PROJECTID, NAME, DESCRIPTION, DISABLED, CREATION, CREATOR)
                SELECT b,
                       ${branchProject("b")},
                       CASE
                           WHEN ${branchIndex("b")} = 0 THEN 'main'
                           WHEN ${branchIndex("b")} = 1 THEN 'develop'
                           WHEN ${branchIndex("b")} < 5 THEN 'release-1.' || ${branchIndex("b")}
                           ELSE 'feature-' || ${issuePrefix(branchProject("b"))} || '-' || (1000 + b) || '-' || ${pick(TOPICS, "b")}
                       END,
                       CASE WHEN b % 4 = 0 THEN 'Branch for the ' || ${pick(TOPICS, "b")} || ' work' END,
                       FALSE,
                       '2024-01-01T00:00:00',
                       'perf'
                FROM generate_series(1, $branches) b
            """
        )
        jdbc.execute(
            """
                INSERT INTO BUILDS (ID, BRANCHID, NAME, DESCRIPTION, CREATION, CREATOR)
                SELECT n,
                       ${buildBranch("n")},
                       ${buildName("n")},
                       CASE WHEN n % 3 = 0 THEN ${pick(BUILD_DESCRIPTIONS, "n")} || ' from ' || left(md5('c' || n), 7) END,
                       to_char(${time("n", "10 minutes")}, 'YYYY-MM-DD"T"HH24:MI:SS'),
                       'perf'
                FROM generate_series(1, $builds) n
            """
        )
        listOf("PROJECTS", "BRANCHES", "BUILDS").forEach { table ->
            jdbc.execute("SELECT setval(pg_get_serial_sequence('$table', 'id'), (SELECT max(ID) FROM $table))")
        }
    }

    private fun insertDocuments(jdbc: JdbcTemplate, select: String) {
        jdbc.execute(
            """
                INSERT INTO SEARCH_DOCUMENTS (TYPE, KEY, PROJECT_ID, ENTITY_TYPE, ENTITY_ID, TITLE, IDENTIFIERS, TEXT, DATA, UPDATED_AT, INDEXED_AT)
                $select
            """
        )
    }

    private val projectData = "jsonb_build_object('id', pr.ID, 'name', pr.NAME)"

    private val branchData =
        "jsonb_build_object('id', br.ID, 'name', br.NAME, 'description', br.DESCRIPTION, 'disabled', br.DISABLED, 'project', $projectData)"

    private val buildData =
        "jsonb_build_object('id', bu.ID, 'name', bu.NAME, 'description', bu.DESCRIPTION, 'branch', jsonb_build_object('id', br.ID, 'name', br.NAME, 'project', $projectData))"

    private val buildJoins = """
        FROM BUILDS bu
        JOIN BRANCHES br ON br.ID = bu.BRANCHID
        JOIN PROJECTS pr ON pr.ID = br.PROJECTID
    """.trimIndent()

    private fun loadDocuments(jdbc: JdbcTemplate) {
        val now = "now()::TIMESTAMP"
        // Projects
        insertDocuments(
            jdbc, """
                SELECT '$TYPE_PROJECT', pr.ID::TEXT, pr.ID, 'PROJECT', pr.ID,
                       pr.NAME, ${identifiers("pr.NAME")}, pr.DESCRIPTION,
                       jsonb_build_object('project', jsonb_build_object('id', pr.ID, 'name', pr.NAME, 'description', pr.DESCRIPTION, 'disabled', pr.DISABLED)),
                       ${time("pr.ID", "1 minute")}, $now
                FROM PROJECTS pr
            """
        )
        // Branches, and their Git branches
        insertDocuments(
            jdbc, """
                SELECT '$TYPE_BRANCH', br.ID::TEXT, pr.ID, 'BRANCH', br.ID,
                       pr.NAME || '/' || br.NAME, ${identifiers("br.NAME")}, br.DESCRIPTION,
                       jsonb_build_object('branch', $branchData),
                       ${time("br.ID", "1 hour")}, $now
                FROM BRANCHES br
                JOIN PROJECTS pr ON pr.ID = br.PROJECTID
            """
        )
        insertDocuments(
            jdbc, """
                SELECT '$TYPE_GIT_BRANCH', br.ID::TEXT, pr.ID, 'BRANCH', br.ID,
                       ${gitBranchName("br.NAME")}, ${identifiers(gitBranchName("br.NAME"))}, NULL,
                       jsonb_build_object('branch', $branchData, 'gitBranch', ${gitBranchName("br.NAME")}),
                       ${time("br.ID", "1 hour")}, $now
                FROM BRANCHES br
                JOIN PROJECTS pr ON pr.ID = br.PROJECTID
            """
        )
        // Builds
        insertDocuments(
            jdbc, """
                SELECT '$TYPE_BUILD', bu.ID::TEXT, pr.ID, 'BUILD', bu.ID,
                       coalesce(${buildDisplayName("bu.ID")}, bu.NAME), ${identifiers("bu.NAME", buildDisplayName("bu.ID"))}, bu.DESCRIPTION,
                       jsonb_build_object('build', $buildData, 'release', coalesce(${buildDisplayName("bu.ID")}, bu.NAME)),
                       ${time("bu.ID", "10 minutes")}, $now
                $buildJoins
            """
        )
        // Releases, one build out of ten
        val release = "'v' || (1 + pr.ID % 5) || '.' || ${branchIndex("br.ID")} || '.' || ${buildIndex("bu.ID")}"
        insertDocuments(
            jdbc, """
                SELECT '$TYPE_RELEASE', bu.ID::TEXT, pr.ID, 'BUILD', bu.ID,
                       $release, ${identifiers(release)}, NULL,
                       jsonb_build_object('build', $buildData, 'release', $release),
                       ${time("bu.ID", "10 minutes")}, $now
                $buildJoins
                WHERE bu.ID % $RELEASE_EVERY = 0
            """
        )
        // Build links, one per build, to a build of another project
        val target = "(1 + ((bu.ID - 1) * 7919 + ${builds / 2}) % $builds)"
        insertDocuments(
            jdbc, """
                SELECT '$TYPE_BUILD_LINK', bu.ID || '::' || tbu.ID || '::', pr.ID, 'BUILD', bu.ID,
                       tpr.NAME || ':' || coalesce(${buildDisplayName("tbu.ID")}, tbu.NAME),
                       ${identifiers("tpr.NAME || ':' || tbu.NAME", "tpr.NAME || ':' || ${buildDisplayName("tbu.ID")}")},
                       NULL,
                       jsonb_build_object('sourceBuild', $buildData, 'targetBuild', jsonb_build_object('id', tbu.ID, 'name', tbu.NAME), 'qualifier', ''),
                       ${time("bu.ID", "10 minutes")}, $now
                $buildJoins
                JOIN BUILDS tbu ON tbu.ID = $target
                JOIN BRANCHES tbr ON tbr.ID = tbu.BRANCHID
                JOIN PROJECTS tpr ON tpr.ID = tbr.PROJECTID
            """
        )
        // Issues
        val issueKey = "${issuePrefix("pr.ID")} || '-' || (1000 + (i - 1) / $projects)"
        insertDocuments(
            jdbc, """
                SELECT '$TYPE_ISSUE', pr.ID || '::' || $issueKey, pr.ID, NULL, NULL,
                       $issueKey, ${identifiers(issueKey)}, NULL,
                       jsonb_build_object('project', $projectData, 'item', jsonb_build_object('projectName', pr.NAME, 'key', $issueKey, 'displayKey', $issueKey)),
                       ${time("i", "30 minutes")}, $now
                FROM generate_series(1, $issues) i
                JOIN PROJECTS pr ON pr.ID = 1 + (i - 1) % $projects
            """
        )
        // Commits
        val commitId = "(md5('c' || c) || left(md5('d' || c), 8))"
        val shortId = "left(md5('c' || c), 7)"
        val message = """
            ${issuePrefix("pr.ID")} || '-' || (1000 + ${hash(1, "c")} % $ISSUES_PER_PROJECT) || ' '
            || ${pickRandom(VERBS, 2, "c")} || ' ' || ${pickRandom(NOUNS, 3, "c")} || ' ' || ${pickRandom(PREPOSITIONS, 4, "c")}
            || ' ' || ${pickRandom(MODULES, 5, "c")}
            || CASE WHEN ${hash(6, "c")} % 20 = 0 THEN E'\n\n' || repeat('This change ' || lower(${pickRandom(VERBS, 7, "c")}) || 's the '
               || ${pickRandom(NOUNS, 8, "c")} || ' of the ' || ${pickRandom(MODULES, 9, "c")} || ' module, which was reported by the users. ', 4)
               ELSE '' END
        """.trimIndent()
        insertDocuments(
            jdbc, """
                SELECT '$TYPE_COMMIT', pr.ID || '::' || $commitId, pr.ID, NULL, NULL,
                       $shortId, E'\n' || $commitId || E'\n' || $shortId || E'\n', $message,
                       jsonb_build_object('project', $projectData, 'item', jsonb_build_object('projectName', pr.NAME, 'id', $commitId, 'shortId', $shortId, 'author', ${pickRandom(AUTHORS, 10, "c")})),
                       ${time("c", "1 minute")}, $now
                FROM generate_series(1, $commits) c
                JOIN PROJECTS pr ON pr.ID = 1 + (c - 1) % $projects
            """
        )
    }

    /**
     * Number of documents per type, as loaded
     */
    fun documentCounts(jdbc: JdbcTemplate): Map<String, Int> =
        jdbc.queryForList("SELECT TYPE, COUNT(*) AS N FROM SEARCH_DOCUMENTS GROUP BY TYPE ORDER BY TYPE")
            .associate { it["type"] as String to (it["n"] as Number).toInt() }

    /**
     * Some build names, of all the styles, for the exact build scenario
     */
    fun sampleBuildNames(jdbc: JdbcTemplate, count: Int): List<String> {
        val step = max(1, builds / count)
        return jdbc.queryForList(
            "SELECT NAME FROM BUILDS WHERE ID % ? = 1 ORDER BY ID LIMIT ?",
            String::class.java,
            step,
            count,
        ).filterNotNull()
    }

    /**
     * Some commit IDs, full and abbreviated, for the commit lookup scenario
     */
    fun sampleCommits(count: Int): List<String> {
        val step = max(1, commits / count)
        return (0 until count).map { i ->
            val id = commitId(1 + i * step)
            if (i % 2 == 0) id else id.take(7)
        }
    }

    private fun timed(code: () -> Unit): Double {
        val start = System.nanoTime()
        code()
        return (System.nanoTime() - start) / 1e9
    }
}
