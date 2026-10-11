package net.nemerosa.ontrack.database

import java.io.File

/**
 * A rule of the expand and contract migrations (#2057).
 */
enum class MigrationRule(val description: String) {
    EXPAND_DROP("an expand migration drops nothing: move the DROP to db/contract"),
    EXPAND_RENAME("an expand migration renames nothing: add the new name, copy, and drop the old one in db/contract"),
    EXPAND_ALTER_TYPE("a change of column type rewrites the table under an exclusive lock: add a column of the new type instead"),
    EXPAND_TRUNCATE("an expand migration truncates nothing: move the TRUNCATE to db/contract as a DELETE"),
    EXPAND_DELETE_ALL("a DELETE without WHERE belongs to db/contract"),
    EXPAND_SET_NOT_NULL("SET NOT NULL scans the table under an exclusive lock and fails the previous release's inserts"),
    EXPAND_ADD_NOT_NULL_COLUMN("a NOT NULL column needs a DEFAULT, or the previous release's inserts fail"),
    EXPAND_INDEX_NOT_CONCURRENT("an index on an existing table is created CONCURRENTLY, or it blocks the writes while it builds"),
    EXPAND_CONSTRAINT_NOT_VALID("a constraint on an existing table is added NOT VALID (then VALIDATE CONSTRAINT), or UNIQUE USING INDEX"),
    CONCURRENTLY_IN_TRANSACTION("a script running CONCURRENTLY needs a V<n>__x.sql.conf file with executeInTransaction=false"),
    CONTRACT_STATEMENT("a contract migration only drops (DROP, ALTER TABLE ... DROP), deletes (DELETE FROM) and updates the data of removed features (UPDATE)"),
    RECREATES_CONTRACTED_NAME("an expand migration runs before the pending contracts: it cannot create a name which a contract of a lower version drops"),
    DUPLICATE_VERSION("db/migration and db/contract share one version sequence: a version is used in one of them only"),
    CODE_MIGRATION("a Java or Kotlin migration cannot be checked: review it against the rules and allow-list it with a reason"),
    STALE_ALLOWLIST("this allow-list entry exempts no violation: delete its line"),
}

/**
 * A statement of a migration which breaks a [rule].
 */
data class MigrationViolation(
    val file: String,
    val version: Int,
    val rule: MigrationRule,
    val statement: String,
) {
    override fun toString() = "$file: $rule (${rule.description}): $statement"
}

/**
 * Versions exempted from the rules, one `V<n>: reason` per line, `#` starting a comment.
 */
class MigrationAllowList(val entries: Map<Int, String>) {
    companion object {
        val EMPTY = MigrationAllowList(emptyMap())

        private val LINE = Regex("""^V(\d+)\s*:\s*(.+)$""")

        fun read(file: File) = MigrationAllowList(
            file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { line ->
                    val match = LINE.matchEntire(line)
                        ?: error("${file.name}: `$line` is not a `V<n>: reason` line")
                    match.groupValues[1].toInt() to match.groupValues[2]
                }
        )
    }
}

/**
 * Checks the migrations of the expand (`db/migration`) and contract (`db/contract`) locations
 * above a [baseline] version, the files at or below it being grandfathered.
 */
class MigrationRules(
    private val baseline: Int,
    private val allowList: MigrationAllowList,
) {

    fun check(expand: List<File>, contract: List<File>): List<MigrationViolation> {
        val expandFiles = migrationFiles(expand)
        val contractFiles = migrationFiles(contract)
        val checked = expandFiles.filter { it.version > baseline }.flatMap { file ->
            checkCode(file) + checkExpand(file) + checkConcurrently(file) + checkRecreates(file, contractFiles)
        } + contractFiles.filter { it.version > baseline }.flatMap { file ->
            checkCode(file) + checkContract(file) + checkConcurrently(file)
        }
        val allowed = checked.filter { it.version in allowList.entries }
        val stale = allowList.entries.keys
            .filter { version -> allowed.none { it.version == version } }
            .map { version ->
                MigrationViolation("allow-list", version, MigrationRule.STALE_ALLOWLIST, "V$version: ${allowList.entries[version]}")
            }
        return checkDuplicates(expandFiles, contractFiles) + (checked - allowed.toSet()) + stale
    }

    /**
     * Expand and contract migrations share one version sequence, below the baseline as well.
     */
    private fun checkDuplicates(expandFiles: List<MigrationFile>, contractFiles: List<MigrationFile>): List<MigrationViolation> {
        val contractVersions = contractFiles.map { it.version }.toSet()
        return expandFiles.filter { it.version in contractVersions }.map { file ->
            file.violation(MigrationRule.DUPLICATE_VERSION, contractFiles.filter { it.version == file.version }.joinToString { it.file.name })
        }
    }

    private fun checkCode(file: MigrationFile): List<MigrationViolation> =
        if (file.code) listOf(file.violation(MigrationRule.CODE_MIGRATION, file.file.name)) else emptyList()

    private fun checkContract(file: MigrationFile): List<MigrationViolation> =
        file.statements.filterNot { statement ->
            statement.startsWith("DROP ") ||
                    statement.startsWith("DELETE FROM ") ||
                    statement.startsWith("UPDATE ") ||
                    alterTableActions(statement).let { actions -> actions.isNotEmpty() && actions.all { it.startsWith("DROP ") } }
        }.map { file.violation(MigrationRule.CONTRACT_STATEMENT, it) }

    /**
     * Startup runs every pending expand before any pending contract, whatever their versions: an
     * expand creating a name which a contract of a lower version drops would see it dropped.
     */
    private fun checkRecreates(file: MigrationFile, contractFiles: List<MigrationFile>): List<MigrationViolation> {
        val dropped = contractFiles.filter { it.version <= file.version }.flatMap { droppedNames(it) }.toSet()
        return file.statements.flatMap { statement ->
            createdNames(statement).filter { it in dropped }.map { file.violation(MigrationRule.RECREATES_CONTRACTED_NAME, statement) }
        }
    }

    /**
     * Tables, indexes and columns (as `TABLE.COLUMN`) created by a statement.
     */
    private fun createdNames(statement: String): List<String> {
        CREATE_TABLE.find(statement)?.let { return listOf(name(it.groupValues[2])) }
        CREATE_INDEX_NAME.find(statement)?.let { return listOf(name(it.groupValues[1])) }
        val table = alterTableName(statement) ?: return emptyList()
        return alterTableActions(statement)
            .filter { isAddColumn(it) }
            .mapNotNull { ADD_COLUMN.find(it)?.groupValues?.get(1)?.let { column -> "$table.${name(column)}" } }
    }

    /**
     * Tables, indexes and columns (as `TABLE.COLUMN`) dropped by a contract.
     */
    private fun droppedNames(file: MigrationFile): List<String> =
        file.statements.flatMap { statement ->
            DROP_OBJECTS.find(statement)?.let { match ->
                return@flatMap match.groupValues[1].split(',').map { name(it.trim().substringBefore(' ')) }
            }
            val table = alterTableName(statement) ?: return@flatMap emptyList()
            alterTableActions(statement).mapNotNull { action ->
                DROP_COLUMN.find(action)?.groupValues?.get(1)?.let { column -> "$table.${name(column)}" }
            }
        }

    private fun checkExpand(file: MigrationFile): List<MigrationViolation> {
        // Tables created by the script: empty, and unknown to the previous release
        val newTables = file.statements.mapNotNull { CREATE_TABLE.find(it)?.groupValues?.get(2)?.let(::name) }.toSet()
        return file.statements.flatMap { statement ->
            buildList {
                val actions = alterTableActions(statement)
                val onNewTable = alterTableName(statement)?.let { it in newTables } ?: false
                if (!onNewTable) {
                    if (SET_NOT_NULL.containsMatchIn(statement)) {
                        add(file.violation(MigrationRule.EXPAND_SET_NOT_NULL, statement))
                    }
                    actions.filter { isAddColumn(it) && NOT_NULL.containsMatchIn(it) && !DEFAULT.containsMatchIn(it) }.forEach {
                        add(file.violation(MigrationRule.EXPAND_ADD_NOT_NULL_COLUMN, statement))
                    }
                    actions.filter { isAddConstraint(it) && !NOT_VALID.containsMatchIn(it) && !USING_INDEX.containsMatchIn(it) }.forEach {
                        add(file.violation(MigrationRule.EXPAND_CONSTRAINT_NOT_VALID, statement))
                    }
                }
                CREATE_INDEX.find(statement)?.let { index ->
                    if (index.groupValues[2].isBlank() && name(index.groupValues[3]) !in newTables) {
                        add(file.violation(MigrationRule.EXPAND_INDEX_NOT_CONCURRENT, statement))
                    }
                }
                if (statement.startsWith("DROP ") || actions.any { it.startsWith("DROP ") }) {
                    add(file.violation(MigrationRule.EXPAND_DROP, statement))
                }
                if (RENAME.containsMatchIn(statement)) {
                    add(file.violation(MigrationRule.EXPAND_RENAME, statement))
                }
                actions.filter { ALTER_TYPE.containsMatchIn(it) }.forEach {
                    add(file.violation(MigrationRule.EXPAND_ALTER_TYPE, statement))
                }
                if (statement.startsWith("TRUNCATE ")) {
                    add(file.violation(MigrationRule.EXPAND_TRUNCATE, statement))
                }
                if (statement.startsWith("DELETE FROM ") && !WHERE.containsMatchIn(statement)) {
                    add(file.violation(MigrationRule.EXPAND_DELETE_ALL, statement))
                }
            }
        }
    }

    /**
     * A script running a statement `CONCURRENTLY` runs outside a transaction, through its
     * `.sql.conf` file.
     */
    private fun checkConcurrently(file: MigrationFile): List<MigrationViolation> {
        val statement = file.statements.firstOrNull { CONCURRENTLY.containsMatchIn(it) } ?: return emptyList()
        val conf = File(file.file.parentFile, "${file.file.name}.conf")
        val outside = conf.exists() && conf.readLines().any {
            it.replace(" ", "").equals("executeInTransaction=false", ignoreCase = true)
        }
        return if (outside) emptyList() else listOf(file.violation(MigrationRule.CONCURRENTLY_IN_TRANSACTION, statement))
    }

    private fun isAddColumn(action: String) =
        action.startsWith("ADD ") && !ADD_CONSTRAINT.containsMatchIn(action)

    private fun isAddConstraint(action: String) = ADD_CONSTRAINT.containsMatchIn(action)

    private fun alterTableName(statement: String): String? =
        ALTER_TABLE.find(statement)?.groupValues?.get(3)?.let(::name)

    /**
     * Name of a table, an index or a column, without its schema and its quotes.
     */
    private fun name(qualified: String) = qualified.substringAfterLast('.').trim('"')

    /**
     * Actions of an `ALTER TABLE` statement, split on their top-level commas, or nothing for any
     * other statement.
     */
    private fun alterTableActions(statement: String): List<String> {
        val match = ALTER_TABLE.find(statement) ?: return emptyList()
        return splitTopLevel(statement.substring(match.range.last + 1), ',')
    }

    private fun migrationFiles(dirs: List<File>): List<MigrationFile> =
        dirs.filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles().orEmpty().toList() }
            .mapNotNull { file ->
                val match = FILE_NAME.matchEntire(file.name) ?: return@mapNotNull null
                MigrationFile(
                    file = file,
                    version = match.groupValues[1].toInt(),
                    code = !file.name.endsWith(".sql"),
                    statements = if (file.name.endsWith(".sql")) statements(file.readText()) else emptyList(),
                )
            }
            .sortedBy { it.version }

    private class MigrationFile(
        val file: File,
        val version: Int,
        val code: Boolean,
        val statements: List<String>,
    ) {
        fun violation(rule: MigrationRule, statement: String) =
            MigrationViolation(file.name, version, rule, statement)
    }

    companion object {

        private val FILE_NAME = Regex("""^V(\d+)__.+\.(sql|kt|java)$""")
        private val ALTER_TABLE = Regex("""^ALTER TABLE (IF EXISTS )?(ONLY )?(\S+) """)
        private val RENAME = Regex("""\bRENAME\b""")
        private val ALTER_TYPE = Regex("""^ALTER (COLUMN )?\S+ (SET DATA )?TYPE\b""")
        private val WHERE = Regex("""\bWHERE\b""")
        private val CREATE_TABLE = Regex("""^CREATE (UNLOGGED )?TABLE (?:IF NOT EXISTS )?([^\s(]+)""")
        private val CREATE_INDEX = Regex("""^CREATE (UNIQUE )?INDEX (CONCURRENTLY )?(?:IF NOT EXISTS )?(?:\S+ )?ON (?:ONLY )?([^\s(]+)""")
        private val SET_NOT_NULL = Regex("""\bSET NOT NULL\b""")
        private val NOT_NULL = Regex("""\bNOT NULL\b""")
        private val DEFAULT = Regex("""\bDEFAULT\b""")
        private val ADD_CONSTRAINT = Regex("""^ADD (CONSTRAINT|PRIMARY KEY|UNIQUE|FOREIGN KEY|CHECK|EXCLUDE)\b""")
        private val NOT_VALID = Regex("""\bNOT VALID\b""")
        private val USING_INDEX = Regex("""\bUSING INDEX\b""")
        private val CONCURRENTLY = Regex("""\bCONCURRENTLY\b""")
        private val CREATE_INDEX_NAME = Regex("""^CREATE (?:UNIQUE )?INDEX (?:CONCURRENTLY )?(?:IF NOT EXISTS )?(\S+) ON """)
        private val ADD_COLUMN = Regex("""^ADD (?:COLUMN )?(?:IF NOT EXISTS )?(\S+)""")
        private val DROP_OBJECTS = Regex("""^DROP (?:TABLE|INDEX) (?:CONCURRENTLY )?(?:IF EXISTS )?(.+?)(?: CASCADE| RESTRICT)?$""")
        private val DROP_COLUMN = Regex("""^DROP (?!CONSTRAINT )(?:COLUMN )?(?:IF EXISTS )?(\S+)""")

        /**
         * Statements of a script, without their comments and string literals, in upper case, on one
         * line each with single spaces.
         */
        fun statements(sql: String): List<String> {
            val text = StringBuilder()
            var i = 0
            while (i < sql.length) {
                val c = sql[i]
                when {
                    sql.startsWith("--", i) -> {
                        i = sql.indexOf('\n', i).takeIf { it >= 0 } ?: sql.length
                    }

                    sql.startsWith("/*", i) -> {
                        i = (sql.indexOf("*/", i + 2).takeIf { it >= 0 } ?: sql.length) + 2
                        text.append(' ')
                    }

                    c == '\'' -> {
                        // String literal, '' being an escaped quote
                        i++
                        while (i < sql.length) {
                            if (sql[i] == '\'') {
                                if (i + 1 < sql.length && sql[i + 1] == '\'') i++ else break
                            }
                            i++
                        }
                        i++
                        text.append("''")
                    }

                    else -> {
                        text.append(c)
                        i++
                    }
                }
            }
            return text.split(';')
                .map { it.replace(Regex("\\s+"), " ").trim().uppercase() }
                .filter { it.isNotEmpty() }
        }

        /**
         * Splits a text on a separator, outside of any parentheses.
         */
        fun splitTopLevel(text: String, separator: Char): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            val current = StringBuilder()
            for (c in text) {
                when {
                    c == '(' -> depth++
                    c == ')' -> depth--
                }
                if (c == separator && depth == 0) {
                    parts += current.toString().trim()
                    current.clear()
                } else {
                    current.append(c)
                }
            }
            parts += current.toString().trim()
            return parts.filter { it.isNotEmpty() }
        }
    }
}
