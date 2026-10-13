package com.example.solimus.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration pour le client Minio (Stockage d'objets S3).
 * Aligné sur les propriétés définies dans application.properties.
 *
 * Si les clés d'accès sont vides (dev local sans MinIO), le bean retourne null
 * et un WARN est loggé. Les fonctionnalités de stockage seront désactivées.
 */
@Configuration
@Slf4j
public class MinioConfig {

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.access-key:}")
    private String accessKey;

    @Value("${minio.secret-key:}")
    private String secretKey;

    /**
     * Initialise le client Minio.
     * Retourne null si les credentials ne sont pas fournis (dev local).
     * Les services qui en dépendent doivent gérer le cas où le client est null.
     */
    @Bean
    public MinioClient minioClient() {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.warn("⚠️  MinioClient NON initialisé : minio.access-key ou minio.secret-key est vide. "
                    + "Le stockage de fichiers/images sera désactivé. "
                    + "Configurez MINIO_ACCESS_KEY et MINIO_SECRET_KEY pour activer cette fonctionnalité.");
            return null;
        }
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
    }
}
