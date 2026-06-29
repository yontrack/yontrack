package net.nemerosa.ontrack.it

import net.nemerosa.ontrack.common.Document
import net.nemerosa.ontrack.model.buildfilter.BuildFilterProviderData
import net.nemerosa.ontrack.model.buildfilter.BuildFilterService
import net.nemerosa.ontrack.model.buildfilter.StandardFilterProviderDataBuilder
import net.nemerosa.ontrack.model.exceptions.BuildNotFoundException
import net.nemerosa.ontrack.model.labels.*
import net.nemerosa.ontrack.model.security.GlobalFunction
import net.nemerosa.ontrack.model.security.ValidationRunCreate
import net.nemerosa.ontrack.model.security.ValidationRunStatusChange
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.PredefinedPromotionLevelService
import net.nemerosa.ontrack.model.settings.PredefinedValidationStampService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.tx.DefaultTransactionHelper
import net.nemerosa.ontrack.model.tx.TransactionHelper
import net.nemerosa.ontrack.test.TestUtils
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.email
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

abstract class AbstractDSLTestSupport : AbstractServiceTestSupport() {

    @Autowired
    protected lateinit var labelManagementService: LabelManagementService

    @Autowired
    protected lateinit var projectLabelManagementService: ProjectLabelManagementService

    @Autowired
    protected lateinit var buildFilterService: BuildFilterService

    @Autowired
    protected lateinit var settingsService: CachedSettingsService

    @Autowired
    lateinit var settingsRepository: SettingsRepository

    @Autowired
    protected lateinit var predefinedPromotionLevelService: PredefinedPromotionLevelService

    @Autowired
    protected lateinit var predefinedValidationStampService: PredefinedValidationStampService

    @Autowired
    protected lateinit var runInfoService: RunInfoService

    /**
     * When working with asynchronous process, having the transaction isolated
     * at test level becomes very important so that asynchronous data can
     * have access to the test data.
     */

    @Autowired
    private lateinit var platformTransactionManager: PlatformTransactionManager

    private lateinit var transactionHelper: TransactionHelper

    @BeforeEach
    fun setupTxHelper() {
        transactionHelper = DefaultTransactionHelper(platformTransactionManager)
    }

    protected fun <T : Any> inNewTransaction(code: () -> T): T = transactionHelper.inNewTransaction(code)

    protected fun inNewTransactionAndRollback(code: () -> Unit) {
        val transactionTemplate = TransactionTemplate(platformTransactionManager)
        transactionTemplate.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        val status = platformTransactionManager.getTransaction(transactionTemplate)
        try {
            code()
        } finally {
            platformTransactionManager.rollback(status)
        }
    }

    protected inner class Then<T : Any>(private val initial: T) {
        infix fun then(code: (T) -> Unit) {
            inNewTransaction {
                code(initial)
            }
        }
    }

    protected fun <T : Any> startNewTransaction(code: () -> T): Then<T> {
        val result = inNewTransaction { code() }
        return Then(result)
    }

    /**
     * Kotlin friendly
     */
    protected final inline fun <reified T : GlobalFunction> asUserWith(noinline code: () -> Unit): Unit =
        asUserWith<T, Unit>(code)

    protected final inline fun <reified T : GlobalFunction, R> asUserWith(noinline code: () -> R): R =
        asUser().with(T::class.java).call(code)

    /**
     * Kotlin friendly
     */
    fun asUserWithView(vararg entities: ProjectEntity, code: () -> Unit) {
        asUserWithView(*entities).execute(code)
    }

    fun <T> ProjectEntity.asUserWithView(code: () -> T): T = asUserWithView(this).call(code)

    /**
     * Kotlin friendly anonymous execution
     */
    fun <T> asAnonymous(code: () -> T): T = asAnonymous().call(code)

    /**
     * Kotlin friendly admin execution
     */
    fun <T> asAdmin(code: () -> T): T = asAdmin().call(code)

    /**
     * Kotlin friendly user execution
     */
    fun <T> asUser(email: String = email(), code: () -> T): T = asUser(email = email).call(code)

    /**
     * Kotlin friendly account role execution
     */
    fun <T> ProjectEntity.asAccountWithProjectRole(role: String, code: () -> T): T {
        val account = doCreateAccountWithProjectRole(project, role)
        return asFixedAccount(account).call(code)
    }

    /**
     * Kotlin friendly account role execution
     */
    fun <T> asAccountWithGlobalRole(role: String, code: () -> T): T {
        val account = asAdmin {
            doCreateAccountWithGlobalRole(role)
        }
        return asFixedAccount(account).call(code)
    }

    fun <T> withDisabledConfigurationTest(code: () -> T): T {
        val configurationTest = ontrackConfigProperties.configurationTest
        ontrackConfigProperties.configurationTest = false
        return try {
            code()
        } finally {
            ontrackConfigProperties.configurationTest = configurationTest
        }
    }

    /**
     * Shortcut for getting a build
     */
    fun build(): Build {
        return project().branch().build()
    }

    fun project(name: NameDescription = nameDescription(), init: Project.() -> Unit = {}): Project {
        val project = doCreateProject(name)
        securityService.asAdmin {
            project.init()
        }
        return project
    }

    fun project(name: String, init: Project.() -> Unit = {}): Project {
        return project(
            NameDescription.nd(name, ""),
            init
        )
    }

    fun <T> project(init: Project.() -> T): T {
        val project = doCreateProject()
        return asAdmin().call {
            project.init()
        }
    }

    fun Project.branch(name: String = uid("B"), init: Branch.() -> Unit = {}): Branch {
        val branch = doCreateBranch(this, NameDescription.nd(name, ""))
        branch.init()
        return branch
    }

    fun <T> Project.branch(name: String = uid("B"), init: Branch.() -> T): T {
        val branch = doCreateBranch(this, NameDescription.nd(name, ""))
        return branch.init()
    }

    /**
     * Deleting all projects
     */
    fun deleteAllProjects() {
        asAdmin {
            structureService.projectList.forEach {
                structureService.deleteProject(it.id)
            }
        }
    }

    /**
     * Deletes a branch
     */
    fun Branch.delete() {
        asAdmin {
            structureService.deleteBranch(id)
        }
    }

    fun Branch.promotionLevel(
        name: String = uid("P"),
        description: String = "",
        fields: List<PromotionLevelField> = emptyList(),
        init: PromotionLevel.() -> Unit = {}
    ): PromotionLevel {
        val pl = doCreatePromotionLevel(this, NameDescription.nd(name, description))
        return if (fields.isNotEmpty()) {
            structureService.setPromotionLevelFields(pl.id, fields)
            structureService.getPromotionLevel(pl.id)
        } else {
            pl
        }.apply {
            init()
        }
    }

    /**
     * Creates and returns a validation stamp
     *
     * @receiver Branch to create a validation stamp for
     * @param name Name of the validation stamp to create
     * @return Created validation stamp
     */
    fun Branch.validationStamp(
        name: String = uid("VS"),
        validationDataTypeConfig: ValidationDataTypeConfig<*>? = null,
        description: String = "",
    ): ValidationStamp =
        doCreateValidationStamp(this, NameDescription.nd(name, description), validationDataTypeConfig)

    /**
     * Deletes a validation stamp
     */
    fun ValidationStamp.delete() {
        asAdmin {
            structureService.deleteValidationStamp(id)
        }
    }

    fun Branch.build(name: String = uid("B"), init: (Build.() -> Unit)? = {}): Build {
        val build = doCreateBuild(this, NameDescription.nd(name, ""))
        if (init != null) {
            build.init()
        }
        return build
    }

    fun <T> Branch.build(name: String, init: Build.() -> T): T {
        val build = doCreateBuild(this, NameDescription.nd(name, ""))
        return build.init()
    }

    /**
     * Updating the signature of a [Project].
     */
    fun Project.updateProjectSignature(
        user: String? = null,
        time: LocalDateTime? = null
    ) =
        structureService.saveProject(
            withSignature(
                Signature(time ?: signature.time, user?.let { User(user) } ?: signature.user)
            )
        )

    /**
     * Updating the signature of a [Branch].
     */
    fun Branch.updateBranchSignature(
        user: String? = null,
        time: LocalDateTime? = null
    ) {
        structureService.saveBranch(
            withSignature(
                Signature(time ?: signature.time, user?.let { User(user) } ?: signature.user)
            )
        )
    }

    /**
     * Updating the signature of a [Build].
     */
    fun Build.updateBuildSignature(
        user: String? = null,
        time: LocalDateTime? = null
    ) =
        structureService.saveBuild(
            withSignature(
                Signature(time ?: signature.time, user?.let { User(user) } ?: signature.user)
            )
        )

    protected fun <T, P : PropertyType<T>> ProjectEntity.property(type: KClass<P>, value: T?) {
        if (value != null) {
            propertyService.editProperty(
                this,
                type.java,
                value
            )
        } else {
            propertyService.deleteProperty(
                this,
                type.java
            )
        }
    }

    protected fun <T, P : PropertyType<T>> ProjectEntity.property(type: KClass<P>): T? =
        propertyService.getProperty(this, type.java).value

    fun Build.promote(
        promotionLevel: PromotionLevel,
        description: String = "",
        signature: Signature = Signature.of("test"),
        fieldValues: List<PromotionRunFieldValue> = emptyList(),
    ) =
        doPromote(this, promotionLevel, description, signature, fieldValues)

    fun Build.promote(promotionLevel: PromotionLevel, description: String = "", time: LocalDateTime) =
        promote(promotionLevel, description, signature = Signature.Companion.of(time, "test"))

    fun PromotionLevel.run() = branch.build().promote(this)

    /**
     * Creates a validation run on a build.
     *
     * @receiver Build to validate
     * @param validationStamp Stamp to apply
     * @param validationRunStatusID Status to apply
     */
    fun Build.validate(
        validationStamp: ValidationStamp,
        validationRunStatusID: ValidationRunStatusID = ValidationRunStatusID.STATUS_PASSED,
        description: String? = null,
        signature: Signature? = null,
        duration: Int? = null,
        code: ValidationRun.() -> Unit = {}
    ): ValidationRun {
        return this.validateWithData<Any>(
            validationStampName = validationStamp.name,
            validationRunStatusID = validationRunStatusID,
            description = description,
            signature = signature,
            duration = duration,
        ).apply {
            code()
        }
    }

    /**
     * Creates a validation run on a build with a specific time
     *
     * @receiver Build to validate
     * @param validationStamp Stamp to apply
     * @param validationRunStatusID Status to apply
     */
    fun Build.validateWithTime(
        validationStamp: ValidationStamp,
        validationRunStatusID: ValidationRunStatusID = ValidationRunStatusID.STATUS_PASSED,
        description: String? = null,
        time: LocalDateTime? = null,
        duration: Int? = null,
        code: ValidationRun.() -> Unit = {}
    ): ValidationRun = validate(
        validationStamp,
        validationRunStatusID,
        description,
        time?.let { Signature(it, User("test")) },
        duration,
        code,
    )

    /**
     * Creates a validation run on a build, possibly with some data and a status.
     */
    fun <T> Build.validateWithData(
        validationStamp: ValidationStamp,
        validationRunStatusID: ValidationRunStatusID? = null,
        validationDataTypeId: String? = null,
        validationRunData: T? = null,
        description: String? = null,
        signature: Signature? = null,
    ) = validateWithData(
        validationStampName = validationStamp.name,
        validationRunStatusID = validationRunStatusID,
        validationDataTypeId = validationDataTypeId,
        validationRunData = validationRunData,
        description = description,
        signature = signature,
    )

    /**
     * Creates a validation run on a build, possibly with some data and a status.
     */
    fun <T> Build.validateWithData(
        validationStampName: String,
        validationRunStatusID: ValidationRunStatusID? = null,
        validationDataTypeId: String? = null,
        validationRunData: T? = null,
        description: String? = null,
        signature: Signature? = null,
        duration: Int? = null,
    ): ValidationRun {
        return asUser().withView(this).withProjectFunction(this, ValidationRunCreate::class.java).call {
            val run = structureService.newValidationRun(
                this,
                ValidationRunRequest(
                    validationStampName = validationStampName,
                    dataTypeId = validationDataTypeId,
                    data = validationRunData,
                    validationRunStatusId = validationRunStatusID,
                    description = description,
                    signature = signature,
                )
            )
            // Run info duration
            if (duration != null) {
                runInfoService.setRunInfo(
                    run, RunInfoInput(
                        runTime = duration,
                    )
                )
            }
            // OK
            run
        }
    }

    /**
     * Deletes a build
     */
    fun Build.delete() {
        asAdmin {
            structureService.deleteBuild(id)
        }
    }

    fun Build.linkTo(project: Project, buildName: String, qualifier: String = "") {
        val build = structureService.buildSearch(
            project.id,
            BuildSearchForm(
                buildExactMatch = true,
                buildName = buildName,
            )
        ).firstOrNull() ?: throw BuildNotFoundException(project.name, buildName)
        linkTo(build, qualifier)
    }

    fun Build.linkTo(build: Build, qualifier: String = "") {
        structureService.createBuildLink(
            this,
            build,
            qualifier,
        )
    }

    infix fun Build.linkTo(build: Build) {
        structureService.createBuildLink(
            this,
            build,
            ""
        )
    }

    fun Build.unlinkTo(build: Build) {
        structureService.deleteBuildLink(
            this,
            build,
            ""
        )
    }

    /**
     * Change of status for a validation run
     */
    fun ValidationRun.validationStatus(status: ValidationRunStatusID, description: String): ValidationRun {
        return asUser().withProjectFunction(this, ValidationRunStatusChange::class.java).call {
            structureService.newValidationRunStatus(
                this,
                ValidationRunStatus(
                    ID.NONE,
                    Signature.of("test"),
                    status,
                    description
                )
            )
        }
    }

    /**
     * Change of status for a validation run
     */
    fun ValidationRun.validationStatusWithCurrentUser(
        status: ValidationRunStatusID,
        description: String
    ): ValidationRun {
        return structureService.newValidationRunStatus(
            this,
            ValidationRunStatus(
                ID.NONE,
                securityService.currentSignature,
                status,
                description
            )
        )
    }

    /**
     * Creates a label
     */
    fun label(category: String? = uid("C"), name: String = uid("N"), checkForExisting: Boolean = true): Label {
        return if (checkForExisting) {
            val labels = labelManagementService.findLabels(category, name)
            val existingLabel = labels.firstOrNull()
            existingLabel ?: labelManagementService.newLabel(
                LabelForm(
                    category = category,
                    name = name,
                    description = null,
                    color = "#FF0000"
                )
            )
        } else {
            labelManagementService.newLabel(
                LabelForm(
                    category = category,
                    name = name,
                    description = null,
                    color = "#FF0000"
                )
            )
        }
    }

    /**
     * Sets some labels to a project
     */
    var Project.labels: List<Label>
        get() = asUserWithView(this).call {
            projectLabelManagementService.getLabelsForProject(this)
        }
        set(value) {
            asUser().withProjectFunction(this, ProjectLabelManagement::class.java).execute {
                projectLabelManagementService.associateProjectToLabels(
                    this,
                    ProjectLabelForm(
                        value.map { it.id }
                    )
                )
            }
        }

    /**
     * Creation of a predefined promotion level
     */
    protected fun predefinedPromotionLevel(
        name: String = uid("ppl_"),
        description: String = "",
        image: Boolean = false
    ): PredefinedPromotionLevel =
        asAdmin {
            val ppl = predefinedPromotionLevelService.newPredefinedPromotionLevel(
                PredefinedPromotionLevel.of(
                    NameDescription.nd(name, description)
                )
            )
            if (image) {
                val document = Document("image/png", TestUtils.resourceBytes("/promotionLevelImage1.png"))
                predefinedPromotionLevelService.setPredefinedPromotionLevelImage(
                    ppl.id,
                    document
                )
            }
            ppl
        }

    /**
     * Creation of a predefined validation stamp
     */
    protected fun predefinedValidationStamp(
        name: String = uid("VS"),
        description: String = "",
        image: Boolean = false,
        dataType: ValidationDataTypeConfig<*>? = null
    ): PredefinedValidationStamp {
        return asAdmin {
            val pps = predefinedValidationStampService.newPredefinedValidationStamp(
                PredefinedValidationStamp.of(
                    NameDescription.nd(name, description)
                ).withDataType(dataType)
            )
            if (image) {
                val document = Document("image/png", TestUtils.resourceBytes("/validationStampImage1.png"))
                predefinedValidationStampService.setPredefinedValidationStampImage(
                    pps.id,
                    document
                )
            }
            pps
        }
    }

    /**
     * Saving current settings, runs some code and restores the format settings
     */
    protected final inline fun <reified T : Any> withSettings(noinline code: () -> Unit) {
        withSettings(T::class, code)
    }

    /**
     * Saving the the current settings and removing them for the duration of the code
     */
    protected final inline fun <reified T : Any> withCleanSettings(noinline code: () -> Unit) {
        withSettings<T> {
            cachedSettingsService.invalidate(T::class.java)
            settingsRepository.deleteAll(T::class.java)
            code()
        }
    }

    /**
     * Saving current settings, runs some code and restores the format settings
     */
    protected fun <T : Any> withSettings(settingsClass: KClass<T>, code: () -> Unit) {
        val settings: T = settingsService.getCachedSettings(settingsClass.java)
        // Runs the code
        code()
        // Restores the initial settings (only in case of success)
        asAdmin().execute {
            settingsManagerService.saveSettings(settings)
        }
    }

    protected fun Branch.assertBuildSearch(filterBuilder: (StandardFilterProviderDataBuilder) -> Unit): BuildSearchAssertion {
        val data = buildFilterService.standardFilterProviderData(10)
        filterBuilder(data)
        val filter = data.build()
        return BuildSearchAssertion(this, filter)
    }

    /**
     * Checking that a build has been validated
     */
    protected fun assertValidated(
        build: Build,
        validationStamp: ValidationStamp,
        status: ValidationRunStatusID? = ValidationRunStatusID.STATUS_PASSED,
    ) {
        val run = structureService.getValidationRunsForBuildAndValidationStamp(
            build, validationStamp, 0, 1
        ).firstOrNull()
        assertNotNull(run, "Build has been validated") {
            if (status != null) {
                assertEquals(
                    status,
                    it.lastStatus.statusID
                )
            }
        }
    }

    /**
     * Running some at all levels of a model
     */
    protected fun multiLevelTest(code: ProjectEntity.() -> Unit) {
        asAdmin {
            project {
                code()
                branch {
                    code()
                    val build = build {
                        code()
                    }
                    val vs = validationStamp().apply { code() }
                    val pl = promotionLevel { code() }
                    build.validate(vs).apply { code() }
                    build.promote(pl).apply { code() }
                }
            }
        }
    }

    protected class BuildSearchAssertion(
        private val branch: Branch,
        private val filter: BuildFilterProviderData<*>
    ) {
        infix fun returns(expected: Build) {
            returns(listOf(expected))
        }

        infix fun returns(expected: List<Build>) {
            val results = filter.filterBranchBuilds(branch)
            assertEquals(
                expected.map { it.id },
                results.map { it.id }
            )
        }
    }

}