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
```
- Or create a .env file in the root directory of the project.

```shell
ES_HOST="your elasticsearch host"
ES_API_KEY="your elasticsearch api key"
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
