#!/usr/bin/env bash
#
# Tests for security-dast.sh. `curl` and `gh` are stubbed on the PATH, so nothing here reaches the
# demo or GitHub: the stubs answer from canned values and record every call they receive, which is
# what the assertions read. `jq`, `yq` and `awk` are the real ones - they are what is being tested.
#
# Two of these matter more than the rest, and they are the reason this file exists rather than a
# comment claiming the script is careful:
#
#   * `query-schema` must never produce a schema with a mutation root. It is the only thing
#     between a scanner holding an admin token and a shared instance.
#   * nothing this script prints may carry a URL, a parameter or a token. The repository is
#     public.
#
# Usage: scripts/security-dast-test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load security-dast.sh as a library: defines the functions, runs nothing.
SECURITY_DAST_LIB_ONLY=1
export SECURITY_DAST_LIB_ONLY
# shellcheck source=security-dast.sh
source "$SCRIPT_DIR/security-dast.sh"

# Assertions, shared with the other shell suites.
# shellcheck source=shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/security-dast-test.XXXXXX")" || {
    echo "FATAL: could not create a temporary directory" >&2
    exit 1
}
trap '[ -n "${KEEP_WORK:-}" ] || rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

# ===========================================================================
# Stubs
# ===========================================================================

# curl, for the management port probe and for `fetch`. Prints the status code of the first line of
# $SD_STUB_DIR/answers whose pattern the URL contains, and 000 - a connection failure - otherwise,
# which is what a port nothing listens on looks like.
cat > "$WORK/bin/curl" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
url=""
out=""
previous=""
stdin_data=0
for arg in "$@"; do
    case "$arg" in http*) url="$arg" ;; esac
    [ "$previous" = "-o" ] && out="$arg"
    # A header read from a file: what was in the file, which is where a token is meant to be.
    if [ "$previous" = "-H" ]; then
        case "$arg" in @*) cat "${arg#@}" >> "$SD_STUB_DIR/curl.headers"; echo >> "$SD_STUB_DIR/curl.headers" ;; esac
    fi
    [ "$previous" = "--data-binary" ] && [ "$arg" = "@-" ] && stdin_data=1
    previous="$arg"
done
# Every argument, as another process on the runner could read them off /proc.
printf '%s\n' "$*" >> "$SD_STUB_DIR/curl.argv"
echo "$url" >> "$SD_STUB_DIR/curl.log"
[ "$stdin_data" -eq 1 ] && { cat >> "$SD_STUB_DIR/curl.stdin"; echo >> "$SD_STUB_DIR/curl.stdin"; }
# Keycloak's token endpoint and the API: a canned body into -o, and a canned status on stdout.
for endpoint in token graphql; do
    case "$endpoint:$url" in
        token:*/protocol/openid-connect/token|graphql:*/graphql)
            [ -f "$SD_STUB_DIR/$endpoint.fails" ] && { echo "curl: (7) Failed to connect" >&2; exit 7; }
            cp "$SD_STUB_DIR/$endpoint.body" "$out"
            cat "$SD_STUB_DIR/$endpoint.status"
            exit 0
            ;;
    esac
done
# A download: the bytes of $SD_STUB_DIR/download, or a failure when there is none.
if [ -n "$out" ] && [ "$out" != "/dev/null" ]; then
    [ -f "$SD_STUB_DIR/download" ] || { echo "curl: (22) The requested URL returned error: 404" >&2; exit 22; }
    cp "$SD_STUB_DIR/download" "$out"
    exit 0
fi
if [ -f "$SD_STUB_DIR/answers" ]; then
    while IFS=' ' read -r pattern code; do
        [ -n "$pattern" ] || continue
        case "$url" in
            *"$pattern"*) printf '%s' "$code"; exit 0 ;;
        esac
    done < "$SD_STUB_DIR/answers"
fi
printf '000'
exit 0
STUB
chmod +x "$WORK/bin/curl"

# gh, for the publication. Records the call and fails when told to.
cat > "$WORK/bin/gh" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
printf '%s\n' "$*" >> "$SD_STUB_DIR/gh.log"
if [ -f "$SD_STUB_DIR/gh_fails" ]; then
    echo "gh: HTTP 422" >&2
    exit 1
fi
echo '{"content":{"path":"ok"}}'
STUB
chmod +x "$WORK/bin/gh"

PATH="$WORK/bin:$PATH"
export PATH
SD_STUB_DIR="$WORK"
export SD_STUB_DIR

# ===========================================================================
# query-schema - the mechanism that keeps mutations out of the scan
# ===========================================================================

cat > "$WORK/schema.graphql" <<'SDL'
schema {
  query: Query
  mutation: Mutation
  subscription: Subscription
}

type Query {
  projects: [Project]
  "A field that happens to be called subscription, which must survive"
  eventSubscriptions: [EventSubscriptionPayload]
}

type Mutation {
  deleteProject(input: DeleteProjectInput): DeleteProjectPayload
}

input DeleteProjectInput {
  id: Int!
}

type Subscription {
  events: EventPayload
}

type Project {
  name: String!
  subscription: EventSubscriptionPayload
  mutation: String
}
SDL

out="$(sd_query_schema "$WORK/schema.graphql" "$WORK/query-only.graphql" 2>&1)"; rc=$?
assert_eq "0" "$rc" "query-schema: succeeds on the generated shape"
assert_not_contains "$(cat "$WORK/query-only.graphql")" "type Mutation {" \
    "query-schema: removes the Mutation root type"
assert_not_contains "$(cat "$WORK/query-only.graphql")" "type Subscription {" \
    "query-schema: removes the Subscription root type"
assert_not_contains "$(cat "$WORK/query-only.graphql")" "mutation: Mutation" \
    "query-schema: removes the mutation root from the schema block"
assert_not_contains "$(cat "$WORK/query-only.graphql")" "subscription: Subscription" \
    "query-schema: removes the subscription root from the schema block"
assert_contains "$(cat "$WORK/query-only.graphql")" "query: Query" \
    "query-schema: keeps the query root"
# mktemp makes 600, and the scanner reads this from a container running as another uid. A schema
# it cannot open looks exactly like a schema it cannot parse.
assert_contains "$(ls -l "$WORK/query-only.graphql")" "-rw-r--r--" \
    "query-schema: leaves the schema readable by the scanner's uid"
assert_not_contains "$(cat "$WORK/query-only.graphql")" "deleteProject" \
    "query-schema: the mutation's fields go with it"
assert_contains "$(cat "$WORK/query-only.graphql")" "input DeleteProjectInput" \
    "query-schema: leaves the mutation's input types alone - unreachable is not invalid, and a schema that no longer parses is a failed scan"

# The trap this replaces a hand-curated query list to avoid: Yontrack has ordinary fields called
# `subscription` and `mutation` on dozens of types, and a naive strip would gut the schema.
assert_contains "$(cat "$WORK/query-only.graphql")" "  subscription: EventSubscriptionPayload" \
    "query-schema: keeps a field that happens to be called subscription"
assert_contains "$(cat "$WORK/query-only.graphql")" "  mutation: String" \
    "query-schema: keeps a field that happens to be called mutation"

# A schema with nothing to strip is a schema this did not understand.
cat > "$WORK/no-roots.graphql" <<'SDL'
schema {
  query: Query
}
type Query { projects: [Project] }
SDL
out="$(sd_query_schema "$WORK/no-roots.graphql" "$WORK/nope.graphql" 2>&1)"; rc=$?
assert_eq "1" "$rc" "query-schema: refuses a schema it found no mutation root in"
assert_contains "$out" "refusing to guess" "query-schema: says why it refused"

out="$(sd_query_schema "$WORK/absent.graphql" "$WORK/nope.graphql" 2>&1)"; rc=$?
assert_eq "1" "$rc" "query-schema: fails when the input does not exist"

# ===========================================================================
# render-plan
# ===========================================================================

# The tokens of the scanner roles (#1769), as `login` leaves them: one file per role.
mkdir -p "$WORK/tokens"
printf '%s' 'tok&en\with/specials' > "$WORK/tokens/scan-readonly.token"
export DAST_TOKEN_DIR="$WORK/tokens"

cat > "$WORK/plan.yaml" <<'PLAN'
jobs:
  - type: replacer
    rules:
      - matchString: "Authorization"
        replacementString: "Bearer ${DAST_BEARER_TOKEN}"
  - type: spider
    parameters:
      url: "${DAST_TARGET}/"
PLAN

out="$(sd_render_plan "$WORK/plan.yaml" "$WORK/plan-rendered.yaml" scan-readonly 2>&1)"; rc=$?
assert_eq "0" "$rc" "render-plan: succeeds"
assert_contains "$(cat "$WORK/plan-rendered.yaml")" 'replacementString: "Bearer tok&en\with/specials"' \
    "render-plan: substitutes the role's token, containing regex and sed metacharacters, literally"
# shellcheck disable=SC2016  # deliberate: the literal ZAP expands is what is being asserted
assert_contains "$(cat "$WORK/plan-rendered.yaml")" '${DAST_TARGET}/' \
    "render-plan: leaves the variables ZAP itself expands alone"
assert_not_contains "$out" "tok&en" "render-plan: does not print the token"

out="$(sd_render_plan "$WORK/plan.yaml" "$WORK/plan-rendered.yaml" scan-nobody 2>&1)"; rc=$?
assert_eq "1" "$rc" "render-plan: refuses to render for a role that did not log in"
assert_contains "$out" "scan-nobody" "render-plan: names the role with no token"

out="$(sd_render_plan "$WORK/plan.yaml" "$WORK/plan-rendered.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "render-plan: refuses to render without a role"

printf 'jobs: []\n' > "$WORK/plan-notoken.yaml"
out="$(sd_render_plan "$WORK/plan-notoken.yaml" "$WORK/plan-rendered.yaml" scan-readonly 2>&1)"; rc=$?
assert_eq "1" "$rc" "render-plan: refuses a plan with no placeholder rather than scanning unauthenticated"

# The old placeholder is gone with DEMO_TOKEN: a plan still carrying it would send it literally.
# shellcheck disable=SC2016  # deliberate: the literal placeholder is what is being written
printf 'x: "${DEMO_TOKEN}"\n' > "$WORK/plan-old.yaml"
out="$(sd_render_plan "$WORK/plan-old.yaml" "$WORK/plan-rendered.yaml" scan-readonly 2>&1)"; rc=$?
assert_eq "1" "$rc" "render-plan: a plan still written for DEMO_TOKEN is refused"

# ===========================================================================
# actuator - the one check that stops the run
# ===========================================================================

: > "$WORK/curl.log"
out="$(sd_actuator "https://demo.example.com" 2>&1)"; rc=$?
assert_eq "0" "$rc" "actuator: passes when nothing answers"
assert_contains "$(cat "$WORK/curl.log")" ":8800/manage/health" "actuator: probes the management port"
assert_contains "$(cat "$WORK/curl.log")" "/manage/health" "actuator: probes the management path on the public host too"

: > "$WORK/curl.log"
echo ":8800/manage/health 200" > "$WORK/answers"
out="$(sd_actuator "https://demo.example.com" 2>&1)"; rc=$?
rm -f "$WORK/answers"
assert_eq "1" "$rc" "actuator: fails when the management port answers"
assert_contains "$out" "incident" "actuator: says this is an incident rather than a finding"

: > "$WORK/curl.log"
echo ":8800/actuator/health 401" > "$WORK/answers"
out="$(sd_actuator "https://demo.example.com" 2>&1)"; rc=$?
rm -f "$WORK/answers"
assert_eq "1" "$rc" "actuator: an authenticated management port is still an exposed one"

: > "$WORK/curl.log"
echo "/manage/health 404" > "$WORK/answers"
out="$(sd_actuator "https://demo.example.com" 2>&1)"; rc=$?
rm -f "$WORK/answers"
assert_eq "0" "$rc" "actuator: a 404 is not an exposure"

# ===========================================================================
# login - a Keycloak password grant per scanner role (#1769)
# ===========================================================================

# No DAST_KEYCLOAK_REALM_URL: the realm is derived from the target, so that the workflow does not
# print a URL beyond the demo's base in every step's environment.
export DAST_TARGET="https://demo.example.com"
export DAST_KEYCLOAK_CLIENT_ID="yontrack-client"
export DEMO_KEYCLOAK_CLIENT_SECRET='client&secret'
export DAST_SCAN_READONLY_PASSWORD='pa ss&wo=rd%'
unset GITHUB_ACTIONS

login_reset() {
    rm -f "$WORK/curl.argv" "$WORK/curl.stdin" "$WORK/curl.headers" "$WORK/token.fails"
    : > "$WORK/curl.log"
    rm -rf "$WORK/login"
}

JWT_RO='eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzY2FuLXJlYWRvbmx5In0.c2lnLXJlYWRvbmx5'
login_reset
printf '{"access_token":"%s","expires_in":3600,"token_type":"Bearer","refresh_token":"r"}' "$JWT_RO" > "$WORK/token.body"
printf '200' > "$WORK/token.status"
out="$(sd_login scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_eq "0" "$rc" "login: a good password gets a token"
assert_eq "$JWT_RO" "$(cat "$WORK/login/scan-readonly.token")" "login: writes the access token to the role's token file"
assert_contains "$(ls -l "$WORK/login/scan-readonly.token")" "-rw-------" "login: the token file is readable by the runner's user only"
assert_contains "$(cat "$WORK/curl.log")" "https://demo.example.com/keycloak/realms/ontrack/protocol/openid-connect/token" \
    "login: asks the demo realm's token endpoint"
assert_contains "$out" "scan-readonly" "login: names the account that logged in"
assert_contains "$out" "3600" "login: says how long the token is valid for"
assert_not_contains "$out" "$JWT_RO" "login: does not print the token"
assert_not_contains "$(cat "$WORK/curl.argv")" "pa ss" "login: the password is on no command line"
assert_not_contains "$(cat "$WORK/curl.argv")" "client&secret" "login: the client secret is on no command line"
assert_contains "$(cat "$WORK/curl.stdin")" "grant_type=password" "login: a password grant"
assert_contains "$(cat "$WORK/curl.stdin")" "username=scan-readonly" "login: as the role's own account"
assert_contains "$(cat "$WORK/curl.stdin")" "client_id=yontrack-client" "login: with the demo's client"
assert_contains "$(cat "$WORK/curl.stdin")" "password=pa%20ss%26wo%3Drd%25" \
    "login: the password is form-encoded, so a & or = in it does not break the request"
assert_contains "$(cat "$WORK/curl.stdin")" "client_secret=client%26secret" "login: so is the client secret"

# In Actions, the token is also registered as a mask, so that the runner blanks it from the log too.
login_reset
out="$(GITHUB_ACTIONS=true sd_login scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_contains "$out" "::add-mask::$JWT_RO" "login: masks the token in Actions"

# A pass must not outlive its token.
login_reset
printf '{"access_token":"%s","expires_in":300}' "$JWT_RO" > "$WORK/token.body"
out="$(sd_login scan-readonly "$WORK/login" 900 2>&1)"; rc=$?
assert_eq "1" "$rc" "login: refuses a token that would expire before the pass it is for ends"
assert_contains "$out" "300" "login: says how long the token lives"
assert_contains "$out" "900" "login: and how long the pass may take"
assert_eq "no" "$([ -e "$WORK/login/scan-readonly.token" ] && echo yes || echo no)" "login: and leaves no token behind"
printf '{"access_token":"%s","expires_in":3600}' "$JWT_RO" > "$WORK/token.body"

# A bad password is an error that stops the run - never a scan that quietly goes on unauthenticated.
login_reset
printf '{"error":"invalid_grant","error_description":"Invalid user credentials"}' > "$WORK/token.body"
printf '401' > "$WORK/token.status"
out="$(sd_login scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "login: a bad password fails"
assert_contains "$out" "scan-readonly" "login: names the account that could not log in"
assert_contains "$out" "invalid_grant" "login: gives the error class"
assert_contains "$out" "401" "login: and the status"
assert_not_contains "$out" "pa ss" "login: never prints the password"
assert_eq "no" "$([ -e "$WORK/login/scan-readonly.token" ] && echo yes || echo no)" "login: writes no token file on a failure"

# A 200 without a token is not a login either.
login_reset
printf '{"token_type":"Bearer"}' > "$WORK/token.body"
printf '200' > "$WORK/token.status"
out="$(sd_login scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "login: an answer with no access token fails"

login_reset
touch "$WORK/token.fails"
out="$(sd_login scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "login: Keycloak not answering fails"
rm -f "$WORK/token.fails"
printf '{"access_token":"%s","expires_in":3600}' "$JWT_RO" > "$WORK/token.body"

# A missing secret fails before anything is sent.
login_reset
out="$(sd_login scan-admin "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "login: a role whose password secret is not set fails"
assert_contains "$out" "DAST_SCAN_ADMIN_PASSWORD" "login: names the missing secret"
assert_eq "" "$(cat "$WORK/curl.log")" "login: and sends nothing"
login_reset
out="$(DEMO_KEYCLOAK_CLIENT_SECRET='' sd_login scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "login: a missing client secret fails"
assert_contains "$out" "DEMO_KEYCLOAK_CLIENT_SECRET" "login: names the missing client secret"
assert_eq "" "$(cat "$WORK/curl.log")" "login: and sends nothing either"
login_reset
out="$(sd_login scan-root "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "login: an account that is not one of the three scanner roles is refused"

# ===========================================================================
# whoami - the API accepts the token, as the group the CasC gives the role
# ===========================================================================

login_reset
mkdir -p "$WORK/login"
printf '%s' "$JWT_RO" > "$WORK/login/scan-readonly.token"
printf '%s' 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzY2FuLXByb2plY3QifQ.c2lnLXByb2plY3Q' > "$WORK/login/scan-project.token"
export DAST_CASC="$SCRIPT_DIR/../security/dast/casc.yaml"
export DAST_TARGET="https://demo.example.com"

whoami_body() { # groups-json project-count
    jq -n -c --argjson groups "$1" --argjson n "$2" \
        '{data: {user: {mappedGroups: ($groups | map({name: .}))}, projects: [range($n) | {id: .}], info: {version: {full: "5.5.0-abc1234"}}}}' \
        > "$WORK/graphql.body"
}

whoami_body '["DAST Read-only"]' 12
printf '200' > "$WORK/graphql.status"
: > "$WORK/github-output"
out="$(GITHUB_OUTPUT="$WORK/github-output" sd_whoami scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_eq "0" "$rc" "whoami: passes when the API maps the role to its CasC group"
assert_contains "$out" "DAST Read-only" "whoami: names the group - declared in the public casc.yaml"
assert_contains "$out" "12 project(s)" "whoami: counts the projects the role can see"
assert_contains "$(cat "$WORK/github-output")" "version=5.5.0-abc1234" "whoami: reports the version the demo runs"
assert_contains "$(cat "$WORK/curl.headers")" "Authorization: Bearer $JWT_RO" "whoami: sends the token as a bearer token"
assert_not_contains "$(cat "$WORK/curl.argv")" "$JWT_RO" "whoami: the token is on no command line"
assert_not_contains "$out" "$JWT_RO" "whoami: does not print the token"
assert_not_contains "$out" "demo.example.com" "whoami: prints no URL"

whoami_body '[]' 12
out="$(sd_whoami scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "whoami: a token the API accepts without the role's group is not the role being scanned"
assert_contains "$out" "DAST Read-only" "whoami: says which group was expected"

printf '401' > "$WORK/graphql.status"
printf '' > "$WORK/graphql.body"
out="$(sd_whoami scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "whoami: the API refusing the token fails"
assert_contains "$out" "401" "whoami: with the status"
printf '200' > "$WORK/graphql.status"

printf '{"errors":[{"message":"boom"}]}' > "$WORK/graphql.body"
out="$(sd_whoami scan-readonly "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "whoami: GraphQL errors fail"

whoami_body '["DAST Project"]' 0
out="$(sd_whoami scan-project "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "whoami: scan-project with no project means the CasC was not re-applied - its scan would test nothing"

whoami_body '["DAST Project"]' 1
out="$(sd_whoami scan-project "$WORK/login" 2>&1)"; rc=$?
assert_eq "0" "$rc" "whoami: scan-project with its one project passes"
assert_contains "$out" "1 project(s)" "whoami: counts it"
assert_not_contains "$out" "WARNING" "whoami: no warning when it sees what the CasC grants"

whoami_body '["DAST Project"]' 4
out="$(sd_whoami scan-project "$WORK/login" 2>&1)"; rc=$?
assert_eq "0" "$rc" "whoami: scan-project seeing more is reported, not a failure - the instance may grant view to all"
assert_contains "$out" "WARNING" "whoami: but it is flagged"
assert_contains "$out" "4 project(s)" "whoami: with the counts"
assert_contains "$out" "grants it 1" "whoami: against what casc.yaml grants"
assert_contains "$out" "security/dast/casc.yaml grants it 1" "whoami: naming casc.yaml by its path in the repository"
assert_not_contains "$out" "$SD_ROOT" "whoami: and not by the runner's checkout directory"

out="$(sd_whoami scan-admin "$WORK/login" 2>&1)"; rc=$?
assert_eq "1" "$rc" "whoami: a role that did not log in fails"

# ===========================================================================
# assert-authenticated - no API pass outlived its token
# ===========================================================================

cat > "$WORK/auth.har" <<'JSON'
{"log": {"entries": [
  {"request": {"url": "https://demo.example.com/graphql", "method": "POST"}, "response": {"status": 200}},
  {"request": {"url": "https://demo.example.com/graphql", "method": "POST"}, "response": {"status": 200}},
  {"request": {"url": "https://demo.example.com/", "method": "GET"}, "response": {"status": 401}}
]}}
JSON
out="$(sd_assert_authenticated "$WORK/auth.har" 2>&1)"; rc=$?
assert_eq "0" "$rc" "assert-authenticated: passes when every /graphql request was authenticated"
assert_contains "$out" "2" "assert-authenticated: prints the counts"
assert_not_contains "$out" "demo.example.com" "assert-authenticated: prints no URL"

cat > "$WORK/unauth.har" <<'JSON'
{"log": {"entries": [
  {"request": {"url": "https://demo.example.com/graphql", "method": "POST"}, "response": {"status": 200}},
  {"request": {"url": "https://demo.example.com/graphql", "method": "POST"}, "response": {"status": 401}}
]}}
JSON
out="$(sd_assert_authenticated "$WORK/unauth.har" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-authenticated: a 401 on /graphql means part of the pass ran unauthenticated"

cat > "$WORK/nographql.har" <<'JSON'
{"log": {"entries": [
  {"request": {"url": "https://demo.example.com/", "method": "GET"}, "response": {"status": 200}}
]}}
JSON
out="$(sd_assert_authenticated "$WORK/nographql.har" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-authenticated: an API pass that sent nothing to /graphql proves nothing"
out="$(sd_assert_authenticated "$WORK/absent.har" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-authenticated: a missing HAR fails"

# ===========================================================================
# normalize-zap
# ===========================================================================

cat > "$WORK/zap.json" <<'JSON'
{
  "@programName": "ZAP",
  "@version": "2.16.1",
  "site": [
    {
      "@name": "https://demo.example.com",
      "alerts": [
        {"pluginid": "10038", "alertRef": "10038-1", "alert": "CSP Header Not Set",
         "riskcode": "2", "confidence": "3", "desc": "<p>CSP is an <b>added</b> layer.</p>",
         "solution": "<p>Set it.</p>", "reference": "", "cweid": "693",
         "instances": [{"uri": "https://demo.example.com/", "method": "GET", "param": "", "evidence": "", "attack": "", "otherinfo": ""}]},
        {"pluginid": "10098", "alertRef": "10098", "alert": "Cross-Domain Misconfiguration",
         "riskcode": "2", "confidence": "2", "desc": "<p>CORS.</p>", "solution": "", "reference": "", "cweid": "264",
         "instances": [{"uri": "https://demo.example.com/graphql", "method": "POST", "param": "", "evidence": "*", "attack": "", "otherinfo": ""}]},
        {"pluginid": "10037", "alertRef": "10037", "alert": "Server Leaks Information",
         "riskcode": "0", "confidence": "2", "desc": "<p>Info.</p>", "solution": "", "reference": "", "cweid": "200",
         "instances": [{"uri": "https://demo.example.com/", "method": "GET", "param": "", "evidence": "Next.js", "attack": "", "otherinfo": ""}]},
        {"pluginid": "99999", "alertRef": "99999", "alert": "GraphQL Introspection Enabled",
         "riskcode": "1", "confidence": "3", "desc": "<p>Schema.</p>", "solution": "", "reference": "", "cweid": "200",
         "instances": [{"uri": "https://demo.example.com/graphql", "method": "POST", "param": "", "evidence": "", "attack": "", "otherinfo": ""}]}
      ]
    },
    {
      "@name": "https://demo.example.com:443",
      "alerts": [
        {"pluginid": "10038", "alertRef": "10038-1", "alert": "CSP Header Not Set",
         "riskcode": "2", "confidence": "3", "desc": "<p>CSP.</p>", "solution": "", "reference": "", "cweid": "693",
         "instances": [{"uri": "https://demo.example.com/mobile", "method": "GET", "param": "", "evidence": "", "attack": "", "otherinfo": ""}]}
      ]
    }
  ]
}
JSON

normalized="$(sd_normalize_zap "$WORK/zap.json")"; rc=$?
assert_eq "0" "$rc" "normalize-zap: reads a traditional-json report"
assert_eq "2.16.1" "$(printf '%s' "$normalized" | jq -r '.version')" \
    "normalize-zap: carries the scanner version, so a report says what found what"
assert_eq "4" "$(printf '%s' "$normalized" | jq -r '.findings | length')" \
    "normalize-zap: one finding per rule, not per site"
assert_eq "2" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "10038") | .instances | length')" \
    "normalize-zap: merges the instances of the same rule across sites"
assert_eq "MEDIUM" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "10038") | .risk')" \
    "normalize-zap: riskcode 2 is MEDIUM"
assert_eq "INFO" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "10037") | .risk')" \
    "normalize-zap: riskcode 0 is INFO"
assert_eq "CSP is an added layer." "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "10038") | .description')" \
    "normalize-zap: strips the HTML out of the prose"

out="$(sd_normalize_zap "$WORK/absent.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "normalize-zap: fails when there is no report"
echo 'not json' > "$WORK/broken.json"
out="$(sd_normalize_zap "$WORK/broken.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "normalize-zap: fails rather than reporting no findings at all"

printf '%s' "$normalized" > "$WORK/findings.json"

# ===========================================================================
# assert-no-mutations
# ===========================================================================

cat > "$WORK/clean.har" <<'JSON'
{"log": {"entries": [
  {"request": {"url": "https://demo.example.com/", "method": "GET"}},
  {"request": {"url": "https://demo.example.com/graphql", "method": "POST",
   "postData": {"text": "{\"query\":\"query {projects {name}}\"}"}}},
  {"request": {"url": "https://demo.example.com/graphql", "method": "POST",
   "postData": {"text": "{\"query\":\"{mutationLogEntries {id}}\"}"}}},
  {"request": {"url": "https://demo.example.com/graphql", "method": "POST",
   "postData": {"text": "{\"query\":\"subscription {__typename}\"}"}}}
]}}
JSON
out="$(sd_assert_no_mutations "$WORK/clean.har" 2>&1)"; rc=$?
assert_eq "0" "$rc" "assert-no-mutations: passes on a query-only run"
assert_contains "$out" "Mutations: 0" "assert-no-mutations: prints the counts"
assert_not_contains "$out" "demo.example.com" "assert-no-mutations: prints no URL"

cat > "$WORK/dirty.har" <<'JSON'
{"log": {"entries": [
  {"request": {"url": "https://demo.example.com/graphql", "method": "POST",
   "postData": {"text": "{\"query\":\"mutation {deleteProject(input: {id: 1}) {errors {message}}}\"}"}}}
]}}
JSON
out="$(sd_assert_no_mutations "$WORK/dirty.har" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-no-mutations: fails when a mutation was sent"
assert_contains "$out" "wrote to the target" "assert-no-mutations: says what went wrong"

cat > "$WORK/sub.har" <<'JSON'
{"log": {"entries": [
  {"request": {"url": "https://demo.example.com/graphql", "method": "POST",
   "postData": {"text": "{\"query\":\"subscription {events {id}}\"}"}}}
]}}
JSON
out="$(sd_assert_no_mutations "$WORK/sub.har" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-no-mutations: a subscription beyond the add-on's probe is a failure"

out="$(sd_assert_no_mutations "$WORK/absent.har" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-no-mutations: a missing HAR proves nothing and fails"

# ===========================================================================
# report - levels, suppressions and counting
# ===========================================================================

cat > "$WORK/rules.tsv" <<'TSV'
# comment
10037	IGNORE	noise everywhere
99998	WARN	we take this less seriously than ZAP
TSV
cat > "$WORK/suppressions.yaml" <<'YAML'
version: 1
suppressions:
  - id: graphql-introspection
    name: ".*[Ii]ntrospection( [Qq]uery)?( [Ee]nabled)?"
    url: ".*/graphql.*"
    statement: "The schema is public."
    expired_at: "2099-01-01"
  - id: gone
    rule: "10098"
    statement: "Expired long ago."
    expired_at: "2020-01-01"
YAML
cat > "$WORK/mitigations.yaml" <<'YAML'
version: 1
mitigations:
  - id: headers
    rule: "10038"
    title: "Security headers"
    issue: "https://github.com/yontrack/yontrack/issues/1770"
    change: "Add headers() to next.config.js."
YAML

export DAST_RULES="$WORK/rules.tsv"
export DAST_SUPPRESSIONS="$WORK/suppressions.yaml"
export DAST_MITIGATIONS="$WORK/mitigations.yaml"
SD_RULES="$DAST_RULES"
SD_SUPPRESSIONS="$DAST_SUPPRESSIONS"
SD_MITIGATIONS="$DAST_MITIGATIONS"
SD_DATE="2026-09-16"
export DAST_TARGET="https://demo.example.com"
export DAST_VERSION="5.5.0-abc1234"
export DAST_BUILD="20260916-42"
export DAST_RUN_URL="https://github.com/yontrack/yontrack/actions/runs/7"
export DAST_RUN_ID="7"
export DAST_SCANNERS="OWASP ZAP 2.16.1"
export DAST_DATE="2026-09-16"

counts="$(sd_report "$WORK/report.md" "$WORK/findings.json")"; rc=$?
assert_eq "0" "$rc" "report: succeeds"
assert_contains "$counts" "critical=0" "report: ZAP never yields a CRITICAL"
assert_contains "$counts" "high=0" "report: no HIGH in the fixture"
# 10038 and 10098 count; 10037 is IGNOREd by rules.tsv; 99999 is fully suppressed.
assert_contains "$counts" "medium=2" "report: counts the two MEDIUM rules"
assert_contains "$counts" "low=0" "report: the introspection LOW is suppressed away"
assert_contains "$counts" "suppressed=1" "report: says how many instances were subtracted"

report="$(cat "$WORK/report.md")"
assert_contains "$report" "5.5.0-abc1234" "report: names the version scanned"
assert_contains "$report" "20260916-42" "report: names the build the stamp lands on"
assert_contains "$report" "actions/runs/7" "report: links back to the workflow run"
assert_contains "$report" "Add headers() to next.config.js." "report: carries the mitigation"
assert_contains "$report" "no entry in \`security/dast/mitigations.yaml\`" \
    "report: says so when a rule has no mitigation, rather than staying silent"
assert_contains "$report" "The schema is public." "report: states why a suppression applied"
assert_contains "$report" "Expired, and therefore not applied" \
    "report: an expired suppression is announced, not silently dropped"
assert_not_contains "$report" "Server Leaks Information" \
    "report: an IGNOREd rule is not reported at all"

# The disclosure rule, asserted rather than trusted: counts leave this script, nothing else.
assert_not_contains "$counts" "demo.example.com" "report: the printed counts carry no URL"
assert_not_contains "$counts" "/graphql" "report: the printed counts carry no path"

# A suppression without a statement or an expiry is a finding that would vanish for good.
cat > "$WORK/bad-suppressions.yaml" <<'YAML'
version: 1
suppressions:
  - id: sloppy
    rule: "10038"
YAML
SD_SUPPRESSIONS="$WORK/bad-suppressions.yaml"
out="$(sd_report "$WORK/report2.md" "$WORK/findings.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "report: refuses a suppression with no statement and no expiry"
SD_SUPPRESSIONS="$WORK/suppressions.yaml"

# A misspelt level does nothing at all, quietly, which is worse than failing.
cat > "$WORK/bad-rules.tsv" <<'TSV'
10038	IGNORED	typo
TSV
SD_RULES="$WORK/bad-rules.tsv"
out="$(sd_report "$WORK/report3.md" "$WORK/findings.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "report: refuses a rule level it does not know"
SD_RULES="$WORK/rules.tsv"

out="$(sd_report "$WORK/report4.md" "$WORK/absent.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "report: fails when a findings document is missing"

# ===========================================================================
# graphql-cop (#1766) - its raw stdout, as the workflow captures it
# ===========================================================================

# What `graphql-cop.py -o json` prints: anything the tool says before the JSON (here, nothing), then
# one line holding every test that ran, passed or not. `curl_verify` is the request it sent, headers
# included - the API token with them, which is why it must never reach a report or a log.
cat > "$WORK/cop.out" <<'JSON'
[{"result": true, "title": "Alias Overloading", "description": "Alias Overloading with 100+ aliases is allowed", "impact": "Denial of Service - /graphql", "severity": "HIGH", "color": "red", "curl_verify": "curl -X POST -H \"User-Agent: graphql-cop/1.15\" -H \"X-Ontrack-Token: SECRET-COP\" -d '{\"query\": \"query cop { alias0:__typename \\n }\", \"operationName\": \"cop\"}' 'https://demo.example.com/graphql'"}, {"result": false, "title": "Array-based Query Batching", "description": "Batch queries allowed with 10+ simultaneous queries", "impact": "Denial of Service - /graphql", "severity": "HIGH", "color": "red", "curl_verify": "curl -X POST -H \"X-Ontrack-Token: SECRET-COP\" -d '[{\"query\": \"query cop { __typename }\"}, {\"query\": \"query cop { __typename }\"}]' 'https://demo.example.com/graphql'"}, {"result": true, "title": "Field Suggestions", "description": "Field Suggestions are Enabled", "impact": "Information Leakage - /graphql", "severity": "LOW", "color": "blue", "curl_verify": "curl -X POST -H \"X-Ontrack-Token: SECRET-COP\" -d '{\"query\": \"query cop { __schema { directive } }\", \"operationName\": \"cop\"}' 'https://demo.example.com/graphql'"}, {"result": true, "title": "GET Method Query Support", "description": "GraphQL queries allowed using the GET method", "impact": "Possible Cross Site Request Forgery (CSRF) - /graphql", "severity": "MEDIUM", "color": "yellow", "curl_verify": "curl -X GET -H \"X-Ontrack-Token: SECRET-COP\" -d '' 'https://demo.example.com/graphql?query=query+cop+%7B__typename%7D'"}, {"result": true, "title": "Introspection", "description": "Introspection Query Enabled", "impact": "Information Leakage - /graphql", "severity": "HIGH", "color": "red", "curl_verify": "curl -X POST -H \"X-Ontrack-Token: SECRET-COP\" -d '{\"query\": \"query cop { __schema { types { name fields { name } } } }\", \"operationName\": \"cop\"}' 'https://demo.example.com/graphql'"}, {"result": true, "title": "Introspection-based Circular Query", "description": "Circular-query using Introspection", "impact": "Denial of Service - /graphql", "severity": "HIGH", "color": "red", "curl_verify": "curl -X POST -H \"X-Ontrack-Token: SECRET-COP\" -d '{\"query\": \"query cop { __schema { types { fields { type { name } } } } }\", \"operationName\": \"cop\"}' 'https://demo.example.com/graphql'"}, {"result": true, "title": "POST based url-encoded query (possible CSRF)", "description": "GraphQL accepts non-JSON queries over POST", "impact": "Possible Cross Site Request Forgery - /graphql", "severity": "MEDIUM", "color": "yellow", "curl_verify": "curl -X POST -H \"X-Ontrack-Token: SECRET-COP\" -d 'query=query+cop+%7B+__typename+%7D' 'https://demo.example.com/graphql'"}, {"result": true, "title": "Trace Mode", "description": "Tracing is Enabled", "impact": "Information Leakage - /graphql", "severity": "INFO", "color": "green", "curl_verify": "curl -X POST -H \"X-Ontrack-Token: SECRET-COP\" -d '{\"query\": \"query cop { __typename }\", \"operationName\": \"cop\"}' 'https://demo.example.com/graphql'"}]
JSON

# ---------------------------------------------------------------------------
# normalize-graphql-cop
# ---------------------------------------------------------------------------

normalized="$(sd_normalize_graphql_cop "$WORK/cop.out" "1.16" 2>&1)"; rc=$?
assert_eq "0" "$rc" "normalize-graphql-cop: reads graphql-cop's JSON output"
assert_eq "graphql-cop" "$(printf '%s' "$normalized" | jq -r '.scanner')" \
    "normalize-graphql-cop: names the scanner"
assert_eq "1.16" "$(printf '%s' "$normalized" | jq -r '.version')" \
    "normalize-graphql-cop: carries the pinned version it was given - the tool's own version.py lags its tag"
assert_eq "7" "$(printf '%s' "$normalized" | jq -r '.findings | length')" \
    "normalize-graphql-cop: a test that passed is not a finding"
assert_eq "alias_overloading" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.name == "Alias Overloading") | .rule')" \
    "normalize-graphql-cop: the rule id is graphql-cop's own test name, the one -e takes"
assert_eq "circular_query_introspection" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.name == "Introspection-based Circular Query") | .rule')" \
    "normalize-graphql-cop: the circular query is its own rule, not introspection"
assert_eq "HIGH" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "introspection") | .risk')" \
    "normalize-graphql-cop: HIGH is HIGH"
assert_eq "MEDIUM" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "get_method_support") | .risk')" \
    "normalize-graphql-cop: MEDIUM is MEDIUM"
assert_eq "LOW" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "field_suggestions") | .risk')" \
    "normalize-graphql-cop: LOW is LOW"
assert_eq "INFO" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "trace_mode") | .risk')" \
    "normalize-graphql-cop: INFO stays INFO, for the counting layer to drop"
assert_eq "https://demo.example.com/graphql" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "get_method_support") | .instances[0].uri')" \
    "normalize-graphql-cop: the instance is the endpoint, without the query string the test sent"
assert_eq "GET" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "get_method_support") | .instances[0].method')" \
    "normalize-graphql-cop: keeps the method the test used"
assert_not_contains "$normalized" "SECRET-COP" \
    "normalize-graphql-cop: drops curl_verify, and the API token with it"
assert_not_contains "$normalized" "curl -X" \
    "normalize-graphql-cop: no request detail survives into the findings"

# A run that tested nothing is the tool failing to recognise the endpoint - it says so on stdout and
# prints `[]`. That is a scanner error, never a clean result.
printf '%s\n' "https://demo.example.com/graphql does not seem to be running GraphQL." "[]" > "$WORK/cop-empty.out"
out="$(sd_normalize_graphql_cop "$WORK/cop-empty.out" "1.16" 2>&1)"; rc=$?
assert_eq "1" "$rc" "normalize-graphql-cop: an empty result is a scanner error, not zero findings"
assert_not_contains "$out" "demo.example.com" "normalize-graphql-cop: its error carries no URL"
: > "$WORK/cop-nothing.out"
out="$(sd_normalize_graphql_cop "$WORK/cop-nothing.out" "1.16" 2>&1)"; rc=$?
assert_eq "1" "$rc" "normalize-graphql-cop: empty output is a scanner error"
out="$(sd_normalize_graphql_cop "$WORK/absent.out" "1.16" 2>&1)"; rc=$?
assert_eq "1" "$rc" "normalize-graphql-cop: fails when there is no output"
printf 'Traceback (most recent call last):\n  AttributeError\n' > "$WORK/cop-crash.out"
out="$(sd_normalize_graphql_cop "$WORK/cop-crash.out" "1.16" 2>&1)"; rc=$?
assert_eq "1" "$rc" "normalize-graphql-cop: a crash with no JSON fails"

# ---------------------------------------------------------------------------
# assert-graphql-cop-no-mutations - what graphql-cop says it sent
# ---------------------------------------------------------------------------

out="$(sd_assert_graphql_cop_no_mutations "$WORK/cop.out" 2>&1)"; rc=$?
assert_eq "0" "$rc" "assert-graphql-cop-no-mutations: passes on a query-only run"
assert_contains "$out" "Tests run: 8" "assert-graphql-cop-no-mutations: counts the tests it read requests from"
assert_contains "$out" "Mutations: 0" "assert-graphql-cop-no-mutations: prints the counts"
assert_not_contains "$out" "demo.example.com" "assert-graphql-cop-no-mutations: prints no URL"
assert_not_contains "$out" "SECRET-COP" "assert-graphql-cop-no-mutations: prints no token"
assert_not_contains "$out" "Alias" "assert-graphql-cop-no-mutations: prints no test name"

# The test graphql-cop ships that sends `mutation cop {__typename}` over GET, had it not been excluded.
cat > "$WORK/cop-get-mutation.out" <<'JSON'
[{"result": false, "title": "Mutation is allowed over GET (possible CSRF)", "description": "GraphQL mutations allowed using the GET method", "impact": "Possible Cross Site Request Forgery - /graphql", "severity": "MEDIUM", "color": "yellow", "curl_verify": "curl -X GET -H \"X-Ontrack-Token: SECRET-COP\" -d '' 'https://demo.example.com/graphql?query=mutation+cop+%7B__typename%7D'"}]
JSON
out="$(sd_assert_graphql_cop_no_mutations "$WORK/cop-get-mutation.out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-graphql-cop-no-mutations: fails on a mutation sent in a GET query string"
assert_contains "$out" "wrote to the target" "assert-graphql-cop-no-mutations: says what went wrong"
assert_not_contains "$out" "SECRET-COP" "assert-graphql-cop-no-mutations: even its failure prints no token"

cat > "$WORK/cop-post-mutation.out" <<'JSON'
[{"result": false, "title": "Something new", "description": "", "impact": "", "severity": "LOW", "color": "blue", "curl_verify": "curl -X POST -H \"X-Ontrack-Token: SECRET-COP\" -d '{\"query\": \"mutation cop { deleteProject(input: {id: 1}) { errors { message } } }\", \"operationName\": \"cop\"}' 'https://demo.example.com/graphql'"}]
JSON
out="$(sd_assert_graphql_cop_no_mutations "$WORK/cop-post-mutation.out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-graphql-cop-no-mutations: fails on a mutation in a JSON body"

cat > "$WORK/cop-form-mutation.out" <<'JSON'
[{"result": false, "title": "Something else", "description": "", "impact": "", "severity": "LOW", "color": "blue", "curl_verify": "curl -X POST -H \"X-Ontrack-Token: SECRET-COP\" -d 'query=mutation+cop+%7B+__typename+%7D' 'https://demo.example.com/graphql'"}]
JSON
out="$(sd_assert_graphql_cop_no_mutations "$WORK/cop-form-mutation.out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-graphql-cop-no-mutations: fails on a mutation in a url-encoded body"

# graphql-cop does not fail on an exclusion it does not recognise: it says so and runs the test.
{ echo "get_based_mutaton cannot be excluded, skipping"; cat "$WORK/cop.out"; } > "$WORK/cop-typo.out"
out="$(sd_assert_graphql_cop_no_mutations "$WORK/cop-typo.out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-graphql-cop-no-mutations: an exclusion graphql-cop ignored is a failure"

out="$(sd_assert_graphql_cop_no_mutations "$WORK/cop-empty.out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-graphql-cop-no-mutations: no test run proves nothing and fails"
out="$(sd_assert_graphql_cop_no_mutations "$WORK/absent.out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "assert-graphql-cop-no-mutations: no output proves nothing and fails"

# ---------------------------------------------------------------------------
# graphql-cop-preflight - before a request is sent
# ---------------------------------------------------------------------------

mkdir -p "$WORK/cop-src/lib/tests"
cat > "$WORK/cop-src/lib/tests/__init__.py" <<'PY'
from lib.tests.info_introspect import introspection
from lib.tests.info_get_based_mutation import get_based_mutation
from lib.tests.dos_field_duplication import field_duplication

tests = {
    "introspection":introspection,
    "get_based_mutation":get_based_mutation,
    # "field_duplication":field_duplication,
}
PY
printf '%s\n' '"""Perform introspection tests."""' "q = 'query cop { __schema { types { name } } }'" \
    > "$WORK/cop-src/lib/tests/info_introspect.py"
printf '%s\n' '"""Checks mutation support over on GET."""' "q = 'mutation cop {__typename}'" \
    > "$WORK/cop-src/lib/tests/info_get_based_mutation.py"
printf '%s\n' "q = 'mutation cop { x }'" > "$WORK/cop-src/lib/tests/dos_field_duplication.py"

out="$(sd_graphql_cop_preflight "$WORK/cop-src" "get_based_mutation" 2>&1)"; rc=$?
assert_eq "0" "$rc" "graphql-cop-preflight: passes when every test that mentions a mutation is excluded"
assert_contains "$out" "1 test(s) will run" "graphql-cop-preflight: says how many tests will run"
assert_contains "$out" "get_based_mutation" \
    "graphql-cop-preflight: names what it excluded - test names, not findings"

out="$(sd_graphql_cop_preflight "$WORK/cop-src" "" 2>&1)"; rc=$?
assert_eq "1" "$rc" "graphql-cop-preflight: refuses to run a registered test that mentions a mutation"
assert_contains "$out" "get_based_mutation" "graphql-cop-preflight: names the test"

out="$(sd_graphql_cop_preflight "$WORK/cop-src" "get_based_mutaton" 2>&1)"; rc=$?
assert_eq "1" "$rc" "graphql-cop-preflight: refuses an exclusion that names no registered test - graphql-cop would skip it silently"

out="$(sd_graphql_cop_preflight "$WORK/absent-src" "get_based_mutation" 2>&1)"; rc=$?
assert_eq "1" "$rc" "graphql-cop-preflight: fails without the source"

# ---------------------------------------------------------------------------
# render-graphql-cop-headers - the token in a run-time file, never on a command line
# ---------------------------------------------------------------------------

printf '%s' 'to"ken\with' > "$WORK/tokens/scan-project.token"
out="$(sd_render_graphql_cop_headers "$WORK/cop-run/headers.json" scan-project 2>&1)"; rc=$?
assert_eq "0" "$rc" "render-graphql-cop-headers: succeeds"
assert_eq 'Bearer to"ken\with' "$(jq -r '.Authorization' "$WORK/cop-run/headers.json")" \
    "render-graphql-cop-headers: writes the role's bearer token as JSON, quotes and backslashes intact"
assert_eq "null" "$(jq -r '."X-Ontrack-Token"' "$WORK/cop-run/headers.json")" \
    "render-graphql-cop-headers: sends no X-Ontrack-Token any more"
assert_not_contains "$out" 'to"ken' "render-graphql-cop-headers: does not print the token"
out="$(sd_render_graphql_cop_headers "$WORK/cop-run/headers2.json" scan-nobody 2>&1)"; rc=$?
assert_eq "1" "$rc" "render-graphql-cop-headers: refuses to scan unauthenticated"
assert_eq "no" "$([ -e "$WORK/cop-run/headers2.json" ] && echo yes || echo no)" \
    "render-graphql-cop-headers: leaves no headers file behind for a role with no token"
rm -f "$WORK/tokens/scan-project.token"

# ---------------------------------------------------------------------------
# The introspection suppression, as committed, against graphql-cop
# ---------------------------------------------------------------------------

printf '%s' "$normalized" > "$WORK/findings-cop.json"
SD_SUPPRESSIONS="$SCRIPT_DIR/../security/dast/suppressions.yaml"
: > "$WORK/empty-rules.tsv"
SD_RULES="$WORK/empty-rules.tsv"
counts="$(sd_report "$WORK/report-cop.md" "$WORK/findings-cop.json")"; rc=$?
assert_eq "0" "$rc" "report: counts graphql-cop findings"
# alias_overloading and the circular query HIGH; introspection HIGH but suppressed.
assert_contains "$counts" "high=2" "report: the introspection suppression takes graphql-cop's Introspection out"
assert_contains "$counts" "medium=2" "report: graphql-cop's MEDIUMs count"
assert_contains "$counts" "low=1" "report: graphql-cop's LOW counts, its INFO does not"
assert_contains "$counts" "suppressed=1" "report: exactly one graphql-cop instance suppressed"
assert_contains "$(cat "$WORK/report-cop.md")" "| \`graphql-introspection\` | 1 |" \
    "report: the committed suppression is the one that applied"
assert_not_contains "$(cat "$WORK/report-cop.md")" "Declared and matched nothing this run: \`graphql-introspection\`" \
    "report: the introspection suppression no longer matches nothing"
SD_SUPPRESSIONS="$WORK/suppressions.yaml"
SD_RULES="$WORK/rules.tsv"

# ===========================================================================
# Nuclei (#1766) - its JSONL output
# ===========================================================================

# One line per match. `curl-command` is the request; nothing of it may reach the findings.
cat > "$WORK/nuclei.jsonl" <<'JSONL'
{"template-id":"CVE-2099-0001","info":{"name":"Critical Thing","tags":["cve","springboot"],"description":"Bad.","reference":["https://example.org/cve"],"severity":"critical","classification":{"cve-id":["cve-2099-0001"],"cwe-id":["cwe-94"]},"remediation":"Upgrade."},"type":"http","host":"demo.example.com","matched-at":"https://demo.example.com/x","curl-command":"curl -X 'GET' 'https://demo.example.com/x'","matcher-status":true}
{"template-id":"git-config","info":{"name":"Git Configuration - Detect","tags":["config","git","exposure"],"description":"Git config.","severity":"high","classification":{"cve-id":null,"cwe-id":["cwe-200"]}},"type":"http","host":"demo.example.com","matched-at":"https://demo.example.com/.git/config","extracted-results":["core"],"curl-command":"curl -X 'GET' 'https://demo.example.com/.git/config'","matcher-status":true}
{"template-id":"git-config","info":{"name":"Git Configuration - Detect","tags":["config","git","exposure"],"description":"Git config.","severity":"high","classification":{"cve-id":null,"cwe-id":["cwe-200"]}},"type":"http","host":"demo.example.com","matched-at":"https://demo.example.com/mobile/.git/config","curl-command":"curl -X 'GET' 'https://demo.example.com/mobile/.git/config'","matcher-status":true}
{"template-id":"medium-thing","info":{"name":"Medium Thing","tags":["misconfig"],"severity":"medium"},"type":"http","host":"demo.example.com","matched-at":"https://demo.example.com/m","matcher-status":true}
{"template-id":"low-thing","info":{"name":"Low Thing","tags":["misconfig"],"severity":"low"},"type":"http","host":"demo.example.com","matched-at":"https://demo.example.com/l","matcher-status":true}
{"template-id":"http-missing-security-headers","info":{"name":"HTTP Missing Security Headers","tags":["misconfig","headers"],"severity":"info"},"matcher-name":"strict-transport-security","type":"http","host":"demo.example.com","matched-at":"https://demo.example.com","matcher-status":true}
{"template-id":"http-missing-security-headers","info":{"name":"HTTP Missing Security Headers","tags":["misconfig","headers"],"severity":"info"},"matcher-name":"permissions-policy","type":"http","host":"demo.example.com","matched-at":"https://demo.example.com","matcher-status":true}
{"template-id":"odd-thing","info":{"name":"Odd Thing","tags":["exposure"],"severity":"unknown"},"type":"http","host":"demo.example.com","matched-at":"https://demo.example.com/o","matcher-status":true}
JSONL

normalized="$(sd_normalize_nuclei "$WORK/nuclei.jsonl" "3.11.1 (templates v10.4.8)" 2>&1)"; rc=$?
assert_eq "0" "$rc" "normalize-nuclei: reads Nuclei's JSONL"
assert_eq "nuclei" "$(printf '%s' "$normalized" | jq -r '.scanner')" "normalize-nuclei: names the scanner"
assert_eq "3.11.1 (templates v10.4.8)" "$(printf '%s' "$normalized" | jq -r '.version')" \
    "normalize-nuclei: carries the engine and the template versions"
assert_eq "6" "$(printf '%s' "$normalized" | jq -r '.findings | length')" \
    "normalize-nuclei: one finding per template, not per match"
assert_eq "2" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "git-config") | .instances | length')" \
    "normalize-nuclei: merges the matches of one template into its instances"
assert_eq "CRITICAL" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "CVE-2099-0001") | .risk')" \
    "normalize-nuclei: critical is CRITICAL"
assert_eq "HIGH" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "git-config") | .risk')" \
    "normalize-nuclei: high is HIGH"
assert_eq "MEDIUM" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "medium-thing") | .risk')" \
    "normalize-nuclei: medium is MEDIUM"
assert_eq "LOW" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "low-thing") | .risk')" \
    "normalize-nuclei: low is LOW"
assert_eq "INFO" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "http-missing-security-headers") | .risk')" \
    "normalize-nuclei: info is INFO"
assert_eq "INFO" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "odd-thing") | .risk')" \
    "normalize-nuclei: an unknown severity is not promoted to a count"
assert_eq "94" "$(printf '%s' "$normalized" | jq -r '.findings[] | select(.rule == "CVE-2099-0001") | .cwe')" \
    "normalize-nuclei: the CWE, as a bare number like ZAP's"
assert_eq "strict-transport-security" "$(printf '%s' "$normalized" | jq -r '[.findings[] | select(.rule == "http-missing-security-headers") | .instances[].param] | sort | .[1]')" \
    "normalize-nuclei: a matcher name is kept as the instance's parameter"
assert_not_contains "$normalized" "curl -X" "normalize-nuclei: drops the request"

: > "$WORK/nuclei-empty.jsonl"
out="$(sd_normalize_nuclei "$WORK/nuclei-empty.jsonl" "3.11.1" 2>&1)"; rc=$?
assert_eq "0" "$rc" "normalize-nuclei: no match is a result - Nuclei writes nothing when nothing matched"
assert_eq "0" "$(printf '%s' "$out" | jq -r '.findings | length')" "normalize-nuclei: no match is zero findings"
out="$(sd_normalize_nuclei "$WORK/absent.jsonl" "3.11.1" 2>&1)"; rc=$?
assert_eq "1" "$rc" "normalize-nuclei: no output file at all is a scanner error"
printf '{"template-id":"x"\n' > "$WORK/nuclei-broken.jsonl"
out="$(sd_normalize_nuclei "$WORK/nuclei-broken.jsonl" "3.11.1" 2>&1)"; rc=$?
assert_eq "1" "$rc" "normalize-nuclei: a truncated line fails rather than dropping a finding"

printf '%s' "$normalized" > "$WORK/findings-nuclei.json"

# ---------------------------------------------------------------------------
# nuclei-preflight - the tag exclusions, observed on the templates that will actually run
# ---------------------------------------------------------------------------

cat > "$WORK/nuclei.yaml" <<'YAML'
tags:
  - exposure
exclude-tags:
  - intrusive
  - dos
  - fuzz
  - bruteforce
rate-limit: 10
YAML
mkdir -p "$WORK/templates"
printf 'id: a\ninfo:\n  name: A\n  tags: config,git,exposure\n' > "$WORK/templates/a.yaml"
printf 'id: b\ninfo:\n  name: B\n  tags: exposure,ddos-protection\n' > "$WORK/templates/b.yaml"
printf 'id: c\ninfo:\n  name: C\n  tags: "exposure, intrusive"\n' > "$WORK/templates/c.yaml"
printf 'noise before the list\n%s\n%s\n' "$WORK/templates/a.yaml" "$WORK/templates/b.yaml" > "$WORK/tl-clean.txt"
out="$(sd_nuclei_preflight "$WORK/tl-clean.txt" "$WORK/nuclei.yaml" 2>&1)"; rc=$?
assert_eq "0" "$rc" "nuclei-preflight: passes when no selected template carries an excluded tag"
assert_contains "$out" "2 template(s)" "nuclei-preflight: counts the selected templates"
assert_contains "$out" "intrusive, dos, fuzz, bruteforce" "nuclei-preflight: says which tags are excluded"
assert_contains "$out" "10 request(s) per second" "nuclei-preflight: says what the rate limit is"
assert_not_contains "$out" "templates/a.yaml" "nuclei-preflight: prints no template"

printf '%s\n%s\n' "$WORK/templates/a.yaml" "$WORK/templates/c.yaml" > "$WORK/tl-dirty.txt"
out="$(sd_nuclei_preflight "$WORK/tl-dirty.txt" "$WORK/nuclei.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "nuclei-preflight: fails when a selected template carries an excluded tag"

# Nuclei selects some two thousand templates, more paths than one command line holds on Linux, so
# they are read in several batches - and every batch counts. The first run of #1766 counted the
# first batch only, and would have missed an excluded tag in any other.
printf '%s\n%s\n%s\n' "$WORK/templates/a.yaml" "$WORK/templates/c.yaml" "$WORK/templates/b.yaml" > "$WORK/tl-batched.txt"
out="$(SD_XARGS_BATCH=1 sd_nuclei_preflight "$WORK/tl-batched.txt" "$WORK/nuclei.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "nuclei-preflight: an excluded tag in any batch of templates fails, not only in the last"
out="$(SD_XARGS_BATCH=1 sd_nuclei_preflight "$WORK/tl-clean.txt" "$WORK/nuclei.yaml" 2>&1)"; rc=$?
assert_eq "0" "$rc" "nuclei-preflight: passes in batches"
assert_contains "$out" "2 template(s)" "nuclei-preflight: counts the templates of every batch, not only the first"

printf 'nothing\n' > "$WORK/tl-none.txt"
out="$(sd_nuclei_preflight "$WORK/tl-none.txt" "$WORK/nuclei.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "nuclei-preflight: no template selected is a broken selection, not a quiet scan"

printf 'tags: [exposure]\nrate-limit: 10\n' > "$WORK/nuclei-noexclude.yaml"
out="$(sd_nuclei_preflight "$WORK/tl-clean.txt" "$WORK/nuclei-noexclude.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "nuclei-preflight: refuses a configuration that does not exclude the four tags"
printf 'tags: [exposure]\nexclude-tags: [intrusive, dos, fuzz, bruteforce]\n' > "$WORK/nuclei-norate.yaml"
out="$(sd_nuclei_preflight "$WORK/tl-clean.txt" "$WORK/nuclei-norate.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "nuclei-preflight: refuses a configuration with no rate limit"

# ---------------------------------------------------------------------------
# nuclei-summary - did the scan happen, and at what rate
# ---------------------------------------------------------------------------

cat > "$WORK/nuclei.log" <<'LOG'
[INF] Templates loaded for current scan: 2244
{"template-id":"git-config","matched-at":"https://demo.example.com/.git/config"}
[INF] Skipped demo.example.com:5814 from target list as found unresponsive permanently: Get "https://demo.example.com:5814/autopass"
{"duration":"0:09:05","errors":"159","hosts":"1","matched":"8","percent":"100","requests":"5458","rps":"10","startedAt":"2026-09-16T13:11:34Z","templates":"2244","total":"5451"}
[INF] Scan completed in 9m5s. 8 matches found.
LOG
out="$(sd_nuclei_summary "$WORK/nuclei.log" "$WORK/nuclei.jsonl" "https://demo.example.com" 2>&1)"; rc=$?
assert_eq "0" "$rc" "nuclei-summary: passes on a completed scan"
assert_contains "$out" "2244 template(s)" "nuclei-summary: prints the template count"
assert_contains "$out" "5458 request(s)" "nuclei-summary: prints the request count"
assert_contains "$out" "10 request(s) per second" "nuclei-summary: prints the effective rate"
assert_not_contains "$out" "demo.example.com" "nuclei-summary: prints no URL"
assert_not_contains "$out" "git-config" "nuclei-summary: prints no template match"
assert_not_contains "$out" "8 match" "nuclei-summary: prints no match count - the counts are the report's"

grep -v '"duration"' "$WORK/nuclei.log" > "$WORK/nuclei-unfinished.log"
out="$(sd_nuclei_summary "$WORK/nuclei-unfinished.log" "$WORK/nuclei.jsonl" "https://demo.example.com" 2>&1)"; rc=$?
assert_eq "1" "$rc" "nuclei-summary: a scan with no final statistics did not finish"

{ echo '[INF] Skipped demo.example.com:443 from target list as found unresponsive permanently: x'; cat "$WORK/nuclei.log"; } > "$WORK/nuclei-skipped.log"
out="$(sd_nuclei_summary "$WORK/nuclei-skipped.log" "$WORK/nuclei.jsonl" "https://demo.example.com" 2>&1)"; rc=$?
assert_eq "1" "$rc" "nuclei-summary: the target given up on as unresponsive is a scanner error, not a clean scan"
assert_not_contains "$out" "demo.example.com" "nuclei-summary: its failure prints no URL either"

sed 's/"matched":"8"/"matched":"9"/' "$WORK/nuclei.log" > "$WORK/nuclei-mismatch.log"
out="$(sd_nuclei_summary "$WORK/nuclei-mismatch.log" "$WORK/nuclei.jsonl" "https://demo.example.com" 2>&1)"; rc=$?
assert_eq "1" "$rc" "nuclei-summary: fails when the output file lost matches the scan reported"

# ---------------------------------------------------------------------------
# All three scanners in one report
# ---------------------------------------------------------------------------

counts="$(sd_report "$WORK/report-all.md" "$WORK/findings.json" "$WORK/findings-cop.json" "$WORK/findings-nuclei.json")"; rc=$?
assert_eq "0" "$rc" "report: takes the three scanners' documents together"
assert_contains "$counts" "critical=1" "report: a Nuclei critical is a CRITICAL"
# ZAP: no HIGH. graphql-cop: alias overloading and the circular query - its Introspection is taken out
# by the fixture's suppression, which is written like the committed one. Nuclei: git-config.
assert_contains "$counts" "high=3" "report: HIGHs add up across scanners"
assert_contains "$counts" "medium=5" "report: MEDIUMs add up across scanners"
assert_contains "$counts" "low=2" "report: LOWs add up across scanners"
report="$(cat "$WORK/report-all.md")"
assert_contains "$report" "## Findings — \`zap\`" "report: groups findings by tool - ZAP"
assert_contains "$report" "## Findings — \`graphql-cop\`" "report: groups findings by tool - graphql-cop"
assert_contains "$report" "## Findings — \`nuclei\`" "report: groups findings by tool - Nuclei"
assert_contains "$report" "| \`nuclei\` | 3.11.1 (templates v10.4.8) | 1 | 1 | 1 | 1 |" \
    "report: the per-tool split, with each tool's version"
assert_contains "$report" "| \`graphql-cop\` | 1.16 | 0 | 2 | 2 | 1 |" \
    "report: graphql-cop's own line in the split"
assert_not_contains "$report" "SECRET-COP" "report: the graphql-cop token never reaches the report"
assert_not_contains "$counts" "nuclei" "report: the printed counts carry no per-tool split"

# The report is grouped by tool: a finding must not fall out of it because its document names
# another scanner.
jq '.findings[0].scanner = "somebody-else"' "$WORK/findings-nuclei.json" > "$WORK/findings-stray.json"
counts="$(sd_report "$WORK/report-stray.md" "$WORK/findings-stray.json")"; rc=$?
assert_contains "$(cat "$WORK/report-stray.md")" "## Findings — \`somebody-else\`" \
    "report: a finding of a scanner no document declares still has its section"
assert_contains "$counts" "critical=1" "report: and still counts"

# A suppression scoped to one scanner leaves the others alone.
cat > "$WORK/suppressions-scoped.yaml" <<'YAML'
version: 1
suppressions:
  - id: nuclei-low
    scanner: "nuclei"
    rule: "low-thing"
    statement: "Scoped."
    expired_at: "2099-01-01"
YAML
SD_SUPPRESSIONS="$WORK/suppressions-scoped.yaml"
counts="$(sd_report "$WORK/report-scoped.md" "$WORK/findings-cop.json" "$WORK/findings-nuclei.json")"; rc=$?
assert_contains "$counts" "low=1" "report: a scanner-scoped suppression applies to Nuclei's findings"
SD_SUPPRESSIONS="$WORK/suppressions.yaml"

# ---------------------------------------------------------------------------
# One pass per scanner role (#1769): de-duplicated by rule + URL, attributed to roles
# ---------------------------------------------------------------------------

sd_normalize_zap "$WORK/zap.json" scan-admin > "$WORK/findings-zap-admin.json"
assert_eq "scan-admin" "$(jq -r '.role' "$WORK/findings-zap-admin.json")" \
    "normalize-zap: records the role of the pass it was given"
assert_eq "null" "$(sd_normalize_zap "$WORK/zap.json" | jq -r '.role')" \
    "normalize-zap: no role for an unauthenticated pass"
# scan-readonly sees the CSP finding on one more URL and not the CORS one.
sd_normalize_zap "$WORK/zap.json" scan-readonly \
    | jq '.findings |= (map(select(.rule != "10098"))
          | map(if .rule == "10038" then .instances += [{uri: "https://demo.example.com/extra", method: "GET", param: "", evidence: "", attack: "", info: ""}] else . end))' \
    > "$WORK/findings-zap-readonly.json"
# The unauthenticated UI pass sees the CSP finding on the root page only.
sd_normalize_zap "$WORK/zap.json" \
    | jq '.findings |= (map(select(.rule == "10038")) | map(.instances |= .[:1]))' \
    > "$WORK/findings-zap-ui.json"

SD_RULES="$WORK/rules.tsv"
SD_SUPPRESSIONS="$WORK/suppressions.yaml"
single="$(sd_report "$WORK/report-single.md" "$WORK/findings.json")"
counts="$(sd_report "$WORK/report-roles.md" "$WORK/findings-zap-ui.json" "$WORK/findings-zap-admin.json" "$WORK/findings-zap-readonly.json")"; rc=$?
assert_eq "0" "$rc" "report: takes one document per role"
assert_eq "$single" "$counts" \
    "report: the same rule seen by several roles is counted once - the counts of three passes are the counts of one"
report="$(cat "$WORK/report-roles.md")"
assert_eq "1" "$(grep -c '^### MEDIUM — CSP Header Not Set$' "$WORK/report-roles.md")" \
    "report: one section per rule, not one per role"
assert_contains "$report" "3 instance(s) counted" \
    "report: instances are de-duplicated by URL across roles - /, /mobile and the one only scan-readonly saw"
assert_contains "$report" "Seen by \`scan-admin\`, \`scan-readonly\`, \`unauthenticated\`." \
    "report: says which roles saw a finding"
assert_contains "$report" "Seen by \`scan-admin\`." \
    "report: a finding only one role saw says so"
assert_contains "$report" "| \`https://demo.example.com/extra\` | GET | — | — | \`scan-readonly\` |" \
    "report: each instance names the roles that saw it"
assert_contains "$report" "| \`https://demo.example.com/\` | GET | — | — | \`scan-admin\`, \`scan-readonly\`, \`unauthenticated\` |" \
    "report: an instance several roles saw names all of them, once"
assert_contains "$report" "## Counts by role" "report: splits the counts by role"
assert_contains "$report" "| \`scan-admin\` | 0 | 0 | 2 | 0 |" "report: what scan-admin saw"
assert_contains "$report" "| \`scan-readonly\` | 0 | 0 | 1 | 0 |" "report: what scan-readonly saw"
assert_contains "$report" "| \`unauthenticated\` | 0 | 0 | 1 | 0 |" "report: what the unauthenticated passes saw"
assert_not_contains "$counts" "scan-" "report: the printed counts carry no per-role split"

# graphql-cop, once per role: the same tests failing for three roles are the same findings.
SD_SUPPRESSIONS="$SCRIPT_DIR/../security/dast/suppressions.yaml"
SD_RULES="$WORK/empty-rules.tsv"
for role in scan-admin scan-readonly scan-project; do
    sd_normalize_graphql_cop "$WORK/cop.out" "1.16" "$role" > "$WORK/findings-cop-$role.json"
done
assert_eq "scan-project" "$(jq -r '.role' "$WORK/findings-cop-scan-project.json")" \
    "normalize-graphql-cop: records the role of the pass"
single="$(sd_report "$WORK/report-cop-single.md" "$WORK/findings-cop.json")"
counts="$(sd_report "$WORK/report-cop-roles.md" "$WORK/findings-cop-scan-admin.json" "$WORK/findings-cop-scan-readonly.json" "$WORK/findings-cop-scan-project.json")"
assert_eq "$single" "$counts" "report: graphql-cop's three passes count like one"
assert_contains "$(cat "$WORK/report-cop-roles.md")" "| \`graphql-introspection\` | 1 |" \
    "report: a suppression subtracts a de-duplicated instance once, not once per role"
SD_SUPPRESSIONS="$WORK/suppressions.yaml"
SD_RULES="$WORK/rules.tsv"

# Every role's token is redacted from the report, not only one.
printf '%s' 'TOKEN-OF-ADMIN' > "$WORK/tokens/scan-admin.token"
printf '%s' 'TOKEN-OF-PROJECT' > "$WORK/tokens/scan-project.token"
jq '(.findings[] | select(.rule == "10038") | .description) = "leaked TOKEN-OF-ADMIN and TOKEN-OF-PROJECT and tok&en\\with/specials"' \
    "$WORK/findings-zap-admin.json" > "$WORK/findings-leaky.json"
sd_report "$WORK/report-leaky.md" "$WORK/findings-leaky.json" > /dev/null
report="$(cat "$WORK/report-leaky.md")"
assert_not_contains "$report" "TOKEN-OF-ADMIN" "report: redacts scan-admin's token"
assert_not_contains "$report" "TOKEN-OF-PROJECT" "report: redacts scan-project's token"
assert_not_contains "$report" "tok&en" "report: redacts scan-readonly's token"
assert_contains "$report" "leaked <redacted> and <redacted> and <redacted>" "report: in place, leaving the rest"

# ===========================================================================
# fetch - every downloaded tool is checked against a committed checksum
# ===========================================================================

printf 'scanner bytes\n' > "$WORK/download"
good="$(shasum -a 256 "$WORK/download" 2>/dev/null | cut -d' ' -f1 || sha256sum "$WORK/download" | cut -d' ' -f1)"
out="$(sd_fetch "https://example.org/tool.zip" "$good" "$WORK/fetched/tool.zip" 2>&1)"; rc=$?
assert_eq "0" "$rc" "fetch: accepts a download matching its checksum"
assert_eq "scanner bytes" "$(cat "$WORK/fetched/tool.zip")" "fetch: leaves the file where it was asked"
out="$(sd_fetch "https://example.org/tool.zip" "0000000000000000000000000000000000000000000000000000000000000000" "$WORK/fetched/bad.zip" 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch: refuses a download whose checksum does not match"
assert_eq "no" "$([ -e "$WORK/fetched/bad.zip" ] && echo yes || echo no)" "fetch: and does not leave it behind"
rm -f "$WORK/download"
out="$(sd_fetch "https://example.org/tool.zip" "$good" "$WORK/fetched/missing.zip" 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch: fails when the download fails"

# ===========================================================================
# report-path
# ===========================================================================

assert_eq "dast/passive/2026/2026-09-16-5.5.0-abc1234-7.md" "$(sd_report_path)" \
    "report-path: the layout the private repository's README sets out"
SD_KIND=active
assert_eq "dast/active/2026/2026-09-16-5.5.0-abc1234-7.md" "$(sd_report_path)" \
    "report-path: the active scan writes beside the passive one, not over it"
SD_KIND=passive

# ===========================================================================
# publish
# ===========================================================================

: > "$WORK/gh.log"
out="$(sd_publish "yontrack/security-reports" "dast/passive/2026/x.md" "$WORK/report.md" 2>&1)"; rc=$?
assert_eq "0" "$rc" "publish: succeeds"
assert_contains "$(cat "$WORK/gh.log")" "--method PUT" "publish: creates the file"
assert_contains "$(cat "$WORK/gh.log")" "repos/yontrack/security-reports/contents/dast/passive/2026/x.md" \
    "publish: writes at the documented path"
assert_not_contains "$(cat "$WORK/gh.log")" "sha=" \
    "publish: sends no sha, so an existing path fails rather than being overwritten"

touch "$WORK/gh_fails"
out="$(sd_publish "yontrack/security-reports" "dast/passive/2026/x.md" "$WORK/report.md" 2>&1)"; rc=$?
rm -f "$WORK/gh_fails"
assert_eq "1" "$rc" "publish: fails when GitHub refuses"

out="$(sd_publish "yontrack/security-reports" "dast/passive/2026/x.md" "$WORK/absent.md" 2>&1)"; rc=$?
assert_eq "1" "$rc" "publish: fails when there is no report to publish"

# ===========================================================================
# scrub - the last line of defence before a public log
# ===========================================================================

noisy='Spider found https://demo.example.com/project/1?token=abc and header X-Ontrack-Token: SECRET1'
scrubbed="$(printf '%s\n' "$noisy" | DEMO_TOKEN=SECRET1 sd_scrub)"
assert_not_contains "$scrubbed" "demo.example.com" "scrub: removes URLs"
assert_not_contains "$scrubbed" "SECRET1" "scrub: removes the token"
assert_contains "$scrubbed" "Spider found" "scrub: keeps enough to diagnose a failure"

# Every role's token, wherever it appears - not only after a header name or inside a URL.
printf '%s' 'SECRET-ADMIN' > "$WORK/tokens/scan-admin.token"
printf '%s' 'SECRET-PROJECT' > "$WORK/tokens/scan-project.token"
noisy='replacer: SECRET-ADMIN / tok&en\with/specials / SECRET-PROJECT'
scrubbed="$(printf '%s\n' "$noisy" | sd_scrub)"
assert_not_contains "$scrubbed" "SECRET-ADMIN" "scrub: removes scan-admin's token"
assert_not_contains "$scrubbed" "tok&en" "scrub: removes scan-readonly's token"
assert_not_contains "$scrubbed" "SECRET-PROJECT" "scrub: removes scan-project's token"
assert_contains "$scrubbed" "replacer: <redacted>" "scrub: in place"

scrubbed="$(printf '%s\n' 'H: Authorization: Bearer abc.def-ghi' | DAST_TOKEN_DIR="" sd_scrub)"
assert_not_contains "$scrubbed" "abc.def-ghi" "scrub: a bearer header is redacted even with no token file to go on"
scrubbed="$(printf '%s\n' 'value eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2ln end' | DAST_TOKEN_DIR="" sd_scrub)"
assert_not_contains "$scrubbed" "eyJzdWIiOiJ4In0" "scrub: so is anything shaped like a JWT"
assert_contains "$scrubbed" "value <redacted> end" "scrub: in place"

scrubbed="$(printf 'java.lang.OutOfMemoryError\n' | sd_scrub)"
assert_eq "java.lang.OutOfMemoryError" "$scrubbed" "scrub: leaves a plain diagnostic alone"

# ===========================================================================
# active-schema (#1767) - the ACTIVE scan sends mutations, but never the dangerous ones
# ===========================================================================

cat > "$WORK/active-in.graphql" <<'SDL'
schema {
  query: Query
  mutation: Mutation
}

type Query {
  projects: [Project]
}

type Mutation {
  "Creates a project - kept, the active scan sends it."
  createProject(
    "Input for the mutation"
    input: CreateProjectInput
  ): CreateProjectPayload
  "Revokes every token - denied, it would revoke the scanner's own."
  revokeAllTokens: RevokeAllTokensPayload
  "Revokes a token - denied."
  revokeToken(
    "Input for the mutation"
    input: RevokeTokenInput
  ): RevokeTokenPayload
  "Deletes an account - denied, it could delete a scan-* account."
  deleteAccount(
    "Input for the mutation"
    input: DeleteAccountInput
  ): DeleteAccountPayload
  "Deletes an account group - denied, and must not take deleteAccount with it."
  deleteAccountGroup(input: DeleteAccountGroupInput): DeleteAccountGroupPayload
  "Re-applies the CasC - denied, it could undo grantProjectViewToAll."
  reloadCasc: ReloadCascPayload
  "Creates a build - kept."
  createBuild(input: CreateBuildInput): CreateBuildPayload
}

type Project {
  name: String!
}
SDL

out="$(sd_active_schema "$WORK/active-in.graphql" "$WORK/active-out.graphql" 2>&1)"; rc=$?
assert_eq "0" "$rc" "active-schema: succeeds on the generated shape"
assert_contains "$(cat "$WORK/active-out.graphql")" "type Mutation {" \
    "active-schema: keeps the Mutation root - the active scan sends mutations on purpose"
assert_contains "$(cat "$WORK/active-out.graphql")" "createProject(" \
    "active-schema: keeps a benign multiline mutation"
assert_contains "$(cat "$WORK/active-out.graphql")" "createBuild(input:" \
    "active-schema: keeps a benign single-line mutation"
assert_not_contains "$(cat "$WORK/active-out.graphql")" "revokeAllTokens" \
    "active-schema: strips token revocation (single-line)"
assert_not_contains "$(cat "$WORK/active-out.graphql")" "revokeToken(" \
    "active-schema: strips token revocation (multiline)"
assert_not_contains "$(cat "$WORK/active-out.graphql")" "deleteAccount(" \
    "active-schema: strips scan-account deletion"
assert_not_contains "$(cat "$WORK/active-out.graphql")" "deleteAccountGroup" \
    "active-schema: strips scan-group deletion"
assert_not_contains "$(cat "$WORK/active-out.graphql")" "reloadCasc" \
    "active-schema: strips reloadCasc, which could undo the throwaway stack's settings mid-run"
# The description line of a denied field goes with it - a floating description would attach to the
# wrong field or not parse.
assert_not_contains "$(cat "$WORK/active-out.graphql")" "would revoke the scanner" \
    "active-schema: removes the denied field's description line too"
assert_contains "$(cat "$WORK/active-out.graphql")" "the active scan sends it" \
    "active-schema: keeps a kept field's description line"
# mktemp makes 600; the ZAP container reads this as another uid.
assert_contains "$(ls -l "$WORK/active-out.graphql")" "-rw-r--r--" \
    "active-schema: leaves the schema readable by the scanner's uid"

# A schema with no Mutation root would scan no mutation at all - refused rather than written.
cat > "$WORK/active-noroot.graphql" <<'SDL'
schema {
  query: Query
}
type Query { projects: [Project] }
SDL
out="$(sd_active_schema "$WORK/active-noroot.graphql" "$WORK/active-nope.graphql" 2>&1)"; rc=$?
assert_eq "1" "$rc" "active-schema: refuses a schema with no Mutation root"
assert_contains "$out" "no Mutation root" "active-schema: says why"
assert_eq "no" "$([ -e "$WORK/active-nope.graphql" ] && echo yes || echo no)" "active-schema: writes nothing on refusal"

out="$(sd_active_schema "$WORK/absent.graphql" "$WORK/active-nope.graphql" 2>&1)"; rc=$?
assert_eq "1" "$rc" "active-schema: fails when the input does not exist"

# ===========================================================================
# whoami active-strict - more than the granted project count stops the run (#1767)
# ===========================================================================

# The passive scan warns; the active scan fails, because on the throwaway stack we own the setting.
whoami_body '["DAST Project"]' 4
printf '200' > "$WORK/graphql.status"
SD_KIND=active
out="$(sd_whoami scan-project "$WORK/login" 2>&1)"; rc=$?
SD_KIND=passive
assert_eq "1" "$rc" "whoami (active): scan-project seeing more than its grant fails before scanning"
assert_contains "$out" "grantProjectViewToAll" "whoami (active): names the setting to turn off on the throwaway stack"

whoami_body '["DAST Project"]' 1
SD_KIND=active
out="$(sd_whoami scan-project "$WORK/login" 2>&1)"; rc=$?
SD_KIND=passive
assert_eq "0" "$rc" "whoami (active): scan-project seeing exactly its one project passes"

# ===========================================================================
# access-control (#1767) - the authorization checks, and their normalisation
# ===========================================================================

mkdir -p "$WORK/ac-tokens" "$WORK/ac-out"
printf '%s' 'tok-readonly' > "$WORK/ac-tokens/scan-readonly.token"
printf '%s' 'tok-project' > "$WORK/ac-tokens/scan-project.token"
export DAST_TARGET="https://demo.example.com"
printf '200' > "$WORK/graphql.status"

# Every probe correctly refused: the response shows the write did not happen and the read is empty.
cat > "$WORK/graphql.body" <<'JSON'
{"data":{"createProject":{"project":null,"errors":[{"message":"Access denied"}]},"projects":[],"createBranchOrGet":{"branch":null,"errors":[{"message":"Access denied"}]}}}
JSON
: > "$WORK/ac-github-output"
out="$(DAST_RUN_ID=99 GITHUB_OUTPUT="$WORK/ac-github-output" sd_access_control "$WORK/ac-tokens" "$WORK/ac-out" 2>&1)"; rc=$?
assert_eq "0" "$rc" "access-control: succeeds when every probe is refused"
assert_contains "$out" "0 escalation(s)" "access-control: reports no escalation"
assert_contains "$(cat "$WORK/ac-github-output")" "access_control_escalations=0" "access-control: writes the escalation count"
assert_not_contains "$out" "demo.example.com" "access-control: prints no URL"
assert_not_contains "$out" "tok-readonly" "access-control: prints no token"
# Normalisation of a clean run: no finding.
norm="$(sd_normalize_access_control "$WORK/ac-out/access-control-scan-readonly.raw.json" scan-readonly)"; rc=$?
assert_eq "0" "$rc" "normalize-access-control: reads a clean raw document"
assert_eq "0" "$(printf '%s' "$norm" | jq '.findings | length')" "normalize-access-control: a refused check is not a finding"

# Every probe escalates: the write's payload shows it happened, the cross-project read returns data.
cat > "$WORK/graphql.body" <<'JSON'
{"data":{"createProject":{"project":{"id":"1"},"errors":[]},"projects":[{"id":"2","name":"common-library"}],"createBranchOrGet":{"branch":{"id":"3"},"errors":[]}}}
JSON
out="$(DAST_RUN_ID=99 sd_access_control "$WORK/ac-tokens" "$WORK/ac-out" 2>&1)"; rc=$?
assert_eq "0" "$rc" "access-control: an escalation is a finding, not a run error - still returns 0"
assert_contains "$out" "3 escalation(s)" "access-control: counts the escalations"
# Counts only: no request, payload or evidence in what it prints.
assert_not_contains "$out" "common-library" "access-control: prints no project name it reached"

norm_ro="$(sd_normalize_access_control "$WORK/ac-out/access-control-scan-readonly.raw.json" scan-readonly)"
assert_eq "1" "$(printf '%s' "$norm_ro" | jq '.findings | length')" "normalize-access-control: an escalation is one HIGH finding"
assert_eq "HIGH" "$(printf '%s' "$norm_ro" | jq -r '.findings[0].risk')" "normalize-access-control: at HIGH"
assert_eq "scan-readonly" "$(printf '%s' "$norm_ro" | jq -r '.role')" "normalize-access-control: attributed to the role"
norm_pr="$(sd_normalize_access_control "$WORK/ac-out/access-control-scan-project.raw.json" scan-project)"
assert_eq "2" "$(printf '%s' "$norm_pr" | jq '.findings | length')" "normalize-access-control: scan-project's read and write escalations"
# The finding carries no exploit detail - a kind, not a payload.
assert_not_contains "$norm_pr" "common-library" "normalize-access-control: the finding names no project it reached"

# A probe the API refuses to answer at all is a broken scan, not a clean one.
printf '500' > "$WORK/graphql.status"
out="$(DAST_RUN_ID=99 sd_access_control "$WORK/ac-tokens" "$WORK/ac-out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "access-control: a probe the API will not answer fails the run"
printf '200' > "$WORK/graphql.status"

out="$(sd_normalize_access_control "$WORK/absent.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "normalize-access-control: a missing raw document fails"

# ===========================================================================
# The active ZAP plan carries the exclusions and the active schema
# ===========================================================================

ACTIVE_PLAN="$SCRIPT_DIR/../security/dast/zap/active.yaml"
assert_contains "$(cat "$ACTIVE_PLAN")" 'active-only.graphql' \
    "active plan: feeds ZAP the active schema, not the query-only one"
# shellcheck disable=SC2016  # deliberate: the literal placeholder is what is being asserted
assert_contains "$(cat "$ACTIVE_PLAN")" '${DAST_BEARER_TOKEN}' \
    "active plan: carries the bearer-token placeholder, rendered per role like the passive one"
assert_contains "$(cat "$ACTIVE_PLAN")" 'type: activeScan' \
    "active plan: actually runs the active scanner"
assert_contains "$(cat "$ACTIVE_PLAN")" 'logout' \
    "active plan: excludes the logout endpoint from attack"
assert_contains "$(cat "$ACTIVE_PLAN")" '8800' \
    "active plan: excludes the management path from attack"
# render-plan works on the active plan exactly as on the passive one.
printf '%s' 'active-tok' > "$WORK/tokens/scan-admin.token"
out="$(sd_render_plan "$ACTIVE_PLAN" "$WORK/active-plan-rendered.yaml" scan-admin 2>&1)"; rc=$?
assert_eq "0" "$rc" "active plan: renders with the role's bearer token"
assert_contains "$(cat "$WORK/active-plan-rendered.yaml")" "Bearer active-tok" \
    "active plan: the token is in place after rendering"
# shellcheck disable=SC2016  # deliberate: the literal placeholder is what must be gone
assert_not_contains "$(cat "$WORK/active-plan-rendered.yaml")" '${DAST_BEARER_TOKEN}' \
    "active plan: no placeholder is left unrendered"

# ===========================================================================
# The command surface
# ===========================================================================

out="$(sd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no command: fails with the usage"
assert_contains "$out" "query-schema" "no command: prints the usage"
assert_contains "$out" "active-schema" "no command: lists the active-scan commands"

out="$(sd_main report-path 2>&1)"; rc=$?
assert_eq "0" "$rc" "report-path: is reachable as a command"

report_tests
