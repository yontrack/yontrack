package net.nemerosa.ontrack.it

import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SecuritySettings
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.Branch.Companion.of
import net.nemerosa.ontrack.model.structure.Build.Companion.of
import net.nemerosa.ontrack.model.structure.ID.Companion.of
import net.nemerosa.ontrack.model.structure.Project.Companion.of
import net.nemerosa.ontrack.model.structure.PromotionRun.Companion.of
import net.nemerosa.ontrack.model.structure.Signature.Companion.of
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.AccountGroupRepository
import net.nemerosa.ontrack.repository.AccountRepository
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import java.util.concurrent.Callable
import kotlin.test.fail


abstract class AbstractServiceTestSupport : AbstractITTestSupport() {

    @Autowired
    protected lateinit var ontrackConfigProperties: OntrackConfigProperties

    @Autowired
    protected lateinit var accountService: AccountService

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var accountGroupRepository: AccountGroupRepository

    @Autowired
    protected lateinit var structureService: StructureService

    @Autowired
    protected lateinit var propertyService: PropertyService

    @Autowired
    protected lateinit var settingsManagerService: SettingsManagerService

    @Autowired
    lateinit var cachedSettingsService: CachedSettingsService

    @Autowired
    protected lateinit var securityService: SecurityService

    @Autowired
    protected lateinit var rolesService: RolesService

    @Autowired
    protected lateinit var accountACLService: AccountACLService

    @Autowired
    private lateinit var securityTestSupport: SecurityTestSupport

    protected fun doCreateAccountGroup(
        name: String = uid("G"),
    ): AccountGroup {
        return accountService.createGroup(
            AccountGroupInput(name, "")
        )
    }

    protected fun doCreateAccount(accountGroup: AccountGroup): Account {
        return doCreateAccount(listOf(accountGroup))
    }

    protected fun doCreateAccount(
        accountGroups: List<AccountGroup> = emptyList(),
        email: String = "${uid("A")}@test.com",
    ): Account {
        return accountService.create(
            AccountInput(
                "Test $email",
                email,
                accountGroups.map { it.id() },
            )
        )
    }

    protected fun doCreateAccountWithGlobalRole(role: String): Account {
        val account = doCreateAccount()
        accountService.saveGlobalPermission(
            PermissionTargetType.ACCOUNT,
            account.id(),
            PermissionInput(role)
        )
        return account
    }

    protected fun doCreateAccountWithProjectRole(project: Project, role: String): Account {
        val account = doCreateAccount()
        accountService.saveProjectPermission(
            project.id,
            PermissionTargetType.ACCOUNT,
            account.id(),
            PermissionInput(role)
        )
        return account
    }

    protected fun doCreateAccountGroupWithGlobalRole(role: String): AccountGroup {
        val group = doCreateAccountGroup()
        accountService.saveGlobalPermission(
            PermissionTargetType.GROUP,
            group.id(),
            PermissionInput(role)
        )
        return group
    }

    fun <T> setProperty(projectEntity: ProjectEntity, propertyTypeClass: Class<out PropertyType<T>>, data: T) {
        propertyService.editProperty(
            projectEntity,
            propertyTypeClass,
            data
        )
    }

    protected fun <T> deleteProperty(projectEntity: ProjectEntity, propertyTypeClass: Class<out PropertyType<T>>) {
        propertyService.deleteProperty(
            projectEntity,
            propertyTypeClass
        )
    }

    protected fun <T> getProperty(projectEntity: ProjectEntity, propertyTypeClass: Class<out PropertyType<T>>): T? {
        return propertyService.getProperty(
            projectEntity,
            propertyTypeClass
        ).value
    }

    protected fun <T> getRequiredProperty(
        projectEntity: ProjectEntity,
        propertyTypeClass: Class<out PropertyType<T>>
    ): T {
        return getProperty(projectEntity, propertyTypeClass) ?: fail("Property not found")
    }

    @JvmOverloads
    protected fun doCreateProject(nameDescription: NameDescription = nameDescription()): Project {
        return structureService.newProject(
            of(nameDescription)
        )
    }

    @JvmOverloads
    protected fun doCreateBranch(
        project: Project = doCreateProject(),
        nameDescription: NameDescription = nameDescription()
    ): Branch {
        return structureService.newBranch(
            of(project, nameDescription)
        )
    }

    @JvmOverloads
    fun doCreateBuild(
        branch: Branch = doCreateBranch(),
        nameDescription: NameDescription = nameDescription(),
        signature: Signature = of("test")
    ): Build {
        return structureService.newBuild(
            of(
                branch,
                nameDescription,
                signature
            )
        )
    }

    @JvmOverloads
    fun doValidateBuild(
        build: Build,
        vs: ValidationStamp,
        statusId: ValidationRunStatusID?,
        runData: ValidationRunData<*>? = null
    ): ValidationRun {
        return structureService.newValidationRun(
            build,
            ValidationRunRequest(
                vs.name,
                statusId,
                runData?.descriptor?.id,
                runData?.data,
                null
            )
        )
    }

    @JvmOverloads
    protected fun doCreatePromotionLevel(
        branch: Branch = doCreateBranch(),
        nameDescription: NameDescription = nameDescription()
    ): PromotionLevel {
        return structureService.newPromotionLevel(
            PromotionLevel.of(
                branch,
                nameDescription,
            )
        )
    }

    protected fun doCreateValidationStamp(): ValidationStamp {
        return doCreateValidationStamp(doCreateBranch(), nameDescription())
    }

    protected fun doCreateValidationStamp(config: ValidationDataTypeConfig<*>?): ValidationStamp {
        return doCreateValidationStamp(doCreateBranch(), nameDescription(), config)
    }

    @JvmOverloads
    fun doCreateValidationStamp(
        branch: Branch,
        nameDescription: NameDescription,
        config: ValidationDataTypeConfig<*>? = null
    ): ValidationStamp {
        return structureService.newValidationStamp(
            ValidationStamp.of(
                branch,
                nameDescription
            ).withDataType(config)
        )
    }

    @JvmOverloads
    protected fun doPromote(
        build: Build,
        promotionLevel: PromotionLevel,
        description: String?,
        signature: Signature = of("test"),
        fieldValues: List<PromotionRunFieldValue> = emptyList(),
    ): PromotionRun {
        return structureService.newPromotionRun(
            of(
                build,
                promotionLevel,
                signature,
                description
            ).withFieldValues(fieldValues)
        )
    }

    protected fun asUser(email: String = uid("u-") + "@ontrack.local"): UserCall = UserCall(email = email)

    protected fun asAdmin() = FixedAccountCall(
        account = securityTestSupport.createAdminAccount(),
    )

    protected fun asAnonymous(): AnonymousCall {
        return AnonymousCall()
    }

    protected fun asUserWithView(vararg entities: ProjectEntity): ConfigurableAccountCall {
        var user: ConfigurableAccountCall = asUser()
        for (entity in entities) {
            user = user.withView(entity)
        }
        return user
    }

    protected fun asFixedAccount(account: Account): AccountCall<*> {
        return FixedAccountCall(account)
    }

    protected fun <T> asFixedAccount(account: Account, code: () -> T): T = asFixedAccount(account).call(code)

    protected fun asConfigurableAccount(account: Account): ConfigurableAccountCall {
        return ConfigurableAccountCall(account)
    }

    protected fun asGlobalRole(role: String): AccountCall<*> {
        return FixedAccountCall(doCreateAccountWithGlobalRole(role))
    }

    protected fun <T> asGlobalRole(role: String, code: () -> T): T = asGlobalRole(role).call(code)

    protected fun <T> view(projectEntity: ProjectEntity, callable: Callable<T>): T {
        return asUser().with(projectEntity.projectId(), ProjectView::class.java).call { callable.call() }
    }

    /**
     * This must always be called from [withGrantViewToAll] or [withNoGrantViewToAll].
     */
    private fun securitySettings(settings: SecuritySettings): SecuritySettings {
        val old = cachedSettingsService.getCachedSettings(SecuritySettings::class.java)
        settingsManagerService.saveSettings(settings)
        return old
    }

    private fun <T> withSettings(grantViewToAll: Boolean, grantParticipationToAll: Boolean = true, task: () -> T): T {
        val old = asAdmin().call {
            securitySettings(
                SecuritySettings(
                    isGrantProjectViewToAll = grantViewToAll,
                    isGrantProjectParticipationToAll = grantParticipationToAll
                )
            )
        }
        return try {
            task()
        } finally {
            asAdmin().call {
                securitySettings(old)
            }
        }
    }

    protected fun <T> withGrantViewToAll(task: () -> T): T = withSettings(
        grantViewToAll = true,
        grantParticipationToAll = true,
        task = task
    )

    protected fun <T> withGrantViewAndNOParticipationToAll(task: () -> T): T = withSettings(
        grantViewToAll = true,
        grantParticipationToAll = false,
        task = task
    )

    protected fun <T> withNoGrantViewToAll(task: () -> T): T = withSettings(
        grantViewToAll = false,
        grantParticipationToAll = true,
        task = task
    )

    protected interface ContextCall {
        fun <T> call(call: () -> T): T
    }

    abstract class AbstractContextCall : ContextCall {

        override fun <T> call(call: () -> T): T {
            // Gets the current context
            val oldContext = SecurityContextHolder.getContext()
            return try {
                // Sets the new context
                contextSetup()
                // Call
                call()
            } finally {
                // Restores the context
                SecurityContextHolder.setContext(oldContext)
            }
        }

        fun execute(task: () -> Unit) = call(task)

        fun execute(task: Runnable) {
            call {
                task.run()
            }
        }

        protected abstract fun contextSetup()
    }

    protected class AnonymousCall : AbstractContextCall() {
        override fun contextSetup() {
            val context: SecurityContext = SecurityContextImpl()
            context.authentication = null
            SecurityContextHolder.setContext(context)
        }
    }

    abstract inner class AuthenticatedUserCall : AbstractContextCall() {

        override fun contextSetup() {
            val user = createOntrackAuthenticatedUser()
            securityTestSupport.setupSecurityContext(user, securityRole)
        }

        protected open val securityRole: SecurityRole = SecurityRole.USER

        protected abstract fun createOntrackAuthenticatedUser(): AuthenticatedUser

    }

    open inner class AccountCall<T : AccountCall<T>>(
        protected val account: Account
    ) : AuthenticatedUserCall() {

        override fun createOntrackAuthenticatedUser(): AuthenticatedUser =
            securityTestSupport.createOntrackAuthenticatedUser(account = account)

    }

    protected inner class FixedAccountCall(account: Account) : AccountCall<FixedAccountCall>(account)

    open inner class ConfigurableAccountCall(
        account: Account
    ) : AccountCall<ConfigurableAccountCall>(account) {

        /**
         * Global function associated to any global role to create
         */
        private val globalFunctions = mutableSetOf<Class<out GlobalFunction>>()

        /**
         * Project function associated to any project role to create
         */
        private val projectFunctions = mutableMapOf<Int, MutableSet<Class<out ProjectFunction>>>()

        /**
         * Associates a list of global functions to this account
         */
        @SafeVarargs
        fun with(vararg fn: Class<out GlobalFunction>): ConfigurableAccountCall {
            globalFunctions.addAll(fn)
            return this
        }

        /**
         * Associates a list of project functions for a given project to this account
         */
        fun with(projectId: Int, fn: Class<out ProjectFunction>): ConfigurableAccountCall {
            val projectFns = projectFunctions.getOrPut(projectId) { mutableSetOf() }
            projectFns.add(fn)
            return this
        }

        /**
         * Associates a list of project functions for a given project (designated by the [entity][e]) to this account
         */
        @Deprecated("Use withProjectFunction", ReplaceWith("withProjectFunction(e, fn)"))
        fun with(e: ProjectEntity, fn: Class<out ProjectFunction>): ConfigurableAccountCall {
            return with(e.projectId(), fn)
        }

        /**
         * Associates a list of project functions for a given project (designated by the [entity][e]) to this account
         */
        fun withProjectFunction(e: ProjectEntity, fn: Class<out ProjectFunction>): ConfigurableAccountCall {
            return with(e.projectId(), fn)
        }

        /**
         * Grants the [ProjectView] function to this account and the project designated by the [entity][e].
         */
        fun withView(e: ProjectEntity): ConfigurableAccountCall {
            return withProjectFunction(e, ProjectView::class.java)
        }

        override fun contextSetup() {
            val context: SecurityContext = SecurityContextImpl()
            val ontrackAuthenticatedUser = createOntrackAuthenticatedUser()
            val authentication = TestingAuthenticationToken(
                ontrackAuthenticatedUser,
                "",
                account.role.name
            )
            context.authentication = authentication
            SecurityContextHolder.setContext(context)
        }

        override fun createOntrackAuthenticatedUser(): AuthenticatedUser {
            // Configures the account
            securityService.asAdmin {
                // Creating a global role if some global functions are required
                if (globalFunctions.isNotEmpty()) {
                    val globalRoleId = uid("GR")
                    rolesService.registerGlobalRole(
                        id = globalRoleId,
                        name = "Test role $globalRoleId",
                        description = "Test role $globalRoleId",
                        globalFunctions = globalFunctions.toList(),
                        projectFunctions = emptyList()
                    )
                    accountService.saveGlobalPermission(
                        PermissionTargetType.ACCOUNT,
                        account.id(),
                        PermissionInput(globalRoleId)
                    )
                }
                // Project permissions
                projectFunctions.forEach { (projectId, functions) ->
                    if (functions.isNotEmpty()) {
                        val projectRoleId = uid("PR")
                        rolesService.registerProjectRole(
                            id = projectRoleId,
                            name = "Test role $projectRoleId",
                            description = "Test role $projectRoleId",
                            projectFunctions = functions.toList()
                        )
                        accountService.saveProjectPermission(
                            of(projectId),
                            PermissionTargetType.ACCOUNT,
                            account.id(),
                            PermissionInput(projectRoleId)
                        )
                    }
                }
            }
            // Loading the account
            return super.createOntrackAuthenticatedUser()
        }
    }

    protected inner class UserCall(
        email: String = uid("u-") + "@ontrack.local",
    ) : ConfigurableAccountCall(
        securityService.asAdmin {
            val accountInput = AccountInput(
                "$email von Test",
                email,
                emptyList(),
            )
            accountService.create(accountInput)
        }
    )
}

