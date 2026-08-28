package net.nemerosa.ontrack.extension.av.versionrules

import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SemVerVersionRuleTest {

    private val rule = SemVerVersionRule(AutoVersioningExtensionFeature(SCMExtensionFeature()))

    private val branch = Branch.of(Project.of(nd("target", "")), nd("main", ""))

    @Test
    fun `Upgrades are accepted`() {
        assertAccepted(current = "1.0.0", target = "2.0.0")
        assertAccepted(current = "1.0.0", target = "1.1.0")
        assertAccepted(current = "1.0.0", target = "1.0.1")
        assertAccepted(current = "1.0.0-rc.1", target = "1.0.0")
        assertAccepted(current = "v1.0.0", target = "v1.0.1")
    }

    @Test
    fun `The same version is accepted`() {
        assertAccepted(current = "1.0.0", target = "1.0.0")
        assertAccepted(current = "v1.0", target = "1.0.0")
    }

    @Test
    fun `Downgrades are rejected`() {
        val result = check(current = "2.0.0", target = "1.0.0")
        assertFalse(result.accepted, "Downgrade must be rejected")
        val reason = assertNotNull(result.rejectionReason)
        assertTrue("1.0.0" in reason && "2.0.0" in reason, "Reason must name both versions: $reason")
    }

    @Test
    fun `Prerelease downgrades are rejected`() {
        assertRejected(current = "1.0.0", target = "1.0.0-rc.1")
        assertRejected(current = "1.0.0-beta.11", target = "1.0.0-beta.2")
    }

    @Test
    fun `Unparseable current version is rejected by default`() {
        val result = check(current = "not-a-version", target = "1.0.0")
        assertFalse(result.accepted)
        val reason = assertNotNull(result.rejectionReason)
        assertTrue("not-a-version" in reason, "Reason must name the unparseable version: $reason")
    }

    @Test
    fun `Unparseable target version is rejected by default`() {
        val result = check(current = "1.0.0", target = "not-a-version")
        assertFalse(result.accepted)
        val reason = assertNotNull(result.rejectionReason)
        assertTrue("not-a-version" in reason, "Reason must name the unparseable version: $reason")
    }

    @Test
    fun `Both versions unparseable are rejected by default`() {
        assertRejected(current = "abcdef1", target = "1234567")
    }

    @Test
    fun `Unparseable versions can be accepted`() {
        val config = SemVerVersionRuleConfig(onUnparseable = SemVerUnparseablePolicy.ACCEPT)
        assertAccepted(current = "not-a-version", target = "1.0.0", config = config)
        assertAccepted(current = "1.0.0", target = "not-a-version", config = config)
        assertAccepted(current = "abcdef1", target = "1234567", config = config)
    }

    @Test
    fun `A downgrade is still rejected when unparseable versions are accepted`() {
        assertRejected(
            current = "2.0.0",
            target = "1.0.0",
            config = SemVerVersionRuleConfig(onUnparseable = SemVerUnparseablePolicy.ACCEPT),
        )
    }

    @Test
    fun `Default configuration rejects unparseable versions`() {
        assertEquals(SemVerUnparseablePolicy.REJECT, rule.parseAndValidate(null).onUnparseable)
        assertEquals(SemVerUnparseablePolicy.REJECT, rule.parseAndValidate(mapOf<String, String>().asJson()).onUnparseable)
    }

    @Test
    fun `Parsing of the configuration`() {
        assertEquals(
            SemVerVersionRuleConfig(onUnparseable = SemVerUnparseablePolicy.ACCEPT),
            rule.parseAndValidate(mapOf("onUnparseable" to "ACCEPT").asJson())
        )
    }

    private fun check(
        current: String,
        target: String,
        config: SemVerVersionRuleConfig = SemVerVersionRuleConfig(),
    ) = rule.check(
        config = config,
        context = AutoVersioningVersionRuleContext(
            order = branch.createOrder(sourceProject = "source", targetVersion = target),
            path = "gradle.properties",
            currentVersion = current,
            targetVersion = target,
        )
    )

    private fun assertAccepted(
        current: String,
        target: String,
        config: SemVerVersionRuleConfig = SemVerVersionRuleConfig(),
    ) {
        val result = check(current, target, config)
        assertTrue(result.accepted, "$current -> $target must be accepted, but was: ${result.rejectionReason}")
    }

    private fun assertRejected(
        current: String,
        target: String,
        config: SemVerVersionRuleConfig = SemVerVersionRuleConfig(),
    ) {
        val result = check(current, target, config)
        assertFalse(result.accepted, "$current -> $target must be rejected")
    }

}
