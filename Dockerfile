FROM amazoncorretto:17

WORKDIR /app

ENV ARTIFACT_NAME=ocean-current-0.0.1-SNAPSHOT.jar
COPY ./build/libs/$ARTIFACT_NAME /app/$ARTIFACT_NAME

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "$ARTIFACT_NAME"]
