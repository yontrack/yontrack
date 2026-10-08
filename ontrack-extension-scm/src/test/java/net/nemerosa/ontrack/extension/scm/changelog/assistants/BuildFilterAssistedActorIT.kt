package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildAgentCriteria
import net.nemerosa.ontrack.model.structure.BuildSearchForm
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * The branch build filter, and the project build search, on whether the build was assisted and on the
 * actor who created it (#2036).
 *
 * The fixture is one branch with seven builds, from the oldest to the newest:
 *
 * | Build | Created by | Assisted change                    | GOLD |
 * |-------|------------|------------------------------------|------|
 * | b1    | a person   | none (UNKNOWN)                     |      |
 * | b2    | a person   | assisted (YES)                     |      |
 * | b3    | agent A    | no assistant (NO)                  |      |
 * | b4    | agent B    | assisted (YES)                     | yes  |
 * | b5    | a person   | could not be computed (UNKNOWN)    | yes  |
 * | b6    | agent A    | none (UNKNOWN)                     | yes  |
 * | b7    | a person   | no assistant, computed (NO)        | yes  |
 */
class BuildFilterAssistedActorIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private class Fixture(
        val branch: Branch,
        val gold: PromotionLevel,
        val agentA: AgentTestSupport.TestAgent,
        val agentB: AgentTestSupport.TestAgent,
    )

    @Test
    fun `The storage ID of the assisted change property is the one the core filters on`() {
        assertEquals(BuildAgentCriteria.ASSISTED_CHANGE_PROPERTY_TYPE, AssistedChangePropertyType::class.java.name)
        assertEquals(BuildAgentCriteria.ASSISTED_CHANGE_BASIS_UNKNOWN, AssistedChangeBasis.UNKNOWN.name)
        assertEquals(BuildAgentCriteria.ASSISTED_CHANGE_BASIS_DEFAULT, AssistedChangeProperty().basis.name)
    }

    @Test
    fun `Assisted YES`() {
        withFixture { f ->
            assertEquals(listOf("b4", "b2"), f.filter(assisted = "YES"))
            assertEquals(listOf("b4"), f.filter(assisted = "YES", withPromotionLevel = f.gold.name))
        }
    }

    @Test
    fun `Assisted NO`() {
        withFixture { f ->
            assertEquals(listOf("b7", "b3"), f.filter(assisted = "NO"))
            assertEquals(listOf("b7"), f.filter(assisted = "NO", withPromotionLevel = f.gold.name))
        }
    }

    @Test
    fun `Assisted UNKNOWN, for a build without the property and for a value which could not be computed`() {
        withFixture { f ->
            assertEquals(listOf("b6", "b5", "b1"), f.filter(assisted = "UNKNOWN"))
            assertEquals(listOf("b6", "b5"), f.filter(assisted = "UNKNOWN", withPromotionLevel = f.gold.name))
        }
    }

    @Test
    fun `Assisted is case-insensitive`() {
        withFixture { f ->
            assertEquals(listOf("b4", "b2"), f.filter(assisted = "yes"))
        }
    }

    @Test
    fun `Actor HUMAN`() {
        withFixture { f ->
            assertEquals(listOf("b7", "b5", "b2", "b1"), f.filter(actor = "HUMAN"))
            assertEquals(listOf("b7", "b5"), f.filter(actor = "HUMAN", withPromotionLevel = f.gold.name))
        }
    }

    @Test
    fun `Actor AGENT`() {
        withFixture { f ->
            assertEquals(listOf("b6", "b4", "b3"), f.filter(actor = "AGENT"))
            assertEquals(listOf("b6", "b4"), f.filter(actor = "AGENT", withPromotionLevel = f.gold.name))
        }
    }

    @Test
    fun `Actor is one agent`() {
        withFixture { f ->
            assertEquals(listOf("b6", "b3"), f.filter(actor = f.agentA.account.email))
            assertEquals(listOf("b6"), f.filter(actor = f.agentA.account.email, withPromotionLevel = f.gold.name))
            assertEquals(listOf("b4"), f.filter(actor = f.agentB.account.email.uppercase()))
            assertEquals(emptyList(), f.filter(actor = "unknown[agent]"))
        }
    }

    @Test
    fun `Assisted and actor combined`() {
        withFixture { f ->
            assertEquals(listOf("b4"), f.filter(assisted = "YES", actor = "AGENT"))
            assertEquals(listOf("b2"), f.filter(assisted = "YES", actor = "HUMAN"))
            assertEquals(listOf("b3"), f.filter(assisted = "NO", actor = f.agentA.account.email))
            assertEquals(
                listOf("b5"),
                f.filter(assisted = "UNKNOWN", actor = "HUMAN", withPromotionLevel = f.gold.name)
            )
        }
    }

    @Test
    fun `Invalid criteria return no build`() {
        withFixture { f ->
            assertEquals(emptyList(), f.filter(assisted = "maybe"))
            assertEquals(emptyList(), f.filter(actor = "someone@yontrack.test"))
        }
    }

    @Test
    fun `GraphQL paginated builds of a branch`() {
        withFixture { f ->
            run(
                """
                    query Builds(${'$'}id: Int!, ${'$'}filter: StandardBuildFilter) {
                        branch(id: ${'$'}id) {
                            buildsPaginated(filter: ${'$'}filter, size: 10) {
                                pageInfo {
                                    totalSize
                                }
                                pageItems {
                                    name
                                }
                            }
                        }
                    }
                """,
                mapOf(
                    "id" to f.branch.id(),
                    "filter" to mapOf(
                        "assisted" to "UNKNOWN",
                        "actor" to "AGENT",
                        "withPromotionLevel" to f.gold.name,
                    ),
                )
            ) { data ->
                val page = data.path("branch").path("buildsPaginated")
                assertEquals(1, page.path("pageInfo").path("totalSize").asInt())
                assertEquals(listOf("b6"), page.path("pageItems").values().map { it.path("name").asText() })
            }
        }
    }

    @Test
    fun `Project build search`() {
        withFixture { f ->
            fun search(assisted: String? = null, actor: String? = null, promotionName: String? = null) =
                asAdmin {
                    structureService.buildSearch(
                        f.branch.project.id,
                        BuildSearchForm(
                            maximumCount = 20,
                            promotionName = promotionName,
                            assisted = assisted,
                            actor = actor,
                        )
                    ).map { it.name }
                }
            assertEquals(listOf("b4", "b2"), search(assisted = "YES"))
            assertEquals(listOf("b6", "b5", "b1"), search(assisted = "UNKNOWN"))
            assertEquals(listOf("b7"), search(assisted = "NO", promotionName = f.gold.name))
            assertEquals(listOf("b6", "b4"), search(actor = "AGENT", promotionName = f.gold.name))
            assertEquals(listOf("b6", "b3"), search(actor = f.agentA.account.email))
            assertEquals(emptyList(), search(actor = "robot"))
        }
    }

    @Test
    fun `GraphQL project build search`() {
        withFixture { f ->
            run(
                """
                    query Search(${'$'}project: String!, ${'$'}filter: BuildSearchForm) {
                        builds(project: ${'$'}project, buildProjectFilter: ${'$'}filter) {
                            name
                        }
                    }
                """,
                mapOf(
                    "project" to f.branch.project.name,
                    "filter" to mapOf(
                        "assisted" to "YES",
                        "actor" to "HUMAN",
                    ),
                )
            ) { data ->
                assertEquals(listOf("b2"), data.path("builds").values().map { it.path("name").asText() })
            }
        }
    }

    private fun Fixture.filter(
        assisted: String? = null,
        actor: String? = null,
        withPromotionLevel: String? = null,
    ): List<String> = asAdmin {
        var builder = buildFilterService.standardFilterProviderData(20)
        assisted?.let { builder = builder.withAssisted(it) }
        actor?.let { builder = builder.withActor(it) }
        withPromotionLevel?.let { builder = builder.withWithPromotionLevel(it) }
        builder.build().filterBranchBuilds(branch).map { it.name }
    }

    private fun withFixture(code: (Fixture) -> Unit) {
        val owner = asAdmin { doCreateAccount() }
        val agentA = agentTestSupport.registerAgent(owner = owner)
        val agentB = agentTestSupport.registerAgent(owner = owner)
        asAdmin {
            project {
                branch {
                    val gold = promotionLevel("GOLD")
                    val fixture = Fixture(this, gold, agentA, agentB)

                    personBuild("b1")
                    personBuild("b2").assisted(listOf("Claude Code"))
                    agentA.build(this, "b3").assisted(emptyList())
                    agentB.build(this, "b4").assisted(listOf("Codex")).promote(gold)
                    personBuild("b5").apply {
                        setProperty(
                            this,
                            AssistedChangePropertyType::class.java,
                            AssistedChangeProperty.unknown(AssistedChangeProperty.REASON_NO_SCM),
                        )
                    }.promote(gold)
                    agentA.build(this, "b6").promote(gold)
                    personBuild("b7").assisted(emptyList(), basis = AssistedChangeBasis.COMPUTED).promote(gold)

                    code(fixture)
                }
            }
        }
    }

    private fun Branch.personBuild(name: String): Build = build(name)

    private fun AgentTestSupport.TestAgent.build(branch: Branch, name: String): Build =
        agentTestSupport.withToken(token) {
            securityService.asAdmin {
                structureService.newBuild(
                    Build.of(branch, NameDescription.nd(name, ""), securityService.currentSignature)
                )
            }
        }

    private fun Build.assisted(
        assistants: List<String>,
        basis: AssistedChangeBasis = AssistedChangeBasis.SET_BY_CI,
    ): Build = apply {
        setProperty(
            this,
            AssistedChangePropertyType::class.java,
            AssistedChangeProperty(
                basis = basis,
                assistants = assistants,
                assistedCommits = assistants.size,
                totalCommits = 2,
            ),
        )
    }
}
