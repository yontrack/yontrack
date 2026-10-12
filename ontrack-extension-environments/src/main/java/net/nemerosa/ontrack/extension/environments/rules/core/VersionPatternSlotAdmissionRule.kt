package net.nemerosa.ontrack.extension.environments.rules.core

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.storage.SlotRepository
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Admits a build on its version: the property holding its display name (the `release` property)
 * when set, its name otherwise.
 *
 * It is what tells a GA or a beta from an alpha when all three come from the same branch and reach
 * the same promotion.
 */
@Component
class VersionPatternSlotAdmissionRule(
    private val buildDisplayNameService: BuildDisplayNameService,
    private val propertyService: PropertyService,
    private val slotRepository: SlotRepository,
) : SlotAdmissionRule<VersionPatternSlotAdmissionRuleConfig, Any> {

    companion object {
        const val ID = "versionPattern"

        /**
         * Turns a pattern into one PostgreSQL matches in full, like Kotlin's [Regex.matches].
         *
         * PostgreSQL's `~` finds the pattern anywhere in the text, so `\d+\.\d+\.\d+` would admit
         * `6.0.0-alpha.3`. The group keeps an alternation such as `6\..*|7\..*` anchored as a whole.
         */
        fun anchor(pattern: String) = "^(?:$pattern)$"
    }

    override val id: String = ID
    override val name: String = "Version pattern"

    override val configType: KClass<VersionPatternSlotAdmissionRuleConfig> =
        VersionPatternSlotAdmissionRuleConfig::class

    override fun parseConfig(jsonRuleConfig: JsonNode) = jsonRuleConfig.parse<VersionPatternSlotAdmissionRuleConfig>()

    /**
     * The version of a build is read from the property which [BuildDisplayNameService.displayNameProperty]
     * names, without this extension knowing the `release` property.
     *
     * Not [BuildDisplayNameService.getFirstBuildDisplayName]: it also honours the project's "use
     * label" setting, which only decides how a build is *shown*, and which the eligible-builds
     * query cannot read - the two readings would then disagree on the same build.
     */
    private fun getVersion(build: Build): String =
        buildDisplayNameService.displayNameProperty?.let { property ->
            propertyService.getProperty<Any>(build, property.propertyTypeName).value
                ?.asJson()
                ?.get(property.jsonField)
                ?.takeIf { it.isString }
                ?.stringValue()
                // Empty, not blank: the eligible-builds query reads it the same way
                ?.takeIf { it.isNotEmpty() }
        } ?: build.name

    override fun isBuildEligible(build: Build, slot: Slot, config: VersionPatternSlotAdmissionRuleConfig): Boolean =
        isVersionEligible(getVersion(build), config)

    fun isVersionEligible(version: String, config: VersionPatternSlotAdmissionRuleConfig): Boolean =
        refusal(version, config) == null

    /**
     * Why the version is refused, or `null` when it is admitted: it must match one of the includes,
     * if any, and none of the excludes - full, case-insensitive matches, like `FilterHelper`'s.
     *
     * Eligibility and the deployment check both read it, so that the reason given is always the
     * reason for the verdict.
     */
    private fun refusal(version: String, config: VersionPatternSlotAdmissionRuleConfig): String? {
        fun matches(pattern: String) = pattern.toRegex(RegexOption.IGNORE_CASE).matches(version)
        if (config.includes.isNotEmpty() && config.includes.none(::matches)) {
            return "Build version $version matches none of ${config.includes.joinToString { "`$it`" }}"
        }
        return config.excludes.firstOrNull(::matches)?.let { "Build version $version is excluded by `$it`" }
    }

    /**
     * The SQL twin of [getVersion] and [isVersionEligible].
     */
    override fun fillEligibilityCriteria(
        slot: Slot,
        config: VersionPatternSlotAdmissionRuleConfig,
        queries: MutableList<String>,
        params: MutableMap<String, Any?>,
        deployable: Boolean,
    ) {
        if (config.includes.isEmpty() && config.excludes.isEmpty()) return
        // Parameters are shared by all the rules of the slot, and a slot may have several of this
        // one: the prefix keeps each rule's parameters apart.
        val prefix = "versionPattern${queries.size}"
        val property = buildDisplayNameService.displayNameProperty
        val version = if (property != null) {
            params["${prefix}Type"] = property.propertyTypeName
            params["${prefix}Field"] = property.jsonField
            """
                COALESCE(NULLIF((
                    SELECT VP.JSON->>:${prefix}Field
                    FROM PROPERTIES VP
                    WHERE VP.BUILD = BD.ID
                    AND VP.TYPE = :${prefix}Type
                    LIMIT 1
                ), ''), BD.NAME)
            """.trimIndent()
        } else {
            "BD.NAME"
        }
        if (config.includes.isNotEmpty()) {
            queries += config.includes.indices.joinToString(" OR ") { index ->
                params["${prefix}Include$index"] = anchor(config.includes[index])
                "$version ~* :${prefix}Include$index"
            }
        }
        config.excludes.forEachIndexed { index, pattern ->
            params["${prefix}Exclude$index"] = anchor(pattern)
            queries += "$version !~* :${prefix}Exclude$index"
        }
    }

    override fun isBuildDeployable(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig,
        ruleConfig: VersionPatternSlotAdmissionRuleConfig,
        ruleData: SlotAdmissionRuleTypedData<Any>?
    ): SlotDeploymentCheck = checkBuildDeployable(pipeline.build, pipeline.slot, ruleConfig)

    /**
     * The version is a fact of the build alone, so deployability is the same as eligibility.
     */
    override fun checkBuildDeployable(
        build: Build,
        slot: Slot,
        config: VersionPatternSlotAdmissionRuleConfig
    ): SlotDeploymentCheck {
        val version = getVersion(build)
        return refusal(version, config)
            ?.let { SlotDeploymentCheck.nok(it) }
            ?: SlotDeploymentCheck.ok("Build version $version is valid")
    }

    override fun parseData(node: JsonNode): Any = ""

    override fun checkConfig(ruleConfig: JsonNode) {
        val config = ruleConfig.parseOrNull<VersionPatternSlotAdmissionRuleConfig>()
            ?: throw SlotAdmissionRuleConfigException("Cannot parse the rule config")
        // Checked in both dialects: Kotlin reads the patterns for a build, PostgreSQL for the
        // list of eligible builds
        (config.includes + config.excludes).forEach { pattern ->
            val validInKotlin = runCatching { pattern.toRegex() }.isSuccess
            if (!validInKotlin || !slotRepository.isValidRegex(anchor(pattern))) {
                throw SlotAdmissionRuleConfigException("Invalid version pattern: $pattern")
            }
        }
    }
}
