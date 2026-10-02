# AI agents in your delivery: what Yontrack answers

One page for release managers, product owners and compliance teams. The design behind it is in
[`README.md`](README.md).

## The questions you are asking

Coding agents now open branches, write commits, run pipelines and ask to deploy. Four questions
come up every time:

1. **Can agents push to Yontrack?**
2. **How do I know which builds an agent made?**
3. **Can an agent promote, or deploy?**
4. **How do we audit what agents did?**

## The principle: same record, same gates

An agent is one more actor on your delivery record. It records its evidence, it earns levels, it
is held to the same promotions and approval rules as everyone else. The one thing Yontrack adds
is the ability to **tell an agent from a human, and name the human who answers for it**.

## What that gives you

**1. Yes, agents push to Yontrack, and they say who they are.** An agent is registered with a
name, a tool, and an **owner**: the person accountable for it. It gets its own access tokens, and
it can never do more than its owner can. Every action it takes (a build, a test result, a
promotion, a deployment) is signed by the agent, and can carry a link to the agent's session so
you can see the conversation behind the act.

**2. You see which builds were agent-assisted, and how much.** Yontrack reads the markers coding
agents leave in commits and tells you, on every build: how many of its commits were assisted, by
which agents, with links to their sessions. The change log shows it per commit; your release
notes and Slack notifications can say it too.

**3. Agents promote and deploy only where you let them.** By default an agent can record
evidence but cannot promote or deploy. You open a promotion level or an environment to agents
explicitly. A manual approval is never satisfied by an agent: the agent can start a deployment
and wait, but a human resolves it. You can also require that evidence for a given check (a test
suite, a security scan) comes from CI and not from an agent, and that an agent-assisted build
passes a human review before it reaches a level.

**4. You can audit it.** Every action by an agent is on the record with its actor, owner, time
and session. You can watch what an agent did on a build before approving it, list what an agent
did over the last week, and see across all projects how much of your delivery agents drive.
When the ledger export lands, "everything agent X did between these two dates" becomes a file you
hand to an auditor.

**And agents get the context they lack.** Agents already get delivery data from the tools you
have evaluated: the GitHub and GitLab MCP servers expose pipeline runs and logs, Harness, Octopus
and Argo expose deployments and let an agent trigger one behind an approval. All of them answer
"what ran" and "what is deployed". None of them has a notion of a build *earning* a level, so none
can tell an agent that a build is green and verified on staging but not yet approved for release,
and what exactly is missing. Yontrack can, because the levels and the rules that grant them are
its model. An agent that reads that answer before proposing a merge or a deployment is an agent
that stops at the right gate instead of discovering it after the fact. A published conduct guide
tells agents how to behave on your record: identify yourself, record your evidence, ask before you
act.

## What Yontrack will not become

Not an agent runner, not an AI model host, not a trace tool for agent conversations, not a code
reviewer, not a CI engine. Yontrack records what happened and enforces the gates you set.

## Where this stands

This is a design, decided on 2026-10-02, not a release. The cheapest steps (agent markers in
change logs, readiness queries for agents, the conduct guide) come first; the identity model
follows. Reporting across agents, the compliance export, the per-actor delivery metrics and the
rules that condition a level on agent involvement are part of the commercial offering.
