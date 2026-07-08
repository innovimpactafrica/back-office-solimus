# ════════════════════════════════════════════════════════════════════════════
# ÉTAPE 1 : BUILD MAVEN (compile le projet à partir du code source)
# ════════════════════════════════════════════════════════════════════════════

FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copie le pom.xml en premier pour profiter du cache Docker sur les dépendances
COPY pom.xml .

# Copie le code source
COPY src ./src

# Compile le projet et génère le jar (sans lancer les tests, pour un build plus rapide)
RUN mvn clean package -DskipTests

# ════════════════════════════════════════════════════════════════════════════
# ÉTAPE 2 : LIVRAISON (image finale légère avec juste le jar compilé)
# ════════════════════════════════════════════════════════════════════════════

# 1. Image JRE 17 légère
FROM eclipse-temurin:17-jre

# 2. Dossier de travail
WORKDIR /app

# 3. Installation de wget pour le healthcheck Docker
RUN apt-get update && \
    apt-get install -y wget && \
    rm -rf /var/lib/apt/lists/*

# 4. Copie du fichier JAR généré à l'étape "build" précédente (plus besoin de le pré-compiler en local)
COPY --from=build /app/target/back-office-solimus-0.0.1-SNAPSHOT.jar /app/app.jar

# 5. Port d'écoute
EXPOSE 8082

# 6. Lancement avec le profil docker
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]