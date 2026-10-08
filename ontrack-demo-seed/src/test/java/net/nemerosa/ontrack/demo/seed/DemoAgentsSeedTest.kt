package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The agents of the demo (#2037): two registered agents, the commits written with assistants, what an
 * agent does on `petclinic-vets` and what a person does for it, the gates on agents, and the widget.
 *
 * The in-memory target enforces the agent policy, the *Assisted builds require* condition and the
 * *Evidence from non-agents only* restriction as the server does, and computes the assisted change of
 * a build from the trailers of its commits, so that a dataset the server would refuse - an agent
 * promoting where it may not, an assisted build promoted without its review - fails here. The real
 * server is checked by seeding the dev stack.
 */
class DemoAgentsSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seeded(): InMemoryDemoTarget =
        InMemoryDemoTarget().also { DemoSeed(it, clock, log = {}).run(DemoContent.dataset(emptyList())) }

    private fun InMemoryDemoTarget.vets(): InMemoryDemoTarget.InMemoryBranch =
        projects().filterIsInstance<InMemoryDemoTarget.InMemoryProject>()
            .single { it.name == DemoContent.VETS }
            .branches.single { it.name == DemoContent.MAIN }

    private fun InMemoryDemoTarget.vetsBuild(name: String): InMemoryDemoTarget.InMemoryBuild =
        vets().builds.single { it.name == name }

    private val claude = "agent:${DemoContent.AGENT_CLAUDE}[agent]"
    private val codex = "agent:${DemoContent.AGENT_CODEX}[agent]"

    @Test
    fun `the two agents exist, owned by the seeding account, each with the token the seed acts through`() {
        val target = seeded()

        assertEquals(
            listOf("${DemoContent.AGENT_CLAUDE}[agent]", "${DemoContent.AGENT_CODEX}[agent]"),
            target.agents.map { it.identifier },
        )
        assertEquals(listOf("Claude Code", "Codex"), target.agents.map { it.spec.tool })
        target.agents.forEach { agent ->
            assertEquals(setOf(DemoSeed.AGENT_TOKEN), agent.tokens, "The token of ${agent.identifier} is kept")
        }
    }

    @Test
    fun `the reset deletes the agents the dataset declares, and only those`() {
        val target = InMemoryDemoTarget()
        target.registerAgent(AgentSpec(DemoContent.AGENT_CLAUDE, "Left over", "Claude Code", ""))
        target.registerAgent(AgentSpec("somebody-else", "Somebody's agent", "Other", ""))

        DemoSeed(target, clock, log = {}).run(DemoContent.dataset(emptyList()))

        assertEquals(
            listOf("somebody-else[agent]", "${DemoContent.AGENT_CLAUDE}[agent]", "${DemoContent.AGENT_CODEX}[agent]"),
            target.agents.map { it.identifier },
        )
        assertEquals(
            "Claude Code (demo)",
            target.agents.single { it.identifier == "${DemoContent.AGENT_CLAUDE}[agent]" }.spec.displayName,
            "Registered again",
        )
    }

    @Test
    fun `the assisted builds have their assisted change, and a human co-author does not count`() {
        val target = seeded()

        assertEquals(
            InMemoryDemoTarget.InMemoryAssistedChange(basis = "UNKNOWN"),
            target.vetsBuild("201").assistedChange,
            "The first build of the branch has no change log",
        )
        assertEquals(
            InMemoryDemoTarget.InMemoryAssistedChange(basis = "COMPUTED", totalCommits = 2),
            target.vetsBuild("202").assistedChange,
            "A person as a co-author makes no assisted change",
        )
        assertEquals(
            InMemoryDemoTarget.InMemoryAssistedChange(
                basis = "COMPUTED",
                assistants = listOf("Claude Code", "Codex"),
                assistedCommits = 3,
                totalCommits = 4,
            ),
            target.vetsBuild(DemoContent.VETS_REVIEWED).assistedChange,
        )
        assertEquals(
            InMemoryDemoTarget.InMemoryAssistedChange(
                basis = "COMPUTED",
                assistants = listOf("Claude Code"),
                assistedCommits = 2,
                totalCommits = 2,
            ),
            target.vetsBuild(DemoContent.VETS_BLOCKED).assistedChange,
        )
    }

    @Test
    fun `the commits of the agents carry the trailers of their tools, and the Claude session`() {
        val repository = DemoContent.dataset(emptyList()).projects.single { it.name == DemoContent.VETS }
        val messages = repository.branches.single().builds.flatMap { it.commits }

        assertTrue(messages.any { "Co-Authored-By: Claude <noreply@anthropic.com>" in it })
        assertTrue(messages.any { "Claude-Session: https://" in it })
        assertTrue(messages.any { "Co-authored-by: Codex <codex@openai.com>" in it })
        assertTrue(
            messages.any { "Co-authored-by:" in it && InMemoryDemoTarget.assistantsOf(it).isEmpty() },
            "One commit has a co-author who is a person",
        )
    }

    @Test
    fun `an agent creates and validates its builds, and promotes them where agents are admitted`() {
        val target = seeded()

        listOf(DemoContent.VETS_REVIEWED, DemoContent.VETS_BLOCKED).forEach { name ->
            val build = target.vetsBuild(name)
            val session = "$claude session vets-$name"
            assertEquals(session, target.describe(build.token), "$name is created by the agent")
            assertTrue(
                build.validations.filter { it.stamp != DemoContent.CODE_REVIEW }.all { it.actor == session },
                "The agent records the validations of $name",
            )
            assertEquals(session, build.promotionActors[build.promotions.indexOfFirst { it.first == DemoContent.BRONZE }])
            // Backdating is editing the build, which the agent policy denies
            assertEquals(InMemoryDemoTarget.SEED_ACTOR, build.trail.single { it.type == "build.updated" }.actor)
        }
    }

    @Test
    fun `a person records the review and makes the promotions which do not admit agents`() {
        val reviewed = seeded().vetsBuild(DemoContent.VETS_REVIEWED)

        assertEquals(
            InMemoryDemoTarget.SEED_ACTOR,
            reviewed.validations.single { it.stamp == DemoContent.CODE_REVIEW }.actor,
        )
        assertEquals(
            listOf(DemoContent.BRONZE to "$claude session vets-${DemoContent.VETS_REVIEWED}") +
                    listOf(DemoContent.SILVER, DemoContent.GOLD).map { it to InMemoryDemoTarget.SEED_ACTOR },
            reviewed.promotions.map { it.first }.zip(reviewed.promotionActors),
        )
    }

    @Test
    fun `the assisted build nobody reviewed is short of GOLD, and the server would refuse it`() {
        val target = seeded()
        val blocked = target.vetsBuild(DemoContent.VETS_BLOCKED)

        assertEquals(listOf(DemoContent.BRONZE, DemoContent.SILVER), blocked.promotions.map { it.first })
        assertTrue(blocked.validations.none { it.stamp == DemoContent.CODE_REVIEW })
        val refusal = assertFailsWith<IllegalStateException> {
            blocked.promote(DemoContent.GOLD, "", clock.instant().atZone(ZoneOffset.UTC).toLocalDateTime(), byPerson = true)
        }
        assertEquals("Assisted build: ${DemoContent.CODE_REVIEW} must pass first.", refusal.message)
    }

    @Test
    fun `the build which is not assisted reaches GOLD without a review`() {
        val build = seeded().vetsBuild("202")

        assertTrue(DemoContent.GOLD in build.promotions.map { it.first })
        assertTrue(build.validations.none { it.stamp == DemoContent.CODE_REVIEW })
    }

    @Test
    fun `the gates on agents are configured`() {
        val branch = seeded().vets()

        assertEquals(setOf(DemoContent.BRONZE), branch.agentsAdmitted)
        assertEquals(mapOf(DemoContent.GOLD to listOf(DemoContent.CODE_REVIEW)), branch.assistedBuildsRequire.toMap())
        assertEquals(setOf(DemoContent.CODE_REVIEW), branch.nonAgentStamps)
    }

    @Test
    fun `the deployment an agent asks for waits at CANDIDATE for a person's approval`() {
        val target = seeded()
        val slot = target.environments().filterIsInstance<InMemoryDemoTarget.InMemoryEnvironment>()
            .single { it.name == DemoContent.STAGING }
            .slots.single { it.project.name == DemoContent.VETS }

        assertTrue(slot.agentsAdmitted, "The slot admits agents")
        assertTrue(slot.admissionRules.any { it.ruleId == SlotAdmissionRules.MANUAL }, "The slot asks for an approval")
        val deployment = slot.deployments.single()
        assertEquals(DemoContent.VETS_BLOCKED, deployment.build.name)
        assertEquals(DeploymentStop.CANDIDATE, deployment.stopAt)
        assertEquals("$codex session vets-deploy-${DemoContent.VETS_BLOCKED}", deployment.actor)
    }

    @Test
    fun `the demo dashboard shows the agent activity over the last week`() {
        val widget = DemoContent.dataset(emptyList()).dashboard!!.widgets
            .single { it.key == "extension/agents/AgentActivity" }

        assertEquals(DemoContent.AGENT_ACTIVITY_WIDGET_UUID, widget.uuid)
        assertEquals(7, widget.config.path("window").asInt())
    }

    @Test
    fun `the agents part of the demo is reproducible`() {
        val target = InMemoryDemoTarget()
        val seed = DemoSeed(target, clock, log = {})
        seed.run(DemoContent.dataset(emptyList()))
        val first = target.snapshot()
        seed.run(DemoContent.dataset(emptyList()))

        assertEquals(first, target.snapshot())
        assertTrue("agent ${DemoContent.AGENT_CLAUDE}[agent]" in first, first)
    }

    // The dataset's checks, before anything is deleted

    private fun refusal(dataset: DemoDataset): String =
        assertFailsWith<IllegalArgumentException> { dataset.validate() }.message.orEmpty()

    private val agent = AgentSpec("bot", "Bot", "Other", "")

    private fun dataset(
        agents: List<AgentSpec> = listOf(agent),
        branch: BranchSpec,
        environments: List<EnvironmentSpec> = emptyList(),
        deployments: List<DeploymentSpec> = emptyList(),
    ) = DemoDataset(
        agents = agents,
        projects = listOf(ProjectSpec(name = "p", description = "", branches = listOf(branch))),
        environments = environments,
        deployments = deployments,
    )

    private fun build(name: String = "1", agent: AgentSessionSpec? = null, token: String? = null) =
        BuildSpec(name = name, description = "", creation = BuildCreation.DaysAgo(1), agent = agent, token = token)

    @Test
    fun `a build by an agent the dataset never registers is refused`() {
        val message = refusal(
            dataset(
                agents = emptyList(),
                branch = BranchSpec("main", "", builds = listOf(build(agent = AgentSessionSpec("bot", "s1")))),
            )
        )
        assertTrue("the agent bot, which the dataset never registers" in message, message)
    }

    @Test
    fun `an agent session link which is not https is refused`() {
        val message = refusal(
            dataset(branch = BranchSpec("main", "", builds = listOf(build(agent = AgentSessionSpec("bot", "s1", "http://x"))))),
        )
        assertTrue("not an absolute https URL" in message, message)
    }

    @Test
    fun `a build both through a token and by an agent is refused`() {
        val message = refusal(
            dataset(branch = BranchSpec("main", "", builds = listOf(build(agent = AgentSessionSpec("bot", "s1"), token = "ci")))),
        )
        assertTrue("both through the token ci and by the agent bot" in message, message)
    }

    @Test
    fun `an invalid agent slug is refused`() {
        val message = refusal(
            dataset(agents = listOf(AgentSpec("Bot!", "Bot", "Other", "")), branch = BranchSpec("main", "")),
        )
        assertTrue("Agent slug \"Bot!\"" in message, message)
    }

    @Test
    fun `an assisted builds condition naming a stamp the branch does not have is refused`() {
        val message = refusal(
            dataset(
                branch = BranchSpec(
                    "main", "",
                    promotionLevels = listOf(PromotionLevelSpec("GOLD", "", assistedBuildsRequire = listOf("REVIEW"))),
                ),
            )
        )
        assertTrue("requires REVIEW of assisted builds, which the branch does not keep" in message, message)
    }

    @Test
    fun `an agent's test run on a stamp taking evidence from non-agents only is refused`() {
        val message = refusal(
            dataset(
                branch = BranchSpec(
                    "main", "",
                    validationStamps = listOf(ValidationStampSpec("TESTS", "", tests = true, nonAgentEvidence = true)),
                    builds = listOf(
                        build(agent = AgentSessionSpec("bot", "s1"))
                            .copy(tests = listOf(TestRunSpec("TESTS", passed = 1))),
                    ),
                ),
            )
        )
        assertTrue("posts a run of TESTS as the agent bot" in message, message)
    }

    @Test
    fun `a deployment by an agent on a slot which does not admit agents is refused`() {
        val message = refusal(
            dataset(
                branch = BranchSpec("main", "", builds = listOf(build())),
                environments = listOf(EnvironmentSpec("staging", 1, "", slots = listOf(SlotSpec("p", "")))),
                deployments = listOf(
                    DeploymentSpec(
                        "staging",
                        BuildRef("p", "main", "1"),
                        stopAt = DeploymentStop.CANDIDATE,
                        agent = AgentSessionSpec("bot", "s1"),
                    ),
                ),
            )
        )
        assertTrue("the slot does not admit agents" in message, message)
    }

    @Test
    fun `an agent never promotes where agents are not admitted, even if the dataset says so`() {
        // The seed hands such a promotion over to a person: this is the server's refusal it avoids
        val target = InMemoryDemoTarget()
        DemoSeed(target, clock, log = {}).run(
            dataset(
                branch = BranchSpec(
                    "main", "",
                    promotionLevels = listOf(PromotionLevelSpec("SILVER", "")),
                    builds = listOf(build(agent = AgentSessionSpec("bot", "s1")).copy(promotionLevels = listOf("SILVER"))),
                ),
            )
        )
        val build = target.projects().filterIsInstance<InMemoryDemoTarget.InMemoryProject>().single()
            .branches.single().builds.single()
        assertEquals(listOf(InMemoryDemoTarget.SEED_ACTOR), build.promotionActors)
        val refusal = assertFailsWith<IllegalStateException> {
            build.promote("SILVER", "", clock.instant().atZone(ZoneOffset.UTC).toLocalDateTime())
        }
        assertTrue("does not admit agents" in refusal.message.orEmpty(), refusal.message)
        assertNull(build.assistedChange, "No commit, no assisted change")
    }
}
