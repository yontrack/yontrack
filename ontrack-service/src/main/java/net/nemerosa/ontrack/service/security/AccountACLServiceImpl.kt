package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.dashboards.DashboardEdition
import net.nemerosa.ontrack.model.dashboards.DashboardSharing
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SecuritySettings
import net.nemerosa.ontrack.repository.RoleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass

@Service
@Transactional
class AccountACLServiceImpl(
    private val cachedSettingsService: CachedSettingsService,
    private val roleRepository: RoleRepository,
    private val rolesService: RolesService,
) : AccountACLService {

    override fun getAuthorizations(account: Account): Authorisations {
        return Authorisations()
            .withProjectFunctions(autoProjectFunctions)
            .withGlobalFunctions(autoGlobalFunctions)
            .withGlobalRoles(globalRoles(roleRepository.findGlobalRolesByAccount(account.id())))
            .withProjectRoles(roleRepository.findProjectRoleAssociationsByAccount(account.id()) { project: Int, roleId: String ->
                rolesService.getProjectRoleAssociation(
                    project,
                    roleId
                )
            })
    }

    override val autoProjectFunctions: Set<KClass<out ProjectFunction>>
        get() {
            val settings = cachedSettingsService.getCachedSettings(SecuritySettings::class.java)
            return if (settings.isGrantProjectViewToAll) {
                if (settings.isGrantProjectParticipationToAll) {
                    setOf(
                        ProjectView::class,
                        ValidationRunStatusChange::class,
                        ValidationRunStatusCommentEditOwn::class
                    )
                } else {
                    setOf(ProjectView::class)
                }
            } else {
                emptySet()
            }
        }

    override val autoGlobalFunctions: Set<KClass<out GlobalFunction>>
        get() {
            val settings = cachedSettingsService.getCachedSettings(SecuritySettings::class.java)
            val functions = mutableSetOf<KClass<out GlobalFunction>>()
            if (settings.grantDashboardEditionToAll) {
                functions += DashboardEdition::class
                if (settings.grantDashboardSharingToAll) {
                    functions += DashboardSharing::class
                }
            }
            return functions.toSet()
        }

    override fun getAuthorizedGroups(groups: List<AccountGroup>): List<AuthorizedGroup> =
        groups.map {
            AuthorizedGroup(
                group = it,
                authorisations = getGroupACL(it)
            )
        }

    private fun getGroupACL(group: AccountGroup): Authorisations =
        Authorisations()
            // Global roles
            .withGlobalRoles(globalRoles(roleRepository.findGlobalRolesByGroup(group.id())))
            // Project roles
            .withProjectRoles(
                roleRepository.findProjectRoleAssociationsByGroup(group.id()) { project: Int, roleId: String ->
                    rolesService.getProjectRoleAssociation(
                        project,
                        roleId,
                    )
                }
            )

    /**
     * Global roles from their IDs, ignoring the ones which are no longer defined.
     */
    private fun globalRoles(ids: List<String>): List<GlobalRole> =
        ids.mapNotNull { rolesService.getGlobalRole(it).getOrNull() }
}
