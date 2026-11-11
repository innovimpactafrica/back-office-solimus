# ════════════════════════════════════════════════════════════════════════════
# Image de PRODUCTION (Dokploy, mode « Application », build type Dockerfile).
# Les variables viennent UNIQUEMENT de l'onglet Environment de Dokploy (voir .env.example).
# ════════════════════════════════════════════════════════════════════════════

# ── Étape 1 : compilation Maven ──
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# ── Étape 2 : exécution (JRE seul, utilisateur non-root) ──
FROM eclipse-temurin:17-jre

# wget : utilisé par le HEALTHCHECK ci-dessous (absent de l'image de base)
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system solimus \
    && useradd --system --gid solimus --home-dir /app --shell /usr/sbin/nologin solimus

WORKDIR /app
COPY --from=build --chown=solimus:solimus /app/target/back-office-solimus-0.0.1-SNAPSHOT.jar /app/app.jar
USER solimus

# Mémoire calée sur la limite du conteneur ; arrêt net (et redémarrage par Swarm) en cas d'OutOfMemoryError.
# Surchargeable depuis Dokploy (variable JAVA_OPTS).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"

# Port d'écoute de l'API dans le conteneur : c'est CE port que le domaine Dokploy doit viser.
EXPOSE 8082

# Liveness seulement (ni base, ni SMTP, ni MinIO) : Swarm ne route que vers un conteneur sain,
# et une lenteur de la base partagée ne fait pas tuer l'API. start-period : démarrage ~40 s + marge.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8082/actuator/health/liveness || exit 1

# exec : Java reçoit directement SIGTERM, l'arrêt gracieux (server.shutdown=graceful) s'applique.
# Le profil « docker » est le profil de production.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=docker"]
