FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace/server

COPY server/.mvn .mvn
COPY server/mvnw server/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY server/src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system omni \
    && useradd --system --gid omni --home-dir /app --shell /usr/sbin/nologin omni

WORKDIR /app
COPY --from=build --chown=omni:omni /workspace/server/target/*.jar app.jar

USER omni
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=10 \
    CMD timeout 3 bash -c '</dev/tcp/127.0.0.1/8080'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
