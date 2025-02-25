FROM amazoncorretto:17

WORKDIR /app
COPY ./build/libs/ocean-current-*-SNAPSHOT.jar /app/app.jar

# Create a non-privileged user using a method that works in minimal images
RUN set -eux; \
    echo "appuser:x:1001:1001::/app:/sbin/nologin" >> /etc/passwd && \
    echo "appgroup:x:1001:" >> /etc/group && \
    chown -R 1001:1001 /app && \
    chmod -R 755 /app

# Switch to non-root user
USER 1001:1001

EXPOSE 8080

ENTRYPOINT [\
    "java",\
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", \
    "-Delasticsearch.host=${ES_HOST}",\
    "-Delasticsearch.apiKey=${ES_API_KEY}",\
    "-Delasticsearch.remote.json.baseUrl=${REMOTE_JSON_BASE_URL}",\
    "-jar",\
    "/app/app.jar"]
