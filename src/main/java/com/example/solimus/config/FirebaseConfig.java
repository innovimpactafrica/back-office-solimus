package com.example.solimus.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;

//On lit le fichier JSON qui contient nos identifiants, et on les utilise au démarrage pour connecter notre application Spring Boot à Firebase, une seule fois.

@Configuration // Spring détecte cette classe au démarrage et l'exécute automatiquement
public class FirebaseConfig {

    @PostConstruct // cette méthode sera appelée une seule fois, juste après que Spring ait démarré
    public void initFirebase() throws IOException { // throws IOException car on lit un fichier — ça peut échouer

        // on vérifie que Firebase n'est pas déjà initialisé — évite les doublons au redémarrage à chaud
        if (FirebaseApp.getApps().isEmpty()) {

            // on crée les identifiants Google
            GoogleCredentials credentials = GoogleCredentials
                    // en lisant depuis un flux de données (le fichier JSON)
                    .fromStream(
                            new ClassPathResource("firebase-service-account.json").getInputStream() // on pointe vers le fichier dans src/main/resources/
                    );

            // on prépare les options de connexion
            FirebaseOptions options = FirebaseOptions.builder()
                    // on dit à Firebase d'utiliser ces identifiants pour s'authentifier
                    .setCredentials(credentials)
                    // on finalise la construction des options
                    .build();

            // on initialise Firebase avec ces options — connexion établie
            FirebaseApp.initializeApp(options);
        }
    }
}