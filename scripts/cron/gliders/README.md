# Gliders cron generators

These are the canonical Gliders generators.
They use only Python's standard library; no production dependency installation is needed.
The existing pytest CI job discovers the migrated tests and entry-point tests automatically.
Captured fixtures and byte-for-byte expected JSON are in
`../tests/mnt/oceancurrent/website/gliders/generators/`. Tests copy mutable inputs to temporary directories.

`run_gliders.sh` downloads one GitHub repository archive from `main` with a 15-second
connection limit and 120-second overall limit. A single archive fixes all Python
modules to one Git revision, even if `main` advances during execution. Extraction is
limited to the publisher modules and their logging helper; the archive and modules
are removed on exit or handled signals.
The bootstrap script is also fetched on every cron execution from
`https://raw.githubusercontent.com/aodn/ocean-current-api/main/scripts/cron/gliders/run_gliders.sh`.
The Chef cron command already points to this entry point. There is no assumed
installed checkout or script location. GNU `tar`, `curl`, `timeout`, Bash and
`/usr/bin/python3` are required, matching the existing job's tools plus tar.

Catalogue runs before yearmaps, each with `timeout -k 30s 15m` and
`--stability-seconds 30`. A catalogue failure does not skip yearmaps: its publisher
validates the previously published catalogue. Both statuses are logged, and either
failure makes the entry point return nonzero. Download/extraction failure logs both
stages as failed and does not publish. Validation, source stability checks, schema checks and atomic
replacement remain in the Python publishers.

Both publishers append timestamped INFO, WARNING and ERROR records to
`/var/log/imos/gliders_publishers.log`. INFO and WARNING records also go to stdout;
ERROR records go to stderr. The Chef cron discards stdout and tees stderr to
`/var/log/imos/cron_po_gliders_catalogue.log`, so successful runs remain available
on the host without generating email. Download, extraction, timeout and publisher
failures are retained in the cron log, emailed to `developers@emii.org.au`, and keep
their nonzero monitoring result through Bash `pipefail`.

Publication paths:

- Catalogue input: `/mnt/oceancurrent/website/gliders/index.html`
- Yearmaps input: `/mnt/oceancurrent/website/gliders/yearmaps`
- Catalogue output: `/mnt/oceancurrent/website/api/gliders/gliders-catalogue.json`
- Coordinates output: `/mnt/oceancurrent/website/api/gliders/gliders-yearmaps.json`

### Moving the published JSON files

The catalogue publisher includes dated mission images in `image_history`, grouped
by the linked 4d, 12d and 30d folders. The scanner no longer generates Gliders indexes.
The catalogue and geographic yearmaps are the two published JSON files in this directory.
Both continue to refresh at 02:00 and 14:00 in the server timezone.

Deploy API support before the expanded catalogue is published. Once the mission detail
endpoint returns its history correctly, remove the retired `api/gliders/gliders-mission.json`.
The generator reads local GIF files directly and does not depend on this retired index.

1. Coordinate the API rollout with the file move. Ensure the old scanner has finished
   and can no longer write the GIF inventory to `api/gliders/gliders-yearmaps.json`.
2. Before the new publishers run, copy the existing catalogue and geographic yearmaps
   from the root of `website/api/` into `website/api/gliders/` using atomic replacement
   on the same filesystem. Replace the scanner inventory at the destination
   `gliders-yearmaps.json` with the geographic document. This preserves the publishers'
   previous baselines and gives the API and frontend valid data at the new paths.
3. Verify both scheduled jobs succeed, the mission catalogue API serves missions, and
   the frontend can read the geographic yearmaps from the new URL. Once the deployed
   consumers use the new paths, remove these retired files from `website/`:
   - `api/gliders-catalogue.json`
   - `api/gliders-yearmaps.json`
   - `api/gliders/gliders-mission-video.json`
   - `api/gliders/gliders-navigation.json`
   - `api/gliders/gliders-navigation-video.json`
   - `api/gliders/gliders-ts.json`

Detailed schema and historical design notes are in [GLIDERS_GENERATORS.md](GLIDERS_GENERATORS.md).
