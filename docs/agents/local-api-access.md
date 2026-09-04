# Calling the local instance's API

The dev stack's API is **Bearer/OIDC only**. `curl -u admin:admin` returns `401` with
`WWW-Authenticate: Bearer`, and so does the management port. There is no basic-auth fallback
and no pre-made token lying around, so anything that talks to the API — seeding the demo,
checking what a GraphQL field actually returns, running a Playwright spec — needs a token
minted first.

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

`ontrack-web-tests` gets its token from `ONTRACK_MGT_URL/manage/account/<user>`, an actuator
endpoint that is **not exposed on the dev stack** — it answers `401`. The fixture calls it
unconditionally, so specs cannot run against the dev stack as-is.

Point `ONTRACK_MGT_URL` at a stub that serves the token from above. No repo change, and the
fixture is none the wiser:

```bash
# stub.py — returns the token for any path
python3 - "$TOKEN" <<'PY' &
import http.server, socketserver, sys
T = sys.argv[1].encode()
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200); self.send_header('Content-Length', str(len(T))); self.end_headers()
        self.wfile.write(T)
    def log_message(self, *a): pass
socketserver.TCPServer.allow_reuse_address = True
socketserver.TCPServer(("127.0.0.1", 8899), H).serve_forever()
PY

cd ontrack-web-tests
ONTRACK_MGT_URL=http://127.0.0.1:8899 npx playwright test <spec> --reporter=list
```

Running one spec this way against a stack you already have up takes seconds, against minutes
for `./gradlew uiTest`, which builds and starts its own stack. Use the Gradle task for a full
verification run and this for the edit-run loop.
