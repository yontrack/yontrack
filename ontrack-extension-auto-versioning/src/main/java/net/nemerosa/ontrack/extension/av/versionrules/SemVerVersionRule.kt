package net.nemerosa.ontrack.extension.av.versionrules

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.SemanticVersion
import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parse
import org.springframework.stereotype.Component

/**
 * Version rule making sure that the version set by an auto-versioning order is not older than
 * the version already present in the target file.
 *
 * This protects against the re-promotion of an old build, which would otherwise roll the target
 * file backwards.
 */
@Component
class SemVerVersionRule(
    extensionFeature: AutoVersioningExtensionFeature,
) : AbstractExtension(extensionFeature), AutoVersioningVersionRule<SemVerVersionRuleConfig> {

    override val id: String = "semver"

    override val name: String = "Semantic versioning"

    override fun parseAndValidate(config: JsonNode?): SemVerVersionRuleConfig =
        config?.takeIf { !it.isNull }?.parse() ?: SemVerVersionRuleConfig()

    override fun check(
        config: SemVerVersionRuleConfig,
        context: AutoVersioningVersionRuleContext,
    ): AutoVersioningVersionRuleResult {
        val current = SemanticVersion.parse(context.currentVersion)
        val target = SemanticVersion.parse(context.targetVersion)
        // Any version which cannot be parsed is handled according to the configuration. Rejecting
        // is the default: the guard must not silently disappear on the versions which are the most
        // likely to be mis-ordered.
        if (current == null || target == null) {
            return when (config.onUnparseable) {
                SemVerUnparseablePolicy.ACCEPT -> AutoVersioningVersionRuleResult.accepted()
                SemVerUnparseablePolicy.REJECT -> AutoVersioningVersionRuleResult.rejected(
                    unparseableReason(context, current == null)
                )
            }
        }
        // Downgrade check (an unchanged version is not a downgrade)
        return if (target < current) {
            AutoVersioningVersionRuleResult.rejected(
                """Version "${context.targetVersion}" is older than the version "${context.currentVersion}" already present in the target file."""
            )
        } else {
            AutoVersioningVersionRuleResult.accepted()
        }
    }

    private fun unparseableReason(context: AutoVersioningVersionRuleContext, currentUnparseable: Boolean): String {
        val version = if (currentUnparseable) context.currentVersion else context.targetVersion
        val origin = if (currentUnparseable) "version already present in the target file" else "version to set"
        return """The $origin, "$version", is not a semantic version and cannot be compared."""
    }

}
