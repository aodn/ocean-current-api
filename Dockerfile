FROM openjdk:17-alpine
WORKDIR /app
COPY ./build/libs/ocean-current-*-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT [\
    "java",\
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", \
    "-Delasticsearch.host=${ES_HOST}",\
    "-Delasticsearch.apiKey=${ES_API_KEY}",\
    "-Dremote.baseUrl=${REMOTE_BASE_URL}",\
    "-Daws.region=${AWS_REGION}",\
    "-Daws.s3.bucket-name=${DATA_BUCKET}",\
    "-jar",\
    "/app/app.jar"]
