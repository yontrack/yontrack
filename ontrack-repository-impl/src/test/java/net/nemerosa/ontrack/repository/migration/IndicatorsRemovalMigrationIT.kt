package net.nemerosa.ontrack.repository.migration

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountGroup
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.security.SecurityRole
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.repository.AbstractRepositoryTestSupport
import net.nemerosa.ontrack.repository.AccountGroupRepository
import net.nemerosa.ontrack.repository.AccountRepository
import net.nemerosa.ontrack.repository.RoleRepository
import net.nemerosa.ontrack.repository.StorageRepository
import net.nemerosa.ontrack.repository.support.store.EntityDataStore
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import tools.jackson.databind.node.IntNode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The migration removing the data of the indicators (#1893) deletes their data, their
 * settings and the grants of their roles - and nothing else.
 */
class IndicatorsRemovalMigrationIT : AbstractRepositoryTestSupport() {

    @Autowired
    private lateinit var storageRepository: StorageRepository

    @Autowired
    private lateinit var entityDataStore: EntityDataStore

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var accountGroupRepository: AccountGroupRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    private val indicatorStores = listOf(
        "net.nemerosa.ontrack.extension.indicators.model.IndicatorCategory",
        "net.nemerosa.ontrack.extension.indicators.model.IndicatorType",
        "net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorView",
        "net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorPortfolio",
        "net.nemerosa.ontrack.extension.indicators.computing.ConfigurableIndicatorState",
        "net.nemerosa.ontrack.extension.jenkins.indicator.JenkinsPipelineLibraryIndicatorSettings",
    )

    private val indicatorCategory = "net.nemerosa.ontrack.extension.indicators.model.Indicator"

    @Test
    fun `Indicators data, settings and grants are removed, and nothing else`() {
        // Indicator stores, and another store
        val key = uid("k-")
        indicatorStores.forEach { store ->
            storageRepository.storeJson(store, key, mapOf("key" to key).asJson())
        }
        val otherStore = uid("net.nemerosa.ontrack.OtherStore-")
        storageRepository.storeJson(otherStore, key, mapOf("key" to key).asJson())

        // Indicator values on a project, and another category of data on the same project
        val project = do_create_project()
        val signature = Signature.of("test")
        entityDataStore.add(project, indicatorCategory, "some-type", signature, null, IntNode(1))
        val otherCategory = uid("net.nemerosa.ontrack.OtherCategory-")
        entityDataStore.add(project, otherCategory, "some-type", signature, null, IntNode(2))

        // Grants of the indicator roles, and of other roles
        val indicatorManager = newAccount()
        roleRepository.saveGlobalRoleForAccount(indicatorManager, "GLOBAL_INDICATOR_MANAGER")
        val indicatorManagers = newGroup()
        roleRepository.saveGlobalRoleForGroup(indicatorManagers, "GLOBAL_INDICATOR_MANAGER")
        val projectIndicatorManager = newAccount()
        roleRepository.saveProjectRoleForAccount(project.id(), projectIndicatorManager, "PROJECT_INDICATOR_MANAGER")
        val projectIndicatorManagers = newGroup()
        roleRepository.saveProjectRoleForGroup(project.id(), projectIndicatorManagers, "PROJECT_INDICATOR_MANAGER")

        val controller = newAccount()
        roleRepository.saveGlobalRoleForAccount(controller, Roles.GLOBAL_CONTROLLER)
        val controllers = newGroup()
        roleRepository.saveGlobalRoleForGroup(controllers, Roles.GLOBAL_CONTROLLER)
        val projectOwner = newAccount()
        roleRepository.saveProjectRoleForAccount(project.id(), projectOwner, Roles.PROJECT_OWNER)
        val projectOwners = newGroup()
        roleRepository.saveProjectRoleForGroup(project.id(), projectOwners, Roles.PROJECT_OWNER)

        // Running the migration
        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V88__1893_indicators_removal.sql")
        ).execute(dataSource)

        // Indicator stores are gone, not the other one
        indicatorStores.forEach { store ->
            assertNull(storageRepository.retrieveJson(store, key), "Store $store is gone")
        }
        assertNotNull(storageRepository.retrieveJson(otherStore, key), "Other store is kept")

        // Indicator values are gone, not the other category
        assertEquals(
            0,
            entityDataStore.getCountByCategory(project, indicatorCategory),
            "Indicator values are gone"
        )
        assertEquals(
            1,
            entityDataStore.getCountByCategory(project, otherCategory),
            "Other category is kept"
        )

        // Indicator grants are gone, not the others
        assertEquals(null, roleRepository.findGlobalRoleByAccount(indicatorManager).orElse(null))
        assertEquals(null, roleRepository.findGlobalRoleByGroup(indicatorManagers).orElse(null))
        assertEquals(emptyList(), projectRoles("PROJECT_AUTHORIZATIONS", "ACCOUNT", projectIndicatorManager))
        assertEquals(emptyList(), projectRoles("GROUP_PROJECT_AUTHORIZATIONS", "ACCOUNTGROUP", projectIndicatorManagers))

        assertEquals(Roles.GLOBAL_CONTROLLER, roleRepository.findGlobalRoleByAccount(controller).orElse(null))
        assertEquals(Roles.GLOBAL_CONTROLLER, roleRepository.findGlobalRoleByGroup(controllers).orElse(null))
        assertEquals(listOf(Roles.PROJECT_OWNER), projectRoles("PROJECT_AUTHORIZATIONS", "ACCOUNT", projectOwner))
        assertEquals(listOf(Roles.PROJECT_OWNER), projectRoles("GROUP_PROJECT_AUTHORIZATIONS", "ACCOUNTGROUP", projectOwners))
    }

    private fun newAccount(): Int {
        val name = uid("user-")
        return accountRepository.newAccount(
            Account(
                id = ID.NONE,
                fullName = name,
                email = "$name@test.com",
                role = SecurityRole.USER,
            )
        ).id()
    }

    private fun newGroup(): Int =
        accountGroupRepository.newAccountGroup(
            AccountGroup(
                id = ID.NONE,
                name = uid("group-"),
                description = null,
            )
        ).id()

    private fun projectRoles(table: String, column: String, id: Int): List<String> =
        namedParameterJdbcTemplate.queryForList(
            "SELECT ROLE FROM $table WHERE $column = :id",
            mapOf("id" to id),
            String::class.java
        ).filterNotNull()

}
