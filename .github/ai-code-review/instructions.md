This repository is `ocean-current-api`, the REST API of the IMOS Ocean Current
website: Java 17, Spring Boot 3, Gradle, Lombok, the Elasticsearch Java client,
AWS SDK v2 (S3), SQLite via plain JDBC, Caffeine caches, JUnit 5, Mockito and
Testcontainers (Elasticsearch). It never serves images. It indexes metadata
about pre-rendered images on the legacy file server (from JSON index files
under `REMOTE_BASE_URL` + `/resource/`, plus surface-wave files in S3) into
Elasticsearch, and tells `ocean-current-frontend` which products, regions and
dates exist. All endpoints live under the `/api/v1` context path.

Pay particular attention to:

- Reindexing (`IndexingService`): a new timestamped index is built, validated
  (not empty, at least 80% of the old doc count, no productId from the old
  index missing) and only then swapped onto the alias, after which old indices
  are deleted. Check that failures leave the alias on the old index, that
  partial failures are logged rather than silently dropped, and that new
  indices are cleaned up on failure.
- Elasticsearch: query construction (term vs match, keyword fields, sorting,
  `size` limits and pagination), bulk request error handling, and document IDs
  from `DocumentIdGenerator` (`productId_region_fileName`), which collide when
  file names are not unique within a region.
- Product configuration: `config/products.yaml` (served to the frontend and
  used to validate `productId`, `region` and `depth`) and
  `config/json-paths-config.yaml` (what gets indexed) must stay consistent
  with each other, with `ProductConstants.PRODUCT_ID_MAPPINGS`, and with the
  product IDs the frontend uses. Renaming or removing a productId breaks the
  frontend and blocks the reindex validation.
- API contracts: changed response fields, status codes, request parameter
  names or defaults are regressions for `ocean-current-frontend`. Invalid
  input should return 4xx through `GlobalExceptionHandler`, not 500.
- Dates and file names: `latest-dates` only matches `\d{8,14}\.gif`; products
  encode dates in file names in different formats and resolutions. Watch for
  off-by-one days/months and timezone mistakes. `IndexingScheduler` and
  `CacheInvalidationScheduler` use Australia/Hobart; `SqliteDownloadScheduler`
  uses the server timezone.
- Caching: cache names come from `CacheNames`. Only `image-list` is evicted on
  reindex; the others stay until the 03:00 eviction. Check cache keys include
  every parameter the result depends on, and that cached values are not
  mutated.
- Remote calls (`RestTemplate`, S3, SQLite download): timeouts, error handling,
  resource and stream closing, and URLs built from user or remote data. Use
  `UrlUtils.maskSensitiveUrl` before logging URLs.
- Security: `SecurityConfig` permits all requests except `/monitoring/**`,
  which `Ec2InstanceAuthenticationFilter` protects with an EC2 instance
  identity signature in the `production` and `edge` profiles.
  `IndexingController` and `DebugController` must stay limited to the `dev`
  and `edge` profiles. Flag new endpoints that mutate state or expose internal
  data without that protection, SQL built by string concatenation in the
  SQLite services, and anything committed that looks like a credential
  (`.env*`, `application*.yaml`, keys).
- Configuration: profiles are `dev`, `edge`, `production` and `test`; a value
  added to `application.yaml` may need overriding in the profile files, the
  `Dockerfile` or `.env.sample`. `REMOTE_BASE_URL` must be `https://`.
- Tests: behaviour changes and bug fixes should have JUnit coverage under
  `src/test/java`, mirroring the main package. Product or JSON path changes
  should extend `ProductConfigIntegrationTest`, `JsonPathsConfigTest` and
  `ProductServiceTest`. Elasticsearch behaviour belongs in Testcontainers
  tests based on `ElasticsearchTestBase`, not in mocks only.

Reusable code, for the duplicate-implementation check (package root
`src/main/java/au/org/aodn/oceancurrent/`):

- Utilities: `util/` (`ProductIdUtils` for product ID versions, `UrlUtils`,
  `WaveFileValidator`), `util/elasticsearch/` (`BulkRequestProcessor`,
  `DocumentIdGenerator`) and `util/converter/` (`ImageMetadataConverter`).
- Constants: `constant/` (`CacheNames`, `ConfigConstants`, `ProductConstants`,
  `DateDirection`).
- Services: `service/` (`SearchService` for Elasticsearch queries,
  `RemoteJsonService` for remote JSON, `S3Service`, `SqliteBaseService`,
  `ProductService` for the product tree) and `service/tags/` (implement
  `ProductTagService` rather than adding product-specific code to
  `TagService`).
- Configuration and properties: `configuration/` (`@ConfigurationProperties`
  classes, `RestTemplateConfig`, `CacheConfig`).
- Exceptions: `exception/`, mapped to responses in `GlobalExceptionHandler`.

Only report duplication when the behaviour really matches, not when the two
only look alike. Name the existing code the PR should reuse, with its file
and line, and say whether it can be used as is or needs a small change.

Conventions: Lombok (`@RequiredArgsConstructor`, `@Slf4j`, `@Data`) with
constructor injection; config in `@ConfigurationProperties` classes under
`configuration/`; product knowledge in `products.yaml` and
`json-paths-config.yaml` rather than in code; new endpoints only when the
generic `image-list` endpoint cannot express the query; Conventional Commits.
Do not report issues the Java compiler would catch.
