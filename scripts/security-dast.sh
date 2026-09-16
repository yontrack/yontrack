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
#   sanitize-sarif IN OUT     Strips the full request and response of every result out of a ZAP
#                             SARIF report - they carry the API token - before it is uploaded to
#                             code scanning.
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
# rather than a rewrite. `normalize-zap` produces it; #1766 adds `normalize-graphql-cop` and
# `normalize-nuclei` beside it, and everything downstream - levels, suppressions, counting, the
# report, the disclosure rules - is already written.
#
#     {
#       "scanner": "zap",
#       "version": "2.16.1",
#       "findings": [
#         {
#           "scanner":     "zap",
#           "rule":        "10038",        // the tool's own rule id, matched by rules.tsv
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
# needs `gh` authenticated as the private reports token, and `actuator` needs curl.

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
# sanitize-sarif
# ---------------------------------------------------------------------------------------------

# ZAP's `sarif-json` template embeds the whole exchange under `webRequest` and `webResponse`:
# every request header of every result, and for a result on /graphql that includes
# `X-Ontrack-Token` with the live API token in it. Uploading that to code scanning would put a
# working admin credential for the demo into GitHub's alert store - where it would sit until
# somebody noticed, and where the report's own disclosure rules have no say.
#
# So the exchange goes and the finding stays: rule, level, message, and the URL it was found on,
# which is the whole point of uploading a DAST SARIF at all. The token is redacted from what is
# left as well, in case a future ZAP version puts it somewhere else.
sd_sanitize_sarif() {
    local input="${1:-}" output="${2:-}"
    [ -n "$input" ] && [ -n "$output" ] || { sd_fail "Usage: $0 sanitize-sarif IN OUT"; return 1; }
    [ -f "$input" ] || { sd_fail "No SARIF report at $input"; return 1; }

    jq -e '
        .runs = [ (.runs // [])[] | .results = [ (.results // [])[] | del(.webRequest, .webResponse) ] ]
    ' "$input" \
        | { if [ -n "${DEMO_TOKEN:-}" ]; then sed "s#$(printf '%s' "$DEMO_TOKEN" | sed 's/[&/\]/\\&/g')#<redacted>#g"; else cat; fi; } \
        > "$output" \
        || { sd_fail "Could not sanitize $input"; return 1; }

    if [ -n "${DEMO_TOKEN:-}" ] && grep -qF "$DEMO_TOKEN" "$output"; then
        rm -f "$output"
        sd_fail "The sanitized SARIF still carries the API token: refusing to upload it."
        return 1
    fi
    sd_log "SARIF sanitized: $(jq -r '[.runs[].results[]] | length' "$output") result(s), no request or response bodies."
    return 0
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
            overrides: [ $scored[] | select(.override != null) | {rule: .rule, name: .name, level: .override, risk: .risk} ]
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
          "## Findings",
          ""
        ]
        + (if (.findings | length) == 0 then ["Nothing reported. That is unusual rather than reassuring — check that the scan actually reached the target."] else
            [ .findings[] |
              (
                "### \(.risk) — \(.name)",
                "",
                "`\(.scanner)` rule `\(.rule)`\(if .ref != .rule then " (`\(.ref)`)" else "" end), confidence \(.confidence)\(if .cwe != "" then ", CWE-\(.cwe)" else "" end)\(if .counted then "" else " — **not counted**\(if (.kept | length) == 0 then " (every instance suppressed)" else " (informational)" end)" end)\(if .override != null then " — level forced to `\(.override)` by `rules.tsv`" else "" end)",
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
            ] end)
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
    local out="${1:-}" findings rules suppressions mitigations meta verdict duration=0
    [ -n "$out" ] || { sd_fail "Usage: $0 report OUT_MD FILE..."; return 1; }
    shift
    [ $# -gt 0 ] || { sd_fail "No normalised findings document given."; return 1; }

    local file
    for file in "$@"; do
        [ -f "$file" ] || { sd_fail "No findings document at $file"; return 1; }
    done

    findings="$(jq -e -s -c '[.[] | .findings[]?]' "$@")" \
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
        sanitize-sarif) sd_sanitize_sarif "$@" ;;
        report) sd_report "$@" ;;
        report-path) sd_report_path ;;
        publish) sd_publish "$@" ;;
        scrub) sd_scrub ;;
        *)
            echo "Usage: $0 query-schema|render-plan|actuator|normalize-zap|assert-no-mutations|sanitize-sarif|report|report-path|publish|scrub ..." >&2
            return 1
            ;;
    esac
}

# Sourced by the test suite, which wants the functions and nothing else.
if [ -z "${SECURITY_DAST_LIB_ONLY:-}" ]; then
    sd_main "$@"
    exit $?
fi
