package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.common.replaceGroup
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.config.*
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.dispatcher.VersionSourceFactory
import net.nemerosa.ontrack.extension.av.dispatcher.getBuildVersion
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEventService
import net.nemerosa.ontrack.extension.av.metrics.AutoVersioningMetricsService
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingNotFoundException
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingRegistry
import net.nemerosa.ontrack.extension.av.properties.FilePropertyType
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.extension.scm.service.uploadLines
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.StructureService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AutoVersioningProcessingServiceImpl(
    private val scmDetector: SCMDetector,
    private val autoVersioningTargetFileService: AutoVersioningTargetFileService,
    private val postProcessingRegistry: PostProcessingRegistry,
    private val autoVersioningAuditService: AutoVersioningAuditService,
    private val metrics: AutoVersioningMetricsService,
    private val autoVersioningEventService: AutoVersioningEventService,
    private val autoVersioningCompletionListeners: MutableList<out AutoVersioningCompletionListener>,
    private val autoVersioningTemplatingService: AutoVersioningTemplatingService,
    private val versionSourceFactory: VersionSourceFactory,
    private val structureService: StructureService,
) : AutoVersioningProcessingService {

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningProcessingServiceImpl::class.java)

    override fun process(order: AutoVersioningOrder): AutoVersioningProcessingOutcome {
        logger.debug("Processing auto versioning order: {}", order)
        autoVersioningAuditService.onProcessingStart(order)
        val branch = order.branch
        // Source build
        val sourceBuild: Build? by lazy {
            order.sourceBuildId?.let {
                structureService.getBuild(ID.of(it))
            }
        }
        // Gets the SCM configuration for the project
        val scm = scmDetector.getSCM(branch.project)
        val scmBranch: String? = scm?.getSCMBranch(branch)
        if (scm != null && scmBranch != null) {
            // Gets the clone URL & name for the repository
            val repositoryURI = scm.repositoryURI
            val repository = scm.repository
            // Sanitization of the version (for the branch name)
            val sanitizedVersion: String = NameDescription.escapeName(order.targetVersion)
            // The source promotion may be null (when AV is triggered from another source than a promotion run for example)
            val sourcePromotion = order.sourcePromotion ?: "none"
            // Name of the upgrade branch
            val upgradeBranch = AutoVersioningSourceConfig.getUpgradeBranch(
                upgradeBranchPattern = order.upgradeBranchPattern,
                project = order.sourceProject,
                version = sanitizedVersion,
                branch = scmBranch,
                promotion = sourcePromotion,
                paths = order.allPaths.flatMap { it.paths },
                branchHash = true // Target branch name and other elements as a hash (to avoid too long names)
            )
            // Commit ID of the new branch (not null when target branch is actually created)
            val commitId: String by lazy {
                // Creates an upgrade branch and gets its commit ID
                try {
                    logger.debug(
                        "Processing auto versioning order creating branch {}: {}",
                        upgradeBranch,
                        order
                    )
                    // Audit
                    autoVersioningAuditService.onProcessingCreatingBranch(order, upgradeBranch)
                    // Cleaning the branch first
                    scm.deleteBranch(upgradeBranch)
                    // Creating the branch
                    scm.createBranch(scmBranch, upgradeBranch)
                } catch (e: Exception) {
                    autoVersioningEventService.sendError(order, "Failed to create branch $upgradeBranch", e)
                    throw e
                }
            }
            // Map of current versions per path
            val currentVersions = mutableMapOf<String, String>()
            // Caching the updated content of each path
            val updatedContent = mutableMapOf<String, List<String>>()
            // For each target path
            val targetPathUpdated: List<Boolean> = try {
                order.allPaths.flatMap { configPath ->
                    // Target version
                    val versionSource = configPath.versionSource
                    val targetVersion: String = if (versionSource.isNullOrBlank()) {
                        order.targetVersion
                    } else {
                        val build = sourceBuild
                        if (build != null) {
                            versionSourceFactory.getBuildVersion(
                                build = build,
                                versionSource = versionSource,
                            )
                        } else {
                            throw AutoVersioningCustomVersionNotFoundException(
                                configPath.path,
                                versionSource
                            )
                        }
                    }
                    configPath.paths.map { targetPath ->
                        // Gets the content of the target file
                        val lines = updatedContent.getOrPut(targetPath) {
                            scm.download(scmBranch, targetPath, retryOnNotFound = true)
                                ?.toString(Charsets.UTF_8)
                                ?.lines()
                                ?: throw AutoVersioningNoContentException(scmBranch, targetPath)
                        }
                        // Gets the current version in this file
                        val currentVersion: String = autoVersioningTargetFileService.readVersion(configPath, lines)
                            ?: throw AutoVersioningVersionNotFoundException(targetPath)
                        // If different version
                        if (currentVersion != order.targetVersion) {
                            // Storing the version
                            currentVersions[targetPath] = currentVersion
                            // Changes the version
                            val updatedLines = configPath.replaceVersion(lines, targetVersion)
                            // Audit
                            autoVersioningAuditService.onProcessingUpdatingFile(order, upgradeBranch, targetPath)
                            // Updating the cache
                            updatedContent[targetPath] = updatedLines
                            // Changed
                            true
                        } else {
                            // Not changed
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                autoVersioningEventService.sendError(
                    order,
                    e.message?.takeIf { it.isNotBlank() } ?: "Issue while processing the change",
                    e
                )
                throw e
            }
            // At least one path was changed
            if (targetPathUpdated.any { it }) {

                // Uploading the file contents
                try {
                    updatedContent.forEach { (targetPath, updatedLines) ->
                        scm.uploadLines(
                            upgradeBranch,
                            commitId,
                            targetPath,
                            updatedLines,
                            message = order.getCommitMessage()
                        )
                    }
                } catch (e: Exception) {
                    autoVersioningEventService.sendError(
                        order,
                        e.message?.takeIf { it.isNotBlank() } ?: "Issue while uploading the change",
                        e
                    )
                    throw e
                }

                // Templating renderer
                val avRenderer = autoVersioningTemplatingService.createAutoVersioningTemplateRenderer(
                    order = order,
                    currentVersions = currentVersions,
                )

                // Post-processing
                if (!order.postProcessing.isNullOrBlank()) {
                    // Gets the post processor
                    val postProcessing = postProcessingRegistry.getPostProcessingById<Any>(order.postProcessing)
                    // If processing cannot be found, we consider this an error
                    if (postProcessing == null) {
                        throw PostProcessingNotFoundException(order.postProcessing)
                    }
                    // Launching the post-processing
                    else {
                        logger.debug("Processing auto versioning order launching post processing: {}", order)
                        autoVersioningAuditService.onPostProcessingStart(order, upgradeBranch)
                        measureAndLaunchPostProcessing(
                            postProcessing,
                            order,
                            repositoryURI,
                            repository,
                            upgradeBranch,
                            scm,
                            avRenderer,
                        )
                        logger.debug("Processing auto versioning order end of post processing: {}", order)
                        autoVersioningAuditService.onPostProcessingEnd(order, upgradeBranch)
                    }
                }

                return when (order.pushMode) {
                    AutoVersioningPushMode.PR -> prPush(order, upgradeBranch, avRenderer, scm, scmBranch)
                    AutoVersioningPushMode.PUSH -> directPush(order, upgradeBranch, scm, scmBranch)
                }

            } else {
                logger.debug("Processing auto versioning order same version: {}", order)
                autoVersioningAuditService.onProcessingAborted(order, "Same version")
                return AutoVersioningProcessingOutcome.SAME_VERSION
            }
        } else {
            logger.debug("Processing auto versioning order no config: {}", order)
            autoVersioningAuditService.onProcessingAborted(order, "Target branch is not configured")
            return AutoVersioningProcessingOutcome.NO_CONFIG
        }
    }

    private fun directPush(
        order: AutoVersioningOrder,
        upgradeBranch: String,
        scm: SCM,
        scmBranch: String,
    ): AutoVersioningProcessingOutcome =
        try {
            logger.debug("Processing auto-versioning order direct push: {}", order)
            // Audit
            autoVersioningAuditService.onPushing(order, upgradeBranch)
            // Pushing the branch directly
            val commit: SCMCommit = scm.mergeBranch(
                head = upgradeBranch,
                base = scmBranch,
            )
            // Audit
            autoVersioningAuditService.onPushed(
                order = order,
                upgradeBranch = upgradeBranch,
                commit = commit.id,
                commitLink = commit.link,
            )
            autoVersioningEventService.sendSuccessPush(
                order = order,
                message = "Auto-versioning pushed.",
                commit = commit.id,
                commitLink = commit.link,
            )
            // OK
            AutoVersioningProcessingOutcome.CREATED
        } catch (e: Exception) {
            autoVersioningEventService.sendError(order, "Failed to directly push", e)
            throw e
        }

    private fun prPush(
        order: AutoVersioningOrder,
        upgradeBranch: String,
        avRenderer: AutoVersioningTemplateRenderer,
        scm: SCM,
        scmBranch: String,
    ): AutoVersioningProcessingOutcome {
        try {
            logger.debug("Processing auto versioning order creating PR: {}", order)
            // Audit
            autoVersioningAuditService.onPRCreating(order, upgradeBranch)
            // PR title & message
            val (prTitle, prBody) = autoVersioningTemplatingService.generatePRInfo(order, avRenderer)
            // PR creation
            val pr = scm.createPR(
                from = upgradeBranch,
                to = scmBranch,
                title = prTitle,
                // Change log as description?
                description = prBody,
                // Auto approval
                autoApproval = order.autoApproval,
                // Remote auto merge?
                remoteAutoMerge = (order.autoApprovalMode == AutoApprovalMode.SCM),
                // Commit message to use on merge
                message = order.getCommitMessage(),
                // List of reviewers
                reviewers = order.reviewers,
            )
            // If auto approval mode = CLIENT and PR is not merged, we had a timeout
            logger.debug("Processing auto versioning order end of PR process: {}", order)
            val outcome = if (order.autoApproval) {
                when (order.autoApprovalMode) {
                    AutoApprovalMode.SCM -> {
                        autoVersioningAuditService.onPRApproved(
                            order = order,
                            upgradeBranch = upgradeBranch,
                            prName = pr.name,
                            prLink = pr.link
                        )
                        autoVersioningEventService.sendSuccess(
                            order,
                            "Auto versioning PR has been created and approved. Its merge process will be done at SCM level.",
                            pr
                        )
                        // OK
                        AutoVersioningProcessingOutcome.CREATED
                    }

                    AutoApprovalMode.CLIENT -> if (pr.status != SCMPullRequestStatus.MERGED) {
                        logger.debug("Processing auto versioning order PR timed out: {}", order)
                        // Audit
                        autoVersioningAuditService.onPRTimeout(
                            order = order,
                            upgradeBranch = upgradeBranch,
                            prName = pr.name,
                            prLink = pr.link
                        )
                        // Notification
                        autoVersioningEventService.sendPRMergeTimeoutError(
                            order,
                            pr = pr
                        )
                        // OK
                        AutoVersioningProcessingOutcome.TIMEOUT
                    } else {
                        autoVersioningAuditService.onPRMerged(
                            order = order,
                            upgradeBranch = upgradeBranch,
                            prName = pr.name,
                            prLink = pr.link
                        )
                        autoVersioningEventService.sendSuccess(
                            order,
                            "Auto versioning PR has been created, approved and merged.",
                            pr
                        )
                        // OK
                        AutoVersioningProcessingOutcome.CREATED
                    }
                }
            } else {
                autoVersioningAuditService.onPRCreated(
                    order = order,
                    upgradeBranch = upgradeBranch,
                    prName = pr.name,
                    prLink = pr.link
                )
                autoVersioningEventService.sendSuccess(
                    order,
                    "Auto versioning PR has been created.",
                    pr
                )
                // OK
                AutoVersioningProcessingOutcome.CREATED
            }
            // Back validation
            onCompletion(order, outcome)
            // OK
            return outcome
        } catch (e: Exception) {
            autoVersioningEventService.sendError(order, "Failed to create PR", e)
            throw e
        }
    }

    private fun onCompletion(order: AutoVersioningOrder, outcome: AutoVersioningProcessingOutcome) {
        autoVersioningCompletionListeners.forEach { autoVersioningCompletionListener ->
            autoVersioningCompletionListener.onAutoVersioningCompletion(order, outcome)
        }
    }

    private fun <T> measureAndLaunchPostProcessing(
        postProcessing: PostProcessing<T>,
        order: AutoVersioningOrder,
        repositoryURI: String,
        repository: String,
        upgradeBranch: String,
        scm: SCM,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
    ) {
        // Count
        metrics.onPostProcessingStarted(order, postProcessing)
        try {
            // Timing
            metrics.postProcessingTiming(order, postProcessing) {
                launchPostProcessing(
                    postProcessing,
                    order,
                    repositoryURI,
                    repository,
                    upgradeBranch,
                    scm,
                    avTemplateRenderer,
                )
            }
            // Success
            metrics.onPostProcessingSuccess(order, postProcessing)
        } catch (ex: Exception) {
            // Metric for the error
            metrics.onPostProcessingError(order, postProcessing)
            // Going on with error processing
            throw ex
        }
    }

    private fun <T> launchPostProcessing(
        postProcessing: PostProcessing<T>,
        order: AutoVersioningOrder,
        repositoryURI: String,
        repository: String,
        upgradeBranch: String,
        scm: SCM,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
    ) {
        // Parsing and validation of the configuration
        val config: T = postProcessing.parseAndValidate(order.postProcessingConfig)
        // Collecting the post-processing information
        val onPostProcessingInfo = { info: PostProcessingInfo ->
            autoVersioningAuditService.onPostProcessingLaunched(order, info)
        }
        // Launching the post-processing
        postProcessing.postProcessing(
            config = config,
            autoVersioningOrder = order,
            repositoryURI = repositoryURI,
            repository = repository,
            upgradeBranch = upgradeBranch,
            scm = scm,
            avTemplateRenderer = avTemplateRenderer,
            onPostProcessingInfo = onPostProcessingInfo,
        )
    }

    private fun AutoVersioningSourceConfigPath.replaceVersion(
        content: List<String>,
        targetVersion: String
    ): List<String> {
        val type = filePropertyType
        val regex = propertyRegex
        val actualValue = if (regex.isNullOrBlank()) {
            targetVersion
        } else {
            val regex = regex.toRegex()
            val previousValue = type.readProperty(content, property) ?: error("Cannot find target property")
            regex.replaceGroup(previousValue, 1, targetVersion)
        }
        return type.replaceProperty(content, property, actualValue)
    }

    private val AutoVersioningSourceConfigPath.filePropertyType: FilePropertyType
        get() = autoVersioningTargetFileService.getFilePropertyType(this)

}