# Calling the local instance's API

The dev stack's API is **Bearer/OIDC only**. `curl -u admin:admin` returns `401` with
`WWW-Authenticate: Bearer`. There is no basic-auth fallback
and no pre-made token lying around, so anything that talks to the API — seeding the demo,
checking what a GraphQL field actually returns — needs a token minted first. A Playwright spec
is the exception: its fixture gets its own token, see
[Running Playwright against the dev stack](#running-playwright-against-the-dev-stack).

`admin`/`admin` are the credentials, but they are Keycloak's, not the API's.

Read the ports from `.yontrack-dev/instance.env` rather than assuming them; every checkout
gets its own (see [DEVELOPMENT.md](../../DEVELOPMENT.md)).

## Getting a token

Two steps: Keycloak gives a bearer token, and the API turns that into a Yontrack API token.

**1. Bearer token from Keycloak.** The dev realm enables the password grant
(`directAccessGrantsEnabled`), and its client secret is committed in
`compose/keycloak/import/dev/ontrack.json` — it is a fixture, not a secret:

```bash
source .yontrack-dev/instance.env
BEARER=$(curl -s -X POST \
  "http://localhost:${YONTRACK_DEV_KEYCLOAK_PORT}/realms/ontrack/protocol/openid-connect/token" \
  -d grant_type=password \
  -d client_id=ontrack-client \
  -d client_secret=ontrack-client-secret \
  -d username=admin -d password=admin | jq -r .access_token)
```

**2. Yontrack API token from the API.** The bearer token is enough to call `generateToken`:

```bash
TOKEN=$(curl -s -X POST "${YONTRACK_DEV_APP_URL}/graphql" \
  -H "Authorization: Bearer $BEARER" -H 'Content-Type: application/json' \
  -d '{"query":"mutation { generateToken(input: {name: \"agent-local\"}) { token { value } } }"}' \
  | jq -r .data.generateToken.token.value)
```

That token goes in the `X-Ontrack-Token` header, and is what `YONTRACK_TOKEN` wants:

```bash
curl -s -X POST "${YONTRACK_DEV_APP_URL}/graphql" \
  -H "X-Ontrack-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"query":"{ projects { id name } }"}'
```

Tokens on a local instance need no cleanup — leave them. A local stack is disposable and
reachable from nowhere else.

## Seeding the demo locally

With a token, the demo dataset is one command — useful whenever you need realistic data
(deployments, promotions, a change log) rather than hand-built fixtures:

```bash
YONTRACK_URL=$YONTRACK_DEV_APP_URL YONTRACK_TOKEN=$TOKEN ./gradlew :ontrack-demo-seed:run
```

**It deletes every project on the target first.** Check what is there before running it —
`select count(*) from projects` through the Postgres container is enough — and do not run it
against an instance holding work you did not put there. See
[demo-seed.md](../../doc/dev-guide/demo-seed.md).

## Running Playwright against the dev stack

`ontrack-web-tests` gets its token from `${ONTRACK_MGT_URL}/manage/account/<user>`, an actuator
endpoint on the management port. The dev stack exposes it (`scripts/dev-stack.sh` sets
`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` and `MANAGEMENT_ENDPOINT_ACCOUNT_ACCESS`), so no
token has to be minted by hand — point the fixture at this checkout's ports:

```bash
source .yontrack-dev/instance.env
cd ontrack-web-tests
npm ci   # first time only
ONTRACK_MGT_URL=http://localhost:${YONTRACK_DEV_MGMT_PORT} \
ONTRACK_UI_URL=${YONTRACK_DEV_UI_URL} \
ONTRACK_BACKEND_URL=${YONTRACK_DEV_APP_URL} \
  npx playwright test <spec> --reporter=list
```

Set all three, every time:

- `ONTRACK_MGT_URL` is the management **root**, without `/manage` — the fixture appends it.
  The API port answers that path with `401`: a `401` means the URL points at the wrong port.
- The fixture's defaults are the main working copy's ports (`8800`, `3000`, `8080`). From a
  linked worktree, a missing variable does not fail: if the main copy's stack is up, the spec
  silently gets its token from — and runs against — **that** stack.

Running one spec this way against a stack you already have up takes seconds, against minutes
for `./gradlew uiTest`, which builds and starts its own stack. Use the Gradle task for a full
verification run and this for the edit-run loop.

The Gradle tasks — `uiTest`, `uiLdapTest`, `uiOidcTest` — need none of this: they run against
the KDSL acceptance stack, not the dev stack, and set the three variables themselves from that
stack's slot (the one in `.yontrack-kdsl/instance.env`). A variable already set in the
environment still wins. On CI they are left alone.
