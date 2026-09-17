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
#                             This is what keeps mutations out of the passive scan - see
#                             security/dast/graphql/README.md.
#   active-schema IN OUT      The ACTIVE scan's counterpart (#1767): keeps the `Mutation` root -
#                             the active scan sends mutations on purpose - but strips the fields a
#                             scanner must never fire, whatever role it runs as: token revocation
#                             (`revokeToken`, `revokeAllTokens`, `revokeAccountTokens`) and every
#                             account/group/role/CasC/settings mutation, so ZAP cannot delete the
#                             `scan-*` accounts, revoke its own token, or turn `grantProjectViewToAll`
#                             back on mid-run. Refuses to write a file that lost its Mutation root or
#                             that still declares a denied field. Denylist: $DAST_ACTIVE_MUTATION_DENYLIST.
#   access-control DIR OUT_DIR
#                             The authorization checks of the active scan (#1767) - the ones that
#                             would have caught #1739/#1741/#1742. Sends a small, curated set of
#                             privileged requests as `scan-readonly` and `scan-project`, each of
#                             which the role MUST be refused, and records an escalation when one
#                             succeeds: `scan-readonly` creating a project, `scan-project` reading or
#                             writing a project it does not hold. Writes one raw result document per
#                             role to OUT_DIR (never printed - a success would carry data), and prints
#                             the escalation counts per role and kind. Returns non-zero only when a
#                             probe could not be sent at all; an escalation is a finding, reported by
#                             the stamp, not a run error.
#   normalize-access-control RAW [ROLE]
#                             Reads an `access-control` raw result document and writes the normalised
#                             findings document to stdout: one HIGH finding per escalation, none for a
#                             check that passed. A passing authorization check is not a finding.
#   login ROLE DIR [BUDGET]   Logs scanner ROLE (scan-admin, scan-readonly, scan-project) in by a
#                             Keycloak password grant and writes its access token to DIR/ROLE.token.
#                             Fails - naming the account and Keycloak's error class - when it
#                             cannot, or when the token would expire within BUDGET seconds (#1769).
#   whoami ROLE DIR           Fails unless the API accepts ROLE's token and maps it to the group
#                             security/dast/casc.yaml gives it. Prints counts; writes `version`.
#   render-plan IN OUT ROLE   Copies the ZAP plan, substituting `${DAST_BEARER_TOKEN}` with ROLE's
#                             token. The Automation Framework expands `${...}` in job parameters but
#                             not inside a replacer rule, so the one place the token is needed is
#                             the one place ZAP will not fill in.
#   assert-authenticated HAR  Fails if any request of an API pass to /graphql was answered 401: the
#                             pass outlived its token. Prints counts only.
#   actuator BASE_URL         Probes for a reachable Spring Boot management port. Exit 0 when it
#                             is not reachable, 1 when it is - which is an incident, not a
#                             finding.
#   normalize-zap FILE [ROLE] Reads a ZAP `traditional-json` report and writes the normalised
#                             findings document (below) to stdout, naming the role of the pass.
#   assert-no-mutations HAR   Reads the HAR of every request ZAP sent and fails if any of them was
#                             a GraphQL mutation or subscription. Prints counts only.
#   fetch URL SHA256 OUT      Downloads URL to OUT and refuses it unless its SHA-256 is SHA256.
#                             How every scanner that is not an image is pinned (#1766).
#   graphql-cop-preflight SRC EXCLUDED
#                             Before graphql-cop sends anything: fails unless every test registered
#                             in SRC that mentions a mutation is in EXCLUDED (comma-separated), and
#                             every name in EXCLUDED is a registered test.
#   render-graphql-cop-headers OUT ROLE
#                             Writes ROLE's bearer token header as JSON to OUT, for the run-time
#                             graphql-cop configuration to read. Never on a command line.
#   assert-graphql-cop-no-mutations OUT
#                             Reads graphql-cop's own record of the request each test sent and fails
#                             if any was a mutation or subscription. Prints counts only.
#   normalize-graphql-cop OUT VERSION [ROLE]
#                             Reads graphql-cop's `-o json` stdout and writes the normalised
#                             findings document to stdout, naming the role of the pass.
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
#   report OUT_MD FILE...     De-duplicates one or more normalised findings documents by rule and
#                             URL, attributing each finding to the roles that saw it, applies the
#                             rule levels and the suppressions, writes the markdown report to OUT_MD,
#                             and prints the counts. With $GITHUB_OUTPUT set, writes `critical`,
#                             `high`, `medium`, `low`, `findings` and `suppressed` to it.
#   report-path               Prints the path the report takes in yontrack/security-reports.
#   publish REPO PATH FILE    Creates PATH in REPO with the contents of FILE. Never updates: one
#                             file per run, never rewritten.
#   scrub                     Copies stdin to stdout with URLs and every token redacted. Every
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
#   DAST_TOKEN_DIR     where `login` wrote the scanner roles' tokens; every one of them is redacted
#                      wherever it appears, in the report and in scrubbed output - and so is
#                      DEMO_TOKEN, should anything still set it
#   DAST_CASC          the scanner roles' CasC (default: security/dast/casc.yaml) (whoami)
#   DAST_KEYCLOAK_REALM_URL (default: $DAST_TARGET/keycloak/realms/ontrack), DAST_KEYCLOAK_CLIENT_ID,
#   DEMO_KEYCLOAK_CLIENT_SECRET,
#   DAST_SCAN_{ADMIN,READONLY,PROJECT}_PASSWORD
#                      the demo realm, its client, and each role's password (login)
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
#       "role": "scan-readonly",           // the scanner role of the pass; absent when unauthenticated
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
# `report` adds a `roles` list to every finding and every instance when it merges the documents.
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
# The tokens first, and unconditionally: a redaction that only runs when something looks like a URL
# is a redaction that misses the one line that mattered.
#
# Every token this run holds, not one (#1769): each scanner role's bearer token, read from the
# `<role>.token` files `login` wrote into $DAST_TOKEN_DIR, and $DEMO_TOKEN when something still sets
# it. Literally, with awk's index/substr: a token is data, and a regex or a sed replacement built
# from it would mangle a `&`, a `/` or a backslash - and then quietly not match.
sd_redact_tokens() {
    local dir="${DAST_TOKEN_DIR:-}" file
    local files=()
    if [ -n "$dir" ] && [ -d "$dir" ]; then
        for file in "$dir"/*.token; do
            [ -f "$file" ] && files+=("$file")
        done
    fi
    # The file names are awk's arguments, emptied once read, so that awk then reads stdin.
    awk '
        BEGIN {
            n = 0
            if (ENVIRON["DEMO_TOKEN"] != "") { tok[++n] = ENVIRON["DEMO_TOKEN"] }
            for (a = 1; a < ARGC; a++) {
                while ((getline line < ARGV[a]) > 0) { if (line != "") { tok[++n] = line } }
                close(ARGV[a])
                ARGV[a] = ""
            }
        }
        {
            for (k = 1; k <= n; k++) {
                rest = $0; done = ""; len = length(tok[k])
                while ((i = index(rest, tok[k])) > 0) {
                    done = done substr(rest, 1, i - 1) "<redacted>"
                    rest = substr(rest, i + len)
                }
                $0 = done rest
            }
            print
        }
    ' ${files[@]+"${files[@]}"}
}

sd_scrub() {
    # Then the shapes, for a token no file knows about: anything that looks like a JWT, and whatever
    # follows a token header's name - `Authorization: Bearer x` included.
    sd_redact_tokens | sed -E \
        -e 's#eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*#<redacted>#g' \
        -e 's#https?://[^[:space:]"'"'"'<>]+#<url>#g' \
        -e 's#(X-Ontrack-Token|Authorization)"?[:= ]+(Bearer[[:space:]]+)?[^[:space:]"'"'"',}]+#\1: <redacted>#gI'
}

# The file holding a scanner role's bearer token, as `login` wrote it. Fails, naming the role, when
# there is none: a pass for a role that did not log in must not run.
sd_token_file() {
    local role="${1:-}" file
    [ -n "$role" ] || { sd_fail "No scanner role given."; return 1; }
    file="${DAST_TOKEN_DIR:-}/$role.token"
    if [ -z "${DAST_TOKEN_DIR:-}" ] || [ ! -s "$file" ]; then
        sd_fail "No token for $role: it has not logged in. Refusing to scan unauthenticated."
        return 1
    fi
    printf '%s\n' "$file"
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
# the pinned image: the header went out with the literal placeholder as its value.
#
# So the one value ZAP will not fill in is the one that is a secret, and it is written into a copy
# of the plan instead: `${DAST_BEARER_TOKEN}` becomes the bearer token of ROLE, read from the file
# `login` wrote (#1769) - never from an argument, never from the environment. That copy lives in
# $RUNNER_TEMP, is never uploaded - this workflow uploads nothing - and dies with the runner. It is
# left world-readable because the ZAP container runs as its own uid and has to read it; on an
# ephemeral runner that is a narrower exposure than the alternatives (the token on the `docker run`
# command line, where any process could read it off /proc, or in a plan committed to a public
# repository, which is not an alternative at all).
sd_render_plan() {
    local input="${1:-}" output="${2:-}" role="${3:-}" token_file
    [ -n "$input" ] && [ -n "$output" ] && [ -n "$role" ] \
        || { sd_fail "Usage: $0 render-plan IN OUT ROLE"; return 1; }
    [ -f "$input" ] || { sd_fail "No ZAP plan at $input"; return 1; }
    token_file="$(sd_token_file "$role")" || return 1

    # Literal index/substr rather than gsub, and the token read from its file rather than passed
    # with -v: a regex replacement would mangle a `&` or a backslash, and awk expands escape
    # sequences in a -v assignment - both turn a good token into a wrong one, which reads as an
    # expired one.
    awk -v token_file="$token_file" '
        BEGIN { getline tok < token_file; close(token_file); needle = "${DAST_BEARER_TOKEN}"; n = length(needle) }
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
        3) rm -f "$output"; sd_fail "No \${DAST_BEARER_TOKEN} placeholder in $input: the API would be scanned unauthenticated."; return 1 ;;
        *) rm -f "$output"; sd_fail "Could not render $input"; return 1 ;;
    esac

    chmod 644 "$output" || return 1
    sd_log "ZAP plan rendered for $role: its bearer token is in place."
    return 0
}

# ---------------------------------------------------------------------------------------------
# The scanner roles (#1769) - login, whoami, assert-authenticated
# ---------------------------------------------------------------------------------------------

# The three accounts of security/dast/casc.yaml, and nothing else: a typo in the workflow must not
# turn into a login attempt as some other user of the demo.
SD_ROLES="scan-admin scan-readonly scan-project"
SD_CASC="${DAST_CASC:-$SD_ROOT/security/dast/casc.yaml}"

sd_known_role() {
    case " $SD_ROLES " in
        *" ${1:-} "*) return 0 ;;
        *) sd_fail "\"${1:-}\" is not one of the scanner roles ($SD_ROLES)."; return 1 ;;
    esac
}

# A Keycloak password grant on the demo realm, as ROLE: `scan-readonly` logs in with
# $DAST_SCAN_READONLY_PASSWORD - the name of the Actions secret it comes from - through the client
# $DAST_KEYCLOAK_CLIENT_ID and its secret $DEMO_KEYCLOAK_CLIENT_SECRET.
#
# The access token goes to DIR/ROLE.token, readable by the runner's user only, and nowhere else;
# every later step reads it from there, and `scrub` and `report` redact whatever is in it. In Actions
# it is also registered as a mask, so the runner blanks it from the log should anything print it.
#
# The password and the client secret travel on curl's stdin, form-encoded: never on its command line.
#
# A pass must not outlive its token. The workflow logs in again right before every pass, and gives
# the pass's time budget as BUDGET: a token that expires sooner is refused here rather than
# discovered half-way through as a run of 401s - which `assert-authenticated` would catch anyway.
#
# Any failure stops the run. A scanner account that cannot log in is a credential or demo
# configuration problem, and a scan that went on unauthenticated would report a clean API.
sd_login() {
    local role="${1:-}" dir="${2:-}" budget="${3:-0}" secret realm tmp status token expires error
    [ -n "$role" ] && [ -n "$dir" ] || { sd_fail "Usage: $0 login ROLE DIR [BUDGET_SECONDS]"; return 1; }
    sd_known_role "$role" || return 1
    secret="DAST_$(printf '%s' "$role" | tr 'a-z-' 'A-Z_')_PASSWORD"
    [ -n "${!secret:-}" ] || { sd_fail "$secret is not set: $role cannot log in, and the scan does not go on unauthenticated."; return 1; }
    [ -n "${DEMO_KEYCLOAK_CLIENT_SECRET:-}" ] || { sd_fail "DEMO_KEYCLOAK_CLIENT_SECRET is not set: no scanner account can log in."; return 1; }
    [ -n "${DAST_KEYCLOAK_CLIENT_ID:-}" ] || { sd_fail "DAST_KEYCLOAK_CLIENT_ID is not set."; return 1; }
    # Derived from the target unless given: the chart serves Keycloak under /keycloak and names the
    # realm `ontrack`. Not a workflow variable, which every step would print in its environment.
    realm="${DAST_KEYCLOAK_REALM_URL:-${DAST_TARGET:-}/keycloak/realms/ontrack}"
    realm="${realm%/}"

    mkdir -p "$dir" || return 1
    rm -f "$dir/$role.token" "$dir/$role.expires"
    tmp="$(mktemp -d "${TMPDIR:-/tmp}/dast-login.XXXXXX")" || return 1
    chmod 700 "$tmp"

    # jq reads the secrets out of its environment by name and form-encodes them; the pipe hands the
    # result to curl without it ever being an argument.
    status="$(jq -r -n --arg secret "$secret" --arg user "$role" '
            "grant_type=password&scope=openid"
            + "&client_id=" + (env.DAST_KEYCLOAK_CLIENT_ID | @uri)
            + "&client_secret=" + (env.DEMO_KEYCLOAK_CLIENT_SECRET | @uri)
            + "&username=" + ($user | @uri)
            + "&password=" + (env[$secret] | @uri)' \
        | tr -d '\n' \
        | curl -sS --max-time 30 -o "$tmp/body" -w '%{http_code}' -X POST \
            -H 'Content-Type: application/x-www-form-urlencoded' \
            --data-binary @- "$realm/protocol/openid-connect/token" 2> "$tmp/err")" || status="000"

    if [ "$status" != "200" ]; then
        # Keycloak's error class and its one-line description - "invalid_grant (Invalid user
        # credentials)", "unauthorized_client (Invalid client credentials)" - which name the problem
        # and carry nothing secret. Scrubbed all the same.
        error="$(jq -r '[.error // empty, (.error_description // empty | "(\(.))")] | join(" ")' "$tmp/body" 2> /dev/null)"
        [ -n "$error" ] || error="$(head -c 200 "$tmp/err" 2> /dev/null)"
        rm -rf "$tmp"
        sd_fail "$role could not log in: Keycloak answered $status $(printf '%s' "${error:-with no error}" | sd_scrub). Check the account and its password secret; the scan does not go on unauthenticated."
        return 1
    fi

    token="$(jq -r '.access_token // empty' "$tmp/body" 2> /dev/null)"
    expires="$(jq -r '.expires_in // 0 | floor' "$tmp/body" 2> /dev/null)"
    rm -rf "$tmp"
    if [ -z "$token" ]; then
        sd_fail "$role could not log in: Keycloak answered 200 with no access token."
        return 1
    fi
    case "$expires" in ''|*[!0-9]*) expires=0 ;; esac
    if [ "$expires" -lt "$budget" ]; then
        sd_fail "$role's token is valid for ${expires}s, less than the ${budget}s its pass may take: it would expire mid-pass and the rest of the pass would run unauthenticated. Raise the realm's access token lifespan."
        return 1
    fi

    if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
        echo "::add-mask::$token"
    fi
    ( umask 077 && printf '%s' "$token" > "$dir/$role.token" && echo $(( $(date +%s) + expires )) > "$dir/$role.expires" ) \
        || { rm -f "$dir/$role.token"; sd_fail "Could not store $role's token"; return 1; }
    sd_log "$role logged in: token valid for ${expires}s."
    return 0
}

# One authenticated call to the API as ROLE, proving three things before anything is scanned: the
# API accepts the bearer token (an OIDC token is resolved by Spring's resource server from the
# `Authorization: Bearer` header - WebSecurityConfig), the account arrives in the Yontrack group
# security/dast/casc.yaml maps its Keycloak group to, and - for a role granted on projects rather
# than globally - that it has a project to scan at all.
#
# A project-scoped role seeing more projects than casc.yaml grants it is printed as a WARNING rather
# than failing: an instance may grant project view to every user, which this cannot read. Counts
# only: the group name is the one the public casc.yaml declares.
#
# With $GITHUB_OUTPUT set, writes `version` - the version the demo runs - to it.
sd_whoami() {
    local role="${1:-}" dir="${2:-}" token_file url tmp status body groups expected visible granted global version casc
    [ -n "$role" ] && [ -n "$dir" ] || { sd_fail "Usage: $0 whoami ROLE DIR"; return 1; }
    # By its path in the repository: the runner's checkout directory is nothing a log needs.
    casc="${SD_CASC#"$SD_ROOT"/}"
    sd_known_role "$role" || return 1
    token_file="$(DAST_TOKEN_DIR="$dir" sd_token_file "$role")" || return 1
    url="${DAST_TARGET:-}"
    url="${url%/}/graphql"

    tmp="$(mktemp -d "${TMPDIR:-/tmp}/dast-whoami.XXXXXX")" || return 1
    chmod 700 "$tmp"
    ( umask 077 && printf 'Authorization: Bearer %s\n' "$(cat "$token_file")" > "$tmp/headers" ) || { rm -rf "$tmp"; return 1; }
    status="$(printf '%s' '{"query":"{ user { mappedGroups { name } } projects { id } info { version { full } } }"}' \
        | curl -sS --max-time 30 -o "$tmp/body" -w '%{http_code}' -X POST \
            -H 'Content-Type: application/json' -H "@$tmp/headers" \
            --data-binary @- "$url" 2> /dev/null)" || status="000"
    body="$(cat "$tmp/body" 2> /dev/null)"
    rm -rf "$tmp"

    if [ "$status" != "200" ]; then
        sd_fail "The API answered $status to $role's token: it does not accept it, and the scan does not go on unauthenticated."
        return 1
    fi
    if ! printf '%s' "$body" | jq -e '(.errors // [] | length) == 0 and .data.user != null' > /dev/null 2>&1; then
        sd_fail "The API did not answer $role's query without errors: cannot tell which user it scans as."
        return 1
    fi

    expected="$(yq -r '.casc.ontrack.admin["group-mappings"][] | select(.idp == "/dast-'"${role#scan-}"'") | .group' "$SD_CASC")" \
        || { sd_fail "Could not read the group mappings from $casc"; return 1; }
    [ -n "$expected" ] || { sd_fail "$casc maps no group for $role."; return 1; }
    groups="$(printf '%s' "$body" | jq -r '.data.user.mappedGroups[]?.name')"
    if ! printf '%s\n' "$groups" | grep -qxF "$expected"; then
        sd_fail "$role is authenticated but not mapped to the group \`$expected\`: its scan would not test that role. Check the group mappings of $casc on the instance."
        return 1
    fi

    visible="$(printf '%s' "$body" | jq -r '.data.projects // [] | length')"
    global="$(yq -r '[.casc.ontrack.admin["group-permissions"][] | select(.group == "'"$expected"'")] | length' "$SD_CASC")" || return 1
    granted="$(yq -r '[.casc.ontrack.admin["project-permissions"][] | select(.group == "'"$expected"'") | .projects[]] | length' "$SD_CASC")" || return 1
    if [ "$global" = "0" ] && [ "${granted:-0}" != "0" ]; then
        if [ "$visible" = "0" ]; then
            sd_fail "$role sees no project, where $casc grants it $granted: the project permissions were not re-applied after the demo seed, and its scan would test nothing."
            return 1
        fi
        if [ "$visible" -gt "$granted" ]; then
            # On the passive scan of the shared demo this is a WARNING: the instance is not ours to
            # reconfigure, and Yontrack's default grants project view to every authenticated user, so
            # seeing more than the CasC grants is expected there and cannot be read as an escalation.
            # On the ACTIVE scan (#1767) the stack is a throwaway we own and have turned
            # `grantProjectViewToAll` off on, so a project-scoped role seeing more than its one
            # project means the cross-project authorization checks would test nothing - and the run
            # stops here, before ZAP, rather than reporting a scan that proved nothing.
            if [ "$SD_KIND" = "active" ]; then
                sd_fail "$role sees $visible project(s), where $casc grants it $granted. On the active scan the throwaway stack must have grantProjectViewToAll off so the cross-project checks mean something. Refusing to scan."
                return 1
            fi
            sd_log "WARNING: $role sees $visible project(s), where $casc grants it $granted. Either the instance grants project view to all users - Yontrack's default, unless its security settings say otherwise - or the role reaches projects it should not."
        fi
    fi

    sd_log "$role: authenticated by the API as a member of \`$expected\`, $visible project(s) visible."
    version="$(printf '%s' "$body" | jq -r '.data.info.version.full // empty')"
    if [ -n "$version" ] && [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "version=$version" >> "$GITHUB_OUTPUT"
    fi
    return 0
}

# After an authenticated ZAP pass, from its HAR: every request to /graphql was answered as an
# authenticated one. A 401 means the token was refused or expired part-way, and the rest of the pass
# scanned the API as nobody - which would read as a cleaner API than there is. Counts only.
sd_assert_authenticated() {
    local har="${1:-}" summary total refused ok
    [ -n "$har" ] || { sd_fail "Usage: $0 assert-authenticated HAR"; return 1; }
    [ -f "$har" ] || { sd_fail "No HAR at $har: cannot prove the pass was authenticated."; return 1; }
    summary="$(jq -e -r '
        [(.log.entries // [])[] | select((.request.url // "") | test("/graphql"))] as $g
        | "\($g | length) \([$g[] | select((.response.status // 0) == 401)] | length) \([$g[] | select((.response.status // 0) >= 200 and (.response.status // 0) < 300)] | length)"
    ' "$har")" || { sd_fail "Could not read the request HAR at $har"; return 1; }
    read -r total refused ok <<< "$summary"
    if [ "${refused:-1}" != "0" ]; then
        sd_fail "$refused of the $total requests to /graphql answered 401: the token was refused or expired mid-pass, and part of the pass ran unauthenticated."
        return 1
    fi
    if [ "${ok:-0}" = "0" ]; then
        sd_fail "None of the $total requests to /graphql succeeded: the pass did not scan the API."
        return 1
    fi
    sd_log "Authenticated: $ok of the $total requests to /graphql succeeded, none answered 401."
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
# Two shapes are probed, and they mean different things:
#
#   * the management PORT itself (8800): any answer at all - 200, or even 401/403 - means the port
#     is listening where it must not be, so all three count. This is the demo/chart concern.
#   * the management BASE PATH on the NORMAL port (`/manage`, `/actuator`): only an *unauthenticated*
#     response (200) is an exposure - an ingress routing the management path to the backend's
#     management port, which answers actuator health with no auth. A 401/403 there is NOT an
#     exposure: on a bare backend (the throwaway active-scan stack, reached directly rather than
#     through the demo's ingress) every unknown path answers 401, because the actuator endpoints
#     live on the separate management port and the API chain's catch-all is `authenticated`
#     (WebSecurityConfig). Counting that 401 would fail the active scan on a stack that publishes
#     no management port at all. On the demo these paths land on the Next UI and answer 404.
sd_actuator() {
    local base="${1:-${DAST_TARGET:-}}" host scheme found=0 url code
    [ -n "$base" ] || { sd_fail "Usage: $0 actuator BASE_URL"; return 1; }
    base="${base%/}"
    scheme="${base%%://*}"
    host="${base#*://}"
    host="${host%%/*}"
    host="${host%%:*}"

    # The management port: listening at all is the exposure.
    for url in "$scheme://$host:8800/manage/health" "$scheme://$host:8800/actuator/health"; do
        code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$url" 2>/dev/null)" || code="000"
        case "$code" in
            200|401|403)
                sd_log "The management port answered $code on $(printf '%s' "$url" | sed -E 's#^(https?://[^/]+).*#\1#')/<manage>."
                found=1
                ;;
            *) ;;
        esac
    done

    # The management base path on the normal port: only an unauthenticated actuator response counts.
    for url in "$base/manage/health" "$base/actuator/health"; do
        code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$url" 2>/dev/null)" || code="000"
        case "$code" in
            200)
                sd_log "An unauthenticated management endpoint answered 200 on the normal port at $(printf '%s' "$url" | sed -E 's#^(https?://[^/]+).*#\1#')/<manage>."
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
    local file="${1:-}" role="${2:-}"
    [ -n "$file" ] || { sd_fail "Usage: $0 normalize-zap FILE [ROLE]"; return 1; }
    [ -f "$file" ] || { sd_fail "No ZAP report at $file"; return 1; }

    jq -e --arg role "$role" '
        def text: (. // "") | tostring
            | gsub("<(br|BR)[^>]*>"; "\n") | gsub("</p>"; "\n") | gsub("<[^>]*>"; "")
            | gsub("&lt;"; "<") | gsub("&gt;"; ">") | gsub("&amp;"; "&") | gsub("&quot;"; "\"")
            | gsub("[ \t]+"; " ") | gsub("\n[ \n]*"; "\n") | sub("^\\s+"; "") | sub("\\s+$"; "");
        def risk: ({"0": "INFO", "1": "LOW", "2": "MEDIUM", "3": "HIGH"}[(. // 0) | tostring] // "INFO");
        (if $role == "" then {} else {role: $role} end) + {
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

# ROLE's bearer token, as the JSON the run-time graphql-cop configuration reads (security/dast/
# graphql-cop/config.py). graphql-cop's own way in is `-H '{"Authorization": "..."}'` on its command
# line, and a command line is readable from /proc by anything else on the runner - the reason ZAP's
# plan is rendered with the token rather than handed it as an argument.
sd_render_graphql_cop_headers() {
    local output="${1:-}" role="${2:-}" token_file
    [ -n "$output" ] && [ -n "$role" ] || { sd_fail "Usage: $0 render-graphql-cop-headers OUT ROLE"; return 1; }
    token_file="$(sd_token_file "$role")" || return 1
    mkdir -p "$(dirname "$output")" || return 1
    # --rawfile rather than --arg: jq writes the token as a JSON string whatever it contains, and it
    # appears in no process's arguments on the way - only the name of its file does.
    ( umask 077 && jq -n --rawfile token "$token_file" '{"Authorization": ("Bearer " + ($token | rtrimstr("\n")))}' > "$output" ) \
        || { rm -f "$output"; sd_fail "Could not write the graphql-cop headers for $role"; return 1; }
    sd_log "graphql-cop headers rendered for $role: its bearer token is in place."
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
# dropped: it is the whole request, the bearer token header included.
sd_normalize_graphql_cop() {
    local file="${1:-}" version="${2:-unknown}" role="${3:-}" json
    [ -n "$file" ] || { sd_fail "Usage: $0 normalize-graphql-cop OUT VERSION [ROLE]"; return 1; }
    json="$(sd_graphql_cop_json "$file")" || return 1
    if [ "$(printf '%s\n' "$json" | jq 'length')" = "0" ]; then
        sd_fail "graphql-cop ran no test - it did not recognise the endpoint as GraphQL. A scan that did not happen is not a clean result."
        return 1
    fi

    printf '%s\n' "$json" | jq -e --arg version "$version" --arg role "$role" '
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
        (if $role == "" then {} else {role: $role} end) + {
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

    # awk over every selected template: the first `tags:` line of each, split on commas. Two
    # thousand paths do not fit one command line, so xargs runs awk in batches - each prints its own
    # `files bad` line, and they are added up. Reading only the first line is how the first run of
    # #1766 checked 1,393 of 2,244 templates.
    local result
    # shellcheck disable=SC2016  # deliberate: an awk program, expanded by awk
    result="$(grep -E '\.ya?ml$' "$list" | while IFS= read -r path; do
                  [ -f "$path" ] && printf '%s\0' "$path"
              done | xargs -0 -n "${SD_XARGS_BATCH:-500}" awk -v excluded="$excluded" '
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
    ' | awk '{ files += $1; bad += $2 } END { printf "%d %d\n", files, bad }')" || true
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
            | .instances = (if (.instances | length) == 0 then [{uri: "", method: "", param: "", evidence: "", attack: "", info: "", roles: (.roles // [])}] else .instances end)
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
            # The per-role split (#1769): a counted finding counts for every role that saw one of its
            # counted instances. For the private report only, like the per-tool split.
            by_role: [ ($meta.roles // [])[] | . as $r
              | ($counted | map(select([.kept[] | (.roles // [])[]] | index($r)))) as $c
              | {
                  role: $r,
                  counts: {
                    CRITICAL: ($c | map(select(.risk == "CRITICAL")) | length),
                    HIGH: ($c | map(select(.risk == "HIGH")) | length),
                    MEDIUM: ($c | map(select(.risk == "MEDIUM")) | length),
                    LOW: ($c | map(select(.risk == "LOW")) | length)
                  }
                }
            ],
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
        + [
          "## Counts by role",
          "",
          "What each role saw. The API passes ran once per scanner account; `unauthenticated` is the UI part of the ZAP scan and Nuclei. A rule several roles saw is counted once above, and once in each of their lines here.",
          "",
          "| Role | CRITICAL | HIGH | MEDIUM | LOW |",
          "|---|---|---|---|---|"
        ]
        + [ .by_role[] | "| `\(.role)` | \(.counts.CRITICAL) | \(.counts.HIGH) | \(.counts.MEDIUM) | \(.counts.LOW) |" ]
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
                "Seen by \([(.roles // [])[] | "`\(.)`"] | join(", "))\(if (.roles // []) == [] then "no role recorded" else "" end).",
                "",
                (.description | blockquote),
                "",
                "**Affected** — \(.kept | length) instance(s) counted, \(.suppressed | length) suppressed:",
                "",
                "| URL | Method | Parameter | Evidence | Roles |",
                "|---|---|---|---|---|",
                ( [ (.kept + .suppressed)[:20][] |
                    "| `\(.uri | md)` | \(.method | md) | \(if .param == "" then "—" else "`\(.param | md)`" end) | \(if .evidence == "" then "—" else "`\(.evidence | md)`" end) | \([(.roles // [])[] | "`\(.)`"] | join(", ")) |" ] | join("\n") ),
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
    local out="${1:-}" findings roles tools rules suppressions mitigations meta verdict duration=0
    [ -n "$out" ] || { sd_fail "Usage: $0 report OUT_MD FILE..."; return 1; }
    shift
    [ $# -gt 0 ] || { sd_fail "No normalised findings document given."; return 1; }

    local file
    for file in "$@"; do
        [ -f "$file" ] || { sd_fail "No findings document at $file"; return 1; }
    done

    # One document per scanner pass, and since #1769 one pass per scanner role: the documents are
    # merged here, before anything is counted.
    #
    #   * every finding and instance is attributed to the role of its document - `unauthenticated`
    #     when the document names none (Nuclei, the UI part of the ZAP scan);
    #   * findings are de-duplicated by scanner and rule - `ref`, the finer id, which is what one
    #     finding already was: three roles tripping the same rule are one finding, not three;
    #   * their instances are de-duplicated by URL, the roles that saw each one merged.
    #
    # So the counts of three passes are the counts of one pass that saw everything any of them saw,
    # and the report says which role saw what.
    findings="$(jq -e -s -c '
        [ .[] | (.role // "unauthenticated") as $r | .findings[]?
          | .roles = ((.roles // []) + [$r] | unique)
          | .instances = ((.instances // []) | map(.roles = ((.roles // []) + [$r] | unique)))
        ]
        | group_by([(.scanner // "unknown"), ((.ref // .rule) | tostring)])
        | map(
            .[0] + {
              roles: ([.[].roles[]] | unique),
              instances: (
                [.[].instances[]]
                | group_by(.uri // "")
                | map(.[0] + {
                    method: ([.[].method // "" | select(. != "")] | unique | join(", ")),
                    roles: ([.[].roles[]] | unique)
                  })
              )
            }
          )' "$@")" \
        || { sd_fail "Could not read the findings documents"; return 1; }
    roles="$(jq -e -s -c '[.[] | .role // "unauthenticated"] | unique' "$@")" \
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
        --argjson roles "$roles" \
        '$ARGS.named')" || return 1

    verdict="$(sd_verdict "$findings" "$rules" "$suppressions" "$mitigations" "$meta")" || return 1

    mkdir -p "$(dirname "$out")" || return 1
    # Every token is redacted from the report too, not only from the logs. None is ever put into
    # one - but the report is the file that leaves this runner, and a belt is cheap.
    printf '%s\n' "$verdict" | sd_render | sd_redact_tokens > "$out" \
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
# active-schema - the ACTIVE scan sends mutations, but never the dangerous ones (#1767)
# ---------------------------------------------------------------------------------------------

# The active scan's whole point is to send mutations, so - unlike the passive scan's query-only
# schema - this keeps the `Mutation` root. What it strips is the handful of mutation fields no
# scanner may ever fire, whatever role it runs as:
#
#   * token revocation - `revokeToken`, `revokeAllTokens`, `revokeAccountTokens` - which would
#     revoke the scanner's own bearer token and leave the rest of the pass running as nobody;
#   * every account, group and role mutation - so ZAP cannot delete, edit or re-map the `scan-*`
#     accounts and their groups (the issue's "deletion or change of the scan-* accounts and
#     groups"), nor grant itself a role it should not have;
#   * `reloadCasc` and `saveSettings`, which could re-apply CasC or turn `grantProjectViewToAll`
#     back on halfway through and quietly invalidate the cross-project authorization checks.
#
# The lever is the schema, exactly as for the passive scan: ZAP's GraphQL add-on generates one
# request per mutation field of the schema it is given and has no per-field switch, so a field
# that is not in the schema cannot be generated. The denylist is by exact field name, so
# `deleteAccount` never takes `deleteAccountGroup` with it.
#
# The post-condition is checked, not assumed: this refuses to write a schema that lost its
# Mutation root (a bad edit would then scan no mutation at all and read as a clean pass) or that
# still declares a denied field.
SD_ACTIVE_MUTATION_DENYLIST="${DAST_ACTIVE_MUTATION_DENYLIST:-revokeToken,revokeAllTokens,revokeAccountTokens,deleteAccount,deleteAccountGroup,editAccount,editAccountGroup,createAccountGroup,createTestAccount,grantGlobalRoleToAccount,grantGlobalRoleToAccountGroup,deleteGlobalRoleFromAccount,deleteGlobalRoleFromAccountGroup,mapGroup,reloadCasc,saveSettings}"

sd_active_schema() {
    local input="${1:-}" output="${2:-}" denylist="${3:-$SD_ACTIVE_MUTATION_DENYLIST}" tmp
    [ -n "$input" ] && [ -n "$output" ] || { sd_fail "Usage: $0 active-schema IN OUT"; return 1; }
    [ -f "$input" ] || { sd_fail "No GraphQL schema at $input"; return 1; }

    tmp="$(mktemp "${TMPDIR:-/tmp}/active-schema.XXXXXX")" || return 1

    # The generated SDL has a predictable shape: a top-level `type Mutation {` opening at column 0
    # and its closing `}` at column 0; each field at two-space indent, optionally preceded by a
    # `"..."` description line; a field with arguments opens with a trailing `(` and closes on a
    # `  ): ReturnType` line. A denied field is dropped together with its description line.
    awk -v denylist="$denylist" '
        function flush() { if (pending != "") { print pending; pending = "" } }
        BEGIN { n = split(denylist, a, ","); for (i = 1; i <= n; i++) if (a[i] != "") deny[a[i]] = 1 }
        !inmut {
            if ($0 ~ /^type Mutation[ \t]*\{/) { inmut = 1 }
            print; next
        }
        # Inside `type Mutation { ... }`.
        infield == 1 {
            # A multiline field: keep or drop every line up to its `  )` close.
            if ($0 ~ /^  \)/) { infield = 0; if (!fielddrop) print; fielddrop = 0; next }
            if (!fielddrop) print
            next
        }
        $0 == "}" { inmut = 0; flush(); print; next }
        # A description line is held until we know whether the field it describes is kept.
        $0 ~ /^  "/ { flush(); pending = $0; next }
        # A field declaration: `  name:` or `  name(`.
        $0 ~ /^  [A-Za-z_][A-Za-z0-9_]*[ \t]*[:(]/ {
            line = $0; sub(/^  /, "", line); name = line; sub(/[ \t]*[:(].*/, "", name)
            dropping = (name in deny)
            trimmed = $0; sub(/[ \t]*$/, "", trimmed)
            if (trimmed ~ /\($/) {
                # Multiline field: consume until its close.
                infield = 1; fielddrop = dropping
                if (dropping) { pending = "" } else { flush(); print }
                next
            }
            # Single-line field.
            if (dropping) { pending = "" } else { flush(); print }
            next
        }
        { flush(); print }
        END { if (inmut == 1 || infield == 1) exit 3 }
    ' "$input" > "$tmp"
    case $? in
        0) ;;
        3) rm -f "$tmp"; sd_fail "Unterminated Mutation block in $input: the schema is not the generated shape."; return 1 ;;
        *) rm -f "$tmp"; sd_fail "Could not read $input"; return 1 ;;
    esac

    # Post-condition: the Mutation root survived and no denied field is left.
    if ! grep -q '^type Mutation[ \t]*{' "$tmp"; then
        rm -f "$tmp"; sd_fail "The derived active schema has no Mutation root: the active scan would send no mutation at all."; return 1
    fi
    local item
    for item in $(printf '%s' "$denylist" | tr ',' ' '); do
        [ -n "$item" ] || continue
        if grep -qE "^  ${item}[ \t]*[:(]" "$tmp"; then
            rm -f "$tmp"; sd_fail "The derived active schema still declares the denied mutation \`$item\`: refusing to hand it to the scanner."; return 1
        fi
    done

    mkdir -p "$(dirname "$output")" || { rm -f "$tmp"; return 1; }
    mv "$tmp" "$output" || return 1
    # World-readable: the ZAP container reads it as its own uid (see query-schema).
    chmod 644 "$output" || return 1
    sd_log "Active schema written: Mutation root kept, $(printf '%s' "$denylist" | tr ',' ' ' | wc -w | tr -d ' ') field(s) denied and removed."
    return 0
}

# ---------------------------------------------------------------------------------------------
# access-control - the authorization checks (#1767)
# ---------------------------------------------------------------------------------------------

# The checks the active scan exists for. #1739, #1741 and #1742 were authorization bugs found by
# hand; this is what would have caught them. Each probe sends one request a role MUST be refused
# and records an *escalation* when the request instead succeeded:
#
#   * scan-readonly (global READ_ONLY) creating a project - a global write it must not have;
#   * scan-project (PARTICIPANT on one project only) reading another project;
#   * scan-project changing another project (creating a branch on it).
#
# Success is read from the response, not from a status code: a denied mutation comes back with a
# GraphQL error and no payload, so "the write happened" means the payload shows it - a created
# entity, an empty `errors`. A refused read of another project returns an empty list.
#
# Nothing here is an attack payload: a probe is a legitimate request that authorization must stop.
# The probes run on the throwaway stack, so a genuine escalation does create data - which is the
# proof, and the stack is torn down after.
#
# The raw result of each role goes to a file OUT_DIR/access-control-<role>.raw.json and is never
# printed: a success carries the data it should not have been allowed to reach. Only counts are
# printed. An escalation is a finding for the report and the stamp, not a run error, so this
# returns 0 even when it finds one; it returns non-zero only when a probe could not be sent, which
# is a broken scan.
# The project scan-project reaching another one is tested against: a seeded project it does NOT
# hold (petclinic is the one it does). Overridable so the probe survives a rename of the seed data.
SD_AC_OTHER_PROJECT="${DAST_AC_OTHER_PROJECT:-common-library}"

# One authenticated GraphQL request as ROLE. Echoes the response body; fails on a transport or
# non-2xx error - never on a GraphQL error, which is a legitimate "denied" answer.
sd_ac_request() {
    local role="$1" query="$2" token_file url tmp status body
    token_file="$(sd_token_file "$role")" || return 1
    url="${DAST_TARGET:-}"; url="${url%/}/graphql"
    tmp="$(mktemp -d "${TMPDIR:-/tmp}/dast-ac.XXXXXX")" || return 1
    chmod 700 "$tmp"
    ( umask 077 && printf 'Authorization: Bearer %s\n' "$(cat "$token_file")" > "$tmp/headers" ) || { rm -rf "$tmp"; return 1; }
    status="$(jq -nc --arg q "$query" '{query: $q}' \
        | curl -sS --max-time 30 -o "$tmp/body" -w '%{http_code}' -X POST \
            -H 'Content-Type: application/json' -H "@$tmp/headers" \
            --data-binary @- "$url" 2> /dev/null)" || status="000"
    body="$(cat "$tmp/body" 2> /dev/null)"
    rm -rf "$tmp"
    case "$status" in
        2??) printf '%s' "$body"; return 0 ;;
        *) sd_fail "The API answered $status to $role's access-control probe: cannot tell what it allowed."; return 1 ;;
    esac
}

sd_access_control() {
    local dir="${1:-}" out_dir="${2:-}"
    [ -n "$dir" ] && [ -n "$out_dir" ] || { sd_fail "Usage: $0 access-control DIR OUT_DIR"; return 1; }
    DAST_TOKEN_DIR="$dir"
    mkdir -p "$out_dir" || return 1

    local other="$SD_AC_OTHER_PROJECT" total=0 escalations=0

    # The probe table, one line per probe: role, area, kind, id, query, and the jq that reads
    # "the request succeeded" (an escalation) from the response. `\(other)` is interpolated below.
    _ac_probe() {
        local role="$1" area="$2" kind="$3" id="$4" query="$5" success_jq="$6"
        local raw="$out_dir/access-control-$role.raw.json"
        local body succeeded
        body="$(sd_ac_request "$role" "$query")" || return 1
        succeeded="$(printf '%s' "$body" | jq -r "$success_jq" 2>/dev/null)" || succeeded="false"
        [ "$succeeded" = "true" ] || succeeded="false"
        total=$((total + 1))
        [ "$succeeded" = "true" ] && escalations=$((escalations + 1))
        # Append to the role's raw array (never printed).
        local prev='[]'
        [ -f "$raw" ] && prev="$(cat "$raw")"
        printf '%s' "$prev" | jq -c \
            --arg role "$role" --arg area "$area" --arg kind "$kind" --arg id "$id" \
            --argjson escalation "$([ "$succeeded" = "true" ] && echo true || echo false)" \
            '. + [{role: $role, area: $area, kind: $kind, id: $id, escalation: $escalation}]' \
            > "$raw.tmp" && mv "$raw.tmp" "$raw"
        sd_log "access-control: $role / $kind / $area -> $([ "$succeeded" = "true" ] && echo "ESCALATION" || echo "refused")."
        return 0
    }

    # Fresh raw files, so a re-run does not accumulate.
    rm -f "$out_dir/access-control-scan-readonly.raw.json" "$out_dir/access-control-scan-project.raw.json"

    _ac_probe scan-readonly global write "readonly-create-project" \
        "mutation { createProject(input: {name: \"dast-ac-probe-${DAST_RUN_ID:-0}-r\"}) { project { id } errors { message } } }" \
        '(.data.createProject.project // null) != null and ((.data.createProject.errors // []) | length) == 0' || return 1

    _ac_probe scan-project cross-project read "project-read-other" \
        "query { projects(name: \"$other\") { id name } }" \
        '((.data.projects // []) | length) > 0' || return 1

    _ac_probe scan-project cross-project write "project-write-other" \
        "mutation { createBranchOrGet(input: {projectName: \"$other\", name: \"dast-ac-probe-${DAST_RUN_ID:-0}-p\"}) { branch { id } errors { message } } }" \
        '(.data.createBranchOrGet.branch // null) != null and ((.data.createBranchOrGet.errors // []) | length) == 0' || return 1

    unset -f _ac_probe
    sd_log "Access control: $total probe(s) sent, $escalations escalation(s) (scan-readonly write, scan-project read, scan-project write)."
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "access_control_escalations=$escalations" >> "$GITHUB_OUTPUT"
    fi
    return 0
}

# An access-control raw document, into the normalised findings document. One HIGH finding per
# escalation; a check that was correctly refused is not a finding.
sd_normalize_access_control() {
    local file="${1:-}" role="${2:-}"
    [ -n "$file" ] || { sd_fail "Usage: $0 normalize-access-control RAW [ROLE]"; return 1; }
    [ -f "$file" ] || { sd_fail "No access-control raw document at $file"; return 1; }

    jq -e --arg role "$role" '
        def describe:
            "Access control: \(.role) can " +
            (if .kind == "read" then "read" else "change" end) +
            (if .area == "cross-project" then " a project it does not hold" else " beyond its role" end);
        (if $role == "" then {} else {role: $role} end) + {
          scanner: "access-control",
          version: "1",
          findings: [
            .[] | select(.escalation == true)
            | {
                scanner: "access-control",
                rule: ("access-control-\(.role)-\(.kind)-\(.area)" | gsub("scan-"; "")),
                ref: ("access-control-\(.role)-\(.kind)-\(.area)" | gsub("scan-"; "")),
                name: describe,
                risk: "HIGH",
                confidence: "Confirmed",
                cwe: "285",
                description: (describe + " — a request that authorization must refuse succeeded. This is the kind of finding the active scan exists to catch (see #1739, #1741, #1742). The detail is deliberately not here: the report says the kind of access, never how to reach it."),
                solution: "",
                reference: "",
                instances: [
                  { uri: "/graphql", method: "POST", param: (.id // ""), evidence: "", attack: "", info: "" }
                ]
              }
          ]
        }
    ' "$file" || { sd_fail "Could not read the access-control raw document at $file"; return 1; }
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
        active-schema) sd_active_schema "$@" ;;
        access-control) sd_access_control "$@" ;;
        normalize-access-control) sd_normalize_access_control "$@" ;;
        render-plan) sd_render_plan "$@" ;;
        login) sd_login "$@" ;;
        whoami) sd_whoami "$@" ;;
        assert-authenticated) sd_assert_authenticated "$@" ;;
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
            echo "Usage: $0 query-schema|active-schema|access-control|normalize-access-control|login|whoami|render-plan|assert-authenticated|actuator|normalize-zap|assert-no-mutations|fetch|graphql-cop-preflight|render-graphql-cop-headers|assert-graphql-cop-no-mutations|normalize-graphql-cop|nuclei-preflight|nuclei-summary|normalize-nuclei|report|report-path|publish|scrub ..." >&2
            return 1
            ;;
    esac
}

# Sourced by the test suite, which wants the functions and nothing else.
if [ -z "${SECURITY_DAST_LIB_ONLY:-}" ]; then
    sd_main "$@"
    exit $?
fi
