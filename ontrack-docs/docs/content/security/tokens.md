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

## Tokens for an agent

An AI coding agent gets tokens of its own, rather than one of yours: register it as an
[agent](../agents/index.md), and generate its tokens from its page.
