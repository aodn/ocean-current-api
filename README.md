# Ocean-Current

## Prerequisites

> JDK 17

## Version Info

> Spring Boot 3.3.4
>
> Gradle 8.10+

## Development

### Environment Variables

- expose to VM or set in IDEA

```shell
elasticsearch.host="your elasticsearch host"
elasticsearch.apiKey="your elasticsearch api key"
elasticsearch.maxResultWindow=20000
remote.json.baseURL="the remote json file server URL"
sqlite.remote-url="the sqlite db file URL"
aws.region="your aws region"
aws.s3.bucket-name="the-bucket-name"
aws.access-key-id="aws-access-key-id"
aws.secret-access-key="aws-secret-access-key"
```

- Or create a .env file in the root directory of the project.

```text
ES_HOST="your elasticsearch host"
ES_API_KEY="your elasticsearch api key"
ES_MAX_RESULT_WINDOW=20000
REMOTE_JSON_BASE_URL="the remote json file server URL"
SQLITE_REMOTE_URL="the sqlite db file URL"
AWS_REGION="your aws region"
AWS_S3_BUCKET_NAME="the-bucket-name"
AWS_ACCESS_KEY_ID="aws-access-key-id"
AWS_SECRET_ACCESS_KEY="aws-secret-access-key"
```

### Run

- By IDEA(IntelliJ, same below)

  Gradle -> Tasks -> application -> bootRun

- By CLI

  ```shell
  ./gradlew bootRun
  ```

### Test

- By IDEA

  Gradle -> Tasks -> verification -> test

- By CLI

  ```shell
  ./gradlew test
  ```

### Build

- By IDEA

  Gradle -> Tasks -> build -> clean

  Gradle -> Tasks -> build -> build

- By CLI

  ```shell
  ./gradlew clean build
  ```

## Documentation

For detailed documentation, see the `docs/` directory:

- **[SQLite Integration](docs/SQLITE_INTEGRATION.md)** - Complete guide to the SQLite wave tags integration, including API endpoints, configuration, and usage examples
- **[Product Tag Architecture](docs/PRODUCT_TAG_ARCHITECTURE.md)** - Technical architecture documentation for the extensible product tag service system


## Simple Architecture Diagram ( at 04/07/2025 )

```mermaid
graph TD

    2["User<br>External Actor"]
    3["AWS S3<br>Cloud Storage"]
    4["Elasticsearch<br>Search Engine"]
    5["Remote JSON Sources<br>External API"]
    6["SQLite Database<br>Embedded DB"]
    subgraph 1["Ocean Current API<br>Spring Boot / Java"]
        10["Application Configuration<br>Spring Config"]
        11["Background Schedulers<br>Spring Scheduling"]
        12["Global Exception Handler<br>Spring ControllerAdvice"]
        13["Data Models &amp; DTOs<br>Java POJO"]
        14["Utility Classes<br>Java"]
        7["OceanCurrentApplication<br>Spring Boot"]
        8["API Controllers<br>Spring Web"]
        9["Business Services<br>Spring Service"]
        %% Edges at this level (grouped by source)
        8["API Controllers<br>Spring Web"] -->|calls| 9["Business Services<br>Spring Service"]
        8["API Controllers<br>Spring Web"] -->|handles errors via| 12["Global Exception Handler<br>Spring ControllerAdvice"]
        10["Application Configuration<br>Spring Config"] -->|configures| 9["Business Services<br>Spring Service"]
        10["Application Configuration<br>Spring Config"] -->|configures| 11["Background Schedulers<br>Spring Scheduling"]
        11["Background Schedulers<br>Spring Scheduling"] -->|triggers| 9["Business Services<br>Spring Service"]
        7["OceanCurrentApplication<br>Spring Boot"] -->|initializes| 10["Application Configuration<br>Spring Config"]
        9["Business Services<br>Spring Service"] -->|uses| 13["Data Models &amp; DTOs<br>Java POJO"]
        9["Business Services<br>Spring Service"] -->|uses| 14["Utility Classes<br>Java"]
    end
    %% Edges at this level (grouped by source)
    2["User<br>External Actor"] -->|makes requests to| 1["Ocean Current API<br>Spring Boot / Java"]
    1["Ocean Current API<br>Spring Boot / Java"] -->|interacts with| 3["AWS S3<br>Cloud Storage"]
    1["Ocean Current API<br>Spring Boot / Java"] -->|interacts with| 4["Elasticsearch<br>Search Engine"]
    1["Ocean Current API<br>Spring Boot / Java"] -->|fetches data from| 5["Remote JSON Sources<br>External API"]
    1["Ocean Current API<br>Spring Boot / Java"] -->|accesses| 6["SQLite Database<br>Embedded DB"]
```
