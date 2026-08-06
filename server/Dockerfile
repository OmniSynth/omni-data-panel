FROM eclipse-temurin:21-jdk-jammy AS build

# mvnw 解压发行包需要 unzip
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace/server

COPY server/.mvn .mvn
COPY server/mvnw server/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY server/src src
RUN ./mvnw -B -DskipTests package \
    && ./mvnw -B exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

FROM eclipse-temurin:21-jre-jammy

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        libglib2.0-0 \
        libnss3 \
        libnspr4 \
        libatk1.0-0 \
        libatk-bridge2.0-0 \
        libcups2 \
        libdrm2 \
        libdbus-1-3 \
        libxcb1 \
        libxkbcommon0 \
        libx11-6 \
        libxcomposite1 \
        libxdamage1 \
        libxext6 \
        libxfixes3 \
        libxrandr2 \
        libgbm1 \
        libpango-1.0-0 \
        libcairo2 \
        libasound2 \
        libatspi2.0-0 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system omni \
    && useradd --system --gid omni --home-dir /app --shell /usr/sbin/nologin omni

WORKDIR /app
COPY --from=build --chown=omni:omni /workspace/server/target/*.jar app.jar
COPY --from=build --chown=omni:omni /root/.cache/ms-playwright /ms-playwright
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

USER omni
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=10 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
