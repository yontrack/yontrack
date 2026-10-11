package net.nemerosa.ontrack.database

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks the migrations of this module against the expand and contract rules (#2057): see
 * [MigrationRule] and `doc/dev-guide/database-migrations.md`.
 */
class MigrationRulesRepositoryTest {

    @Test
    fun `The migrations above the baseline follow the expand and contract rules`() {
        val violations = MigrationRules(
            baseline = BASELINE,
            allowList = MigrationAllowList.read(ALLOW_LIST),
        ).check(expand = EXPAND, contract = CONTRACT)
        if (violations.isNotEmpty()) {
            fail(
                "Migrations breaking the expand and contract rules (see doc/dev-guide/database-migrations.md):\n" +
                        violations.joinToString("\n") { "- $it" } +
                        "\nMove the statement to the right location, or allow-list its version in ${ALLOW_LIST.path} with a reason."
            )
        }
    }

    @Test
    fun `The baseline is a migration of db migration`() {
        assertTrue(
            EXPAND.any { dir -> dir.listFiles().orEmpty().any { it.name.startsWith("V${BASELINE}__") } },
            "V$BASELINE, the baseline of the rules, is a migration of db/migration"
        )
    }

    @Test
    fun `The rules catch the destructive migrations of 6_0, below the baseline`() {
        val violations = MigrationRules(
            baseline = 0,
            allowList = MigrationAllowList.EMPTY,
        ).check(expand = EXPAND, contract = CONTRACT)
            .map { "V${it.version} ${it.rule}" }
            .toSet()
        listOf(
            "V87 EXPAND_DROP",
            "V94 EXPAND_DROP",
            "V102 EXPAND_ALTER_TYPE",
            "V102 EXPAND_INDEX_NOT_CONCURRENT",
            "V29 CODE_MIGRATION",
        ).forEach {
            assertTrue(it in violations, "$it is caught")
        }
        // V105 creates its own table, and indexes it
        assertEquals(emptySet(), violations.filter { it.startsWith("V105 ") }.toSet())
    }

    companion object {
        /**
         * Latest migration when the rules were introduced (#2057): the migrations up to it are
         * grandfathered. Never change it.
         */
        const val BASELINE = 106

        private val EXPAND = listOf(File("src/main/resources/db/migration"), File("src/main/java/db/migration"))
        private val CONTRACT = listOf(File("src/main/resources/db/contract"), File("src/main/java/db/contract"))
        private val ALLOW_LIST = File("src/test/resources/migration/expand-allowlist.txt")
    }
}
