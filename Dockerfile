# ════════════════════════════════════════════════════════════════════════════
# ÉTAPE 1 : LA CUISSON (LE BUILD)
# ════════════════════════════════════════════════════════════════════════════

# 1. Image avec Java 17 et Maven pour compiler
FROM maven:3.9.9-eclipse-temurin-17 AS build

# 2. Dossier de travail dans le conteneur
WORKDIR /app

# 3. Copie du fichier pom.xml
COPY pom.xml .

# 4. Téléchargement des dépendances (mise en cache)
RUN mvn dependency:go-offline

# 5. Copie du code source
COPY src ./src

# 6. Compilation et création du fichier JAR (sans exécuter les tests)
RUN mvn clean package -DskipTests

# ════════════════════════════════════════════════════════════════════════════
# ÉTAPE 2 : LA LIVRAISON (L'EXÉCUTION)
# ════════════════════════════════════════════════════════════════════════════

# 7. Image JRE 17 légère pour l'exécution
FROM eclipse-temurin:17-jre

# 8. Dossier de travail
WORKDIR /app

# 9. Installation de wget pour le healthcheck Docker
#    (utile pour que Dockploy puisse vérifier que l'application est saine)
RUN apt-get update && \
    apt-get install -y wget && \
    rm -rf /var/lib/apt/lists/*

# 10. Copie du fichier JAR généré à l'étape 1
COPY --from=build /app/target/*.jar /app/app.jar

# 11. Port d'écoute
EXPOSE 8082

# 12. Lancement de l'application avec la configuration docker
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.config.location=classpath:/application-docker.properties"]