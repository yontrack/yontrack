package net.nemerosa.ontrack.database

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * The rules of the expand and contract migrations (#2057), one fixture per rule under
 * `src/test/resources/migration/fixtures/<case>/`: a `migration` and a `contract` directory, and an
 * optional `allowlist.txt`. The fixtures use the baseline [BASELINE]: their files up to it are
 * never checked.
 */
class MigrationRulesTest {

    @Test
    fun `A drop in an expand migration is refused`() {
        assertViolations(
            "expand-drop",
            "V101" to MigrationRule.EXPAND_DROP,
            "V101" to MigrationRule.EXPAND_DROP,
            "V101" to MigrationRule.EXPAND_DROP,
            "V101" to MigrationRule.EXPAND_DROP,
        )
    }

    @Test
    fun `A rename in an expand migration is refused`() {
        assertViolations(
            "expand-rename",
            "V101" to MigrationRule.EXPAND_RENAME,
            "V101" to MigrationRule.EXPAND_RENAME,
            "V101" to MigrationRule.EXPAND_RENAME,
        )
    }

    @Test
    fun `A change of column type in an expand migration is refused`() {
        assertViolations(
            "expand-alter-type",
            "V101" to MigrationRule.EXPAND_ALTER_TYPE,
            "V101" to MigrationRule.EXPAND_ALTER_TYPE,
            "V101" to MigrationRule.EXPAND_ALTER_TYPE,
        )
    }

    @Test
    fun `A truncation in an expand migration is refused`() {
        assertViolations(
            "expand-truncate",
            "V101" to MigrationRule.EXPAND_TRUNCATE,
        )
    }

    @Test
    fun `A deletion of every row in an expand migration is refused, a deletion with a WHERE is not`() {
        assertViolations(
            "expand-delete-all",
            "V101" to MigrationRule.EXPAND_DELETE_ALL,
        )
    }

    @Test
    fun `A SET NOT NULL in an expand migration is refused`() {
        assertViolations(
            "expand-set-not-null",
            "V101" to MigrationRule.EXPAND_SET_NOT_NULL,
        )
    }

    @Test
    fun `A NOT NULL column without default added by an expand migration is refused`() {
        assertViolations(
            "expand-add-not-null-column",
            "V101" to MigrationRule.EXPAND_ADD_NOT_NULL_COLUMN,
        )
    }

    @Test
    fun `An index of an expand migration not created concurrently is refused`() {
        assertViolations(
            "expand-index-not-concurrent",
            "V101" to MigrationRule.EXPAND_INDEX_NOT_CONCURRENT,
            "V101" to MigrationRule.EXPAND_INDEX_NOT_CONCURRENT,
        )
    }

    @Test
    fun `A constraint added by an expand migration without NOT VALID is refused`() {
        assertViolations(
            "expand-constraint-not-valid",
            "V101" to MigrationRule.EXPAND_CONSTRAINT_NOT_VALID,
            "V101" to MigrationRule.EXPAND_CONSTRAINT_NOT_VALID,
        )
    }

    @Test
    fun `A table created by the script itself takes indexes, constraints and NOT NULL columns`() {
        assertViolations(
            "expand-new-table",
            "V101" to MigrationRule.EXPAND_INDEX_NOT_CONCURRENT,
        )
    }

    @Test
    fun `A script running CONCURRENTLY needs a conf file taking it out of the transaction`() {
        assertViolations(
            "concurrently-in-transaction",
            "V101" to MigrationRule.CONCURRENTLY_IN_TRANSACTION,
            "V102" to MigrationRule.CONCURRENTLY_IN_TRANSACTION,
        )
    }

    @Test
    fun `A contract migration only drops, deletes and updates`() {
        assertViolations(
            "contract-statement",
            "V101" to MigrationRule.CONTRACT_STATEMENT,
            "V101" to MigrationRule.CONTRACT_STATEMENT,
            "V101" to MigrationRule.CONTRACT_STATEMENT,
        )
    }

    @Test
    fun `An expand migration creating a name dropped by a contract of a lower version is refused`() {
        assertViolations(
            "recreates-contracted-name",
            "V102" to MigrationRule.RECREATES_CONTRACTED_NAME,
            "V102" to MigrationRule.RECREATES_CONTRACTED_NAME,
            "V102" to MigrationRule.RECREATES_CONTRACTED_NAME,
        )
    }

    @Test
    fun `A version used in both locations is refused, below the baseline too`() {
        assertViolations(
            "duplicate-version",
            "V50" to MigrationRule.DUPLICATE_VERSION,
            "V101" to MigrationRule.DUPLICATE_VERSION,
        )
    }

    @Test
    fun `An allow-listed version is not checked`() {
        assertViolations("allowlisted")
    }

    @Test
    fun `An allow-list entry exempting nothing is refused`() {
        assertViolations(
            "stale-allowlist",
            "V101" to MigrationRule.STALE_ALLOWLIST,
            "V102" to MigrationRule.STALE_ALLOWLIST,
        )
    }

    @Test
    fun `A code migration above the baseline needs an allow-list entry`() {
        assertViolations(
            "code-migration",
            "V101" to MigrationRule.CODE_MIGRATION,
        )
    }

    private fun assertViolations(case: String, vararg expected: Pair<String, MigrationRule>) {
        val violations = check(case)
        assertEquals(
            expected.map { (version, rule) -> "$version $rule" }.sorted(),
            violations.map { "V${it.version} ${it.rule}" }.sorted(),
            "Violations of the $case fixture:\n${violations.joinToString("\n")}",
        )
    }

    private fun check(case: String): List<MigrationViolation> {
        val dir = File(FIXTURES, case)
        val allowList = File(dir, "allowlist.txt")
        return MigrationRules(
            baseline = BASELINE,
            allowList = if (allowList.exists()) MigrationAllowList.read(allowList) else MigrationAllowList.EMPTY,
        ).check(
            expand = listOf(File(dir, "migration")),
            contract = listOf(File(dir, "contract")),
        )
    }

    companion object {
        private val FIXTURES = File("src/test/resources/migration/fixtures")
        private const val BASELINE = 100
    }
}
