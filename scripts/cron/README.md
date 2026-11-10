# Cron job scripts

Scripts that run on the legacy EC2 server on a schedule and generate the JSON index files this API reads.

> **Merging to `main` is the deployment.** The cron jobs download these scripts from `main` on GitHub each time they run, so the next scheduled run uses whatever is merged. Gliders migration and cron cutover are described below (aodn/backlog#9723).

| Script | Purpose |
| --- | --- |
| `run_gliders.sh` | Entry point for catalogue and year-map publication |
| `gliders_catalogue.py`, `publish_gliders_catalogue.py` | Generate, validate and atomically publish mission metadata |
| `gliders_yearmaps.py`, `publish_gliders_yearmaps.py` | Generate, validate and atomically publish year-map coordinates |
| `argo_oceancurrent.py` | Generates `profiles.json` for each Argo platform |
| `oceancurrent_file_server_api.py` | Scans product folders and generates the image index JSON files |

Keep this folder flat. See [README_FATAL_LOG_CONFIG.md](README_FATAL_LOG_CONFIG.md) for the scanner's failure notifications.

## Run locally

Run these from the repo root. `DEV_MODE=true` makes the scripts use the test data in `scripts/cron/tests/` instead of `/mnt/oceancurrent/website/`. The JSON files they generate there are ignored by Git.

```bash
# Tests
uv run --project scripts/cron pytest scripts/cron

# Scripts
DEV_MODE=true uv run --project scripts/cron python scripts/cron/argo_oceancurrent.py
DEV_MODE=true uv run --project scripts/cron python scripts/cron/oceancurrent_file_server_api.py
```

# ARGO Profiles JSON Generator for the Ocean Current website

This script processes directories containing `.gif` files and generates a `profiles.json` file per argo platform directory with metadata extracted from the filenames. The filenames follow a specific pattern, and the extracted information includes the date, profile number, and cycle number.

## Directory Structure

The script expects a directory structure as follows:

/mnt/oceancurrent/website/profiles/
├── platform_code1/
│ ├── YYYYMMDD_profileNumber_cycleNumber.gif
│ ├── YYYYMMDD_profileNumber_cycleNumber.html
│ └── ...
├── platform_code2/
│ ├── YYYYMMDD_profileNumber_cycleNumber.gif
│ ├── YYYYMMDD_profileNumber_cycleNumber.html
│ └── ...
└── ...


Where:
- `YYYYMMDD` is the date.
- `profileNumber` is the profile number.
- `cycleNumber` is the cycle number.

## Script Overview

The script performs the following steps:
1. Lists all platform codes in the specified `PROFILES_PATH` directory.
2. For each platform code, lists all files in the corresponding directory.
3. Filters the filenames to include only `.gif` files.
4. Extracts the date, profile number, and cycle number from each filename using a regular expression.
5. Creates a `profiles.json` file per directory containing the extracted information.

## Usage

### Running the Script

1. Confirm the `PROFILES_PATH` variable in the script to point to the base directory containing the platform code directories.
2. Run the script using Python as a cronjob:

```bash
python3 argo_oceancurrent.py
```

## Unittesting
Run the unittests with ```pytest```

# File Server API for JSON Generator for the Ocean Current website
Script `oceancurrent_file_server_api.py` is used for scanning `.gif` files in the Ocean Current file server for selected subproducts and products. JSON files are generated as per subproducts. The JSON file format is designed as below:
```JSON
[
    {
        "path": "/SST_4hr/SST/Adelaide", # /{product folder}/{subproduct folder}/{region}
        "productId": "fourHourSst-sst", # subproduct ID which align with Ocean Current front-end https://github.com/aodn/ocean-current-frontend/blob/main/src/constants/product.ts
        "region": "Adelaide", # region folder
        "depth": null, # this is used for some products which contains depth attribute
        "files": [
            {
                "name": "2024041918.gif" # scanned gif file existed in the file path "/SST_4hr/SST/Adelaide"
            }
        ]
    },
    {
        "path": "/SST_4hr/SST/SAgulfs",
        "productId": "fourHourSst-sst",
        "region": "SAgulfs",
        "depth": null,
        "files": [
            {
                "name": "2024051010.gif"
            }
        ]
    }
]
```

**A selected product** is a product which has been implemented on Ocean Current website. Similarly, **a selected subproduct** is a subproduct which has been implemented on Ocean Current website. To scan the `.gif` files under particular product folders, `FILE_PATH_CONFIG` should be a dictionary in which has the file path configurations.

The following pseudo code explain the scanning logic:
```
FUNCTION recursive_scan(folder, current_layer, include_rules, max_layer, filetype_mode, filetype_pattern):
    INITIALIZE empty list results
    
    # 1. If scan finished, i.e., reach the max_layer, stop the scanning
    IF current_layer > max_layer:
        RETURN results

    # 2. If there’s a filter for this layer, and the folder name doesn’t match it, skip it
    IF include_rules contains key current_layer:
        IF folder.name does NOT match ANY pattern in include_rules[current_layer]:
            RETURN results

    # 3. Otherwise, walk through each entry in this folder
    FOR EACH item IN list_directory(folder):
        IF item is a directory:
            # 4. only recurse further if we haven’t hit max depth
            IF current_layer < max_layer:
                CALL recursive_scan(item,
                                    current_layer + 1,
                                    include_rules,
                                    max_layer,
                                    filetype_mode,
                                    filetype_pattern)
                APPEND all returned paths into results

        ELSE IF item is a file:
            # 5. collect files exactly at max_layer
            IF current_layer == max_layer:
                IF filetype_mode == "suffix":
                    IF file’s extension equals filetype_pattern:
                        ADD item to results
                ELSE:
                    IF file’s name matches filetype_pattern (regex):
                        ADD item to results
    # 6. save the scanned files in memory for further generating JSON
    RETURN results

```

Global variable `FILE_PATH_CONFIG` should be defined at the beginning of the script. A `FILE_PATH_CONFIG` is a list of dictories of pre-defined congirations for selected products. It should strictly with these fields:

field | description | example value
----|----|----|
`productId` | String. The product ID defined in Ocean Current front-end https://github.com/aodn/ocean-current-frontend/blob/main/src/constants/product.ts. **Note:** use the key from the children | `fourHourSst-sstFilled`
`include` | List. Specify the folder name and the layer that needed to be included in the scanning. **Note:** supports regex. The format should be a list of dict. | `"include":{"path": "SST_4hr", "layer": 1},{"path": "SST_Filled", "layer": 2}]`
`filetype` | String. Specify the file type that needed to be scanned. In string format. Can be a suffix, a fixed file name, or a regex. | `"^T_.*.gif$"`
`max_layer` | Integer. The index of last subfolder that need to be scanned. For example, a gif file stored in "\Product\Subproduct\Region\example.gif" (a relative path to the root path of the file system). The `max_layer` is 3. | 3

Optional fields include:

- `region_layer`: Integer. The layer which indicates the regions of the product. For example, given a path "\Product\Subproduct\Region\example.gif", the `region_layer` is 3.
- `depth_layer`: Integer. The layer which indicates the depth of the product. For example, given a path "\Product\Subproduct\Region\Depth\example.gif", the `depth_layer` is 4.
- `save_in_product_folder`: Boolean. True as save the generated JSON file in the product folder. In some cases, there is no product folder but only regional folders in the root path, in that case, the JSON file will be stored at the root path of the file system. 
- `output_folder`: String. Save the generated JSON file as `<productId>.json` in this folder, relative to the root path. Takes precedence over `save_in_product_folder`. Use it for folders CSIRO syncs with `rsync --delete`, which removes JSON files that CSIRO doesn't have. Use a subfolder of `api/`, because other cron jobs publish their own files directly in `api/`.

## Supporting Products
Currently, the script is adaptive to these products:

|subproduct | saved path|
| ---- | ---- | 
|**Four hour SST** | |
`fourHourSst-sstFilled` | `\SST_4hr\SST_Filled\SST_Filled.json`
`fourHourSst-sst` | `\SST_4hr\SST\SST.json`
`fourHourSst-sstAge` | `\SST_4hr\SST_Age\SST_Age.json`
`fourHourSst-windSpeed` | `\SST_4hr\Wind\Wind.json`
| **6-Day SST & Centiles** | |
`sixDaySst-sst` | `DR_SST_daily\SST\SST.json` and `STATE_daily\SST\SST.json`
`sixDaySst-sstAnomaly` | `\DR_SST_daily\SST_ANOM\SST_ANOM.json` and `\STATE_daily\SST_ANOM\SST_ANOM.json`
`sixDaySst-centiles` | `\DR_SST_daily\pctiles\pctiles.json` and `\STATE_daily\pctiles\pctiles.json`
| **SealCTD** | | 
`sealCtd-sealTrack` | `\AATAMS\sealCtd-sealTrack.json`
`sealCtd-sealTrack-video` | `\AATAMS\sealCtd-sealTrack-video.json`
`sealCtd-timeseriesTemperature` | `AATAMS\sealCtd-timeseriesTemperature.json`
`sealCtd-timeseriesSalinity` | `\AATAMS\sealCtd-timeseriesSalinity.json`
| **Adjusted Sea Level Anom.** | |
`adjustedSeaLevelAnomaly-sla` | `\STATE_daily\SLA\SLA.json`
`adjustedSeaLevelAnomaly-centiles` | `\STATE_daily\SLA_pctiles\SLA_pctiles.json`
`adjustedSeaLevelAnomaly-sst` | `\adjustedSeaLevelAnomaly-sst.json` and `\adjustedSeaLevelAnomaly-sst-year.json`
| **Ocean Color** | |
`oceanColour-chlA` | `\STATE_daily\CHL\CHL.json` and `\oceanColour-chlA.json` and `\oceanColour-chlA-year.json`
`oceanColour-chlAAge` | `\STATE_daily\CHL_AGE\CHL_AGE.json`
| **Current Meters** | | 
`currentMetersCalendar-48` | `\timeseries\currentMetersCalendar-48.json`
`currentMetersCalendar-49` | `\timeseries\currentMetersCalendar-49.json`
`currentMetersCalendar-54` | `\timeseries\currentMetersCalendar-54.json`
`currentMetersRegion-48` | `\timeseries\currentMetersRegion-48.json`
`currentMetersRegion-49` | `\timeseries\currentMetersRegion-49.json`
`currentMetersRegion-54` | `\timeseries\currentMetersRegion-54.json`
`currentMetersPlot-48` | `\timeseries\currentMetersPlot-48.json`
`currentMetersPlot-49` | `\timeseries\currentMetersPlot-49.json`
`currentMetersPlot-54` | `\timeseries\currentMetersPlot-54.json`


## Gliders asset indexes

The scanner discovers assets under `/mnt/oceancurrent/website/gliders/`.
Registering these proposed product IDs in `ocean-current-api` is a separate
future task; this change does not integrate Gliders into the API or frontend.

| Product ID | JSON path relative to the website root | Assets |
| --- | --- | --- |
| `gliders-mission` | `/api/gliders/gliders-mission.json` | Standard mission GIFs |
| `gliders-mission-video` | `/api/gliders/gliders-mission-video.json` | Standard mission MP4s |
| `gliders-navigation` | `/api/gliders/gliders-navigation.json` | Navigation GIFs |
| `gliders-navigation-video` | `/api/gliders/gliders-navigation-video.json` | Navigation MP4s |
| `gliders-ts` | `/api/gliders/gliders-ts.json` | GIFs directly under `missionTS/` |
| `gliders-yearmaps` | `/api/gliders/gliders-yearmaps.json` | GIFs directly under `yearmaps/` |

Mission directory names start with a letter and contain letters, digits,
underscores or hyphens, followed by an eight-digit mission date. The date can
have a single-letter suffix or a `_1` / `_2` mission leg. Supported periods are
`_4d`, `_12d` and `_30d`, with optional `_nrt` before or after the period:

- `TasEastCoast20260922_nrt_4d`
- `Brisbane20180306A_4d`
- `JervisBay20180503a_nrt_12d`
- `Perth20110626_1_4d`
- `Cooktown20160217_4d_nrt`

Navigation directories use the same format with a `nav` prefix and are excluded
from standard mission indexes. Only files directly inside matching directories
are indexed. GIF and MP4 extensions are case insensitive; HTML, JSON, PHP,
unsupported periods, scientific variant suffixes and auxiliary folders are excluded.
Missing periods or animations are allowed. An index is written only when matching
assets exist; the existing scanner does not remove stale indexes when assets disappear.

Each mission directory is preserved in `path` and used verbatim as `region`,
so identical filenames from different missions remain distinguishable. `depth`
is null; T-S and annual-map records also have null `region`. Example record:

```json
{
    "path": "/gliders/TasEastCoast20260922_nrt_4d",
    "productId": "gliders-mission",
    "region": "TasEastCoast20260922_nrt_4d",
    "depth": null,
    "files": [{"name": "2026101500.gif"}]
}
```

Merging activates these configurations on the next scheduled run because the
production job downloads the scanner from GitHub at run time. Gliders runs after existing
products and writes up to six indexes under `api/gliders/`; check the existing runtime
and memory logs after deployment. No cron or deployment changes are required.

## Gliders migration and operations

The canonical generators now live here, unchanged from `data-services/ARGO/oceancurrent/`.
They use only Python's standard library; no production dependency installation is needed.
The existing pytest CI job discovers the migrated tests and entry-point tests automatically.
Captured fixtures and byte-for-byte expected JSON are in
`tests/mnt/oceancurrent/website/gliders/generators/`. Tests copy mutable inputs to temporary directories.
The scanner, its inventories and all consumers are outside this migration.

`run_gliders.sh` downloads one GitHub repository archive from `main` with a 15-second
connection limit and 120-second overall limit. A single archive fixes all four Python
modules to one Git revision, even if `main` advances during execution. Extraction is
limited to the four modules; the archive and modules are removed on exit or handled signals.
The bootstrap script is also fetched on every cron execution. There is no assumed
installed checkout or script location. GNU `tar`, `curl`, `timeout`, Bash and
`/usr/bin/python3` are required, matching the existing job's tools plus tar.

Catalogue runs before yearmaps, each with `timeout -k 30s 15m` and
`--stability-seconds 30`. A catalogue failure does not skip yearmaps: its publisher
validates the previously published catalogue. Both statuses are logged, and either
failure makes the entry point return nonzero. Download/extraction failure logs both
stages as failed and does not publish. Validation, source stability checks, schema checks and atomic
replacement remain in the Python publishers.

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
4. Follow `data-services/ARGO/oceancurrent/GLIDERS_RETIREMENT.md` in a separate
   cleanup change. Until cutover, that source must remain available. Rollback before
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
