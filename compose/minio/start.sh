#!/bin/sh
#
# Entry point of the MinIO service of the dev, integration test and KDSL
# stacks (#1962): starts the server, creates the bucket named by
# YONTRACK_MINIO_BUCKET once the server answers, and then stays in the
# foreground with it.
#
# The bucket being there is what the service's healthcheck waits for -- it
# looks for the marker below -- so a stack that is up has its bucket, and
# nothing depends on a one-shot init container, which `docker compose up
# --wait` does not get along with.
#
# The image is pgsty/silo, the community fork of MinIO, whose server binary is
# `silo` and whose bundled client is `mcli`. See buildSrc's `Minio`.

set -eu

: "${YONTRACK_MINIO_BUCKET:?the bucket to create}"

READY=/tmp/yontrack-bucket-ready
rm -f "$READY"

silo server /data --address :9000 --console-address :9001 &
server=$!

# Forwarded, or `docker compose down` waits its ten seconds and kills.
trap 'kill -TERM "$server" 2>/dev/null' TERM INT

attempts=0
until mcli alias set local http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
        echo "MinIO did not answer within 60s" >&2
        kill -TERM "$server" 2>/dev/null || true
        exit 1
    fi
    sleep 1
done

mcli mb --ignore-existing "local/$YONTRACK_MINIO_BUCKET"
touch "$READY"

# The container lives and dies with the server. `wait` returns early when the
# trap fires, so the server is waited for again until it has really stopped.
set +e
wait "$server"
status=$?
if kill -0 "$server" 2>/dev/null; then
    wait "$server"
    status=$?
fi
exit "$status"
