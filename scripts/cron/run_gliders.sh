#!/bin/bash
# Download one repository snapshot: all Python modules come from the same revision.
# No checkout or installed script directory is required on the legacy host.
set -u
work=$(mktemp -d "${TMPDIR:-/tmp}/po_gliders.XXXXXX") || exit 1
trap 'rm -rf "$work"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

if ! curl -fsSL --connect-timeout 15 --max-time 120 \
    https://codeload.github.com/aodn/ocean-current-api/tar.gz/refs/heads/main \
    -o "$work/scripts.tar.gz"; then
    echo 'Gliders catalogue=1 yearmaps=1 (snapshot download failed)' >&2
    exit 1
fi
if ! tar -xzf "$work/scripts.tar.gz" -C "$work" --strip-components=3 \
    ocean-current-api-main/scripts/cron/gliders_catalogue.py \
    ocean-current-api-main/scripts/cron/publish_gliders_catalogue.py \
    ocean-current-api-main/scripts/cron/gliders_yearmaps.py \
    ocean-current-api-main/scripts/cron/publish_gliders_yearmaps.py; then
    echo 'Gliders catalogue=1 yearmaps=1 (snapshot extraction failed)' >&2
    exit 1
fi

w=/mnt/oceancurrent/website
timeout -k 30s 15m /usr/bin/python3 "$work/publish_gliders_catalogue.py" \
    --input "$w/gliders/index.html" --output "$w/api/gliders-catalogue.json" --stability-seconds 30
a=$?
# Deliberately continue after catalogue failure: the publisher validates the previous catalogue.
timeout -k 30s 15m /usr/bin/python3 "$work/publish_gliders_yearmaps.py" \
    --input "$w/gliders/yearmaps" --catalogue "$w/api/gliders-catalogue.json" \
    --output "$w/api/gliders-yearmaps.json" --stability-seconds 30
b=$?
echo "Gliders catalogue=$a yearmaps=$b" >&2
[ "$a" -eq 0 ] && [ "$b" -eq 0 ]
