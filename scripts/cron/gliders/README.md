# Gliders cron generators

The canonical generators now live here, unchanged from `data-services/ARGO/oceancurrent/`.
They use only Python's standard library; no production dependency installation is needed.
The existing pytest CI job discovers the migrated tests and entry-point tests automatically.
Captured fixtures and byte-for-byte expected JSON are in
`../tests/mnt/oceancurrent/website/gliders/generators/`. Tests copy mutable inputs to temporary directories.
The scanner, its inventories and all consumers are outside this migration.

`run_gliders.sh` downloads one GitHub repository archive from `main` with a 15-second
connection limit and 120-second overall limit. A single archive fixes all Python
modules to one Git revision, even if `main` advances during execution. Extraction is
limited to the publisher modules and their logging helper; the archive and modules
are removed on exit or handled signals.
The bootstrap script is also fetched on every cron execution from
`https://raw.githubusercontent.com/aodn/ocean-current-api/main/scripts/cron/gliders/run_gliders.sh`.
Update the prepared Chef command to this path before deploying the cron cutover. There is no assumed
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

Paths remain:

- Catalogue input: `/mnt/oceancurrent/website/gliders/index.html`
- Yearmaps input: `/mnt/oceancurrent/website/gliders/yearmaps`
- Catalogue output: `/mnt/oceancurrent/website/api/gliders-catalogue.json`
- Coordinates output: `/mnt/oceancurrent/website/api/gliders-yearmaps.json`

### Deployment order

1. Merge these scripts and tests into `ocean-current-api/main`. This makes the entry
   point downloadable; existing scanner/Argo cron jobs already download from main,
   so changes to their scripts would deploy on their next run. They are unchanged here.
2. Apply the prepared `chef-private/data_bags/cronjobs/po_gliders_catalogue.json`
   change via the normal Chef deployment. Retain `projectofficer`, minute `0`, hours
   `2,14`, mail recipient and `monitored: true`. Chef's `imos_core::cronjob` recipe
   appends the exit-code log consumed by Nagios. Do not invoke the new entry point
   manually on production as a migration test.
3. After a scheduled run, verify both stage statuses, Nagios exit status, JSON
   validity and freshness at the unchanged output paths. Confirm the effective cron
   no longer references `data-services/master` before retiring the old source.
4. Merge the separate data-services retirement change only after verifying the
   cron cutover. Until then, that source must remain available. Rollback before
   retirement is to restore the previous Chef command.

Repository configuration verifies the schedule, user, notifications and monitoring;
it does not prove which revision is installed on the running host, the host timezone,
or availability of GNU tar there. Verify those during infrastructure rollout.

### Migration validation

- `uv run --locked --project scripts/cron pytest scripts/cron`: 102 passed on Python 3.8.20.
- Legacy Gliders unittest suite: 86 passed; migrated suite: 86 passed.
- Four migrated production modules are byte-identical to their legacy source.
- Running both versions on captured fixtures produced identical bytes: catalogue
  15,297 bytes; yearmaps 16,290 bytes. All outputs were in a temporary directory.
- Entry tests cover both stages succeeding, each failing, both failing, timeout exit
  codes, failed downloads, corrupt archives, argument limits and temporary cleanup.
  External commands are substituted so these tests cannot publish to production.
- Bash syntax checks passed for the entry point and proposed cron command;
  Chef JSON fields other than `command` were compared and are unchanged.

Java/Gradle checks were not run: no Java, API configuration or dependencies changed.
The existing Python CI job runs all new tests without a workflow change.

Detailed schema and historical design notes are in [GLIDERS_GENERATORS.md](GLIDERS_GENERATORS.md).
