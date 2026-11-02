# Tokens

While you authenticate to the Yontrack UI using the identity provider which has been configured, you need to use tokens to access the API. These tokens are therefore needed when you want your CI engine to start feeding or querying information from Yontrack.

To create a token, login into the Yontrack UI and go to your user menu at _User information_ > _User profile_.

In the _API tokens_ section, given a name to this token and click on _Generate token_.

Copy the generated token.

## Using a token for an API call

To perform a GraphQL call you can use the generated token in the `X-Ontrack-Token` HTTP header.

```
X-Ontrack-Token: <your token>
```

## Read-only tokens

A token handed to a script, a dashboard or an MCP client should be unable to alter the record, even
if it leaks. When you generate a token, choose its access: _Full_, preselected, or _Read-only_. The
`generateToken` and `generateAgentToken` mutations take the same choice as their optional `readOnly`
input, which is `false` when not set.

A **read-only token** reads what its owner can read, and changes nothing. The choice is made at
creation and never changes. It follows the usual validity rules of the instance, and it is not
subject to the licence.

### What it refuses

With a read-only token, Yontrack refuses:

* **every GraphQL mutation**, with no exception, including a document naming a mutation among several
  operations. The refusal is a top-level error, and none of the document runs;
* **every REST call** but `GET`, `HEAD` and `OPTIONS`, with a `403`. Only `POST /graphql` and
  `POST /rest/search` are exempted, and nothing below these paths is;
* **every hook** authenticating with it: the hook is recorded as denied.

This includes the data of its owner: tokens, agents, preferences, favourites, selected and own
dashboards, private build filters. In particular, a read-only token cannot generate a full one. The
only thing a read-only token changes is its own _last used_ date.

A refusal always says why, for example:

```
read-only token 'dashboard' may not run a mutation
```

A refusal creates no audit or event entry: nothing happened.

### What it cannot read

A read-only token holds only these functions of its owner:

* the projects and what is in them (`ProjectList`, `ProjectView`), and their findings
  (`ProjectFindingsView`) and subscriptions (`ProjectSubscriptionsRead`);
* the environments and the slots (`EnvironmentList`, `SlotView`);
* the SCM catalog (`SCMCatalogAccessFunction`);
* the audit of the events, the notification recordings and the workflows (`EventsAudit`,
  `NotificationRecordingAccess`, `WorkflowAudit`).

Every other function is denied, including those gating both reads and writes (`AccountManagement`,
`GlobalSettings`, `ApplicationManagement`). The read-only token of an administrator therefore cannot
list the tokens of other accounts, read the settings, or see the jobs and the recordings these gate.

### How a client checks

A client reads the limit before acting, rather than discovering it by being refused:

```graphql
{
  user {
    tokenReadOnly
  }
}
```

`tokenReadOnly` is `true` for a read-only token, `false` for a full one, and `null` when the call did
not use a token. `Token.readOnly` gives the attribute of each listed token.

## Tokens for an agent

An AI coding agent gets tokens of its own, rather than one of yours: register it as an
[agent](../agents/index.md), and generate its tokens from its page.
