"""graphql-cop's configuration, as the passive DAST scan runs it (#1766).

.github/workflows/dast-passive.yml copies this file over the `config.py` of the pinned graphql-cop
source before running it. It is the upstream file - the same `HEADERS` with the same User-Agent -
plus one thing: the API token header, read from a JSON file rendered at run time by
`scripts/security-dast.sh render-graphql-cop-headers`.

graphql-cop's own way to send a header is `-H '{"X-Ontrack-Token": "..."}'` on its command line,
and a command line is readable from /proc by anything else on the runner - on the host, too, for a
process in a container. The rendered file lives in $RUNNER_TEMP, is mounted read-only, and is never
uploaded.
"""
import json
import os

from version import VERSION

HEADERS = {
    'User-Agent': 'graphql-cop/{}'.format(VERSION),
}

with open(os.environ.get('GRAPHQL_COP_HEADERS', '/cop/headers.json'), encoding='utf-8') as headers:
    HEADERS.update(json.load(headers))
