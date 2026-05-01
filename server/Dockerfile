FROM gradle:8.13-jdk17 AS build

WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:17-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/build/libs/*.jar /app/guardian-plants-server.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/guardian-plants-server.jar"]
