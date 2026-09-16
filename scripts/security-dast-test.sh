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
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

# ===========================================================================
# Stubs
# ===========================================================================

# curl, for the management port probe. Prints the status code of the first line of
# $SD_STUB_DIR/answers whose pattern the URL contains, and 000 - a connection failure - otherwise,
# which is what a port nothing listens on looks like.
cat > "$WORK/bin/curl" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
url=""
for arg in "$@"; do
    case "$arg" in http*) url="$arg" ;; esac
done
echo "$url" >> "$SD_STUB_DIR/curl.log"
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

cat > "$WORK/plan.yaml" <<'PLAN'
jobs:
  - type: replacer
    rules:
      - matchString: "X-Ontrack-Token"
        replacementString: "${DEMO_TOKEN}"
  - type: spider
    parameters:
      url: "${DAST_TARGET}/"
PLAN

out="$(DEMO_TOKEN='tok&en\with/specials' sd_render_plan "$WORK/plan.yaml" "$WORK/plan-rendered.yaml" 2>&1)"; rc=$?
assert_eq "0" "$rc" "render-plan: succeeds"
assert_contains "$(cat "$WORK/plan-rendered.yaml")" 'replacementString: "tok&en\with/specials"' \
    "render-plan: substitutes a token containing regex and sed metacharacters literally"
# shellcheck disable=SC2016  # deliberate: the literal ZAP expands is what is being asserted
assert_contains "$(cat "$WORK/plan-rendered.yaml")" '${DAST_TARGET}/' \
    "render-plan: leaves the variables ZAP itself expands alone"
assert_not_contains "$out" "tok&en" "render-plan: does not print the token"

out="$(DEMO_TOKEN='' sd_render_plan "$WORK/plan.yaml" "$WORK/plan-rendered.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "render-plan: refuses to render without a token"

printf 'jobs: []\n' > "$WORK/plan-notoken.yaml"
out="$(DEMO_TOKEN=abc sd_render_plan "$WORK/plan-notoken.yaml" "$WORK/plan-rendered.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "render-plan: refuses a plan with no placeholder rather than scanning unauthenticated"

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
    name: ".*[Ii]ntrospection.*"
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

scrubbed="$(printf 'java.lang.OutOfMemoryError\n' | sd_scrub)"
assert_eq "java.lang.OutOfMemoryError" "$scrubbed" "scrub: leaves a plain diagnostic alone"

# ===========================================================================
# The command surface
# ===========================================================================

out="$(sd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no command: fails with the usage"
assert_contains "$out" "query-schema" "no command: prints the usage"

out="$(sd_main report-path 2>&1)"; rc=$?
assert_eq "0" "$rc" "report-path: is reachable as a command"

report_tests
