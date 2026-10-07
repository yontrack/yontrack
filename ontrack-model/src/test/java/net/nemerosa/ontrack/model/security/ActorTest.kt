package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ActorTest {

    private val alice = Actor(account = "alice@yontrack.test", via = ActorVia.UI)

    private val ciToken = Actor(account = "ci@yontrack.test", via = ActorVia.TOKEN, tokenName = "pipeline")

    @Test
    fun `JSON form leaves the absent fields out and names the channel in lower case`() {
        assertEquals(
            """{"account":"ci@yontrack.test","via":"token","tokenName":"pipeline"}""".parseAsJson(),
            ciToken.asJson(),
        )
    }

    @Test
    fun `JSON form of a JWT actor`() {
        val actor = Actor(
            account = "alice@yontrack.test",
            via = ActorVia.JWT,
            jwt = ActorJwt(iss = "https://idp.yontrack.test/realms/yontrack", sub = "f81d4fae"),
        )
        assertEquals(
            """{"account":"alice@yontrack.test","via":"jwt","jwt":{"iss":"https://idp.yontrack.test/realms/yontrack","sub":"f81d4fae"}}""".parseAsJson(),
            actor.asJson(),
        )
    }

    @Test
    fun `JSON form of a system actor on behalf of a user`() {
        val actor = Actor.system(reason = "auto-promotion", onBehalfOf = ciToken)
        assertEquals(
            """{"account":"system","via":"system","system":"auto-promotion","onBehalfOf":{"account":"ci@yontrack.test","via":"token","tokenName":"pipeline"}}""".parseAsJson(),
            actor.asJson(),
        )
    }

    @Test
    fun `JSON form reads back`() {
        val actor = Actor.system(reason = "github-ingestion", onBehalfOf = ciToken.copy(via = ActorVia.WEBHOOK))
        assertEquals(actor, actor.asJson().parse<Actor>())
    }

    @Test
    fun `Running as admin without anybody acts as the system`() {
        assertEquals(
            Actor(account = "system", via = ActorVia.SYSTEM, system = "stale-branches"),
            null.runAs("stale-branches"),
        )
    }

    @Test
    fun `Running as admin without any reason nor anybody`() {
        assertEquals(
            Actor(account = "system", via = ActorVia.SYSTEM),
            null.runAs(null),
        )
    }

    @Test
    fun `Running as admin keeps the user as the one the system acts on behalf of`() {
        assertEquals(
            Actor(account = "system", via = ActorVia.SYSTEM, system = "auto-promotion", onBehalfOf = ciToken),
            ciToken.runAs("auto-promotion"),
        )
    }

    @Test
    fun `Running as admin without reason still says the system acts on behalf of the user`() {
        assertEquals(
            Actor(account = "system", via = ActorVia.SYSTEM, onBehalfOf = alice),
            alice.runAs(null),
        )
    }

    @Test
    fun `Running as admin again without reason keeps the system actor`() {
        val system = alice.runAs("auto-promotion")
        assertEquals(system, system.runAs(null))
    }

    @Test
    fun `Running as admin again for the same reason keeps the system actor`() {
        val system = alice.runAs("github-ingestion")
        assertEquals(system, system.runAs("github-ingestion"))
    }

    @Test
    fun `Running as admin again gives a reason to a system actor which had none`() {
        assertEquals(
            Actor(account = "system", via = ActorVia.SYSTEM, system = "auto-promotion", onBehalfOf = alice),
            alice.runAs(null).runAs("auto-promotion"),
        )
    }

    @Test
    fun `Running as admin again for another reason chains the system actors`() {
        val ingestion = ciToken.runAs("github-ingestion")
        assertEquals(
            Actor(
                account = "system",
                via = ActorVia.SYSTEM,
                system = "auto-promotion",
                onBehalfOf = Actor(
                    account = "system",
                    via = ActorVia.SYSTEM,
                    system = "github-ingestion",
                    onBehalfOf = ciToken,
                ),
            ),
            ingestion.runAs("auto-promotion"),
        )
    }

    @Test
    fun `A user whose context was carried without its channel is a system actor of its own account`() {
        assertEquals(
            Actor(account = "alice@yontrack.test", via = ActorVia.SYSTEM),
            Actor.degraded("alice@yontrack.test"),
        )
    }

    private val agent = ActorAgent(
        name = "claude[agent]",
        displayName = "Claude",
        tool = "Claude Code",
        owner = "damien@yontrack.test",
    )

    @Test
    fun `JSON form of a person is unchanged by the agent fields`() {
        // Exact text: the audit trail hashes the canonical form of this JSON
        assertEquals(
            """{"account":"ci@yontrack.test","via":"token","tokenName":"pipeline"}""",
            ciToken.asJson().toString(),
        )
    }

    @Test
    fun `JSON form of an agent with its session`() {
        val actor = Actor(
            account = "claude[agent]",
            via = ActorVia.TOKEN,
            tokenName = "ci",
            agent = agent,
            agentSession = ActorAgentSession(id = "session-1", link = "https://claude.ai/code/session-1"),
        )
        assertEquals(
            """{"account":"claude[agent]","via":"token","tokenName":"ci","agent":{"name":"claude[agent]","displayName":"Claude","tool":"Claude Code","owner":"damien@yontrack.test"},"agentSession":{"id":"session-1","link":"https://claude.ai/code/session-1"}}""".parseAsJson(),
            actor.asJson(),
        )
        assertEquals(actor, actor.asJson().parse<Actor>())
    }

    @Test
    fun `The agent actor is the actor itself when it is an agent`() {
        val actor = Actor(account = "claude[agent]", via = ActorVia.TOKEN, agent = agent)
        assertEquals(actor, actor.agentActor)
    }

    @Test
    fun `The agent actor is the one the system acts on behalf of`() {
        val actor = Actor(account = "claude[agent]", via = ActorVia.WEBHOOK, agent = agent)
        assertEquals(actor, actor.runAs("github-ingestion").runAs("auto-promotion").agentActor)
    }

    @Test
    fun `No agent actor for a person`() {
        assertEquals(null, alice.runAs("auto-promotion").agentActor)
    }

    @Test
    fun `Agent of an agent account`() {
        val owner = Account.user("Damien", "damien@yontrack.test")
        val account = Account.agent(
            slug = "claude",
            displayName = "Claude",
            owner = owner,
            tool = "Claude Code",
            description = null,
        )
        assertEquals(agent, ActorAgent.of(account))
        assertEquals(null, ActorAgent.of(owner))
    }
}
