Launching the local dev stack is particularly useful when iterating only on the frontend.

Today, this is done manually from Intellij using:

* launching the docker-compose-dev stack
* launching the Application in KDSL mode: `dev` profile with the following environment variables:
    * MANAGEMENT_ENDPOINT_ACCOUNT_ACCESS=unrestricted
    * ONTRACK_CONFIG_SEARCH_INDEX_IMMEDIATE=true
    * ONTRACK_CONFIG_TEMPLATING_ERRORS=LOGGING_STACK
    * ONTRACK_EXTENSION_JIRA_CLIENT_TYPE=mock
* launching the `npm run dev`

This sequence should be made available to the agents as a simple command (up & down).

Additionally, in order for agents to be able to run in parallel (on different worktrees for example), the stack should
be able to be launched using different ports and different volumes, so that there is no collision between the agents.
