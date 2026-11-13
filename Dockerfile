FROM mirror.gcr.io/library/maven:3.9-eclipse-temurin-21-jammy AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
        fontconfig \
        libfreetype6 \
        fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B


FROM mirror.gcr.io/library/eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
        fontconfig \
        libfreetype6 \
        fonts-dejavu-core \
        curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 sentinelcv \
    && useradd --system --uid 10001 --gid sentinelcv --home-dir /app --shell /usr/sbin/nologin sentinelcv

WORKDIR /app

COPY --from=builder /app/target/sentinelcv-1.0.0.jar /app/sentinelcv.jar

RUN mkdir -p /app/data/datasets /app/data/models /app/data/uploads /app/data/reports /app/data/temp /app/data/keys \
    && chown -R sentinelcv:sentinelcv /app

USER sentinelcv

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -fsS http://localhost:8080/ >/dev/null || exit 1

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "/app/sentinelcv.jar"]

