# ════════════════════════════════════════════════════════════════════════════
# ÉTAPE UNIQUE : LIVRAISON (COPY DU JAR PRÉ-COMPILÉ)
# ════════════════════════════════════════════════════════════════════════════

# 1. Image JRE 17 légère
FROM eclipse-temurin:17-jre

# 2. Dossier de travail
WORKDIR /app

# 3. Installation de wget pour le healthcheck Docker
RUN apt-get update && \
    apt-get install -y wget && \
    rm -rf /var/lib/apt/lists/*

# 4. Copie du fichier JAR depuis le répertoire target (construit en local)
#    Utilisation du nom exact (sans wildcard)
COPY target/back-office-solimus-0.0.1-SNAPSHOT.jar /app/app.jar

# 5. Port d'écoute
EXPOSE 8082

# 6. Lancement avec le profil docker
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]