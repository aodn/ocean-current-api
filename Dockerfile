FROM openjdk:17-alpine
WORKDIR /app
COPY ./build/libs/ocean-current-*-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT [\
    "java",\
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", \
    "-Delasticsearch.host=${ES_HOST}",\
    "-Delasticsearch.apiKey=${ES_API_KEY}",\
    "-Delasticsearch.remote.json.baseUrl=${REMOTE_JSON_BASE_URL}",\
    "-jar",\
    "/app/app.jar"]
