package com.example.solimus.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Initialise Firebase Admin SDK au démarrage de l'application.
 * Nécessaire pour que NotificationService.sendPush() puisse fonctionner.
 *
 * Deux sources de credentials possibles, dans cet ordre :
 * 1. Le fichier firebase-service-account.json sur le classpath (src/main/resources) — pratique en
 *    dev local, mais volontairement exclu de Git (.gitignore) car c'est un secret.
 * 2. La variable d'environnement FIREBASE_SERVICE_ACCOUNT_JSON (contenu JSON brut du fichier) —
 *    utilisée en production/CI-CD, où le fichier gitignored n'arrive jamais sur le serveur déployé
 *    via un simple "git push". C'est la façon standard d'injecter un secret sans jamais le committer.
 *
 * @Lazy(false) : le profil "docker" active spring.main.lazy-initialization=true globalement, et
 * personne n'injecte FirebaseConfig nulle part (NotificationServiceImpl appelle FirebaseMessaging.
 * getInstance() en statique) — sans cette annotation, Spring ne crée jamais ce bean, donc @PostConstruct
 * ne s'exécute jamais, et Firebase reste silencieusement non initialisé ("FirebaseApp with name
 * [DEFAULT] doesn't exist" au premier envoi de push). Cette annotation force sa création au démarrage.
 */
@Component
@Lazy(false)
@Slf4j
public class FirebaseConfig {

    private static final String ENV_VAR_NAME = "FIREBASE_SERVICE_ACCOUNT_JSON";

    @PostConstruct
    public void init() throws IOException {

        // Évite de réinitialiser Firebase si l'app redémarre en hot-reload (dev)
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try (InputStream credentialsStream = loadCredentialsStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialisé avec succès");
        }
    }

    // Cherche d'abord le fichier du classpath (dev local), puis la variable d'environnement (prod/CI-CD)
    private InputStream loadCredentialsStream() throws IOException {

        ClassPathResource fileResource = new ClassPathResource("firebase-service-account.json");
        if (fileResource.exists()) {
            log.info("Credentials Firebase chargés depuis firebase-service-account.json (classpath)");
            return fileResource.getInputStream();
        }

        String jsonFromEnv = System.getenv(ENV_VAR_NAME);
        if (jsonFromEnv != null && !jsonFromEnv.isBlank()) {
            log.info("Credentials Firebase chargés depuis la variable d'environnement {}", ENV_VAR_NAME);
            return new ByteArrayInputStream(jsonFromEnv.getBytes(StandardCharsets.UTF_8));
        }

        // Ni l'un ni l'autre : erreur explicite plutôt qu'un NullPointerException ou un
        // "FirebaseApp with name [DEFAULT] doesn't exist" incompréhensible plus tard à l'usage
        throw new IOException("Aucun credential Firebase trouvé : ni firebase-service-account.json sur le "
                + "classpath, ni variable d'environnement " + ENV_VAR_NAME + ". Les notifications push ne "
                + "fonctionneront pas tant que l'un des deux n'est pas fourni.");
    }
}
