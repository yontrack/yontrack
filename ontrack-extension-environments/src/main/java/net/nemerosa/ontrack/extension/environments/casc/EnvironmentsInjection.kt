package net.nemerosa.ontrack.extension.environments.casc

import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.environments.Environment
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflow
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.model.files.FileRef
import net.nemerosa.ontrack.model.files.FileRefService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.support.ImageHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class EnvironmentsInjection(
    private val environmentService: EnvironmentService,
    private val structureService: StructureService,
    private val slotService: SlotService,
    private val slotWorkflowService: SlotWorkflowService,
    private val fileRefService: FileRefService,
    private val securityService: SecurityService,
) {

    private val logger: Logger = LoggerFactory.getLogger(EnvironmentsInjection::class.java)

    fun defineEnvironments(
        environments: List<EnvironmentCasc>,
        keepEnvironments: Boolean,
    ) {
        // Checking that there is no double declaration of environments
        val duplicateNames = environments
            .groupBy { it.name }
            .mapValues { (_, list) -> list.size }
            .filterValues { it > 1 }
            .map { (name, count) -> "* $name: $count" }
        if (duplicateNames.isNotEmpty()) {
            throw EnvironmentsCascException(
                """
                        Duplicate environment names:
                        ${duplicateNames.joinToString("\n")}
                    """.trimIndent()
            )
        }

        val existingEnvs = environmentService.findAll()
        syncForward(
            from = environments,
            to = existingEnvs,
        ) {
            equality { a, b -> a.name == b.name }
            onCreation { env ->
                logger.info("[casc][environment] Creating ${env.name}")
                val environment = Environment(
                    name = env.name,
                    description = env.description,
                    order = env.order,
                    tags = env.tags,
                    image = false,
                )
                environmentService.save(environment)
                setImage(env, environment)
            }
            onModification { env, existing ->
                logger.info("[casc][environment] Updating ${env.name}")
                val adapted = Environment(
                    id = existing.id,
                    name = existing.name,
                    description = env.description,
                    order = env.order,
                    tags = env.tags,
                    image = existing.image,
                )
                environmentService.save(adapted)
                setImage(env, adapted)
            }
            onDeletion { existing ->
                if (!keepEnvironments) {
                    logger.info("[casc][environment] Deleting ${existing.name}")
                    environmentService.delete(existing)
                }
            }
        }
    }

    private fun setImage(
        env: EnvironmentCasc,
        environment: Environment
    ) {
        if (!env.image.isNullOrBlank()) {
            val image = fileRefService.downloadDocument(
                ref = FileRef.parseUri(env.image) ?: error("Cannot parse image URI: ${env.image}"),
                type = ImageHelper.IMAGE_PNG,
            ) ?: error("Cannot download image at ${env.image}")
            environmentService.setEnvironmentImage(
                id = environment.id,
                document = image
            )
        }
    }

    fun defineSlots(slots: List<SlotCasc>) {
        slots.forEach { slotCasc ->

            val qualifier = slotCasc.qualifier
            // Resolved as admin so that "not found" below means the project really does not exist,
            // rather than merely being invisible to the current user. Authorization is still
            // enforced by the slot mutations themselves, which all check ProjectView on the slot's
            // project (see checkSlotAccess).
            val project = securityService.asAdmin {
                structureService.findProjectByName(slotCasc.project).getOrNull()
            }

            // Checking that there is no double declaration of environments
            val duplicateNames = slotCasc.environments
                .groupBy { it.name }
                .mapValues { (_, list) -> list.size }
                .filterValues { it > 1 }
                .map { (name, count) -> "* $name: $count" }
            if (duplicateNames.isNotEmpty()) {
                throw EnvironmentsCascException(
                    """
                        Duplicate environment names found in slot ${slotCasc.project}[${slotCasc.qualifier}]:
                        ${duplicateNames.joinToString("\n")}
                    """.trimIndent()
                )
            }

            if (project != null) {
                val existingSlots = slotService.findSlotsByProject(project, qualifier)
                syncForward(
                    from = slotCasc.environments,
                    to = existingSlots
                ) {
                    equality { a, b ->
                        a.name == b.environment.name
                    }
                    onCreation { a ->
                        val environment = environmentService.findByName(a.name)
                        if (environment != null) {
                            logger.info("[casc][slot] Creating slot ${project.name}[$qualifier] -> ${a.name}")
                            val slot = Slot(
                                environment = environment,
                                project = project,
                                qualifier = slotCasc.qualifier,
                                description = generateSlotDescription(project, slotCasc, a),
                            ).apply {
                                slotService.addSlot(this)
                            }
                            runSlotAdmissionRules(slot, a)
                            runSlotWorkflows(slot, a)
                        } else {
                            logger.error("[casc][slot] Slot ${project.name}[$qualifier] skipped: environment \"${a.name}\" does not exist.")
                        }
                    }
                    onModification { a, existing ->
                        logger.info("[casc][slot] Existing slot ${project.name}[$qualifier] -> ${a.name}")
                        val description = generateSlotDescription(project, slotCasc, a)
                        if (existing.description != description) {
                            logger.info("[casc][slot] Existing slot ${project.name}[$qualifier] -> ${a.name} - updating description")
                            slotService.saveSlot(existing.withDescription(description))
                        }
                        runSlotAdmissionRules(existing, a)
                        runSlotWorkflows(existing, a)
                    }
                    onDeletion { existing ->
                        logger.info("[casc][slot] Deleting slot ${project.name}[$qualifier] -> ${existing.environment.name}")
                        slotService.deleteSlot(existing)
                    }
                }
            } else {
                logger.error("[casc][slot] Slot ${slotCasc.project}[$qualifier] skipped: project does not exist.")
            }
        }
    }

    private fun generateSlotDescription(
        project: Project,
        slotCasc: SlotCasc,
        slotEnvironmentCasc: SlotEnvironmentCasc
    ): String? =
        if (slotEnvironmentCasc.description.isNotBlank()) {
            slotEnvironmentCasc.description
        } else if (slotCasc.description.isNotBlank()) {
            val s = StringBuilder(slotCasc.description)
            s.append(" ${project.name}")
            if (slotCasc.qualifier.isNotBlank()) {
                s.append("[${slotCasc.qualifier}]")
            }
            s.toString()
        } else {
            null
        }

    private fun runSlotWorkflows(slot: Slot, slotEnvironmentCasc: SlotEnvironmentCasc) {
        val cascWorkflows = slotEnvironmentCasc.workflows
        val existingWorkflows = slotWorkflowService.getSlotWorkflowsBySlot(slot)
        syncForward(
            from = cascWorkflows,
            to = existingWorkflows
        ) {
            equality { a, b ->
                a.name == b.workflow.name
            }
            onCreation { a ->
                val newSlotWorkflow = SlotWorkflow(
                    slot = slot,
                    trigger = a.trigger,
                    workflow = Workflow(
                        name = a.name,
                        nodes = a.nodes,
                    )
                )
                logger.info("[casc][slot-workflow] Creating slot workflow ${slot.project.name}[${slot.qualifier}] -> ${slot.environment.name}: ${a.name} [${newSlotWorkflow.id}]")
                slotWorkflowService.addSlotWorkflow(
                    newSlotWorkflow
                )
            }
            onModification { a, existing ->
                logger.info("[casc][slot-workflow] Updating slot workflow ${slot.project.name}[${slot.qualifier}] -> ${slot.environment.name}: ${a.name} [${existing.id}]")
                slotWorkflowService.updateSlotWorkflow(
                    existing
                        .withTrigger(a.trigger)
                        .withWorkflow(
                            Workflow(
                                name = a.name,
                                nodes = a.nodes
                            )
                        )
                )
            }
            onDeletion { existing ->
                logger.info("[casc][slot-workflow] Deleting slot workflow ${slot.project.name}[${slot.qualifier}] -> ${slot.environment.name}: ${existing.workflow.name} [${existing.id}]")
                slotWorkflowService.deleteSlotWorkflow(existing)
            }
        }
    }

    private fun runSlotAdmissionRules(slot: Slot, slotEnvironmentCasc: SlotEnvironmentCasc) {
        val existingRules = slotService.getAdmissionRuleConfigs(slot)
        syncForward(
            from = slotEnvironmentCasc.admissionRules,
            to = existingRules,
        ) {
            equality { a, b ->
                a.actualName == b.name
            }
            onCreation { a ->
                logger.info("[casc][slot-rule] Creating slot rule ${slot.project.name}[${slot.qualifier}] -> ${slot.environment.name}: ${a.actualName}")
                slotService.addAdmissionRuleConfig(
                    config = SlotAdmissionRuleConfig(
                        slot = slot,
                        name = a.actualName,
                        description = a.description,
                        ruleId = a.ruleId,
                        ruleConfig = a.ruleConfig,
                    )
                )
            }
            onModification { a, existing ->
                logger.info("[casc][slot-rule] Updating slot rule ${slot.project.name}[${slot.qualifier}] -> ${slot.environment.name}: ${a.actualName}")
                slotService.saveAdmissionRuleConfig(
                    existing
                        .withDescription(a.description)
                        .withRuleConfig(a.ruleConfig)
                )
            }
            onDeletion { a ->
                logger.info("[casc][slot-rule] Deleting slot rule ${slot.project.name}[${slot.qualifier}] -> ${slot.environment.name}: ${a.name}")
                slotService.deleteAdmissionRuleConfig(a)
            }
        }
    }
}