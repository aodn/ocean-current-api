# Gliders mission catalogue API

The API reads the version 1 catalogue produced by
[aodn/data-services PR #1276](https://github.com/aodn/data-services/pull/1276).
It uses `REMOTE_BASE_URL` + `REMOTE_RESOURCE_PATH` +
`gliders/gliders-catalogue.json`. The caller cannot override the source URL.
This catalogue is independent of image inventories and Elasticsearch.
Merging the upstream generator does not run or schedule it: an upstream operator
must generate and publish the JSON before the API can serve catalogue data.

## Endpoints and parameters

`GET /api/v1/gliders/missions` returns:

| Parameter | Default | Meaning |
| --- | --- | --- |
| `order` | `deployment_date` | One of `deployment_date`, `mission_name`, `distance_alongshore_to_darwin`, `event_based_sampling` |
| `q` | omitted | Case-insensitive substring of mission name or glider ID; surrounding whitespace is trimmed |
| `nrt` | omitted | `true` or `false` (case-insensitive); omitted includes both |
| `page` | `0` | Zero-based page, nonnegative integer |
| `size` | `25` | Integer from 1 to 100 |

Each order uses the upstream array's exact membership and sequence. Filtering
happens within that order before pagination. `total` counts all matching records
in that order, before pagination. No matches or a page beyond the end returns
`missions: []`. Event membership is never inferred and travelled distance is not
used to calculate distance alongshore to Darwin.

```bash
curl 'https://oceancurrent-edge.aodn.org.au/api/v1/gliders/missions?order=event_based_sampling&q=TwoRocks&nrt=false&page=0&size=25'
```

Illustrative response (fixture data, not a statement of current availability):

```json
{
  "missions": [
    {
      "id": "TwoRocks20260327@5.00",
      "mission_id": "TwoRocks20260327",
      "mission_name": "TwoRocks20260327",
      "catalogue_numbers": ["1"],
      "processing_version": "5.00",
      "is_nrt": false,
      "dates_raw": "27 Mar - 05 Apr 2026",
      "start_date": "2026-03-27",
      "end_date": "2026-04-05",
      "latitude": -31.5,
      "longitude": 115.3,
      "duration_days": 9.0,
      "distance_travelled_km": 130.0,
      "glider_id": "Slocum-1",
      "visualisations": {
        "details": [{"label": "4", "url": "https://oceancurrent.aodn.org.au/gliders/TwoRocks20260327/4.html"}],
        "animations": []
      },
      "temperature_salinity_url": null,
      "scientific_data_url": null
    }
  ],
  "total": 1,
  "page": 0,
  "size": 25,
  "order": "event_based_sampling"
}
```

`GET /api/v1/gliders/missions/{id}` returns one mission object, with the same
fields as an item above:

```bash
curl 'https://oceancurrent-edge.aodn.org.au/api/v1/gliders/missions/TwoRocks20260327@5.00'
```

IDs preserve the exact published name and processing-version string. For example,
`@5.00`, `@5.` and `@3.0.` are distinct suffixes; they are never parsed as numbers.
A missing processing version uses `@unknown`. Use the returned `id` as a single
URL path segment, applying normal URL encoding when constructing the request
(including encoding any literal percent characters already present in the ID).
`mission_id` associates NRT and non-NRT names; it is not a scientific deployment
identifier. `is_nrt: false` does not independently certify processing quality.

## Caching and errors

The service lazily loads one catalogue into an in-process Caffeine cache. Reads
share the snapshot, including requests with different filters or orders. After
one hour, the next read triggers an asynchronous refresh while serving the cached
snapshot. Configure this interval independently with
`GLIDERS_CATALOGUE_REFRESH_INTERVAL` (Spring duration such as `30m` or `2h`, minimum
1 second). Product cache settings and daily product cache eviction do not affect it.
There is no new scheduler and no persistent cache.

Refresh validates the HTTP response, JSON object, schema version, mission fields,
unique stable IDs, all four order arrays and their unique references. Optional
scalar values may be null and visualisation arrays may be empty. Empty event
membership is valid; the other three source views must be nonempty, as required
by the generator. Invalid data never replaces a valid snapshot. Failed refreshes
retain the last valid snapshot and subsequent reads can retry refreshing; there
is no maximum stale age. Restarting the API loses that snapshot.

The existing shared HTTP client's connect and read timeouts are both 30 seconds.
Initial loading waits for the remote request; refreshes run in the background.

| Status | Meaning |
| --- | --- |
| 200 | Matching missions, including an empty list for valid filters with no matches |
| 400 | Invalid order, NRT value, page or size |
| 404 | Unknown mission ID in an available catalogue |
| 503 | No valid catalogue is cached and upstream is unavailable, returns 404, times out, or supplies invalid/unsupported JSON |

Errors follow the existing `{"message": "...", "errors": ["..."]}` shape.
When upstream is unavailable, even an ID lookup returns 503 until a valid
catalogue can establish whether that ID exists. Failed initial loads are retried
on subsequent requests. Refresh failures are logged.

## Edge validation after publication

On 2026-10-19, the edge resource URL returned HTTP 404:
`https://oceancurrent-edge.aodn.org.au/resource/api/gliders-catalogue.json`.
No remote catalogue was assumed available during implementation.

After the upstream operator publishes the JSON and this API change is deployed
to edge through the normal review process:

1. Fetch `/resource/api/gliders-catalogue.json` on the edge host and confirm
   HTTP 200, `schema_version: 1`, nonempty missions, and the four order arrays.
   Confirm the edge API's `REMOTE_BASE_URL` and resource path target that resource.
2. Request `/api/v1/gliders/missions` and each supported order with `size=100`.
   Follow pages and compare IDs and order membership against the upstream arrays.
   Specifically compare event membership and the distance-alongshore sequence.
3. Look up an exact returned ID, including an NRT record and any trailing
   processing-version punctuation; compare all metadata with the upstream JSON.
4. Exercise `q`, `nrt`, multiple pages, an unmatched search, an unknown ID, and
   invalid parameters. Expect 200/empty, 404 and 400 as described above.
5. Inspect API logs and observe refresh after the configured interval. Repeated
   reads before the interval should use the cached snapshot. Recovery from an
   initial 503 requires a later request after the source becomes available.

Local isolated checks:

```bash
./gradlew test --tests '*GlidersCatalogueServiceTest' --tests '*GlidersMissionControllerTest'
```

The fixture in `src/test/resources/gliders/catalogue-v1.json` follows the merged
generator's schema and includes multiple versions, NRT, null optional values and
orders with different membership. These tests require no Elasticsearch or Docker.
