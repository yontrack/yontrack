package net.nemerosa.ontrack.extension.environments

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.Build
import kotlin.reflect.KClass

/**
 * This service is responsible for checking the eligibility of a build into a slot pipeline.
 *
 * @param C Type of the configuration for the rule.
 * @param D Type of the data for the rule.
 */
interface SlotAdmissionRule<C: Any, D> {

    /**
     * Unique ID for this rule
     */
    val id: String

    /**
     * Display name for this rule
     */
    val name: String

    /**
     * Type for the configuration
     */
    val configType: KClass<C>

    /**
     * Parsing a configuration
     */
    fun parseConfig(jsonRuleConfig: JsonNode): C

    /**
     * Checks if this build can be injected into the pipeline of a slot.
     */
    fun isBuildEligible(
        build: Build,
        slot: Slot,
        config: C,
    ): Boolean

    /**
     * Participates into the criteria to find eligible builds for a slot.
     */
    fun fillEligibilityCriteria(
        slot: Slot,
        config: C,
        queries: MutableList<String>,
        params: MutableMap<String, Any?>,
        deployable: Boolean = false,
    )

    /**
     * Checks if this build can be deployed into the slot now, before any pipeline exists for it.
     *
     * It is the build-level reading of [isBuildDeployable], and the Kotlin twin of
     * [fillEligibilityCriteria] with `deployable = true`: a build is listed as deployable exactly
     * when this check is ok or `null`. It must give the same reason as [isBuildDeployable] would.
     *
     * @return The check, or `null` when the rule can only be decided on a pipeline (for example,
     * a manual approval, which is given on the pipeline itself).
     */
    fun checkBuildDeployable(
        build: Build,
        slot: Slot,
        config: C,
    ): SlotDeploymentCheck?

    /**
     * Checks if this build can be deployable into the slot
     */
    fun isBuildDeployable(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig,
        ruleConfig: C,
        ruleData: SlotAdmissionRuleTypedData<D>?,
    ): SlotDeploymentCheck

    /**
     * Parsing the stored/client data into typed data for this rule
     */
    fun parseData(node: JsonNode): D

    /**
     * Checks if the given data is acceptable for the rule & its configuration.
     */
    fun checkData(ruleConfig: JsonNode, data: JsonNode) {}

    /**
     * Checks if the given configuration is valid for the rule
     */
    fun checkConfig(ruleConfig: JsonNode)

    /**
     * Checks if some data is complete for this rule
     */
    fun isDataComplete(ruleConfig: C, ruleData: D): Boolean = true

    /**
     * Does this rule need some data?
     */
    fun isDataNeeded(ruleConfig: C): Boolean = false

}