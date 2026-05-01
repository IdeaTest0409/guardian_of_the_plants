FROM gradle:8.13-jdk17 AS build

WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:17-jre

WORKDIR /app
ARG APP_VERSION=0.0.1-SNAPSHOT
ARG GIT_COMMIT=unknown
ARG BUILD_TIME=unknown
ARG APP_ENV=local
ENV APP_VERSION=${APP_VERSION}
ENV GIT_COMMIT=${GIT_COMMIT}
ENV BUILD_TIME=${BUILD_TIME}
ENV APP_ENV=${APP_ENV}
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/build/libs/*.jar /app/guardian-plants-server.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/guardian-plants-server.jar"]
