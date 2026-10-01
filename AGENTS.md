# Ocean Current API Guidelines

Guidance for AI agents working in this repository.

## What this service is

Spring Boot 3 (Java 17, Gradle) REST API for the [Ocean Current](https://oceancurrent.aodn.org.au/) website redevelopment. Its main job is **data availability**: telling the frontend which pre-rendered images exist for each product, region and date.

The images are produced by a third party (CSIRO) and live on the **legacy Apache EC2 server**, which also still serves the old PHP site. This API never serves images. It indexes image _metadata_ into Elasticsearch. The React frontend ([aodn/ocean-current-frontend](https://github.com/aodn/ocean-current-frontend)) then loads the images itself through its `/resource` proxy.

```
legacy EC2  /mnt/oceancurrent/website/**.gif
   │  daily cron (00:00): aodn/data-services ARGO/oceancurrent/oceancurrent_file_server_api.py
   │  scans folders per FILE_PATH_CONFIG → writes JSON index files next to the images
   ▼
this API  (scheduled reindex)
   │  fetches each JSON in config/json-paths-config.yaml via  REMOTE_BASE_URL + /resource/ + path
   │  + lists surface-wave files in S3 (WAVES/ prefix)
   │  → bulk-indexes one ES document per image into a new timestamped index → validate → alias swap
   ▼
aodn/ocean-current-frontend  calls /api/v1/metadata/... and /api/v1/products
```

Related repos:

| Repo                                                                          | Role                                                                                                                                                                                                                               |
| ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [aodn/ocean-current-frontend](https://github.com/aodn/ocean-current-frontend) | React consumer of this API. Its product IDs must match `products.yaml` (or be mapped in its `src/configs/products/id-mapping.ts`).                                                                                                 |
| [aodn/data-services](https://github.com/aodn/data-services)                   | Only the `ARGO/oceancurrent/` folder is ours. It holds the scanner that produces the JSON this API indexes. The cron on the EC2 runs the script straight from that repo's `master` branch, so **merging there is its deployment**. |
| [aodn/appdeploy](https://github.com/aodn/appdeploy)                           | Infrastructure deployment workflow triggered by this repo's CD.                                                                                                                                                                    |
| [aodn/backlog](https://github.com/aodn/backlog)                               | All issues and tickets. Branch names reference these issue numbers.                                                                                                                                                                |

## Commands

```bash
./gradlew bootRun                         # run locally (port 8080, context path /api/v1)
./gradlew test                            # all tests — Docker must be running (Testcontainers Elasticsearch)
./gradlew test --tests "*SearchService*"  # single class / pattern
./gradlew clean build                     # what CI runs (build + tests)
./gradlew buildWithoutTests               # bootJar without tests
docker compose up --build                 # build and run the jar in Docker with .env
```

- Swagger UI: `http://localhost:8080/api/v1/swagger-ui/index.html`. It is enabled everywhere except the `production` profile.
- Actuator lives under `/api/v1/manage`.

## Configuration

**Profiles** (`SPRING_PROFILES_ACTIVE`): `dev`, `edge`, `production`, plus `test` for tests.

- `application.yaml` holds the defaults. Each `application-{profile}.yaml` overrides them and optionally imports `.env`, `.env.dev` or `.env.edge`.
- `config/products.yaml` is imported as config. Bind it via `ProductProperties`.

Environment variables actually read (see `application.yaml` and `Dockerfile`):

| Var                                                                                                        | Purpose                                                                                                                                                                                                                                                      |
| ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `ES_HOST`, `ES_API_KEY`                                                                                    | Elasticsearch (Elastic Cloud)                                                                                                                                                                                                                                |
| `INDEX_NAME`                                                                                               | Index alias. Default `ocean-current-files`; the edge and production profiles set `-edge` / `-prod`.                                                                                                                                                          |
| `REMOTE_BASE_URL`                                                                                          | **Must be `https://`**; validated at startup. Remote reads use this base URL plus `REMOTE_RESOURCE_PATH` (default `/resource/`). That covers the JSON index files, the waves SQLite DB (`waves/index.db`), and Argo `profiles/map/YYYYMMDD.gif` HEAD probes. |
| `AWS_REGION`, `AWS_S3_BUCKET_NAME` (`DATA_BUCKET` in Docker), `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | S3 listing for surface-waves files                                                                                                                                                                                                                           |
| `AUTHORISED_INSTANCE_IDS`                                                                                  | Comma-separated EC2 instance IDs allowed to call `/monitoring`                                                                                                                                                                                               |
| `ES_SKIP_PRODUCT_ID_CHECK`                                                                                 | Bypass the "no productId may disappear" reindex check (see below)                                                                                                                                                                                            |

The variable names in `.env.sample` and `README.md` are partly stale. `REMOTE_JSON_BASE_URL` and `SQLITE_REMOTE_URL` are not read by anything; use `REMOTE_BASE_URL`. The README's Spring Boot version is also stale; `build.gradle` is the source of truth.

## Architecture

Package root: `au.org.aodn.oceancurrent`.

**Products** are declared in `src/main/resources/config/products.yaml`:

- The file is a tree of standalone products and `ProductGroup`s with `children`.
- A leaf can set `regionRequired` (default `true`) and `depthRequired` (default `false`).
- `ProductController` serves the tree (`/products`, `/products/leaf`, `/products/validate/{id}`).
- `ImageMetadataController` uses the tree to validate `productId`, `region` and `depth` params.

**What gets indexed** is set by `src/main/resources/config/json-paths-config.yaml`. It lists JSON paths on the remote server, grouped by product. Each JSON file is an array of `{path, productId, region, depth, files:[{name}]}` groups. `IndexingService` flattens these into one `ImageMetadataEntry` document per file.

- **Doc ID** = `productId_region_fileName`, lower-cased and sanitised (`DocumentIdGenerator`). Two files with the same product, region and name collide, so choose `region` so that filenames are unique within it.
- `ProductConstants.PRODUCT_ID_MAPPINGS` rewrites some scanner productIds at index time (e.g. `oceanColour-chlA-year` → `oceanColour-chlA`).

**Reindex flow** (`IndexingService.reindexAll`):

1. Create a new timestamped index.
2. Index the remote JSON files.
3. Index the S3 surface-wave files (`S3Service`, `WAVES/` prefix, productId `surfaceWaves-wave`).
4. Refresh the index, then validate it, then swap the alias atomically, then delete the old indices.

Validation fails the whole reindex (the alias stays on the old index) when:

- the new index is empty; or
- the new doc count is below 80% of the old count; or
- **any productId present in the old index is missing from the new one**.

Failed JSON fetches are logged and skipped; reindexing can still pass if the remaining data meets validation. A missing productId or enough missing documents blocks the alias swap. Check logs after changing `json-paths-config.yaml` or the upstream scanner. To rename or remove a productId on purpose, run the `Skip ProductId Check - Edge` workflow (`.github/workflows/deploy_skip_product_id_check_edge.yaml`), or set `ES_SKIP_PRODUCT_ID_CHECK`. This bypasses only the productId check, not the count checks.

**Schedulers** (Indexing and cache eviction use Australia/Hobart; SQLite download uses the server timezone):

| Scheduler                    | When                                                                                                                                 |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `IndexingScheduler`          | Default 02:00 daily. Edge: every 2 h from 01:00. Production: every 2 h from 01:30.                                                   |
| `SqliteDownloadScheduler`    | Every 2 h by default (server timezone). Downloads the wave-tag SQLite DB to `data/sqlite/index.db`, which is queried via plain JDBC. |
| `CacheInvalidationScheduler` | 03:00, clears all Caffeine caches.                                                                                                   |

Note that only `image-list` is evicted on reindex. `latest-files`, `current-meters-plot-list` and `buoy-time-series` can stay stale until 03:00.

**Endpoints** (all under `/api/v1`):

- `/metadata/*` (`ImageMetadataController`)
  - Generic: `image-list/{productId}?region=&depth=`, `latest-dates/{productId}`, `search?productId&region&date&size` (items around a centre date).
  - Product-specific: Tidal Currents month plots, buoy time series, current-meters plots, and `latest-dates/argo` (HEAD-probes the remote server).
  - `latest-dates` only considers filenames matching `\d{8,14}\.gif`.
- `/products/*` serves the product tree.
- `/tags/*` (`TagController` → `TagService`) is a coordinator that auto-discovers every `ProductTagService` bean. `SurfaceWavesTagService` is the real implementation; `ArgoTagService` is a stub. To add a product, implement `ProductTagService` and annotate it `@Service`. See `docs/PRODUCT_TAG_ARCHITECTURE.md`.
- `/monitoring/fatal-log` receives failure reports from the EC2 scanner (and `scripts/trigger_fatal_log.py`).
- `/indexing/*` (manual reindex, S3 index, delete index) and `/debug/*` exist **only in the `dev` and `edge` profiles**, and have no auth there. They are not deployed in production; production indexing is scheduler-only.

**Security:**

- `SecurityConfig` permits all requests, is stateless, and has CSRF disabled. CORS origins come from `app.cors.*` per profile.
- The only authenticated path is `/api/v1/monitoring/**`. `Ec2InstanceAuthenticationFilter` (active in the `production` and `edge` profiles) verifies an EC2 instance-identity PKCS7 signature against `src/main/resources/aws/ec2-instance-identity.pem` and checks the instance against `AUTHORISED_INSTANCE_IDS`. See `docs/EC2_AUTHENTICATION_GUIDE.md`.
- `TrailingSlashNormalizationFilter` maps `/x/` to `/x`. Unmatched paths return 404, not 500.

## Adding a new image product (the common change)

This is usually **config plus tests only**; no new endpoint is needed.

1. Upstream first: add the scanner rule in [aodn/data-services/ARGO/oceancurrent/oceancurrent_file_server_api.py](https://github.com/aodn/data-services/blob/master/ARGO/oceancurrent/oceancurrent_file_server_api.py), merge it to `master`, and wait for a cron run. Confirm the JSON is reachable under `REMOTE_BASE_URL` plus `REMOTE_RESOURCE_PATH`.
2. Here: add the group or leaf to `products.yaml`, setting `regionRequired`/`depthRequired` where needed. Add the JSON path(s) to `json-paths-config.yaml`.
3. Tests: extend `ProductConfigIntegrationTest`, `JsonPathsConfigTest` and `ProductServiceTest`.
4. Deploy only after step 1 is live. Missing upstream data can make the new index fail validation or leave the new product empty.

Reference: the FishSOOP change, PR #78 (`aodn/backlog#8577`).

## Tests

- JUnit 5, Mockito and Spring Boot Test.
- `ElasticsearchTestBase`, `IndexingServiceIntegrationTest` and `SampleElasticsearchTest` start a real Elasticsearch through **Testcontainers**, using the ES version in `src/test/resources/application-test.yaml`. Docker is required locally and in CI.
- The `test` profile disables the production `ElasticsearchConfig` and `OpenApiConfig`. Test resources override `application*.yaml`.

## CI/CD and environments

| Workflow             | Trigger                          | Does                                                                                                                                                                             |
| -------------------- | -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ci.yaml`            | push / PR to `main`              | `./gradlew clean build`                                                                                                                                                          |
| `cd_edge.yaml`       | CI workflow completion on `main` | jar → Docker → ECR (`:sha`, `:latest`) → SSM `/apps/oceancurrent/edge/image_digest` → dispatch `aodn/appdeploy` (edge). The workflow currently does not check the CI conclusion. |
| `cd_production.yaml` | tag `v*.*.*`                     | same, image tagged with the version, metadata attached to the GitHub release → `aodn/appdeploy` (`oceancurrent/ecs`, production)                                                 |

Release to production by pushing a semver tag (e.g. `v0.3.5`) after verifying on edge.

| Env                    | Base URL                                       |
| ---------------------- | ---------------------------------------------- |
| Edge                   | `https://oceancurrent-edge.aodn.org.au/api/v1` |
| Production (beta site) | `https://oceancurrent-beta.aodn.org.au/api/v1` |

## Conventions

- **Branches:** `feature/<backlog-issue>-<desc>`, `bugfix/<issue>-<desc>`, `chore/<issue>-<desc>`. Issue numbers refer to `aodn/backlog`.
- **Commits:** Conventional Commits (`feat:`, `fix:`, `test:`, `chore:`). PRs are merged with merge commits.
- Use Lombok (`@RequiredArgsConstructor`, `@Slf4j`, `@Data`) and constructor injection. Config goes in `@ConfigurationProperties` classes under `configuration/`.
- Keep product knowledge in `products.yaml` / `json-paths-config.yaml` rather than in code. Special-case endpoints exist (current meters, tidal month plots, buoy time series), but add new ones only when the generic `image-list` cannot express the query.
- Never commit `.env*` files with real values, or anything under `data/`.
