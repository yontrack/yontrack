"""graphql-cop's configuration, as the passive DAST scan runs it (#1766).

.github/workflows/dast-passive.yml copies this file over the `config.py` of the pinned graphql-cop
source before running it. It is the upstream file - the same `HEADERS` with the same User-Agent -
plus one thing: the `Authorization: Bearer` header of the scanner role the pass runs as (#1769),
read from the JSON file named by $GRAPHQL_COP_HEADERS and rendered at run time by
`scripts/security-dast.sh render-graphql-cop-headers`.

graphql-cop's own way to send a header is `-H '{"Authorization": "..."}'` on its command line,
and a command line is readable from /proc by anything else on the runner - on the host, too, for a
process in a container. The rendered file lives in $RUNNER_TEMP, is mounted read-only, is deleted after
its pass, and is never uploaded.
"""
import json
import os

from version import VERSION

HEADERS = {
    'User-Agent': 'graphql-cop/{}'.format(VERSION),
}

with open(os.environ.get('GRAPHQL_COP_HEADERS', '/cop/headers.json'), encoding='utf-8') as headers:
    HEADERS.update(json.load(headers))
