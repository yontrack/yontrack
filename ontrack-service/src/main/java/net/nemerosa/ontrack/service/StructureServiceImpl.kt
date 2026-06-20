package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.common.Document
import net.nemerosa.ontrack.extension.api.BuildSearchExtension
import net.nemerosa.ontrack.extension.api.BuildSearchExtensionNotFoundException
import net.nemerosa.ontrack.extension.api.BuildValidationExtension
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.events.BuildLinkListenerService
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.exceptions.*
import net.nemerosa.ontrack.model.extension.PromotionLevelPropertyType
import net.nemerosa.ontrack.model.extension.ValidationStampPropertyType
import net.nemerosa.ontrack.model.metrics.MetricsExportService
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.settings.PredefinedPromotionLevelService
import net.nemerosa.ontrack.model.settings.PredefinedValidationStampService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.Entity.Companion.isEntityDefined
import net.nemerosa.ontrack.model.structure.Entity.Companion.isEntityNew
import net.nemerosa.ontrack.model.support.ImageHelper.checkImage
import net.nemerosa.ontrack.model.support.UserTransaction
import net.nemerosa.ontrack.repository.*
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.function.BiFunction
import kotlin.jvm.optionals.getOrNull

@Service
@UserTransaction
class StructureServiceImpl(
    private val securityService: SecurityService,
    private val eventPostService: EventPostService,
    private val eventFactory: EventFactory,
    private val validationRunStatusService: ValidationRunStatusService,
    private val validationDataTypeService: ValidationDataTypeService,
    private val structureRepository: StructureRepository,
    private val projectRepository: ProjectRepository,
    private val branchRepository: BranchRepository,
    private val buildLinkRepository: BuildLinkRepository,
    private val extensionManager: ExtensionManager,
    private val propertyService: PropertyService,
    private val predefinedPromotionLevelService: PredefinedPromotionLevelService,
    private val predefinedValidationStampService: PredefinedValidationStampService,
    private val promotionRunCheckService: PromotionRunCheckService,
    private val statsRepository: StatsRepository,
    private val buildLinkListenerService: BuildLinkListenerService,
    private val coreBuildFilterRepository: CoreBuildFilterRepository,
    private val metricsExportService: MetricsExportService,
    private val promotionRunRepository: PromotionRunRepository,
    private val promotionLevelRepository: PromotionLevelRepository,
) : StructureService {

    private val logger = LoggerFactory.getLogger(StructureService::class.java)

    private val buildSearchExtensions: Map<String, BuildSearchExtension> by lazy {
        extensionManager.getExtensions(BuildSearchExtension::class.java).associateBy { it.id }
    }

    override val projectList: List<Project>
        get() {
            val list = structureRepository.projectList
            return when {
                securityService.isGlobalFunctionGranted(ProjectList::class.java) -> list
                securityService.isLogged -> list.filter { p ->
                    securityService.isProjectFunctionGranted(
                        p.id(),
                        ProjectView::class.java
                    )
                }

                else -> throw AccessDeniedException("Authentication is required.")
            }
        }

    override fun newProject(project: Project): Project {
        isEntityNew(project, "Project must be defined")
        securityService.checkGlobalFunction(ProjectCreation::class.java)
        val newProject = structureRepository.newProject(project.withSignature(securityService.currentSignature))
        eventPostService.post(eventFactory.newProject(newProject))
        return newProject
    }

    override fun findProjectByID(projectId: ID): Project? {
        return structureRepository.findProjectByID(projectId)?.takeIf {
            securityService.isProjectFunctionGranted(it.id(), ProjectView::class.java)
        }
    }

    override fun findProjectsByNamePattern(pattern: String): List<Project> {
        return structureRepository.findProjectsByNamePattern(pattern).filter {
            securityService.isProjectFunctionGranted(it.id(), ProjectView::class.java)
        }
    }

    override fun getProject(projectId: ID): Project {
        securityService.checkProjectFunction(projectId.value, ProjectView::class.java)
        return structureRepository.getProject(projectId)
    }

    override fun saveProject(project: Project) {
        isEntityDefined(project, "Project must be defined")
        securityService.checkProjectFunction(project.id(), ProjectEdit::class.java)
        structureRepository.saveProject(project)
        eventPostService.post(eventFactory.updateProject(project))
    }

    override fun disableProject(project: Project): Project {
        securityService.checkProjectFunction(project, ProjectDisable::class.java)
        val disabledProject = project.withDisabled(true)
        structureRepository.saveProject(disabledProject)
        eventPostService.post(eventFactory.disableProject(disabledProject))
        return disabledProject
    }

    override fun enableProject(project: Project): Project {
        securityService.checkProjectFunction(project, ProjectDisable::class.java)
        val enabledProject = project.withDisabled(false)
        structureRepository.saveProject(enabledProject)
        eventPostService.post(eventFactory.enableProject(enabledProject))
        return enabledProject
    }

    override fun deleteProject(projectId: ID): Ack {
        Validate.isTrue(projectId.isSet, "Project ID must be set")
        securityService.checkProjectFunction(projectId.value, ProjectDelete::class.java)
        eventPostService.post(eventFactory.deleteProject(getProject(projectId)))
        return structureRepository.deleteProject(projectId)
    }

    override fun findBranchByID(branchId: ID): Branch? =
        structureRepository.findBranchByID(branchId)?.takeIf {
            securityService.isProjectFunctionGranted(it, ProjectView::class.java)
        }

    override fun getBranch(branchId: ID): Branch {
        val branch = structureRepository.getBranch(branchId)
        securityService.checkProjectFunction(branch, ProjectView::class.java)
        return branch
    }

    override fun getBranchesForProject(projectId: ID): List<Branch> {
        securityService.checkProjectFunction(projectId.value, ProjectView::class.java)
        return structureRepository.getBranchesForProject(projectId)
    }

    override fun filterBranchesForProject(project: Project, filter: BranchFilter): List<Branch> {
        securityService.checkProjectFunction(project, ProjectView::class.java)
        // Getting the current user (used for the favorite filter)
        val accountId = securityService.currentUser?.account?.id
        // Specific query
        return branchRepository.filterBranchesForProject(project, accountId, filter)
    }

    override fun newBranch(branch: Branch): Branch {
        // Validation
        isEntityNew(branch, "Branch must be new")
        isEntityDefined(branch.project, "Project must be defined")
        // Security
        securityService.checkProjectFunction(branch.project.id(), BranchCreate::class.java)
        // Creating the branch
        val newBranch = structureRepository.newBranch(branch.withSignature(securityService.currentSignature))
        // Event
        eventPostService.post(eventFactory.newBranch(newBranch))
        // OK
        return newBranch
    }

    override fun saveBranch(branch: Branch) {
        isEntityDefined(branch, "Branch must be defined")
        isEntityDefined(branch.project, "Project must be defined")
        securityService.checkProjectFunction(branch.projectId(), BranchEdit::class.java)
        structureRepository.saveBranch(branch)
        eventPostService.post(eventFactory.updateBranch(branch))
    }

    override fun disableBranch(branch: Branch): Branch {
        securityService.checkProjectFunction(branch, BranchDisable::class.java)
        val disabledBranch = branch.withDisabled(true)
        saveBranch(disabledBranch)
        eventPostService.post(eventFactory.disableBranch(branch))
        return disabledBranch
    }

    override fun enableBranch(branch: Branch): Branch {
        securityService.checkProjectFunction(branch, BranchDisable::class.java)
        val disabledBranch = branch.withDisabled(false)
        saveBranch(disabledBranch)
        eventPostService.post(eventFactory.enableBranch(branch))
        return disabledBranch
    }

    override fun deleteBranch(branchId: ID): Ack {
        Validate.isTrue(branchId.isSet, "Branch ID must be set")
        val branch = getBranch(branchId)
        securityService.checkProjectFunction(branch.projectId(), BranchDelete::class.java)
        eventPostService.post(eventFactory.deleteBranch(branch))
        return structureRepository.deleteBranch(branchId)
    }

    override fun getLastPromotionRunForPromotionLevel(promotionLevel: PromotionLevel): PromotionRun? {
        securityService.checkProjectFunction(promotionLevel.projectId(), ProjectView::class.java)
        return structureRepository.getLastPromotionRunForPromotionLevel(promotionLevel)
    }

    override fun deletePromotionRun(promotionRunId: ID): Ack {
        Validate.isTrue(promotionRunId.isSet, "Promotion run ID must be set")
        val promotionRun = getPromotionRun(promotionRunId)
        securityService.checkProjectFunction(promotionRun, PromotionRunDelete::class.java)
        eventPostService.post(eventFactory.deletePromotionRun(promotionRun))
        return structureRepository.deletePromotionRun(promotionRunId)
    }

    override fun getEarliestPromotionRunAfterBuild(
        promotionLevel: PromotionLevel,
        build: Build
    ): Optional<PromotionRun> {
        securityService.checkProjectFunction(promotionLevel.projectId(), ProjectView::class.java)
        return structureRepository.getEarliestPromotionRunAfterBuild(promotionLevel, build)
    }

    override fun getPromotionRunsForPromotionLevel(promotionLevelId: ID): List<PromotionRun> {
        val promotionLevel = getPromotionLevel(promotionLevelId)
        return structureRepository.getPromotionRunsForPromotionLevel(promotionLevel)
    }

    override fun getLastBuildForBranch(branch: Branch): Build? {
        // Checks the accesses
        securityService.checkProjectFunction(branch.projectId(), ProjectView::class.java)
        // Gets the last build
        return structureRepository.getLastBuildForBranch(branch)
    }

    override fun getBuildCount(branch: Branch): Int {
        return structureRepository.getBuildCount(branch)
    }

    override fun getBuildCountForProject(project: Project): Int = structureRepository.getBuildCountForProject(project)

    override fun deleteBuild(buildId: ID): Ack {
        Validate.isTrue(buildId.isSet, "Build ID must be set")
        val build = getBuild(buildId)
        securityService.checkProjectFunction(build.projectId(), BuildDelete::class.java)
        eventPostService.post(eventFactory.deleteBuild(build))
        return structureRepository.deleteBuild(buildId)
    }

    override fun getPreviousBuild(buildId: ID): Build? {
        return structureRepository.getPreviousBuild(getBuild(buildId))
    }

    override fun getNextBuild(buildId: ID): Build? {
        return structureRepository.getNextBuild(getBuild(buildId))
    }

    protected fun validateBuild(build: Build) {
        extensionManager.getExtensions(BuildValidationExtension::class.java).forEach { x -> x.validateBuild(build) }
    }

    override fun newBuild(build: Build): Build {
        // Validation
        isEntityNew(build, "Build must be new")
        isEntityDefined(build.branch, "Branch must be defined")
        isEntityDefined(build.branch.project, "Project must be defined")
        // Security
        securityService.checkProjectFunction(build.branch.project.id(), BuildCreate::class.java)
        // Build validation
        validateBuild(build)
        // Repository
        val newBuild = structureRepository.newBuild(build)
        // Event
        eventPostService.post(eventFactory.newBuild(newBuild))
        // OK
        return newBuild
    }

    override fun saveBuild(build: Build): Build {
        // Validation
        isEntityDefined(build, "Build must be defined")
        isEntityDefined(build.branch, "Branch must be defined")
        isEntityDefined(build.branch.project, "Project must be defined")
        // Security
        securityService.checkProjectFunction(build.branch.project.id(), BuildEdit::class.java)
        // Signature change check
        validationSignatureChange(build)
        // Build validation
        validateBuild(build)
        // Repository
        val savedBuild = structureRepository.saveBuild(build)
        // Event
        eventPostService.post(eventFactory.updateBuild(savedBuild))
        // OK
        return getBuild(build.id)
    }

    private fun validationSignatureChange(build: Build) {
        // Get the original build signature
        val orig = getBuild(build.id)
        // Compares the signatures
        if (orig.signature != build.signature) {
            // Checks the authorisation
            securityService.checkProjectFunction(build, ProjectEdit::class.java)
        }
    }

    override fun findBuildByID(buildId: ID): Build? =
        structureRepository.findBuildByID(buildId)?.takeIf {
            securityService.isProjectFunctionGranted(it, ProjectView::class.java)
        }

    override fun getBuild(buildId: ID): Build {
        val build = structureRepository.getBuild(buildId)
        securityService.checkProjectFunction(build.branch.project.id(), ProjectView::class.java)
        return build
    }

    override fun findBuild(
        branchId: ID,
        sortDirection: BuildSortDirection,
        buildPredicate: (Build) -> Boolean
    ): Build? {
        // Gets the branch
        val branch = getBranch(branchId)
        // Build being found
        var ref: Build? = null
        // Loops over the builds
        structureRepository.builds(
            branch,
            { build ->
                val ok = buildPredicate(build)
                if (ok) {
                    ref = build
                }
                !ok // Going on if no match
            },
            sortDirection
        )
        // Result
        return ref
    }

    override fun forEachBuild(branch: Branch, sortDirection: BuildSortDirection, processor: (Build) -> Boolean) {
        findBuild(branch.id, sortDirection) { build ->
            val goingOn = processor(build)
            !goingOn // Found = Not going on
        }
    }

    override fun getLastBuild(branchId: ID): Optional<Build> {
        return Optional.ofNullable(
            structureRepository.getLastBuildForBranch(
                getBranch(branchId)
            )
        )
    }

    override fun buildSearch(projectId: ID, form: BuildSearchForm): List<Build> {
        // Gets the project
        val project = getProject(projectId)

        // Creating the helper
        val helper = object : CoreBuildFilterRepositoryHelper {

            override fun propertyTypeAccessor(type: String): PropertyType<*> =
                propertyService.getPropertyTypeByName<Any>(type)

            override fun contribute(
                extension: String,
                value: String,
                tables: MutableList<String>,
                criteria: MutableList<String>,
                params: MutableMap<String, Any?>
            ) {
                val searchExtension = buildSearchExtensions[extension]
                    ?: throw BuildSearchExtensionNotFoundException(extension)
                searchExtension.contribute(
                    value = value,
                    tables = tables,
                    criteria = criteria,
                    params = params
                )
            }

        }

        // Collects the builds for this project and this form
        return coreBuildFilterRepository.projectSearch(project, form, helper)
    }

    override fun createBuildLink(fromBuild: Build, toBuild: Build, qualifier: String) {
        securityService.checkProjectFunction(fromBuild, BuildConfig::class.java)
        securityService.checkProjectFunction(toBuild, ProjectView::class.java)
        buildLinkRepository.createBuildLink(fromBuild, toBuild, qualifier)
        buildLinkListenerService.onBuildLinkAdded(fromBuild, toBuild, qualifier)
    }

    override fun deleteBuildLink(fromBuild: Build, toBuild: Build, qualifier: String) {
        securityService.checkProjectFunction(fromBuild, BuildConfig::class.java)
        securityService.checkProjectFunction(toBuild, ProjectView::class.java)
        buildLinkRepository.deleteBuildLink(fromBuild, toBuild, qualifier)
        buildLinkListenerService.onBuildLinkDeleted(fromBuild, toBuild, qualifier)
    }

    override fun getCountQualifiedBuildsUsedBy(build: Build): Int {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        return buildLinkRepository.getCountQualifiedBuildsUsedBy(build)
    }

    override fun getQualifiedBuildsUsedBy(
        build: Build,
        offset: Int,
        size: Int,
        depth: Int,
        filter: (BuildLink) -> Boolean
    ): PaginatedList<BuildLink> {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        // First level dependency (depth == 0)
        val list = internalQualifiedBuildsUsedBy(
            build = build,
            depth = depth
        )
            // Filtering using ACL
            .filter { b ->
                securityService.isProjectFunctionGranted(b.build, ProjectView::class.java)
            }
        // OK
        return PaginatedList.create(list.filter(filter), offset, size)
    }

    private fun internalQualifiedBuildsUsedBy(
        build: Build,
        depth: Int,
    ): List<BuildLink> {
        // First level dependency (depth == 0)
        val list = buildLinkRepository.getQualifiedBuildsUsedBy(build)
        // Recursivity
        return if (depth > 0) {
            list.flatMap { link ->
                // The top lebel build itself
                listOf(link) +
                        // ... and its children (without any filter)
                        internalQualifiedBuildsUsedBy(
                            build = link.build,
                            depth = depth - 1, // One level less
                        )
            }
        } else {
            list
        }

    }

    override fun getQualifiedBuildsUsing(
        build: Build,
        offset: Int,
        size: Int,
        filter: (BuildLink) -> Boolean
    ): PaginatedList<BuildLink> {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        // Gets the complete list, filtered by ACL
        val list = buildLinkRepository.getQualifiedBuildsUsing(build)
            .filter { b -> securityService.isProjectFunctionGranted(b.build, ProjectView::class.java) }
        // OK
        return PaginatedList.create(list.filter { filter(it) }, offset, size)
    }

    override fun editBuildLinks(build: Build, form: BuildLinkForm) {
        securityService.checkProjectFunction(build, BuildConfig::class.java)
        // Gets the existing links, filtered by authorisations
        val authorisedExistingLinks = buildLinkRepository.getQualifiedBuildsUsedBy(build)
            .filter { securityService.isProjectFunctionGranted(it.build, ProjectView::class.java) }
        // Added links
        val addedLinks = mutableSetOf<BuildLink>()
        // Loops through the new links
        form.links.forEach { item ->
            // Gets the project if possible
            val project = findProjectByName(item.project)
                .orElseThrow { ProjectNotFoundException(item.project) }
            // Finds the build if possible (exact match - no regex)
            val builds = buildSearch(
                project.id, BuildSearchForm(
                    maximumCount = 1,
                    buildName = item.build,
                    buildExactMatch = true,
                )
            )
            if (builds.isNotEmpty()) {
                val target = builds[0]
                // Adds the link
                createBuildLink(build, target, item.qualifier)
                addedLinks.add(BuildLink(target, item.qualifier))
            } else {
                throw BuildNotFoundException(item.project, item.build)
            }
        }
        // Deletes all authorised links which were not added again
        if (!form.addOnly) {
            // Other links, not authorised to view, were not subject to edition and are not visible
            val linksToDelete = HashSet(authorisedExistingLinks)
            linksToDelete.removeAll(addedLinks)
            linksToDelete.forEach { link ->
                deleteBuildLink(
                    build,
                    link.build,
                    link.qualifier,
                )
            }
        }
    }

    override fun isLinkedFrom(build: Build, project: String, buildPattern: String?, qualifier: String?): Boolean {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        return buildLinkRepository.isLinkedFrom(build, project, buildPattern, qualifier)
    }

    override fun isLinkedTo(build: Build, project: String, buildPattern: String?, qualifier: String?): Boolean {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        return buildLinkRepository.isLinkedTo(build, project, buildPattern, qualifier)
    }

    override fun isLinkedTo(build: Build, targetBuild: Build, qualifier: String?): Boolean {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        securityService.checkProjectFunction(targetBuild, ProjectView::class.java)
        return buildLinkRepository.isLinkedTo(build, targetBuild, qualifier)
    }

    override fun forEachBuildLink(code: (from: Build, to: Build, qualifier: String) -> Unit) {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        buildLinkRepository.forEachBuildLink(code)
    }

    override fun getPromotionLevelListForBranch(branchId: ID): List<PromotionLevel> {
        val branch = getBranch(branchId)
        securityService.checkProjectFunction(branch.project.id(), ProjectView::class.java)
        return structureRepository.getPromotionLevelListForBranch(branchId)
    }

    override fun newPromotionLevel(promotionLevel: PromotionLevel): PromotionLevel {
        // Creation
        val newPromotionLevel = rawNewPromotionLevel(promotionLevel)
        // Checking if there is an associated predefined promotion level
        return securityService.asAdmin {
            val predefined: PredefinedPromotionLevel? =
                predefinedPromotionLevelService.findPredefinedPromotionLevelByName(promotionLevel.name)
            if (predefined != null) {
                // Description
                if (promotionLevel.description.isNullOrBlank()) {
                    savePromotionLevel(newPromotionLevel.withDescription(predefined.description))
                }
                // Image
                if (predefined.isImage) {
                    setPromotionLevelImage(
                        newPromotionLevel.id,
                        predefinedPromotionLevelService.getPredefinedPromotionLevelImage(predefined.id)
                    )
                }
                // Reloading
                getPromotionLevel(newPromotionLevel.id)
            } else {
                newPromotionLevel
            }
        }
    }

    private fun rawNewPromotionLevel(promotionLevel: PromotionLevel): PromotionLevel {
        // Validation
        isEntityNew(promotionLevel, "Promotion level must be new")
        isEntityDefined(promotionLevel.branch, "Branch must be defined")
        isEntityDefined(promotionLevel.branch.project, "Project must be defined")
        // Security
        securityService.checkProjectFunction(promotionLevel.branch.project.id(), PromotionLevelCreate::class.java)
        // Repository
        val newPromotionLevel = structureRepository.newPromotionLevel(
            promotionLevel.withSignature(securityService.currentSignature)
        )
        // Event
        eventPostService.post(eventFactory.newPromotionLevel(newPromotionLevel))
        // OK
        return newPromotionLevel
    }

    override fun getPromotionLevel(promotionLevelId: ID): PromotionLevel {
        val promotionLevel = structureRepository.getPromotionLevel(promotionLevelId)
        securityService.checkProjectFunction(promotionLevel.branch.project.id(), ProjectView::class.java)
        val fields = promotionLevelRepository.getPromotionLevelFields(promotionLevelId)
        return promotionLevel.withFields(fields)
    }

    override fun findPromotionLevelByID(promotionLevelId: ID): PromotionLevel? =
        structureRepository.findPromotionLevelByID(promotionLevelId)?.takeIf {
            securityService.isProjectFunctionGranted(it, ProjectView::class.java)
        }

    override fun getPromotionLevelImage(promotionLevelId: ID): Document {
        // Checks access
        getPromotionLevel(promotionLevelId)
        // Repository access
        return structureRepository.getPromotionLevelImage(promotionLevelId)
    }

    override fun setPromotionLevelImage(promotionLevelId: ID, document: Document?) {
        checkImage(document)
        // Checks access
        val promotionLevel = getPromotionLevel(promotionLevelId)
        securityService.checkProjectFunction(promotionLevel.branch.project.id(), PromotionLevelEdit::class.java)
        // Repository
        structureRepository.setPromotionLevelImage(promotionLevelId, document)
        // Event
        eventPostService.post(eventFactory.imagePromotionLevel(promotionLevel))
    }

    override fun savePromotionLevel(promotionLevel: PromotionLevel) {
        // Validation
        isEntityDefined(promotionLevel, "Promotion level must be defined")
        isEntityDefined(promotionLevel.branch, "Branch must be defined")
        isEntityDefined(promotionLevel.branch.project, "Project must be defined")
        // Security
        securityService.checkProjectFunction(promotionLevel.projectId(), PromotionLevelEdit::class.java)
        // Gets any corresponding predefined promotion level
        val ppl = securityService.asAdmin {
            predefinedPromotionLevelService.findPredefinedPromotionLevelByName(promotionLevel.name)
        }
        // Using the predefined description if the input's description is blank or null
        val actualPromotionLevel = if (ppl != null && promotionLevel.description.isNullOrBlank()) {
            promotionLevel.withDescription(ppl.description)
        } else {
            promotionLevel
        }
        // Repository
        structureRepository.savePromotionLevel(actualPromotionLevel)
        // Event
        eventPostService.post(eventFactory.updatePromotionLevel(actualPromotionLevel))
    }

    override fun deletePromotionLevel(promotionLevelId: ID): Ack {
        Validate.isTrue(promotionLevelId.isSet, "Promotion level ID must be set")
        val promotionLevel = getPromotionLevel(promotionLevelId)
        securityService.checkProjectFunction(promotionLevel.projectId(), PromotionLevelDelete::class.java)
        eventPostService.post(eventFactory.deletePromotionLevel(promotionLevel))
        return structureRepository.deletePromotionLevel(promotionLevelId)
    }

    override fun reorderPromotionLevels(branchId: ID, reordering: Reordering) {
        // Loads the branch
        val branch = getBranch(branchId)
        // Checks the access rights
        securityService.checkProjectFunction(branch.projectId(), PromotionLevelEdit::class.java)
        // Loads the promotion levels
        val promotionLevels = getPromotionLevelListForBranch(branchId)
        // Checks the size
        if (reordering.ids.size != promotionLevels.size) {
            throw ReorderingSizeException("The reordering request should have the same number of IDs as the number" + " of the promotion levels")
        }
        // Actual reordering
        structureRepository.reorderPromotionLevels(branchId, reordering)
        // Event
        eventPostService.post(eventFactory.reorderPromotionLevels(branch))
    }

    override fun setPromotionLevelFields(promotionLevelId: ID, fields: List<PromotionLevelField>) {
        val promotionLevel = getPromotionLevel(promotionLevelId)
        securityService.checkProjectFunction(promotionLevel, PromotionLevelEdit::class.java)
        promotionLevelRepository.setPromotionLevelFields(promotionLevelId, fields)
    }

    override fun newPromotionLevelFromPredefined(
        branch: Branch,
        predefinedPromotionLevel: PredefinedPromotionLevel
    ): PromotionLevel {
        val promotionLevel = rawNewPromotionLevel(
            PromotionLevel.of(
                branch,
                NameDescription.nd(predefinedPromotionLevel.name, predefinedPromotionLevel.description)
            )
        )

        // Makes sure the order is the same than for the predefined promotion levels
        val predefinedPromotionLevels =
            securityService.asAdmin { predefinedPromotionLevelService.predefinedPromotionLevels }
        val sortedIds = getPromotionLevelListForBranch(branch.id)
            .sortedBy { pl ->
                val name: String = pl.name
                predefinedPromotionLevels.indexOfFirst { pred -> pred.name == name }
            }
            .map { it.id() }
        reorderPromotionLevels(branch.id, Reordering(sortedIds))

        // Image?
        if (predefinedPromotionLevel.isImage) {
            setPromotionLevelImage(
                promotionLevel.id,
                predefinedPromotionLevelService.getPredefinedPromotionLevelImage(predefinedPromotionLevel.id)
            )
        }
        // OK
        return promotionLevel
    }

    override fun getOrCreatePromotionLevel(
        branch: Branch,
        promotionLevelId: Int?,
        promotionLevelName: String?
    ): PromotionLevel {
        if (promotionLevelId != null) {
            return getPromotionLevel(ID.of(promotionLevelId))
        } else if (promotionLevelName != null) {
            var oPromotionLevel = findPromotionLevelByName(
                branch.project.name,
                branch.name,
                promotionLevelName
            )
            if (oPromotionLevel.isPresent) {
                return oPromotionLevel.get()
            } else {
                val properties = propertyService.getProperties(branch.project)
                for (property in properties) {
                    val type = property.type
                    if (type is PromotionLevelPropertyType<*> && property.value != null) {
                        oPromotionLevel = getPromotionLevelFromProperty(
                            property,
                            branch,
                            promotionLevelName
                        )
                        if (oPromotionLevel.isPresent) {
                            return oPromotionLevel.get()
                        }
                    }
                }
                throw PromotionLevelNotFoundException(
                    branch.project.name,
                    branch.name,
                    promotionLevelName
                )
            }
        } else {
            throw PromotionRunRequestException()
        }
    }

    protected fun <T> getPromotionLevelFromProperty(
        property: Property<T>,
        branch: Branch,
        promotionLevelName: String
    ): Optional<PromotionLevel> {
        val promotionLevelPropertyType = property.type as PromotionLevelPropertyType<T>
        return promotionLevelPropertyType.getOrCreatePromotionLevel(
            property.value,
            branch,
            promotionLevelName
        )
    }

    override fun newPromotionRun(promotionRun: PromotionRun): PromotionRun {
        // Validation
        isEntityNew(promotionRun, "Promotion run must be new")
        isEntityDefined(promotionRun.build, "Build must be defined")
        isEntityDefined(promotionRun.promotionLevel, "Promotion level must be defined")
        Validate.isTrue(
            promotionRun.promotionLevel.branch.id() == promotionRun.build.branch.id(),
            "Promotion for a promotion level can be done only on the same branch than the build."
        )
        // Checks the authorization
        securityService.checkProjectFunction(promotionRun.build.branch.project.id(), PromotionRunCreate::class.java)
        // Validates the field values against the promotion level's field definitions
        validatePromotionRunFieldValues(promotionRun)
        // Checks the preconditions for the creation of the promotion run
        promotionRunCheckService.checkPromotionRunCreation(promotionRun)
        // Actual creation
        val newPromotionRun = structureRepository.newPromotionRun(promotionRun)
        // Save field values
        if (promotionRun.fieldValues.isNotEmpty()) {
            promotionRunRepository.savePromotionRunFieldValues(newPromotionRun.id, promotionRun.fieldValues)
        }
        // Event
        eventPostService.post(eventFactory.newPromotionRun(newPromotionRun))
        // OK
        return newPromotionRun.withFieldValues(promotionRun.fieldValues)
    }

    private fun validatePromotionRunFieldValues(promotionRun: PromotionRun) {
        val fields = promotionLevelRepository.getPromotionLevelFields(promotionRun.promotionLevel.id)
        val valuesByName = promotionRun.fieldValues.associateBy { it.name }
        for (field in fields) {
            val fv = valuesByName[field.name]
            val fvValue = fv?.value
            if (field.required && (fv == null || fvValue == null || fvValue.isNull)) {
                throw PromotionRunFieldRequiredException(field.name)
            }
            val value = fv?.value ?: continue
            when (field.type) {
                PromotionLevelFieldType.TEXT -> if (!value.isTextual) throw PromotionRunFieldTypeException(field.name, "TEXT")
                PromotionLevelFieldType.NUMBER -> if (!value.isNumber) throw PromotionRunFieldTypeException(field.name, "NUMBER")
                PromotionLevelFieldType.BOOLEAN -> if (!value.isBoolean) throw PromotionRunFieldTypeException(field.name, "BOOLEAN")
                PromotionLevelFieldType.CHOICE -> {
                    if (!value.isTextual) throw PromotionRunFieldTypeException(field.name, "CHOICE")
                    val chosen = value.asText()
                    if (chosen !in field.options) throw PromotionRunFieldChoiceException(field.name, chosen, field.options)
                }
                PromotionLevelFieldType.LINK -> if (!value.isTextual) throw PromotionRunFieldTypeException(field.name, "LINK")
            }
        }
    }

    override fun getPromotionRun(promotionRunId: ID): PromotionRun {
        val promotionRun = structureRepository.getPromotionRun(promotionRunId)
        securityService.checkProjectFunction(promotionRun.build.branch.project.id(), ProjectView::class.java)
        val fieldValues = promotionRunRepository.getPromotionRunFieldValues(promotionRunId)
        return promotionRun.withFieldValues(fieldValues)
    }

    override fun findPromotionRunByID(promotionRunId: ID): PromotionRun? =
        structureRepository.findPromotionRunByID(promotionRunId)?.takeIf {
            securityService.isProjectFunctionGranted(it, ProjectView::class.java)
        }

    override fun getPromotionRunsForBuild(buildId: ID): List<PromotionRun> {
        val build = getBuild(buildId)
        securityService.checkProjectFunction(build.branch.project.id(), ProjectView::class.java)
        return structureRepository.getPromotionRunsForBuild(build)
    }

    override fun getLastPromotionRunsForBuild(buildId: ID): List<PromotionRun> {
        val build = getBuild(buildId)
        securityService.checkProjectFunction(build.branch.project.id(), ProjectView::class.java)
        return structureRepository.getLastPromotionRunsForBuild(build)
    }

    override fun getLastPromotionRunsForBuild(build: Build, promotionLevels: List<PromotionLevel>): List<PromotionRun> {
        securityService.checkProjectFunction(build.branch.project.id(), ProjectView::class.java)
        return structureRepository.getLastPromotionRunsForBuild(build, promotionLevels)
    }

    override fun getLastPromotionRunForBuildAndPromotionLevel(
        build: Build,
        promotionLevel: PromotionLevel
    ): Optional<PromotionRun> {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        return structureRepository.getLastPromotionRun(build, promotionLevel)
    }

    override fun getPromotionRunsForBuildAndPromotionLevel(
        build: Build,
        promotionLevel: PromotionLevel
    ): List<PromotionRun> {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        return structureRepository.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel)
    }

    override fun getValidationStampListForBranch(branchId: ID): List<ValidationStamp> {
        val branch = getBranch(branchId)
        securityService.checkProjectFunction(branch.project.id(), ProjectView::class.java)
        return structureRepository.getValidationStampListForBranch(branchId)
    }

    override fun newValidationStamp(validationStamp: ValidationStamp): ValidationStamp {
        // Raw creation
        var newValidationStamp = rawNewValidationStamp(validationStamp)
        // Checking if there is an associated predefined validation stamp
        return securityService.asAdmin {
            val predefined: PredefinedValidationStamp? =
                predefinedValidationStampService.findPredefinedValidationStampByName(validationStamp.name)
            if (predefined != null) {
                // Description
                if (validationStamp.description.isNullOrBlank()) {
                    newValidationStamp = newValidationStamp.withDescription(predefined.description)
                    saveValidationStamp(newValidationStamp)
                }
                // Image
                if (predefined.isImage) {
                    setValidationStampImage(
                        newValidationStamp.id,
                        predefinedValidationStampService.getPredefinedValidationStampImage(predefined.id)
                    )
                }
                // Data type
                if (validationStamp.dataType == null && predefined.dataType != null) {
                    newValidationStamp = newValidationStamp.withDataType(predefined.dataType)
                    saveValidationStamp(newValidationStamp)
                }
                // Reloading
                getValidationStamp(newValidationStamp.id)
            } else {
                newValidationStamp
            }
        }
    }

    private fun rawNewValidationStamp(validationStamp: ValidationStamp): ValidationStamp {
        // Validation
        isEntityNew(validationStamp, "Validation stamp must be new")
        isEntityDefined(validationStamp.branch, "Branch must be defined")
        isEntityDefined(validationStamp.branch.project, "Project must be defined")
        // Security
        securityService.checkProjectFunction(validationStamp.branch.project.id(), ValidationStampCreate::class.java)
        // Repository
        val newValidationStamp = structureRepository.newValidationStamp(
            validationStamp.withSignature(securityService.currentSignature)
        )
        // Event
        eventPostService.post(eventFactory.newValidationStamp(newValidationStamp))
        // OK
        return newValidationStamp
    }

    override fun getValidationStamp(validationStampId: ID): ValidationStamp {
        val validationStamp = structureRepository.getValidationStamp(validationStampId)
        securityService.checkProjectFunction(validationStamp.branch.project.id(), ProjectView::class.java)
        return validationStamp
    }

    override fun findValidationStampByID(validationStampId: ID): ValidationStamp? =
        structureRepository.findValidationStampByID(validationStampId)?.takeIf {
            securityService.isProjectFunctionGranted(it, ProjectView::class.java)
        }

    override fun findValidationStampByName(
        project: String,
        branch: String,
        validationStamp: String
    ): Optional<ValidationStamp> {
        return structureRepository.getValidationStampByName(project, branch, validationStamp)
            .filter { vs -> securityService.isProjectFunctionGranted(vs, ProjectView::class.java) }
    }

    override fun findBuildByName(project: String, branch: String, build: String): Optional<Build> {
        return structureRepository.getBuildByName(project, branch, build)
            .filter { b -> securityService.isProjectFunctionGranted(b, ProjectView::class.java) }
    }

    override fun findBuildAfterUsingNumericForm(id: ID, buildName: String): Optional<Build> {
        return if (StringUtils.isNumeric(buildName)) {
            structureRepository.findBuildAfterUsingNumericForm(id, buildName)
        } else {
            throw IllegalArgumentException("Build name is expected to be numeric: $buildName")
        }
    }

    override fun getValidationStampImage(validationStampId: ID): Document {
        // Checks access
        getValidationStamp(validationStampId)
        // Repository access
        return structureRepository.getValidationStampImage(validationStampId)
    }

    override fun setValidationStampImage(validationStampId: ID, document: Document?) {
        // Checks the image type
        checkImage(document)
        // Checks access
        val validationStamp = getValidationStamp(validationStampId)
        securityService.checkProjectFunction(validationStamp.branch.project.id(), ValidationStampEdit::class.java)
        // Repository
        structureRepository.setValidationStampImage(validationStampId, document)
        // Event
        eventPostService.post(eventFactory.imageValidationStamp(validationStamp))
    }

    override fun saveValidationStamp(validationStamp: ValidationStamp) {
        // Validation
        isEntityDefined(validationStamp, "Validation stamp must be defined")
        isEntityDefined(validationStamp.branch, "Branch must be defined")
        isEntityDefined(validationStamp.branch.project, "Project must be defined")
        // Security
        securityService.checkProjectFunction(validationStamp.projectId(), ValidationStampEdit::class.java)
        // Gets any predefined validation stamp
        val predefined =
            predefinedValidationStampService.findPredefinedValidationStampByName(validationStamp.name)
        val actualValidationStamp = if (predefined != null) {
            // Adapting the description
            val description = if (validationStamp.description.isNullOrBlank()) {
                predefined.description
            } else {
                validationStamp.description
            }
            // Adapting the type
            val dataType = validationStamp.dataType ?: predefined.dataType
            // New stamp
            ValidationStamp(
                id = validationStamp.id,
                name = validationStamp.name,
                description = description,
                branch = validationStamp.branch,
                owner = validationStamp.owner,
                isImage = validationStamp.isImage,
                signature = validationStamp.signature,
                dataType = dataType,
            )
        } else {
            validationStamp
        }
        // Repository
        structureRepository.saveValidationStamp(actualValidationStamp)
        // Adapting the image from the predefined
        if (!validationStamp.isImage && predefined != null && predefined.isImage) {
            securityService.asAdmin {
                setValidationStampImage(
                    validationStamp.id,
                    predefinedValidationStampService.getPredefinedValidationStampImage(predefined.id)
                )
            }
        }
        // Event
        eventPostService.post(eventFactory.updateValidationStamp(actualValidationStamp))
    }

    override fun deleteValidationStamp(validationStampId: ID): Ack {
        Validate.isTrue(validationStampId.isSet, "Validation stamp ID must be set")
        val validationStamp = getValidationStamp(validationStampId)
        securityService.checkProjectFunction(validationStamp.projectId(), ValidationStampDelete::class.java)
        eventPostService.post(eventFactory.deleteValidationStamp(validationStamp))
        return structureRepository.deleteValidationStamp(validationStampId)
    }

    override fun reorderValidationStamps(branchId: ID, reordering: Reordering) {
        // Loads the branch
        val branch = getBranch(branchId)
        // Checks the access rights
        securityService.checkProjectFunction(branch.projectId(), ValidationStampEdit::class.java)
        // Loads the validation stamps
        val validationStamps = getValidationStampListForBranch(branchId)
        // Checks the size
        if (reordering.ids.size != validationStamps.size) {
            throw ReorderingSizeException("The reordering request should have the same number of IDs as the number" + " of the validation stamps")
        }
        // Actual reordering
        structureRepository.reorderValidationStamps(branchId, reordering)
        // Event
        eventPostService.post(eventFactory.reorderValidationStamps(branch))
    }

    override fun getOrCreateValidationStamp(branch: Branch, validationStampName: String): ValidationStamp {
        var oValidationStamp = findValidationStampByName(
            branch.project.name,
            branch.name,
            validationStampName
        )
        if (oValidationStamp.isPresent) {
            return oValidationStamp.get()
        } else {
            val properties = propertyService.getProperties(branch.project)
            for (property in properties) {
                val type = property.type
                if (type is ValidationStampPropertyType<*> && property.value != null) {
                    oValidationStamp = getValidationStampFromProperty(
                        property,
                        branch,
                        validationStampName
                    )
                    if (oValidationStamp.isPresent) {
                        return oValidationStamp.get()
                    }
                }
            }
            throw ValidationStampNotFoundException(
                branch.project.name,
                branch.name,
                validationStampName
            )
        }
    }

    override fun bulkUpdateValidationStamps(validationStampId: ID): Ack {
        // Checks access
        securityService.checkGlobalFunction(ValidationStampBulkUpdate::class.java)
        // As admin
        securityService.asAdmin {
            val validationStamp = getValidationStamp(validationStampId)
            // Defining or replacing the predefined validation stamp
            val o = predefinedValidationStampService.findPredefinedValidationStampByName(validationStamp.name)
            if (o != null) {
                // Updating the predefined validation stamp description & data type
                predefinedValidationStampService.savePredefinedValidationStamp(
                    o
                        .withDescription(validationStamp.description)
                        .withDataType(validationStamp.dataType)
                )
                // Sets its image
                val image = getValidationStampImage(validationStampId)
                predefinedValidationStampService.setPredefinedValidationStampImage(
                    o.id,
                    image
                )
            } else {
                // Creating the predefined validation stamp
                val predefinedValidationStamp = predefinedValidationStampService.newPredefinedValidationStamp(
                    PredefinedValidationStamp.of(
                        NameDescription.nd(
                            validationStamp.name,
                            validationStamp.description
                        )
                    ).withDataType(validationStamp.dataType)
                )
                // Sets its image
                val image = getValidationStampImage(validationStampId)
                if (!image.isEmpty) {
                    predefinedValidationStampService.setPredefinedValidationStampImage(
                        predefinedValidationStamp.id,
                        image
                    )
                }
            }
            // For all validation stamps
            structureRepository.bulkUpdateValidationStamps(validationStampId)
        }
        // OK
        return Ack.OK
    }

    override fun bulkUpdatePromotionLevels(promotionLevelId: ID): Ack {
        // Checks access
        securityService.checkGlobalFunction(GlobalSettings::class.java)
        // As admin
        securityService.asAdmin {
            val promotionLevel = getPromotionLevel(promotionLevelId)
            // Defining or replacing the predefined promotion level
            val o = predefinedPromotionLevelService.findPredefinedPromotionLevelByName(promotionLevel.name)
            if (o != null) {
                // Updating the predefined promotion level description
                predefinedPromotionLevelService.savePredefinedPromotionLevel(
                    o.withDescription(promotionLevel.description)
                )
                // Sets its image
                val image = getPromotionLevelImage(promotionLevelId)
                predefinedPromotionLevelService.setPredefinedPromotionLevelImage(
                    o.id,
                    image
                )
            } else {
                // Creating the predefined promotion level
                val predefinedPromotionLevel = predefinedPromotionLevelService.newPredefinedPromotionLevel(
                    PredefinedPromotionLevel.of(
                        NameDescription.nd(
                            promotionLevel.name,
                            promotionLevel.description
                        )
                    )
                )
                // Sets its image
                val image = getPromotionLevelImage(promotionLevelId)
                predefinedPromotionLevelService.setPredefinedPromotionLevelImage(
                    predefinedPromotionLevel.id,
                    image
                )
            }
            // For all promotion levels
            structureRepository.bulkUpdatePromotionLevels(promotionLevelId)
        }
        // OK
        return Ack.OK
    }

    protected fun <T> getValidationStampFromProperty(
        property: Property<T>,
        branch: Branch,
        validationStampName: String?
    ): Optional<ValidationStamp> {
        val validationStampPropertyType = property.type as ValidationStampPropertyType<T>
        return validationStampPropertyType.getOrCreateValidationStamp(
            property.value,
            branch,
            validationStampName
        )
    }

    override fun newValidationStampFromPredefined(branch: Branch, stamp: PredefinedValidationStamp): ValidationStamp {
        val validationStamp = rawNewValidationStamp(
            ValidationStamp.of(
                branch,
                NameDescription.nd(stamp.name, stamp.description)
            ).withDataType(stamp.dataType)
        )
        // Image?
        if (stamp.isImage) {
            setValidationStampImage(
                validationStamp.id,
                predefinedValidationStampService.getPredefinedValidationStampImage(stamp.id)
            )
        }
        // OK
        return validationStamp
    }

    override fun newValidationRun(build: Build, validationRunRequest: ValidationRunRequest): ValidationRun {
        // Gets the validation stamp
        val validationStamp = getOrCreateValidationStamp(
            build.branch,
            validationRunRequest.validationStampName
        )
        // Validation run data
        val rawDataTypeId: String? = validationRunRequest.dataTypeId
            ?: validationStamp.dataType?.descriptor?.id
        val rawData: Any? = validationRunRequest.data
        // Gets the data type
        val rawDataType: ValidationDataType<Any, Any>? = rawDataTypeId?.run {
            validationDataTypeService.getValidationDataType(this)
                ?: throw ValidationRunDataTypeNotFoundException(this)
        }
        // Type descriptor + data
        val rawRunData: ValidationRunData<Any>? = if (rawDataType != null && rawData != null) {
            ValidationRunData(
                rawDataType.descriptor,
                rawData
            )
        } else {
            null
        }
        // Validation of the run data
        val status: ValidationRunDataWithStatus<Any> = validationDataTypeService.validateData(
            rawRunData,
            validationStamp.dataType,
            validationRunRequest.validationRunStatusId
        )
        // Validation run to create
        val validationRun = ValidationRun.of(
            build,
            validationStamp,
            0,
            validationRunRequest.signature ?: securityService.currentSignature,
            status.runStatusID,
            validationRunRequest.description
        ).withData(status.runData)
        // Validation
        isEntityNew(validationRun, "Validation run must be new")
        isEntityDefined(validationRun.build, "Build must be defined")
        isEntityDefined(validationRun.validationStamp, "Validation stamp must be defined")
        Validate.isTrue(
            validationRun.validationStamp.branch.id() == validationRun.build.branch.id(),
            "Validation run for a validation stamp can be done only on the same branch than the build."
        )
        // Checks the authorization
        securityService.checkProjectFunction(validationRun.build.branch.project.id(), ValidationRunCreate::class.java)
        // Actual creation
        val newValidationRun =
            structureRepository.newValidationRun(validationRun) { validationRunStatusService.getValidationRunStatus(it) }
        // Event
        eventPostService.post(eventFactory.newValidationRun(newValidationRun))
        // Metrics
        publishValidationRunMetrics(newValidationRun)
        // Saves the properties
        for ((propertyTypeName, propertyData) in validationRunRequest.properties) {
            propertyService.editProperty(
                newValidationRun,
                propertyTypeName,
                propertyData
            )
        }
        // OK
        return newValidationRun
    }

    override fun deleteValidationRun(validationRun: ValidationRun): Ack {
        securityService.checkProjectFunction(validationRun, ProjectEdit::class.java)
        return structureRepository.deleteValidationRun(validationRun.id)
    }

    private fun publishValidationRunMetrics(validationRun: ValidationRun) {
        val validationRunData: ValidationRunData<*>? = validationRun.data
        if (validationRunData != null) {
            publishValidationRunMetricsData(validationRun, validationRunData)
        }
    }

    private fun <T> publishValidationRunMetricsData(
        validationRun: ValidationRun,
        validationRunData: ValidationRunData<T>,
    ) {
        val dataType: ValidationDataType<Any, T>? =
            validationDataTypeService.getValidationDataType(validationRunData.descriptor.id)
        if (dataType != null) {
            val metrics: Map<String, *>? = dataType.getMetrics(validationRunData.data)
            if (metrics != null && metrics.isNotEmpty()) {
                metricsExportService.exportMetrics(
                    metric = "validation_data",
                    tags = mapOf(
                        "project" to validationRun.project.name,
                        "branch" to validationRun.validationStamp.branch.name,
                        "validation" to validationRun.validationStamp.name,
                        "status" to validationRun.lastStatus.statusID.id,
                        "type" to validationRunData.descriptor.id,
                    ),
                    fields = metrics,
                    timestamp = validationRun.signature.time,
                )
            }
        }
    }

    override fun restoreValidationRunDataMetrics(logger: (String) -> Unit) {
        var count = 0
        val total = statsRepository.validationRunCount
        structureRepository.forEachValidationRun({ validationRunStatusService.getValidationRunStatus(it) }) { validationRun ->
            try {
                publishValidationRunMetrics(validationRun)
                count++
                if (count % 100 == 0) {
                    this.logger.info("Restored $count/$total validation run data metrics...")
                }
            } catch (ex: Exception) {
                this.logger.error("Cannot publish metrics for ${validationRun.entityDisplayName}", ex)
            }
        }
        this.logger.info("Restored $total validation run data metrics.")
    }

    override fun getValidationRun(validationRunId: ID): ValidationRun {
        val validationRun =
            structureRepository.getValidationRun(validationRunId) { validationRunStatusService.getValidationRunStatus(it) }
        securityService.checkProjectFunction(validationRun.build.branch.project.id(), ProjectView::class.java)
        return validationRun
    }

    override fun findValidationRunByID(validationRunId: ID): ValidationRun? =
        structureRepository.findValidationRunByID(validationRunId) {
            validationRunStatusService.getValidationRunStatus(it)
        }?.takeIf {
            securityService.isProjectFunctionGranted(it, ProjectView::class.java)
        }

    override fun getValidationRunsForBuild(
        buildId: ID,
        offset: Int,
        count: Int,
        sortingMode: ValidationRunSortingMode,
        statuses: List<String>?,
    ): List<ValidationRun> {
        val build = getBuild(buildId)
        securityService.checkProjectFunction(build.branch.project.id(), ProjectView::class.java)
        return structureRepository.getValidationRunsForBuild(
            build,
            offset,
            count,
            sortingMode,
            statuses,
        ) { validationRunStatusService.getValidationRunStatus(it) }
    }

    override fun getValidationRunsCountForBuild(
        buildId: ID,
        statuses: List<String>?,
    ): Int {
        val build = getBuild(buildId)
        securityService.checkProjectFunction(build.branch.project.id(), ProjectView::class.java)
        return structureRepository.getValidationRunsCountForBuild(build, statuses)
    }

    override fun getValidationRunsForBuildAndValidationStamp(
        buildId: ID,
        validationStampId: ID,
        offset: Int,
        count: Int,
        sortingMode: ValidationRunSortingMode?,
        statuses: List<String>?,
    ): List<ValidationRun> {
        val build = getBuild(buildId)
        val validationStamp = getValidationStamp(validationStampId)
        return getValidationRunsForBuildAndValidationStamp(build, validationStamp, offset, count, sortingMode, statuses)
    }

    override fun getValidationRunsForBuildAndValidationStamp(
        build: Build,
        validationStamp: ValidationStamp,
        offset: Int,
        count: Int,
        sortingMode: ValidationRunSortingMode?,
        statuses: List<String>?,
    ): List<ValidationRun> {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        return structureRepository.getValidationRunsForBuildAndValidationStamp(
            build,
            validationStamp,
            offset,
            count,
            sortingMode,
            statuses,
        ) { validationRunStatusService.getValidationRunStatus(it) }
    }

    override fun getValidationRunsForBuildAndValidationStampAndStatus(
        buildId: ID,
        validationStampId: ID,
        statuses: List<ValidationRunStatusID>,
        offset: Int,
        count: Int
    ): List<ValidationRun> {
        val build = getBuild(buildId)
        val validationStamp = getValidationStamp(validationStampId)
        securityService.checkProjectFunction(build.branch.project.id(), ProjectView::class.java)
        return structureRepository.getValidationRunsForBuildAndValidationStampAndStatus(
            build,
            validationStamp,
            statuses,
            offset,
            count
        ) { validationRunStatusService.getValidationRunStatus(it) }
    }

    override fun getValidationRunsForValidationStamp(
        validationStamp: ValidationStamp,
        offset: Int,
        count: Int
    ): List<ValidationRun> {
        securityService.checkProjectFunction(validationStamp.branch.project.id(), ProjectView::class.java)
        return structureRepository.getValidationRunsForValidationStamp(
            validationStamp,
            offset,
            count
        ) { validationRunStatusService.getValidationRunStatus(it) }
    }

    override fun getValidationRunsForValidationStamp(
        validationStampId: ID,
        offset: Int,
        count: Int
    ): List<ValidationRun> {
        val validationStamp = getValidationStamp(validationStampId)
        return getValidationRunsForValidationStamp(validationStamp, offset, count)
    }

    override fun getValidationRunsForValidationStampBetweenDates(
        validationStampId: ID,
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<ValidationRun> {
        val validationStamp = getValidationStamp(validationStampId)
        securityService.checkProjectFunction(validationStamp, ProjectView::class.java)
        return structureRepository.getValidationRunsForValidationStampBetweenDates(
            validationStamp,
            start,
            end
        ) { validationRunStatusService.getValidationRunStatus(it) }
    }

    override fun getValidationRunsForValidationStampAndStatus(
        validationStamp: ValidationStamp,
        statuses: List<ValidationRunStatusID>,
        offset: Int,
        count: Int
    ): List<ValidationRun> {
        securityService.checkProjectFunction(validationStamp.branch.project.id(), ProjectView::class.java)
        return structureRepository.getValidationRunsForValidationStampAndStatus(
            validationStamp,
            statuses,
            offset,
            count
        ) {
            validationRunStatusService.getValidationRunStatus(it)
        }
    }

    override fun getValidationRunsForValidationStampAndStatus(
        validationStampId: ID,
        statuses: List<ValidationRunStatusID>,
        offset: Int,
        count: Int
    ): List<ValidationRun> {
        val validationStamp = getValidationStamp(validationStampId)
        return getValidationRunsForValidationStampAndStatus(validationStamp, statuses, offset, count)
    }

    override fun getValidationRunsForStatus(
        branchId: ID,
        statuses: List<ValidationRunStatusID>,
        offset: Int,
        count: Int
    ): List<ValidationRun> {
        val branch = getBranch(branchId)
        return structureRepository.getValidationRunsForStatus(
            branch,
            statuses,
            offset,
            count
        ) {
            validationRunStatusService.getValidationRunStatus(it)
        }
    }

    override fun newValidationRunStatus(validationRun: ValidationRun, runStatus: ValidationRunStatus): ValidationRun {
        // Entity check
        isEntityDefined(validationRun, "Validation run must be defined")
        isEntityNew(runStatus, "Validation run status must not have any defined ID.")
        // Security check
        securityService.checkProjectFunction(
            validationRun.build.branch.project.id(),
            ValidationRunStatusChange::class.java
        )
        // Transition check
        validationRunStatusService.checkTransition(validationRun.lastStatus.statusID, runStatus.statusID)
        // Creation
        val newValidationRun = structureRepository.newValidationRunStatus(validationRun, runStatus)
        // Event
        eventPostService.post(eventFactory.newValidationRunStatus(newValidationRun))
        // OK, reloading to get IDs correct
        return getValidationRun(validationRun.id)
    }

    override fun getParentValidationRun(validationRunStatusId: ID, checkForAccess: Boolean): ValidationRun? {
        // Gets the validation run
        val run = structureRepository.getParentValidationRun(validationRunStatusId) {
            validationRunStatusService.getValidationRunStatus(it)
        }
        // Checks access rights
        return when {
            checkForAccess -> {
                securityService.checkProjectFunction(run, ProjectView::class.java)
                run
            }

            securityService.isProjectFunctionGranted(run, ProjectView::class.java) -> run
            else -> null
        }
    }

    override fun getValidationRunStatus(id: ID): ValidationRunStatus {
        return structureRepository.getValidationRunStatus(id) {
            validationRunStatusService.getValidationRunStatus(it)
        } ?: throw IllegalStateException("Cannot find validation run status with ID = $id")
    }

    override fun isValidationRunStatusCommentEditable(validationRunStatus: ID): Boolean {
        // Loads the parent
        val run = getParentValidationRun(validationRunStatus, checkForAccess = false)
        return isValidationRunStatusCommentEditable(run, validationRunStatus)
    }

    private fun isValidationRunStatusCommentEditable(run: ValidationRun?, validationRunStatus: ID): Boolean {
        return when {
            // Checks if available at all
            run == null -> false
            // Checks the edit right
            securityService.isProjectFunctionGranted(run, ValidationRunStatusCommentEdit::class.java) -> true
            // If not, check the current user vs. the creator of the comment
            securityService.isProjectFunctionGranted(run, ValidationRunStatusCommentEditOwn::class.java) -> {
                // Loads the status
                val status = structureRepository.getValidationRunStatus(validationRunStatus) {
                    validationRunStatusService.getValidationRunStatus(it)
                }
                // Gets the status's author
                val statusAuthor = status?.signature?.user?.name
                // Gets the current user name
                val currentUserName = securityService.currentSignature.user.name
                // Compare both
                statusAuthor != null && statusAuthor == currentUserName
            }
            // No right at all
            else -> false
        }
    }

    override fun saveValidationRunStatusComment(run: ValidationRun, runStatusId: ID, comment: String): ValidationRun {
        // Checks
        isEntityDefined(run, "Validation run must be defined")
        // Checks the edition rights
        if (!isValidationRunStatusCommentEditable(run, runStatusId)) {
            throw AccessDeniedException("Status comment edition denied.")
        }
        // Loading the status
        val runStatus = structureRepository.getValidationRunStatus(runStatusId) {
            validationRunStatusService.getValidationRunStatus(it)
        }
            ?: throw IllegalStateException("Could not find validation run status with id = $runStatusId")
        // Checks the parent run
        val parentOK = run.validationRunStatuses.any { it.id() == runStatus.id() }
        if (!parentOK) {
            throw IllegalStateException("Cannot edit a validation run status without a proper reference to its parent run.")
        }
        // Saving the new comment
        structureRepository.saveValidationRunStatusComment(runStatus, comment)
        // Event
        eventPostService.post(eventFactory.updateValidationRunStatusComment(run))
        // Reloading the run
        return getValidationRun(run.id)
    }

    override fun getValidationRunsCountForBuildAndValidationStamp(
        buildId: ID,
        validationStampId: ID,
        statuses: List<String>?,
    ): Int {
        return structureRepository.getValidationRunsCountForBuildAndValidationStamp(
            buildId,
            validationStampId,
            statuses,
        )
    }

    override fun getValidationRunsCountForValidationStamp(validationStampId: ID): Int {
        return structureRepository.getValidationRunsCountForValidationStamp(validationStampId)
    }

    override fun findProjectByName(project: String): Optional<Project> {
        return structureRepository.getProjectByName(project)
            .filter { p ->
                securityService.isGlobalFunctionGranted(ProjectList::class.java) || securityService.isProjectFunctionGranted(
                    p.id(),
                    ProjectView::class.java
                )
            }
    }

    @Throws(AccessDeniedException::class)
    override fun findProjectByNameIfAuthorized(project: String): Project? {
        // Looks for the project as admin
        val p = securityService.asAdmin { findProjectByName(project).getOrNull() }
        // If it exists
        return if (p != null) {
            // If it is authorized
            if (securityService.isProjectFunctionGranted(p, ProjectView::class.java)) {
                p
            } else {
                throw AccessDeniedException("Project access not granted.")
            }
        }
        // If it does not exist
        else {
            null
        }
    }

    override fun findBranchByName(project: String, branch: String): Optional<Branch> {
        return structureRepository.getBranchByName(project, branch)
            .filter { b -> securityService.isProjectFunctionGranted(b.projectId(), ProjectView::class.java) }
    }

    override fun findPromotionLevelByName(
        project: String,
        branch: String,
        promotionLevel: String
    ): Optional<PromotionLevel> {
        return structureRepository.getPromotionLevelByName(project, branch, promotionLevel)
            .filter { pl -> securityService.isProjectFunctionGranted(pl.projectId(), ProjectView::class.java) }
    }

    override fun entityLoader(): BiFunction<ProjectEntityType, ID, ProjectEntity> {
        return BiFunction { projectEntityType, id ->
            projectEntityType.getEntityFn(this@StructureServiceImpl).apply(id)
        }
    }

    override fun lastActiveProjects(count: Int): List<Project> =
        projectRepository.lastActiveProjects()
            .filter { securityService.isProjectFunctionGranted<ProjectView>(it) }
            .take(count)
}
