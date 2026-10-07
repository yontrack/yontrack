# Agents

AI coding agents — Claude Code, Codex, Copilot, Devin and the like — increasingly act on the
delivery record: they create builds, record validations and read what is deployed. Yontrack gives
such an agent an identity of its own, a **registered agent**, so that the record can tell an agent
from a person and name the person who answers for it.

## What an agent is

An agent is an account of kind *agent*:

* it is identified as `<slug>[agent]`, for example `claude-code-ci[agent]`. The slug has 1 to 32
  lowercase letters, digits or dashes, and never changes;
* it has a display name, the **tool** behind it (Claude Code, Codex, Copilot, Devin, Other, or
  any other name), and an optional description;
* it has exactly one **owner**, the person accountable for it;
* it acts on Yontrack with its own [API tokens](../security/tokens.md), and with nothing else.

An agent **never logs in through the identity provider**. `[` is not valid in an email address, so
no identity provider can issue a login for an agent identifier, and Yontrack refuses any token of
the identity provider whose email is one.

Agents are **never mail recipients**: the mail notifications skip any agent identifier among their
recipients.

!!! note "Rights"

    An agent has **no rights at all** for now: it is created without groups nor permissions, and
    even a role granted to it is ignored. With its token, an agent can tell who it is, and nothing
    more. What an agent may do — its owner's rights, narrowed by an agent policy — comes with the
    agent policy.

## The owner

Every agent has an owner, a person — never another agent.

* Any user registers and manages **their own** agents.
* An administrator manages **every** agent, can register one for another owner, and is the only
  one who can **transfer** an agent to another owner.
* Deleting a person deletes **their agents** and the agents' tokens. The confirmation of the
  deletion names them.

Whatever an agent did stays signed with its name, even once the agent is deleted.

## Registering an agent

In the user menu, go to _User information_ > _My agents_ and click on _Register an agent_:

* **Slug** — identifies the agent as `<slug>[agent]`, for good;
* **Display name** — how the agent is shown;
* **Tool** — pick one of the known tools, or type another name;
* **Description** — optional.

Administrators get an additional **Owner** field, and find every agent of the instance in
_System_ > _Agents_, with its tool, its owner, its number of tokens and the last time one of them
was used. In _System_ > _Account management_, agents are hidden by default: the _Show agents_
switch lists them, with their kind.

Through the API, the `registerAgent` mutation does the same, and the `agents` query lists the agents
visible to the caller: all of them for an administrator, their own for anybody else.

```graphql
mutation {
  registerAgent(input: {
    slug: "claude-code-ci",
    displayName: "Claude Code in CI",
    tool: "Claude Code",
  }) {
    agent { id email }
    errors { message }
  }
}
```

## Tokens

The page of an agent lists its tokens and generates new ones. The owner and the administrators
can generate them:

* an agent has as many **named tokens** as needed — one per machine or per pipeline, say;
* a token follows the usual validity rules of the instance;
* the value of a token is **shown only once**, when it is generated: copy it then;
* a token, or all the tokens of an agent, can be revoked at any time.

The agent then uses its token like any other API token, in the `X-Ontrack-Token` header. A call made
with it is authenticated as the agent:

```graphql
{
  user {
    account { email kind owner { email } }
  }
}
```

The `generateAgentToken`, `revokeAgentToken` and `revokeAllAgentTokens` mutations do the same
through the API.

## A CI pipeline is not an agent

A CI pipeline acting through an account holding the `AUTOMATION` role is **automation**, not an
agent: it runs a script that a person wrote and reviewed, and its account stays as it is. Nothing
converts it into an agent.

An agent is a non-human principal that decides what to do by itself — typically an AI coding
agent working on a person's behalf. Register one when the record must be able to say "this was
done by an agent, owned by this person".
