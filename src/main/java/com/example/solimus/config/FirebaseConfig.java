package com.example.solimus.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Initialise Firebase Admin SDK au démarrage de l'application,
 * à partir du fichier de credentials firebase-service-account.json.
 * Nécessaire pour que NotificationService.sendPush() puisse fonctionner.
 */
@Component
public class FirebaseConfig {

    @PostConstruct
    public void init() throws IOException {

        // Évite de réinitialiser Firebase si l'app redémarre en hot-reload (dev)
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        // Charge le fichier depuis le classpath (src/main/resources)
        InputStream serviceAccount = new ClassPathResource("firebase-service-account.json").getInputStream();

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        FirebaseApp.initializeApp(options);
    }
}
