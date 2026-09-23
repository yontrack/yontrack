package net.nemerosa.ontrack.extension.environments.rules.core

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class EnvironmentSlotAdmissionRule(
    private val environmentService: EnvironmentService,
    private val applicationContext: ApplicationContext,
) : SlotAdmissionRule<EnvironmentSlotAdmissionRuleConfig, Any> {

    companion object {
        const val ID = "environment"
    }

    /**
     * Cannot inject the [SlotService] directly since it would create
     * a circular dependency.
     */
    private val slotService: SlotService by lazy {
        applicationContext.getBean(SlotService::class.java)
    }

    override val id: String = ID
    override val name: String = "Deployed in environment slot"

    override val configType: KClass<EnvironmentSlotAdmissionRuleConfig> = EnvironmentSlotAdmissionRuleConfig::class

    override fun parseConfig(jsonRuleConfig: JsonNode): EnvironmentSlotAdmissionRuleConfig =
        jsonRuleConfig.parse()

    override fun parseData(node: JsonNode): Any = ""

    override fun checkConfig(ruleConfig: JsonNode) {
        ruleConfig.parseOrNull<EnvironmentSlotAdmissionRuleConfig>()
            ?: throw SlotAdmissionRuleConfigException("Cannot parse the rule config")
    }

    /**
     * We need to check that the build project has a slot (and qualifier) on the configured environment.
     */
    override fun isBuildEligible(build: Build, slot: Slot, config: EnvironmentSlotAdmissionRuleConfig): Boolean {
        if (build.project.id() != slot.project.id()) return false
        return findPreviousSlot(build, config) != null
    }

    private fun findPreviousSlot(
        build: Build,
        config: EnvironmentSlotAdmissionRuleConfig
    ): Slot? =
        environmentService.findByName(config.environmentName)?.let { environment ->
            slotService.findSlotsByEnvironment(environment).firstOrNull {
                it.project.id() == build.project.id() && it.qualifier == config.qualifier
            }
        }

    /**
     * Build is deployable if the corresponding slot in the configured environment (and qualifier)
     * has its current pipeline in "deployed" state for this build.
     */
    override fun isBuildDeployable(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig,
        ruleConfig: EnvironmentSlotAdmissionRuleConfig,
        ruleData: SlotAdmissionRuleTypedData<Any>?
    ): SlotDeploymentCheck = checkBuildDeployable(pipeline.build, pipeline.slot, ruleConfig)

    override fun checkBuildDeployable(
        build: Build,
        slot: Slot,
        config: EnvironmentSlotAdmissionRuleConfig
    ): SlotDeploymentCheck {
        // Gets the previous slot
        val previousSlot = findPreviousSlot(build, config)
            ?: return SlotDeploymentCheck.nok("""Cannot find previous slot for project = "${build.project.name}", environment = ${config.environmentName} and qualifier = "${config.qualifier}".""")
        // Gets its last pipeline
        val previousPipeline = slotService.getCurrentPipeline(previousSlot)
            ?: return SlotDeploymentCheck.nok("""Slot for project = "${build.project.name}", environment = ${config.environmentName} and qualifier = "${config.qualifier}" has no pipeline.""")
        // Checks it's for the same build
        if (previousPipeline.build.id() != build.id()) {
            return SlotDeploymentCheck.nok("""Pipeline for project = "${build.project.name}", environment = ${config.environmentName} and qualifier = "${config.qualifier}" is for another build.""")
        }
        // Checks it's deployed
        if (previousPipeline.status != SlotPipelineStatus.DONE) {
            return SlotDeploymentCheck.nok("""Build is in a pipeline for project = "${build.project.name}", environment = ${config.environmentName} and qualifier = "${config.qualifier}" but this pipeline has not been deployed.""")
        }
        // OK
        return SlotDeploymentCheck.ok()
    }

    /**
     * Adding to the criteria the fact that there must exist a slot in the previous environment
     * for the same qualifier & project.
     *
     * If [deployable] is `true`, we also add the criteria that the current (latest) pipeline of
     * this previous slot must be for the build and in status deployed - the same reading as
     * [checkBuildDeployable].
     */
    override fun fillEligibilityCriteria(
        slot: Slot,
        config: EnvironmentSlotAdmissionRuleConfig,
        queries: MutableList<String>,
        params: MutableMap<String, Any?>,
        deployable: Boolean,
    ) {

        val whereDeployable = if (deployable) {
            """
                AND EXISTS (
                    SELECT 1
                    FROM (
                        SELECT PP.BUILD_ID, PP.STATUS
                        FROM ENV_SLOT_PIPELINE PP
                        WHERE PP.SLOT_ID = S.ID
                        ORDER BY PP.NUMBER DESC
                        LIMIT 1
                    ) CURRENT_PP
                    WHERE CURRENT_PP.BUILD_ID = BD.ID
                    AND CURRENT_PP.STATUS = '${SlotPipelineStatus.DONE.name}'
                )
            """.trimIndent()
        } else {
            ""
        }

        queries += """
            EXISTS (
                SELECT S.ID
                FROM ENV_SLOTS S
                INNER JOIN ENVIRONMENTS E ON E.ID = S.ENVIRONMENT_ID
                WHERE E.NAME = :environmentName
                AND S.PROJECT_ID = :projectId
                AND S.QUALIFIER = :qualifier
                $whereDeployable
                LIMIT 1
            )
        """
        params["environmentName"] = config.environmentName
        params["qualifier"] = config.qualifier
        params["projectId"] = slot.project.id()
    }
}