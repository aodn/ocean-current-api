# Dockerfile for Ocean Current API
# Environment Variables Required:
#   - SPRING_PROFILES_ACTIVE: Spring profile (prod, edge)
#   - ES_HOST: Elasticsearch host
#   - ES_API_KEY: Elasticsearch API key
#   - REMOTE_BASE_URL: Remote Server base URL
#   - AWS_REGION: AWS region
#   - DATA_BUCKET: AWS S3 bucket name
#   - AUTHORISED_INSTANCE_IDS: Comma-separated EC2 instance IDs (e.g., "i-xxx,i-yyy")
#

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
    "-Dapp.monitoring-security.authorised-instance-ids=${AUTHORISED_INSTANCE_IDS}",\
    "-jar",\
    "/app/app.jar"]
