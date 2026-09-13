#!/bin/bash

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$here/../scripts/dev-graphql-schema.sh" "$here/ontrack.graphql"
