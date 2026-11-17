# JVM image. For the native image see src/main/docker/Dockerfile.native.

# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package -DskipTests

# --- run ---
FROM eclipse-temurin:21-jre
RUN useradd --system app
WORKDIR /app
COPY --from=build --chown=app /build/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=app /build/target/quarkus-app/*.jar ./
COPY --from=build --chown=app /build/target/quarkus-app/app/ ./app/
COPY --from=build --chown=app /build/target/quarkus-app/quarkus/ ./quarkus/
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
