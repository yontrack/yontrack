#!/usr/bin/env bash
#
# The counting, reporting and disclosure layer of the DAST scans (#1765).
#
# Called by .github/workflows/dast-passive.yml, and meant to be called unchanged by the active
# scan (#1767) and by the extra scanners (#1766). The scanners themselves live in containers; what
# is here is everything that decides what a finding *means* and where it is allowed to be seen.
#
# The logic lives in this script rather than inline in the workflow so that
# scripts/security-dast-test.sh can exercise it against stubs and fixtures - the same arrangement
# as scripts/demo-smoke.sh, scripts/security-code-scan.sh and scripts/security-rescan.sh.
#
# Usage: scripts/security-dast.sh <command> ...
#
#   query-schema IN OUT       Derives a query-only GraphQL SDL from IN into OUT: no `type
#                             Mutation`, no `type Subscription`, and neither of them left in the
#                             `schema { }` block. Refuses to write a file that still declares one.
#                             This is what keeps mutations out of the scan - see
#                             security/dast/graphql/README.md.
#   render-plan IN OUT        Copies the ZAP plan, substituting `${DEMO_TOKEN}` with the token.
#                             The Automation Framework expands `${...}` in job parameters but not
#                             inside a replacer rule, so the one place the token is needed is the
#                             one place ZAP will not fill in.
#   actuator BASE_URL         Probes for a reachable Spring Boot management port. Exit 0 when it
#                             is not reachable, 1 when it is - which is an incident, not a
#                             finding.
#   normalize-zap FILE        Reads a ZAP `traditional-json` report and writes the normalised
#                             findings document (below) to stdout.
#   assert-no-mutations HAR   Reads the HAR of every request ZAP sent and fails if any of them was
#                             a GraphQL mutation or subscription. Prints counts only.
#   fetch URL SHA256 OUT      Downloads URL to OUT and refuses it unless its SHA-256 is SHA256.
#                             How every scanner that is not an image is pinned (#1766).
#   graphql-cop-preflight SRC EXCLUDED
#                             Before graphql-cop sends anything: fails unless every test registered
#                             in SRC that mentions a mutation is in EXCLUDED (comma-separated), and
#                             every name in EXCLUDED is a registered test.
#   render-graphql-cop-headers OUT
#                             Writes the API token header as JSON to OUT, for the run-time
#                             graphql-cop configuration to read. Never on a command line.
#   assert-graphql-cop-no-mutations OUT
#                             Reads graphql-cop's own record of the request each test sent and fails
#                             if any was a mutation or subscription. Prints counts only.
#   normalize-graphql-cop OUT VERSION
#                             Reads graphql-cop's `-o json` stdout and writes the normalised
#                             findings document to stdout.
#   nuclei-preflight LIST CONFIG
#                             Reads Nuclei's `-tl` template list and fails if any selected template
#                             carries a tag CONFIG excludes, or if CONFIG does not exclude
#                             intrusive/dos/fuzz/bruteforce or set a rate limit. Prints counts.
#   nuclei-summary LOG JSONL TARGET
#                             Fails unless Nuclei finished, kept scanning TARGET and wrote every
#                             match it counted to JSONL. Prints counts only.
#   normalize-nuclei JSONL VERSION
#                             Reads Nuclei's JSONL output and writes the normalised findings
#                             document to stdout.
#   report OUT_MD FILE...     Applies the rule levels and the suppressions to one or more
#                             normalised findings documents, writes the markdown report to OUT_MD,
#                             and prints the counts. With $GITHUB_OUTPUT set, writes `critical`,
#                             `high`, `medium`, `low`, `findings` and `suppressed` to it.
#   report-path               Prints the path the report takes in yontrack/security-reports.
#   publish REPO PATH FILE    Creates PATH in REPO with the contents of FILE. Never updates: one
#                             file per run, never rewritten.
#   scrub                     Copies stdin to stdout with URLs and the API token redacted. Every
#                             byte a scanner writes goes through this before it can reach a log.
#
# Environment (report, report-path):
#   DAST_KIND          `passive` or `active` (default: passive) - also the directory in the
#                      private repository
#   DAST_TARGET        what was scanned
#   DAST_VERSION       Yontrack version that was scanned, e.g. 5.5.0-abc1234
#   DAST_BUILD         Yontrack build name the stamp is reported on
#   DAST_RUN_URL       URL of the workflow run
#   DAST_RUN_ID        id of the workflow run (report-path)
#   DAST_DATE          UTC date of the run, yyyy-mm-dd (default: today)
#   DAST_STARTED       epoch seconds the run started, for the duration
#   DAST_SCANNERS      human description of the scanners and their versions
#   DAST_RULES         rule levels (default: security/dast/zap/rules.tsv)
#   DAST_SUPPRESSIONS  accepted findings (default: security/dast/suppressions.yaml)
#   DAST_MITIGATIONS   rule mitigations (default: security/dast/mitigations.yaml)
#   DEMO_TOKEN         redacted wherever it appears, in the report and in scrubbed output
#
# ---------------------------------------------------------------------------------------------
# The normalised findings document
# ---------------------------------------------------------------------------------------------
#
# The one shape every scanner is turned into, and the reason a second scanner costs a converter
# rather than a rewrite. `normalize-zap`, `normalize-graphql-cop` and `normalize-nuclei` produce it,
# and everything downstream - levels, suppressions, counting, the report, the disclosure rules - is
# the same code for all three.
#
#     {
#       "scanner": "zap",
#       "version": "2.16.1",
#       "findings": [
#         {
#           "scanner":     "zap",          // zap | graphql-cop | nuclei
#           "rule":        "10038",        // the tool's own rule id, matched by rules.tsv:
#                                          // ZAP's plugin id, graphql-cop's test name,
#                                          // Nuclei's template id
#           "ref":         "10038-1",      // the tool's finer id, when it has one
#           "name":        "Content Security Policy (CSP) Header Not Set",
#           "risk":        "MEDIUM",       // CRITICAL | HIGH | MEDIUM | LOW | INFO
#           "confidence":  "High",
#           "cwe":         "693",
#           "description": "...",
#           "solution":    "...",
#           "reference":   "...",
#           "instances": [
#             {"uri": "...", "method": "GET", "param": "", "evidence": "", "attack": "", "info": ""}
#           ]
#         }
#       ]
#     }
#
# Counting is **per finding**, not per instance: one misconfiguration shows up on dozens of URLs,
# and counting URLs would make "how bad is it" a function of how far the spider got.
#
# ---------------------------------------------------------------------------------------------
#
# Requires jq and yq (mikefarah's, v4) on the PATH; ubuntu-latest carries both. `publish` also
# needs `gh` authenticated as the private reports token, and `actuator` and `fetch` need curl.

set -uo pipefail

SD_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SD_KIND="${DAST_KIND:-passive}"
SD_DATE="${DAST_DATE:-$(date -u +%Y-%m-%d)}"
SD_RULES="${DAST_RULES:-$SD_ROOT/security/dast/zap/rules.tsv}"
SD_SUPPRESSIONS="${DAST_SUPPRESSIONS:-$SD_ROOT/security/dast/suppressions.yaml}"
SD_MITIGATIONS="${DAST_MITIGATIONS:-$SD_ROOT/security/dast/mitigations.yaml}"

sd_log() { echo "$*"; }
sd_fail() { echo "ERROR: $*" >&2; return 1; }

# ---------------------------------------------------------------------------------------------
# Disclosure
# ---------------------------------------------------------------------------------------------

# stdin to stdout, with everything a public log must not carry taken out.
#
# The repository is public and its workflow logs are readable by any signed-in GitHub user, so a
# scanner's own chatter - which carries the URLs it crawled and the parameters it looked at - never
# reaches the log raw. What is left is enough to tell a crashed container from a refused
# connection, which is all a log is for here.
#
# The token first, and unconditionally: a redaction that only runs when something looks like a URL
# is a redaction that misses the one line that mattered.
sd_scrub() {
    local token="${DEMO_TOKEN:-}"
    if [ -n "$token" ]; then
        sed -e "s#$(printf '%s' "$token" | sed 's/[&/\]/\\&/g')#<redacted>#g"
    else
        cat
    fi | sed -E \
        -e 's#https?://[^[:space:]"'"'"'<>]+#<url>#g' \
        -e 's#(X-Ontrack-Token|Authorization)[: =][^[:space:]"]+#\1: <redacted>#gI'
}

# ---------------------------------------------------------------------------------------------
# query-schema - the mechanism that keeps mutations out of the GraphQL scan
# ---------------------------------------------------------------------------------------------

# ZAP's GraphQL add-on generates one request per root field of every root type in the schema it is
# given - Query, Mutation and Subscription alike - and has no option to leave an operation type
# out. So the schema is the option: hand it one with no mutation root and a mutation is not
# filtered, it is not expressible.
#
# `ontrack-web-core/ontrack.graphql` is generated from the running schema, so its shape is
# predictable: top-level definitions start at column 0 and close with a `}` at column 0. The
# `schema { }` block loses its `mutation:` and `subscription:` entries - leaving them would point
# at types that are no longer there and the schema would not parse at all.
#
# The post-condition is checked, not assumed: this refuses to write a file that still declares a
# mutation or a subscription root, and the workflow stops there rather than scanning.
sd_query_schema() {
    local input="${1:-}" output="${2:-}" tmp
    [ -n "$input" ] && [ -n "$output" ] || { sd_fail "Usage: $0 query-schema IN OUT"; return 1; }
    [ -f "$input" ] || { sd_fail "No GraphQL schema at $input"; return 1; }

    tmp="$(mktemp "${TMPDIR:-/tmp}/query-only.XXXXXX")" || return 1

    # `mutation:` and `subscription:` are ordinary field names elsewhere in this schema - Yontrack
    # has an `EventSubscriptionPayload` on dozens of types - so the root declarations are only
    # recognised inside the `schema { }` block, and a root type only at column 0.
    awk '
        # Inside `type Mutation {` or `type Subscription {`: drop every line up to the closing
        # brace in column 0.
        skipping == 1 {
            if ($0 == "}") { skipping = 0 }
            next
        }
        /^schema[ \t]*\{/ { inschema = 1; print; next }
        inschema == 1 {
            if ($0 == "}") { inschema = 0; print; next }
            if ($0 ~ /^[ \t]*mutation[ \t]*:/) { dropped_root++; next }
            if ($0 ~ /^[ \t]*subscription[ \t]*:/) { dropped_root++; next }
            print
            next
        }
        /^type Mutation[ \t]*\{/ { skipping = 1; dropped_type++; next }
        /^type Subscription[ \t]*\{/ { skipping = 1; dropped_type++; next }
        { print }
        END {
            if (skipping == 1 || inschema == 1) { exit 3 }
            if (dropped_type == 0) { exit 4 }
        }
    ' "$input" > "$tmp"
    case $? in
        0) ;;
        3) rm -f "$tmp"; sd_fail "Unterminated block in $input: the schema is not the generated shape."; return 1 ;;
        4) rm -f "$tmp"; sd_fail "No mutation or subscription root found in $input: refusing to guess what to strip."; return 1 ;;
        *) rm -f "$tmp"; sd_fail "Could not read $input"; return 1 ;;
    esac

    # The post-condition. Cheap, and the only thing between a bad edit here and a scanner with
    # write access to the demo.
    if ! awk '
        /^type (Mutation|Subscription)[ \t]*\{/ { bad = 1 }
        /^schema[ \t]*\{/ { inschema = 1; next }
        inschema == 1 && $0 == "}" { inschema = 0; next }
        inschema == 1 && $0 ~ /^[ \t]*(mutation|subscription)[ \t]*:/ { bad = 1 }
        END { exit (bad ? 1 : 0) }
    ' "$tmp"; then
        rm -f "$tmp"
        sd_fail "The derived schema still declares a mutation or subscription root: refusing to scan."
        return 1
    fi

    mkdir -p "$(dirname "$output")" || { rm -f "$tmp"; return 1; }
    mv "$tmp" "$output" || return 1
    # `mktemp` creates 600, and the scanner reads this from a container running as its own uid: a
    # file it cannot open is reported by the GraphQL add-on as the same "An error occurred while
    # importing from file" it reports for a schema it cannot parse. A bind mount on macOS hides
    # the difference, which is how this survived a local run and failed on the first real one.
    chmod 644 "$output" || return 1
    sd_log "Query-only schema written: $(wc -l < "$output" | tr -d ' ') lines, no mutation or subscription root."
    return 0
}

# ---------------------------------------------------------------------------------------------
# render-plan
# ---------------------------------------------------------------------------------------------

# The Automation Framework expands `${VAR}` from the environment in a job's parameters - the
# requestor URLs, the spider's start URL, the GraphQL endpoint all arrive substituted - but NOT
# inside a `replacer` job's rules, which the replacer add-on parses for itself. Verified against
# the pinned image: the header went out with the literal `${DEMO_TOKEN}` as its value.
#
# So the one value ZAP will not fill in is the one that is a secret, and it is written into a copy
# of the plan instead. That copy lives in $RUNNER_TEMP, is never uploaded - this workflow uploads
# nothing - and dies with the runner. It is left world-readable because the ZAP container runs as
# its own uid and has to read it; on an ephemeral runner that is a narrower exposure than the
# alternatives (the token on the `docker run` command line, where any process could read it off
# /proc, or in a plan committed to a public repository, which is not an alternative at all).
sd_render_plan() {
    local input="${1:-}" output="${2:-}"
    [ -n "$input" ] && [ -n "$output" ] || { sd_fail "Usage: $0 render-plan IN OUT"; return 1; }
    [ -f "$input" ] || { sd_fail "No ZAP plan at $input"; return 1; }
    [ -n "${DEMO_TOKEN:-}" ] || { sd_fail "DEMO_TOKEN is not set: the API cannot be scanned."; return 1; }
    # awk reads it out of ENVIRON below, which only sees exported variables.
    export DEMO_TOKEN

    # Literal index/substr rather than gsub, and the token read from the environment rather than
    # passed with -v: a regex replacement would mangle a `&` or a backslash, and awk expands
    # escape sequences in a -v assignment - both turn a good token into a wrong one, which reads
    # as an expired one.
    awk '
        BEGIN { tok = ENVIRON["DEMO_TOKEN"]; needle = "${DEMO_TOKEN}"; n = length(needle) }
        {
            while ((i = index($0, needle)) > 0) {
                $0 = substr($0, 1, i - 1) tok substr($0, i + n)
                replaced++
            }
            print
        }
        END { if (replaced == 0) exit 3 }
    ' "$input" > "$output"
    case $? in
        0) ;;
        3) rm -f "$output"; sd_fail "No \${DEMO_TOKEN} placeholder in $input: the API would be scanned unauthenticated."; return 1 ;;
        *) rm -f "$output"; sd_fail "Could not render $input"; return 1 ;;
    esac

    chmod 644 "$output" || return 1
    sd_log "ZAP plan rendered: the API token is in place."
    return 0
}

# ---------------------------------------------------------------------------------------------
# actuator - an incident check, not a finding
# ---------------------------------------------------------------------------------------------

# The Spring Boot management port is unauthenticated by design (see WebSecurityConfig) and must
# never be routed from outside the cluster; the chart is what guarantees it
# (yontrack/yontrack-chart#110). If it answers anyway, the run stops and says so rather than
# recording a number: an exposed management port is not a finding to count alongside a missing
# header.
#
# Both shapes are probed: the port on the public host, and `/manage` on the normal one, in case an
# ingress rule ever routes the management base path to the backend.
sd_actuator() {
    local base="${1:-${DAST_TARGET:-}}" host scheme found=0 url code
    [ -n "$base" ] || { sd_fail "Usage: $0 actuator BASE_URL"; return 1; }
    base="${base%/}"
    scheme="${base%%://*}"
    host="${base#*://}"
    host="${host%%/*}"
    host="${host%%:*}"

    for url in "$scheme://$host:8800/manage/health" "$scheme://$host:8800/actuator/health" \
               "$base/manage/health" "$base/actuator/health"; do
        code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$url" 2>/dev/null)" || code="000"
        case "$code" in
            200|401|403)
                # Anything that is not a connection failure, a 404 or a redirect to the UI means
                # something is listening on a management path. 401/403 counts: the port answering
                # at all is the exposure.
                sd_log "A management endpoint answered $code on $(printf '%s' "$url" | sed -E 's#^(https?://[^/]+).*#\1#')/<manage>."
                found=1
                ;;
            *) ;;
        esac
    done

    if [ "$found" -eq 1 ]; then
        sd_fail "The Spring Boot management port is reachable from outside the cluster. This is an incident, not a finding: see security/dast/mitigations.yaml (actuator-exposure), yontrack/yontrack#1772 and yontrack/yontrack-chart#110."
        return 1
    fi
    sd_log "No management endpoint reachable on the target. Good."
    return 0
}

# ---------------------------------------------------------------------------------------------
# normalize-zap
# ---------------------------------------------------------------------------------------------

# ZAP's `traditional-json` template, into the normalised document documented at the top.
#
# Alerts are grouped by `alertRef` - the same plugin can raise several distinct alerts, and the
# same alert appears once per site - so that one rule is one finding with all of its instances,
# whatever the report's own shape was.
#
# `riskcode` is ZAP's 0..3; ZAP has no Critical, which is why a passive ZAP-only run never
# produces one. The prose fields are HTML in the report and are stripped: they end up in markdown.
sd_normalize_zap() {
    local file="${1:-}"
    [ -n "$file" ] || { sd_fail "Usage: $0 normalize-zap FILE"; return 1; }
    [ -f "$file" ] || { sd_fail "No ZAP report at $file"; return 1; }

    jq -e '
        def text: (. // "") | tostring
            | gsub("<(br|BR)[^>]*>"; "\n") | gsub("</p>"; "\n") | gsub("<[^>]*>"; "")
            | gsub("&lt;"; "<") | gsub("&gt;"; ">") | gsub("&amp;"; "&") | gsub("&quot;"; "\"")
            | gsub("[ \t]+"; " ") | gsub("\n[ \n]*"; "\n") | sub("^\\s+"; "") | sub("\\s+$"; "");
        def risk: ({"0": "INFO", "1": "LOW", "2": "MEDIUM", "3": "HIGH"}[(. // 0) | tostring] // "INFO");
        {
          scanner: "zap",
          version: ((.["@version"] // "unknown") | tostring),
          findings: (
            [ (.site // [])[] | (.alerts // [])[] ]
            | group_by((.alertRef // .pluginid // .alert) | tostring)
            | map(
                (.[0]) as $a
                | {
                    scanner: "zap",
                    rule: (($a.pluginid // $a.alertRef // "0") | tostring),
                    ref: (($a.alertRef // $a.pluginid // "0") | tostring),
                    name: (($a.alert // $a.name // "Unnamed") | tostring),
                    risk: (($a.riskcode // "0") | tostring | risk),
                    confidence: ({"0": "False Positive", "1": "Low", "2": "Medium", "3": "High", "4": "Confirmed"}[($a.confidence // "0") | tostring] // "Unknown"),
                    cwe: (($a.cweid // "") | tostring),
                    description: ($a.desc | text),
                    solution: ($a.solution | text),
                    reference: ($a.reference | text),
                    instances: [
                      .[] | (.instances // [])[]
                      | {
                          uri: ((.uri // "") | tostring),
                          method: ((.method // "") | tostring),
                          param: ((.param // "") | tostring),
                          evidence: ((.evidence // "") | tostring),
                          attack: ((.attack // "") | tostring),
                          info: (.otherinfo | text)
                        }
                    ]
                  }
              )
          )
        }
    ' "$file" || { sd_fail "Could not read the ZAP report at $file"; return 1; }
}

# ---------------------------------------------------------------------------------------------
# assert-no-mutations - the only check that observes what actually went over the wire
# ---------------------------------------------------------------------------------------------

# The query-only schema makes a mutation unrepresentable and the plan sends nothing but GETs
# outside the GraphQL job, so this should be unreachable. It exists because "should be" is not
# the standard to hold a scanner with an admin token on a shared instance to.
#
# One subscription is expected and allowed: `subscription {__typename}`. The GraphQL add-on sends
# it as a capability probe whatever schema it was given - verified against the pinned image - and
# it is the one GraphQL document in the run that does not come from the schema. It selects
# `__typename` and nothing else, Yontrack declares no subscription root at all so the server
# refuses it outright, and refusing something is not a write. Any *other* subscription would mean
# the add-on generated one from the schema, which would mean the schema had a subscription root,
# which is exactly what this is here to notice. A mutation is never allowed, probe or not.
#
# Counts only, as everything printed here is.
sd_assert_no_mutations() {
    local har="${1:-}" summary mutations others total graphql probes
    [ -n "$har" ] || { sd_fail "Usage: $0 assert-no-mutations HAR"; return 1; }
    [ -f "$har" ] || { sd_fail "No HAR at $har: cannot prove what was sent."; return 1; }

    summary="$(jq -e -r '
        def body: (.request.postData.text // "");
        # The operation keyword at the start of the document, or right after a JSON "query"
        # property: a *field* called mutationSomething must not trip this, and an operation must
        # not slip past it. The URL is matched too, for the GET form the add-on also uses.
        def document: (body + " " + ((.request.url // "") | @uri));
        def starts_with($kw): document | test("(\"query\"\\s*:\\s*\"|query=|^)\\s*" + $kw + "\\b"; "i");
        def is_probe: document | test("subscription\\s*\\{\\s*__typename\\s*\\}"; "i");
        (.log.entries // []) as $e
        | ($e | length) as $total
        | ([$e[] | select((.request.url // "") | test("/graphql"))]) as $gql
        | ([$e[] | select(starts_with("mutation"))]) as $mut
        | ([$e[] | select(starts_with("subscription"))]) as $sub
        | ([$sub[] | select(is_probe)]) as $probes
        | "total=\($total)", "graphql=\($gql | length)",
          "mutations=\($mut | length)", "probes=\($probes | length)",
          "others=\(($sub | length) - ($probes | length))"
    ' "$har")" || { sd_fail "Could not read the request HAR at $har"; return 1; }

    total="$(printf '%s\n' "$summary" | sed -n 's/^total=//p')"
    graphql="$(printf '%s\n' "$summary" | sed -n 's/^graphql=//p')"
    mutations="$(printf '%s\n' "$summary" | sed -n 's/^mutations=//p')"
    probes="$(printf '%s\n' "$summary" | sed -n 's/^probes=//p')"
    others="$(printf '%s\n' "$summary" | sed -n 's/^others=//p')"

    if [ "${mutations:-1}" != "0" ]; then
        sd_fail "$mutations of the $total requests carried a GraphQL mutation. The scan wrote to the target; fix security/dast/zap/passive.yaml before running it again."
        return 1
    fi
    if [ "${others:-1}" != "0" ]; then
        sd_fail "$others subscription(s) beyond the add-on's \`{__typename}\` probe were sent. The schema handed to the scanner has a subscription root it should not have."
        return 1
    fi
    sd_log "Requests sent: $total, of which $graphql to /graphql. Mutations: 0. Subscriptions: $probes, all the add-on's \`{__typename}\` capability probe."
    return 0
}

# ---------------------------------------------------------------------------------------------
# fetch - pinning what is not an image
# ---------------------------------------------------------------------------------------------

# ZAP is an image and is pinned by digest. graphql-cop and Nuclei are not - graphql-cop publishes no
# image of its current release, and Nuclei's image downloads whatever templates are newest when it
# starts - so each is downloaded at a fixed version and checked against a SHA-256 committed in the
# workflow. A mismatch is a failed run: a scanner that is not the one reviewed does not scan.
sd_sha256() {
    if command -v sha256sum > /dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}

sd_fetch() {
    local url="${1:-}" expected="${2:-}" out="${3:-}" actual
    [ -n "$url" ] && [ -n "$expected" ] && [ -n "$out" ] \
        || { sd_fail "Usage: $0 fetch URL SHA256 OUT"; return 1; }
    mkdir -p "$(dirname "$out")" || return 1

    curl -sSfL --retry 3 -o "$out.part" "$url" \
        || { rm -f "$out.part"; sd_fail "Could not download $(basename "$out")."; return 1; }
    actual="$(sd_sha256 "$out.part")" || { rm -f "$out.part"; return 1; }
    if [ "$actual" != "$expected" ]; then
        rm -f "$out.part"
        sd_fail "$(basename "$out") does not match its pinned checksum (expected $expected, got $actual): refusing to run it."
        return 1
    fi
    mv "$out.part" "$out" || return 1
    sd_log "$(basename "$out") downloaded and matches its pinned checksum."
    return 0
}

# ---------------------------------------------------------------------------------------------
# graphql-cop
# ---------------------------------------------------------------------------------------------

# graphql-cop runs a fixed list of tests, one or a few requests each, and ships one that sends
# `mutation cop {__typename}` over GET to see whether mutations are accepted that way. It selects
# nothing and writes nothing, but it is a mutation, sent to a shared instance with an admin token -
# so it is excluded, and three things make sure of it, the same arrangement as ZAP's:
#
#   1. `graphql-cop-preflight` reads the pinned source before anything is sent, and fails unless
#      every registered test that mentions a mutation is excluded - and unless every exclusion
#      names a registered test, because graphql-cop answers an unknown `-e` name by printing a line
#      and running everything;
#   2. the exclusion itself, `-e`, on the command line;
#   3. `assert-graphql-cop-no-mutations` reads back, after the run, the request graphql-cop reports
#      for every test it ran (its `curl_verify`), and fails on any mutation or subscription.
#
# See security/dast/graphql/README.md.

# The registered tests of a graphql-cop source tree, one `name<TAB>module` per line. Registrations
# are the `"name":function` lines of the `tests = { }` dictionary in lib/tests/__init__.py; a
# commented one is not registered. The module is found through the `from lib.tests.<module> import
# <function>` line that brings the function in.
sd_graphql_cop_tests() {
    awk '
        /^from lib\.tests\.[A-Za-z0-9_]+ import [A-Za-z0-9_]+/ {
            module = $2; sub(/^lib\.tests\./, "", module); modules[$4] = module; next
        }
        /^tests[ \t]*=[ \t]*\{/ { intests = 1; next }
        intests == 1 && /^[ \t]*\}/ { intests = 0; next }
        intests == 1 {
            line = $0
            sub(/^[ \t]+/, "", line)
            if (line ~ /^#/) { next }
            if (match(line, /^"[A-Za-z0-9_]+"[ \t]*:[ \t]*[A-Za-z0-9_]+/)) {
                entry = substr(line, RSTART, RLENGTH)
                name = entry; sub(/^"/, "", name); sub(/".*$/, "", name)
                fn = entry; sub(/^.*:[ \t]*/, "", fn)
                registered[++n] = name; functions[n] = fn
            }
        }
        END { for (i = 1; i <= n; i++) printf "%s\t%s\n", registered[i], modules[functions[i]] }
    ' "$1"
}

sd_graphql_cop_preflight() {
    local src="${1:-}" excluded="${2:-}" init tests name module running=0 bad="" item
    [ -n "$src" ] || { sd_fail "Usage: $0 graphql-cop-preflight SRC EXCLUDED"; return 1; }
    init="$src/lib/tests/__init__.py"
    [ -f "$init" ] || { sd_fail "No graphql-cop source at $src"; return 1; }

    tests="$(sd_graphql_cop_tests "$init")" || { sd_fail "Could not read the graphql-cop test list"; return 1; }
    [ -n "$tests" ] || { sd_fail "No test registered in the graphql-cop source: not the shape this was written for."; return 1; }

    # Every exclusion must name a registered test: graphql-cop would print one line and run it.
    for item in $(printf '%s' "$excluded" | tr ',' ' '); do
        if ! printf '%s\n' "$tests" | cut -f1 | grep -qxF "$item"; then
            sd_fail "The graphql-cop exclusion \"$item\" names no registered test: graphql-cop would ignore it and run everything."
            return 1
        fi
    done

    while IFS=$'\t' read -r name module; do
        [ -n "$name" ] || continue
        if printf ',%s,' "$excluded" | grep -qF ",$name,"; then
            continue
        fi
        running=$((running + 1))
        # Conservative on purpose: any mention of a mutation, in a query or in a docstring, and the
        # test does not run until someone has read it and excluded it or changed this.
        if [ -z "$module" ] || [ ! -f "$src/lib/tests/$module.py" ]; then
            bad="$bad $name(unreadable)"
        elif grep -qi 'mutation' "$src/lib/tests/$module.py"; then
            bad="$bad $name"
        fi
    done <<< "$tests"

    if [ -n "$bad" ]; then
        sd_fail "graphql-cop test(s) that may send a mutation are not excluded:$bad. Add them to the exclusions in .github/workflows/dast-passive.yml."
        return 1
    fi
    sd_log "graphql-cop: $running test(s) will run, none of which mentions a mutation. Excluded: ${excluded:-none}."
    return 0
}

# The token, as the JSON the run-time graphql-cop configuration reads (security/dast/graphql-cop/
# config.py). graphql-cop's own way in is `-H '{"X-Ontrack-Token": "..."}'` on its command line, and
# a command line is readable from /proc by anything else on the runner - the reason ZAP's plan is
# rendered with the token rather than handed it as an argument.
sd_render_graphql_cop_headers() {
    local output="${1:-}"
    [ -n "$output" ] || { sd_fail "Usage: $0 render-graphql-cop-headers OUT"; return 1; }
    [ -n "${DEMO_TOKEN:-}" ] || { sd_fail "DEMO_TOKEN is not set: the API cannot be scanned."; return 1; }
    mkdir -p "$(dirname "$output")" || return 1
    # From the environment rather than interpolated or passed with --arg: jq writes the token as a
    # JSON string whatever it contains, and it appears in no process's arguments on the way.
    export DEMO_TOKEN
    ( umask 077 && jq -n '{"X-Ontrack-Token": env.DEMO_TOKEN}' > "$output" ) \
        || { rm -f "$output"; sd_fail "Could not write the graphql-cop headers"; return 1; }
    sd_log "graphql-cop headers rendered: the API token is in place."
    return 0
}

# The JSON array graphql-cop prints last. `-o json` still lets it print a line first - for an
# exclusion it does not know, for an endpoint it does not recognise - so the array is the last line
# that starts one. jq's own error is dropped: a parse error quotes the input.
sd_graphql_cop_json() {
    local file="$1" line
    [ -f "$file" ] || { sd_fail "No graphql-cop output at $file"; return 1; }
    line="$(grep -E '^\[' "$file" | tail -n 1)"
    [ -n "$line" ] || { sd_fail "graphql-cop printed no result: it did not complete."; return 1; }
    printf '%s\n' "$line" | jq -e -c 'if type == "array" then . else error("not an array") end' 2> /dev/null \
        || { sd_fail "graphql-cop's result is not the JSON array it prints."; return 1; }
}

sd_assert_graphql_cop_no_mutations() {
    local file="${1:-}" json summary total mutations unverifiable
    [ -n "$file" ] || { sd_fail "Usage: $0 assert-graphql-cop-no-mutations OUT"; return 1; }
    [ -f "$file" ] || { sd_fail "No graphql-cop output at $file: cannot prove what was sent."; return 1; }

    if grep -q 'cannot be excluded' "$file"; then
        sd_fail "graphql-cop did not recognise one of its exclusions, and ran every test instead."
        return 1
    fi
    json="$(sd_graphql_cop_json "$file")" || return 1

    summary="$(printf '%s\n' "$json" | jq -r '
        # The operation keyword right after the query is introduced: as a JSON property, as a
        # url-encoded parameter in a GET query string or a form body. A field whose name starts
        # with `mutation` does not match, and neither does a batch of queries.
        def request: (.curl_verify // "");
        def sends($kw): request | test("(\"query\"\\s*:\\s*\"|query=)(\\s|\\+|%20|%0A)*" + $kw + "\\b"; "i");
        length as $total
        | "total=\($total)",
          "mutations=\([.[] | select(sends("mutation") or sends("subscription"))] | length)",
          "unverifiable=\([.[] | select(request == "")] | length)"
    ')" || { sd_fail "Could not read graphql-cop's requests"; return 1; }

    total="$(printf '%s\n' "$summary" | sed -n 's/^total=//p')"
    mutations="$(printf '%s\n' "$summary" | sed -n 's/^mutations=//p')"
    unverifiable="$(printf '%s\n' "$summary" | sed -n 's/^unverifiable=//p')"

    if [ "${total:-0}" = "0" ]; then
        sd_fail "graphql-cop ran no test: nothing to prove, and nothing scanned."
        return 1
    fi
    if [ "${mutations:-1}" != "0" ]; then
        sd_fail "$mutations of the $total graphql-cop tests sent a GraphQL mutation or subscription. The scan wrote to the target; fix the exclusions in .github/workflows/dast-passive.yml before running it again."
        return 1
    fi
    if [ "${unverifiable:-1}" != "0" ]; then
        sd_fail "$unverifiable of the $total graphql-cop tests report no request: cannot prove what they sent."
        return 1
    fi
    sd_log "graphql-cop: Tests run: $total, each read back from the request it reports. Mutations: 0. Subscriptions: 0."
    return 0
}

# graphql-cop's result, into the normalised document.
#
# One entry per test, `result: true` when the weakness is there; a test that passed is not a
# finding. Its severity is already HIGH, MEDIUM, LOW or INFO. It has no rule id in its output, so the
# rule is the test's registered name - the one `-e` takes - through its title, which is what the
# output does carry; a title this does not know falls back to itself, lower-cased.
#
# The instance is the endpoint and the method, read from `curl_verify` - and `curl_verify` itself is
# dropped: it is the whole request, the API token header included.
sd_normalize_graphql_cop() {
    local file="${1:-}" version="${2:-unknown}" json
    [ -n "$file" ] || { sd_fail "Usage: $0 normalize-graphql-cop OUT VERSION"; return 1; }
    json="$(sd_graphql_cop_json "$file")" || return 1
    if [ "$(printf '%s\n' "$json" | jq 'length')" = "0" ]; then
        sd_fail "graphql-cop ran no test - it did not recognise the endpoint as GraphQL. A scan that did not happen is not a clean result."
        return 1
    fi

    printf '%s\n' "$json" | jq -e --arg version "$version" '
        def rule_id:
            {
              "Field Suggestions": "field_suggestions",
              "Introspection": "introspection",
              "GraphQL IDE": "detect_graphiql",
              "GET Method Query Support": "get_method_support",
              "Alias Overloading": "alias_overloading",
              "Array-based Query Batching": "batch_query",
              "Trace Mode": "trace_mode",
              "Directive Overloading": "directive_overloading",
              "Introspection-based Circular Query": "circular_query_introspection",
              "Mutation is allowed over GET (possible CSRF)": "get_based_mutation",
              "POST based url-encoded query (possible CSRF)": "post_based_csrf",
              "Unhandled Errors Detection": "unhandled_error_detection",
              "Field Duplication": "field_duplication"
            }[.title // ""]
            // ((.title // "unnamed") | ascii_downcase | gsub("[^a-z0-9]+"; "_") | ltrimstr("_") | rtrimstr("_"));
        def risk: ((.severity // "") | ascii_upcase) as $s
            | if ["CRITICAL", "HIGH", "MEDIUM", "LOW"] | index($s) then $s else "INFO" end;
        def endpoint: [ (.curl_verify // "") | capture("\u0027(?<u>https?://[^\u0027?]*)[^\u0027]*\u0027\\s*$") ] | (.[0].u // "");
        def method: [ (.curl_verify // "") | capture("^curl -X (?<m>[A-Z]+)") ] | (.[0].m // "");
        {
          scanner: "graphql-cop",
          version: $version,
          findings: [
            .[] | select(.result == true)
            | {
                scanner: "graphql-cop",
                rule: rule_id,
                ref: rule_id,
                name: ((.title // "Unnamed") | tostring),
                risk: risk,
                confidence: "Unknown",
                cwe: "",
                description: ((.description // "") | tostring),
                solution: "",
                reference: "https://github.com/dolevf/graphql-cop",
                instances: [
                  { uri: endpoint, method: method, param: "", evidence: ((.impact // "") | tostring), attack: "", info: "" }
                ]
              }
          ]
        }
    ' || { sd_fail "Could not convert graphql-cop's result"; return 1; }
}

# ---------------------------------------------------------------------------------------------
# Nuclei
# ---------------------------------------------------------------------------------------------

# The exclusions and the rate limit, observed rather than assumed: security/dast/nuclei/passive.yaml
# says which tags are excluded, and this reads the tags of every template Nuclei actually selected
# with that configuration (`nuclei -tl`) and fails if one of them carries an excluded tag anyway.
# It also refuses a configuration that has stopped excluding one of the four tags the demo must
# never see, or that has lost its rate limit - the demo is public and shared.
sd_nuclei_preflight() {
    local list="${1:-}" config="${2:-}" excluded rate required count bad
    [ -n "$list" ] && [ -n "$config" ] || { sd_fail "Usage: $0 nuclei-preflight LIST CONFIG"; return 1; }
    [ -f "$list" ] || { sd_fail "No Nuclei template list at $list"; return 1; }
    [ -f "$config" ] || { sd_fail "No Nuclei configuration at $config"; return 1; }

    excluded="$(yq -r '(.["exclude-tags"] // []) | join(",")' "$config")" \
        || { sd_fail "Could not read the excluded tags from $config"; return 1; }
    for required in intrusive dos fuzz bruteforce; do
        if ! printf ',%s,' "$excluded" | grep -qF ",$required,"; then
            sd_fail "$config does not exclude the \`$required\` tag. The demo is public and shared: it must."
            return 1
        fi
    done
    rate="$(yq -r '.["rate-limit"] // ""' "$config")" || return 1
    case "$rate" in
        ''|*[!0-9]*|0) sd_fail "$config sets no rate limit. The demo is public and shared: it must."; return 1 ;;
        *) ;;
    esac

    # One awk over every selected template: the first `tags:` line of each, split on commas.
    local result
    # shellcheck disable=SC2016  # deliberate: an awk program, expanded by awk
    result="$(grep -E '\.ya?ml$' "$list" | while IFS= read -r path; do
                  [ -f "$path" ] && printf '%s\0' "$path"
              done | xargs -0 awk -v excluded="$excluded" '
        BEGIN { n = split(excluded, ex, ","); for (i = 1; i <= n; i++) if (ex[i] != "") bad_tag[ex[i]] = 1 }
        FNR == 1 { files++ }
        /^[ \t]*tags:/ && !(FILENAME in seen) {
            seen[FILENAME] = 1
            value = $0
            sub(/^[ \t]*tags:[ \t]*/, "", value)
            gsub(/["\047\[\]]/, "", value)
            m = split(value, tags, ",")
            for (j = 1; j <= m; j++) {
                t = tags[j]; gsub(/^[ \t]+|[ \t]+$/, "", t)
                if (t in bad_tag) { bad++; break }
            }
        }
        END { printf "%d %d\n", files, bad }
    ')" || true
    count="${result%% *}"
    bad="${result##* }"
    count="${count:-0}"
    bad="${bad:-0}"

    if [ "$count" = "0" ]; then
        sd_fail "Nuclei selected no template: the selection is broken, and a scan of nothing is not a clean scan."
        return 1
    fi
    if [ "$bad" != "0" ]; then
        sd_fail "$bad of the $count templates Nuclei selected carry an excluded tag. Refusing to scan the demo with them."
        return 1
    fi
    sd_log "Nuclei: $count template(s) selected, none tagged $(printf '%s' "$excluded" | sed 's/,/, /g'). Rate limit: $rate request(s) per second."
    return 0
}

# Whether the Nuclei run is a scan at all, from its log and its output. Nuclei exits 0 in three
# cases that are not a clean result: when it did not finish, when it gave up on the target as
# unresponsive - it then skips every remaining template silently - and when the output file holds
# fewer matches than it counted. Each of them fails here, so that it reports no stamp.
#
# The log itself is never printed: it carries every match, URL included. This prints the final
# statistics, which are counts.
sd_nuclei_summary() {
    local log="${1:-}" jsonl="${2:-}" target="${3:-${DAST_TARGET:-}}" stats host port lines
    [ -n "$log" ] && [ -n "$jsonl" ] && [ -n "$target" ] \
        || { sd_fail "Usage: $0 nuclei-summary LOG JSONL TARGET"; return 1; }
    [ -f "$log" ] || { sd_fail "No Nuclei log at $log"; return 1; }
    [ -f "$jsonl" ] || { sd_fail "No Nuclei output at $jsonl"; return 1; }

    stats="$(grep -E '^\{"duration"' "$log" | tail -n 1)"
    if [ -z "$stats" ] || ! printf '%s\n' "$stats" | jq -e 'type == "object"' > /dev/null 2>&1; then
        sd_fail "Nuclei printed no final statistics: the scan did not finish."
        return 1
    fi

    host="${target#*://}"
    host="${host%%/*}"
    case "$host" in
        *:*) port="${host##*:}"; host="${host%%:*}" ;;
        *) case "$target" in https://*) port=443 ;; *) port=80 ;; esac ;;
    esac
    if grep -qF "Skipped $host:$port from target list" "$log"; then
        sd_fail "Nuclei gave up on the target as unresponsive and skipped the rest of its templates: not a completed scan."
        return 1
    fi

    lines="$(grep -c . "$jsonl" || true)"
    if [ "${lines:-0}" -lt "$(printf '%s\n' "$stats" | jq -r '.matched // 0 | tonumber')" ]; then
        sd_fail "Nuclei counted more matches than its output file holds: the output is incomplete."
        return 1
    fi

    printf '%s\n' "$stats" | jq -r '
        "Nuclei: \(.templates // "?") template(s), \(.requests // "?") request(s) in \(.duration // "?"), \(.rps // "?") request(s) per second, \(.errors // "?") error(s)."
    ' || return 1
    return 0
}

# Nuclei's JSONL, into the normalised document.
#
# One line per match; grouped by template, so that one template matching on several paths is one
# finding with several instances. Its severities map one to one, `critical` included - the only
# scanner of the three that has one. `unknown` is INFO: a template that does not say how bad it is
# does not get to count.
#
# An empty file is zero findings: Nuclei writes nothing when nothing matched, and whether it ran at
# all is `nuclei-summary`'s question. `curl-command`, `request` and `response` are dropped.
sd_normalize_nuclei() {
    local file="${1:-}" version="${2:-unknown}"
    [ -n "$file" ] || { sd_fail "Usage: $0 normalize-nuclei JSONL VERSION"; return 1; }
    [ -f "$file" ] || { sd_fail "No Nuclei output at $file"; return 1; }

    jq -e -s --arg version "$version" '
        def risk: ((.info.severity // "") | ascii_upcase) as $s
            | if ["CRITICAL", "HIGH", "MEDIUM", "LOW"] | index($s) then $s else "INFO" end;
        def text: if . == null then "" elif type == "array" then map(tostring) | join("\n") else tostring end
            | sub("\\s+$"; "");
        {
          scanner: "nuclei",
          version: $version,
          findings: (
            group_by(.["template-id"] // "unknown")
            | map(
                (.[0]) as $a
                | {
                    scanner: "nuclei",
                    rule: (($a["template-id"] // "unknown") | tostring),
                    ref: (($a["template-id"] // "unknown") | tostring),
                    name: (($a.info.name // $a["template-id"] // "Unnamed") | tostring),
                    risk: ($a | risk),
                    confidence: "Unknown",
                    cwe: ((($a.info.classification["cwe-id"] // []) | .[0] // "") | tostring | ascii_downcase | ltrimstr("cwe-")),
                    description: ($a.info.description | text),
                    solution: ($a.info.remediation | text),
                    reference: ($a.info.reference | text),
                    instances: [
                      .[] | {
                        uri: ((.["matched-at"] // .url // .host // "") | tostring),
                        method: "",
                        param: ((.["matcher-name"] // "") | tostring),
                        evidence: (.["extracted-results"] | text | gsub("\n"; ", ")),
                        attack: "",
                        info: ""
                      }
                    ]
                  }
              )
          )
        }
    ' "$file" 2> /dev/null || { sd_fail "Could not read the Nuclei output at $file"; return 1; }
}

# ---------------------------------------------------------------------------------------------
# report
# ---------------------------------------------------------------------------------------------

# security/dast/zap/rules.tsv as a JSON object of rule id to level. Comments and blank lines out,
# anything that is not one of the three levels is a typo and stops the run: a misspelt `IGNORE`
# that silently does nothing is how a rule everyone believes is muted keeps being counted.
sd_rules_json() {
    local file="$1"
    [ -f "$file" ] || { echo '{}'; return 0; }
    awk -F'\t' '
        /^[ \t]*#/ { next }
        /^[ \t]*$/ { next }
        {
            gsub(/^[ \t]+|[ \t]+$/, "", $1); gsub(/^[ \t]+|[ \t]+$/, "", $2)
            if ($1 == "") { next }
            if ($2 != "IGNORE" && $2 != "WARN" && $2 != "FAIL") {
                printf "Unknown level \"%s\" for rule %s\n", $2, $1 > "/dev/stderr"; bad = 1; next
            }
            printf "%s\t%s\n", $1, $2
        }
        END { if (bad) exit 1 }
    ' "$file" | jq -R -s -e 'split("\n") | map(select(length > 0) | split("\t")) | map({key: .[0], value: .[1]}) | from_entries' \
        || { sd_fail "Could not read the rule levels from $file"; return 1; }
}

# A `<key>: [ ... ]` list out of a YAML file, as JSON. A missing file is an empty list: neither
# suppressions nor mitigations are mandatory for a scan to be countable.
sd_yaml_list() {
    local file="$1" key="$2"
    [ -f "$file" ] || { echo '[]'; return 0; }
    yq -o=json -I=0 ".$key // []" "$file" \
        || { sd_fail "Could not read .$key from $file"; return 1; }
}

# The verdict: levels applied, suppressions applied, counts taken. JSON on stdout, and the shape
# the markdown renderer below consumes.
sd_verdict() {
    local findings="$1" rules="$2" suppressions="$3" mitigations="$4" meta="$5"
    jq -n -e \
        --argjson findings "$findings" \
        --argjson rules "$rules" \
        --argjson suppressions "$suppressions" \
        --argjson mitigations "$mitigations" \
        --argjson meta "$meta" '
        def anchored($p): "^(?:" + ($p | tostring) + ")$";
        def rx($p; $v): (($v // "") | tostring) | test(anchored($p));

        # An entry matches a finding when every field it declares matches. `url` is about an
        # instance, not a finding, and is handled separately.
        def fmatch($e; $f):
            (($e.scanner // null) == null or rx($e.scanner; $f.scanner))
            and (($e.rule // null) == null or rx($e.rule; $f.rule))
            and (($e.name // null) == null or rx($e.name; $f.name));

        # A suppression with neither a statement nor an end date is how a finding disappears for
        # good by accident. Both are required, and the run stops rather than counting wrongly.
        ([$suppressions[] | select(((.statement // "") | length) == 0 or ((.expired_at // "") | length) == 0)]) as $invalid
        | if ($invalid | length) > 0 then
            error("suppression \"\(($invalid[0].id // $invalid[0].rule // $invalid[0].name // "?"))\" needs both a statement and an expired_at")
          else . end

        | [ $findings[]
            | . as $f
            | ($rules[$f.rule] // "") as $lv
            | select($lv != "IGNORE")
            | .override = (if $lv == "" then null else $lv end)
            | .risk = (if $lv == "FAIL" then "HIGH" elif $lv == "WARN" then "LOW" else .risk end)
            # An instance-less finding still has to be countable.
            | .instances = (if (.instances | length) == 0 then [{uri: "", method: "", param: "", evidence: "", attack: "", info: ""}] else .instances end)
          ] as $levelled

        | [ $suppressions[] | select(.expired_at >= $meta.date) ] as $active_sup
        | [ $suppressions[] | select(.expired_at < $meta.date) ] as $expired_sup

        | ( $levelled | map(
              . as $f
              | ([$active_sup[] | . as $s | select(fmatch($s; $f))]) as $cand
              | ($f.instances | map(
                    . as $i
                    | . + {suppressed_by: [ $cand[] | . as $s
                          | select((($s.url // null) == null) or (($i.uri // "") | test(anchored($s.url))))
                          | ($s.id // $s.rule // $s.name // "suppression") ]}
                )) as $marked
              | $f + {
                  instances: $marked,
                  kept: [ $marked[] | select((.suppressed_by | length) == 0) ],
                  suppressed: [ $marked[] | select((.suppressed_by | length) > 0) ],
                  mitigation: ([ $mitigations[] | . as $m | select(fmatch($m; $f)) ] | .[0] // null)
                }
              | . + {counted: ((.kept | length) > 0 and .risk != "INFO")}
            )) as $scored

        | ($scored | map(select(.counted))) as $counted
        | {
            meta: $meta,
            counts: {
              CRITICAL: ($counted | map(select(.risk == "CRITICAL")) | length),
              HIGH: ($counted | map(select(.risk == "HIGH")) | length),
              MEDIUM: ($counted | map(select(.risk == "MEDIUM")) | length),
              LOW: ($counted | map(select(.risk == "LOW")) | length)
            },
            totals: {
              findings: ($scored | length),
              counted: ($counted | length),
              informational: ($scored | map(select(.risk == "INFO")) | length),
              suppressed: ($scored | map(select((.counted | not) and ((.suppressed | length) > 0))) | length),
              instances: ($scored | map(.instances | length) | add // 0)
            },
            findings: ($scored | sort_by(
                ({"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3, "INFO": 4}[.risk] // 5),
                .name)),
            suppressions_applied: [
              $active_sup[] | . as $s | ($s.id // $s.rule // $s.name // "suppression") as $id
              | ([ $scored[] | .instances[] | select(.suppressed_by | index($id)) ] | length) as $n
              | select($n > 0) | {id: $id, statement: $s.statement, expired_at: $s.expired_at, instances: $n}
            ],
            suppressions_unused: [
              $active_sup[] | . as $s | ($s.id // $s.rule // $s.name // "suppression") as $id
              | ([ $scored[] | .instances[] | select(.suppressed_by | index($id)) ] | length) as $n
              | select($n == 0) | {id: $id, statement: $s.statement, expired_at: $s.expired_at}
            ],
            suppressions_expired: [
              $expired_sup[] | {id: (.id // .rule // .name // "suppression"), statement: .statement, expired_at: .expired_at}
            ],
            overrides: [ $scored[] | select(.override != null) | {rule: .rule, name: .name, level: .override, risk: .risk} ],
            # The per-tool split. For the private report only: the stamp and the public log carry the
            # totals, and which tool found what is detail.
            by_scanner: [ ($meta.tools // [])[] | . as $t
              | ($counted | map(select(.scanner == $t.scanner))) as $c
              | {
                  scanner: $t.scanner,
                  version: $t.version,
                  reported: ($scored | map(select(.scanner == $t.scanner)) | length),
                  counts: {
                    CRITICAL: ($c | map(select(.risk == "CRITICAL")) | length),
                    HIGH: ($c | map(select(.risk == "HIGH")) | length),
                    MEDIUM: ($c | map(select(.risk == "MEDIUM")) | length),
                    LOW: ($c | map(select(.risk == "LOW")) | length)
                  }
                }
            ]
          }
    ' || { sd_fail "Could not apply the rule levels and suppressions"; return 1; }
}

# The markdown report, from the verdict. Presentation only: every decision was taken above.
#
# What it must carry is set out in the README of yontrack/security-reports - someone opening the
# file months later, with no other context, has to be able to tell what was scanned, how bad it
# was and what to do about it. Grouped by rule, not by URL.
sd_render() {
    jq -r '
        def md: (. // "") | tostring | gsub("\\|"; "\\\\|") | gsub("[\r\n]+"; " ") | gsub("[ \t]+"; " ");
        def blockquote: (. // "") | tostring | split("\n") | map("> " + .) | join("\n");
        def n($x): ($x // 0) | tostring;
        def duration:
            if . == null or . == 0 then "unknown"
            else ((. / 60 | floor) | tostring) + "m " + ((. % 60) | tostring) + "s" end;

        .meta as $m |
        [
          "# \($m.kind | ascii_upcase) DAST scan — \($m.version)",
          "",
          "| | |",
          "|---|---|",
          "| Workflow run | \($m.run_url) |",
          "| Scan | `\($m.kind)` — \($m.scanners) |",
          "| Yontrack version | `\($m.version)` (build `\($m.build)`) |",
          "| Target | `\($m.target)` |",
          "| Date (UTC) | \($m.date) |",
          "| Duration | \($m.duration | duration) |",
          "",
          "## Counts after suppressions",
          "",
          "| CRITICAL | HIGH | MEDIUM | LOW |",
          "|---|---|---|---|",
          "| \(.counts.CRITICAL) | \(.counts.HIGH) | \(.counts.MEDIUM) | \(.counts.LOW) |",
          "",
          "These are the numbers the `SECURITY.DAST\(if $m.kind == "active" then ".ACTIVE" else "" end)` stamp on build `\($m.build)` records, and the only numbers the public workflow log prints.",
          "",
          "Counted **per rule**, not per affected URL: \(.totals.findings) rule(s) reported, \(.totals.counted) counted, \(.totals.informational) informational (dropped), \(.totals.instances) instance(s) in total.",
          "",
          "## Counts by scanner",
          "",
          "| Scanner | Version | CRITICAL | HIGH | MEDIUM | LOW |",
          "|---|---|---|---|---|---|"
        ]
        + [ .by_scanner[] | "| `\(.scanner)` | \(.version | md) | \(.counts.CRITICAL) | \(.counts.HIGH) | \(.counts.MEDIUM) | \(.counts.LOW) |" ]
        + [""]
        + (if (.findings | length) == 0 then ["Nothing reported by any scanner. That is unusual rather than reassuring — check that the scan actually reached the target.", ""] else [] end)
        # Grouped by tool, then by risk and rule within a tool: three tools reporting the same
        # weakness under three names read as three findings otherwise, and a reader comparing two
        # reports compares one tool at a time.
        + ( .findings as $all | [ .by_scanner[] | .scanner as $sc
            | ($all | map(select(.scanner == $sc))) as $mine
            | "## Findings — `\($sc)`",
              "",
              (if ($mine | length) == 0 then "Nothing reported by this scanner.\n" else empty end),
            ( $mine[] |
              (
                "### \(.risk) — \(.name)",
                "",
                "`\(.scanner)` rule `\(.rule)`\(if .ref != .rule then " (`\(.ref)`)" else "" end)\(if .confidence == "Unknown" then "" else ", confidence \(.confidence)" end)\(if .cwe != "" then ", CWE-\(.cwe)" else "" end)\(if .counted then "" else " — **not counted**\(if (.kept | length) == 0 then " (every instance suppressed)" else " (informational)" end)" end)\(if .override != null then " — level forced to `\(.override)` by `rules.tsv`" else "" end)",
                "",
                (.description | blockquote),
                "",
                "**Affected** — \(.kept | length) instance(s) counted, \(.suppressed | length) suppressed:",
                "",
                "| URL | Method | Parameter | Evidence |",
                "|---|---|---|---|",
                ( [ (.kept + .suppressed)[:20][] |
                    "| `\(.uri | md)` | \(.method | md) | \(if .param == "" then "—" else "`\(.param | md)`" end) | \(if .evidence == "" then "—" else "`\(.evidence | md)`" end) |" ] | join("\n") ),
                (if ((.kept | length) + (.suppressed | length)) > 20 then "\n… and \(((.kept | length) + (.suppressed | length)) - 20) more instance(s)." else "" end),
                "",
                (if .mitigation == null then
                   "**Mitigation** — *no entry in `security/dast/mitigations.yaml` for this rule.* Add one: that file is what makes this report readable by someone who did not run the scan."
                 else
                   "**Mitigation** — \(.mitigation.title // .mitigation.id)\(if (.mitigation.issue // "") != "" then " (\(.mitigation.issue))" else "" end)\n\n\(.mitigation.change)"
                 end),
                "",
                (if .solution == "" then "" else "<details><summary>What the scanner suggests</summary>\n\n\(.solution)\n\n</details>" end),
                "",
                "---",
                ""
              )
            )
          ] )
        + ["## Suppressions", ""]
        + (if (.suppressions_applied | length) == 0 then ["No suppression applied to this run."] else
            [ "| Suppression | Instances subtracted | Expires | Statement |", "|---|---|---|---|" ]
            + [ .suppressions_applied[] | "| `\(.id)` | \(.instances) | \(.expired_at) | \(.statement | md) |" ]
          end)
        + [""]
        + (if (.suppressions_unused | length) == 0 then [] else
            ["Declared and matched nothing this run: " + ([.suppressions_unused[] | "`\(.id)`"] | join(", ")) + ". Worth checking at the next review whether the finding is gone or the entry is wrong.", ""]
          end)
        + (if (.suppressions_expired | length) == 0 then [] else
            ["**Expired, and therefore not applied** — the findings they used to cover are back in the counts above:", ""]
            + [ "| Suppression | Expired on | Statement |", "|---|---|---|" ]
            + [ .suppressions_expired[] | "| `\(.id)` | \(.expired_at) | \(.statement | md) |" ]
            + [""]
          end)
        + (if (.overrides | length) == 0 then [] else
            ["## Rule level overrides", "", "| Rule | Level | Counted as |", "|---|---|---|"]
            + [ .overrides[] | "| `\(.rule)` \(.name | md) | \(.level) | \(.risk) |" ]
            + [""]
          end)
        + [
          "## Reading this report",
          "",
          "Compare it with the previous report of the same scan kind: the new rules are the news. For each new rule, read its mitigation; if it is worth fixing, open an issue in `yontrack/yontrack` **describing the change, not the exploit**, and link it to the workflow run rather than to this file. If it is not, add a suppression with a statement and an expiry date in `security/dast/suppressions.yaml`, so that it is a decision rather than noise everyone learns to scroll past.",
          "",
          "Neither the scan nor the stamp blocks anything. `SECURITY.DAST` is in no promotion."
        ]
        | join("\n")
    '
}

sd_report() {
    local out="${1:-}" findings tools rules suppressions mitigations meta verdict duration=0
    [ -n "$out" ] || { sd_fail "Usage: $0 report OUT_MD FILE..."; return 1; }
    shift
    [ $# -gt 0 ] || { sd_fail "No normalised findings document given."; return 1; }

    local file
    for file in "$@"; do
        [ -f "$file" ] || { sd_fail "No findings document at $file"; return 1; }
    done

    findings="$(jq -e -s -c '[.[] | .findings[]?]' "$@")" \
        || { sd_fail "Could not read the findings documents"; return 1; }
    # Which tools ran, at which version, in the order they were given: one line each in the report,
    # a tool with nothing to report included. The report is grouped by these, so a finding whose
    # scanner no document declares still gets a line - it must never fall out of the report.
    tools="$(jq -e -s -c '
        map({scanner: (.scanner // "unknown"), version: ((.version // "unknown") | tostring)})
        + [ .[] | .findings[]? | {scanner: (.scanner // "unknown"), version: "unknown"} ]
        | reduce .[] as $t ([]; if any(.[]; .scanner == $t.scanner) then . else . + [$t] end)' "$@")" \
        || { sd_fail "Could not read the findings documents"; return 1; }
    rules="$(sd_rules_json "$SD_RULES")" || return 1
    suppressions="$(sd_yaml_list "$SD_SUPPRESSIONS" suppressions)" || return 1
    mitigations="$(sd_yaml_list "$SD_MITIGATIONS" mitigations)" || return 1

    if [ -n "${DAST_STARTED:-}" ]; then
        duration=$(( $(date +%s) - DAST_STARTED ))
    fi

    meta="$(jq -n -c \
        --arg kind "$SD_KIND" \
        --arg date "$SD_DATE" \
        --arg target "${DAST_TARGET:-unknown}" \
        --arg version "${DAST_VERSION:-unknown}" \
        --arg build "${DAST_BUILD:-unknown}" \
        --arg run_url "${DAST_RUN_URL:-unknown}" \
        --arg scanners "${DAST_SCANNERS:-unknown}" \
        --argjson duration "$duration" \
        --argjson tools "$tools" \
        '$ARGS.named')" || return 1

    verdict="$(sd_verdict "$findings" "$rules" "$suppressions" "$mitigations" "$meta")" || return 1

    mkdir -p "$(dirname "$out")" || return 1
    # The token is redacted from the report too, not only from the logs. It is never put into one
    # - but the report is the file that leaves this runner, and a belt is cheap.
    printf '%s\n' "$verdict" | sd_render \
        | { if [ -n "${DEMO_TOKEN:-}" ]; then sed "s#$(printf '%s' "$DEMO_TOKEN" | sed 's/[&/\]/\\&/g')#<redacted>#g"; else cat; fi; } > "$out" \
        || { sd_fail "Could not write the report to $out"; return 1; }

    local counts
    counts="$(printf '%s\n' "$verdict" | jq -r '
        "critical=\(.counts.CRITICAL)", "high=\(.counts.HIGH)",
        "medium=\(.counts.MEDIUM)", "low=\(.counts.LOW)",
        "findings=\(.totals.counted)", "suppressed=\((.suppressions_applied | map(.instances) | add) // 0)"')" \
        || return 1

    # Counts, and nothing else. Every other number in this script's output is a count too.
    echo "$counts"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "$counts" >> "$GITHUB_OUTPUT"
    fi
    return 0
}

# ---------------------------------------------------------------------------------------------
# Publication to the private repository
# ---------------------------------------------------------------------------------------------

# dast/<kind>/<yyyy>/<yyyy-mm-dd>-<version>-<run-id>.md, the layout the README of
# yontrack/security-reports sets out. The run id is what makes two scans of the same version on
# the same day two files rather than one overwritten one.
sd_report_path() {
    local year="${SD_DATE%%-*}"
    printf 'dast/%s/%s/%s-%s-%s.md\n' \
        "$SD_KIND" "$year" "$SD_DATE" "${DAST_VERSION:-unknown}" "${DAST_RUN_ID:-0}"
}

# One file per run, and a file is never rewritten - so this creates and never updates. `gh api PUT
# contents` without a `sha` fails on an existing path, which is the behaviour wanted: a collision
# means two runs believe they are the same run, and quietly overwriting the first report would
# destroy the only copy of it.
sd_publish() {
    local repo="${1:-}" path="${2:-}" file="${3:-}" content
    [ -n "$repo" ] && [ -n "$path" ] && [ -n "$file" ] \
        || { sd_fail "Usage: $0 publish REPO PATH FILE"; return 1; }
    [ -f "$file" ] || { sd_fail "No report at $file"; return 1; }

    content="$(base64 < "$file" | tr -d '\n')" || { sd_fail "Could not encode $file"; return 1; }

    # The message names the run, not the findings: the commit log of the private repository is
    # itself a listing, and a subject carrying a count would leak one into every notification.
    gh api --method PUT "repos/$repo/contents/$path" \
        -f "message=$SD_KIND DAST report for ${DAST_VERSION:-unknown} (run ${DAST_RUN_ID:-0})" \
        -f "content=$content" > /dev/null \
        || { sd_fail "Could not publish the report to $repo. The path may already exist, which means two runs share an id."; return 1; }

    sd_log "Report published to $repo at $path."
    return 0
}

sd_main() {
    local command="${1:-}"
    [ $# -gt 0 ] && shift
    case "$command" in
        query-schema) sd_query_schema "$@" ;;
        render-plan) sd_render_plan "$@" ;;
        actuator) sd_actuator "$@" ;;
        normalize-zap) sd_normalize_zap "$@" ;;
        assert-no-mutations) sd_assert_no_mutations "$@" ;;
        fetch) sd_fetch "$@" ;;
        graphql-cop-preflight) sd_graphql_cop_preflight "$@" ;;
        render-graphql-cop-headers) sd_render_graphql_cop_headers "$@" ;;
        assert-graphql-cop-no-mutations) sd_assert_graphql_cop_no_mutations "$@" ;;
        normalize-graphql-cop) sd_normalize_graphql_cop "$@" ;;
        nuclei-preflight) sd_nuclei_preflight "$@" ;;
        nuclei-summary) sd_nuclei_summary "$@" ;;
        normalize-nuclei) sd_normalize_nuclei "$@" ;;
        report) sd_report "$@" ;;
        report-path) sd_report_path ;;
        publish) sd_publish "$@" ;;
        scrub) sd_scrub ;;
        *)
            echo "Usage: $0 query-schema|render-plan|actuator|normalize-zap|assert-no-mutations|fetch|graphql-cop-preflight|render-graphql-cop-headers|assert-graphql-cop-no-mutations|normalize-graphql-cop|nuclei-preflight|nuclei-summary|normalize-nuclei|report|report-path|publish|scrub ..." >&2
            return 1
            ;;
    esac
}

# Sourced by the test suite, which wants the functions and nothing else.
if [ -z "${SECURITY_DAST_LIB_ONLY:-}" ]; then
    sd_main "$@"
    exit $?
fi
