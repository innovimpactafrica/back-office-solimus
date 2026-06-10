package com.example.solimus.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration Swagger/OpenAPI pour la documentation de l'API Solimus.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Solimus Back Office API")
                        .description("Documentation des APIs du système Solimus (Gestion des syndics, interventions, etc.)")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Bearer Authentication", createAPIKeyScheme()))
                // Ordre d'affichage des tags dans Swagger UI
                .addTagsItem(new Tag().name("1. Authentification"))
                .addTagsItem(new Tag().name("2. Administration"))
                .addTagsItem(new Tag().name("2.a Administration - Utilisateurs"))
                .addTagsItem(new Tag().name("2.b Administration - Spécialités"))
                .addTagsItem(new Tag().name("2.c Administration - Délais"))
                .addTagsItem(new Tag().name("3.  Copropriétaire"))
                .addTagsItem(new Tag().name("3.a Copropriétaire - Inscription"))
                .addTagsItem(new Tag().name("3.b Copropriétaire - Dashboard"))
                .addTagsItem(new Tag().name("3.c Copropriétaire - Charges"))
                .addTagsItem(new Tag().name("3.d Copropriétaire - Réunions"))
                .addTagsItem(new Tag().name("3.e Copropriétaire - Documents"))
                .addTagsItem(new Tag().name("3.f Copropriétaire - Profil"))
                .addTagsItem(new Tag().name("3.g Copropriétaire - Interventions"))
                .addTagsItem(new Tag().name("4. Syndic"))
                .addTagsItem(new Tag().name("4.a Syndic - Résidences"))
                .addTagsItem(new Tag().name("4.b Syndic - Biens"))
                .addTagsItem(new Tag().name("4.c Syndic - Interventions"))
                .addTagsItem(new Tag().name("4.d Syndic - Copropriétaires"))
                .addTagsItem(new Tag().name("4.e Syndic - Charges"))
                .addTagsItem(new Tag().name("4.f Syndic - Paiements"))
                .addTagsItem(new Tag().name("4.g Syndic - Retraits"))
                .addTagsItem(new Tag().name("4.h Syndic - Réunions"))
                .addTagsItem(new Tag().name("5. Prestataire"))
                .addTagsItem(new Tag().name("5.a Prestataire - Abonnement"));
    }

    /**
     * Crée le schéma de sécurité JWT pour Swagger.
     * Permet d'ajouter le token "Bearer <token>" dans l'en-tête Authorize de l'interface.
     */
    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");
    }

    /**
     * Force Swagger à toujours utiliser HTTPS et des URLs relatives.
     * 1. L'URL absolue est utilisée pour la production.
     * 2. L'URL relative ("/") permet à Swagger de s'adapter automatiquement à l'environnement local.
     * Cela évite les erreurs "Mixed Content" (blocage HTTP vers HTTPS).
     */
    @Bean
    public org.springdoc.core.customizers.OpenApiCustomizer forceHttpsCustomizer() {
        return openApi -> openApi.setServers(List.of(
                // En production, on force l'URL absolue en HTTPS pour éviter les erreurs de sécurité
                new Server().url("https://solimus.innovimpactdev.cloud").description("Serveur de Production (HTTPS)"),
                // En local ou test, on utilise l'URL relative "/" pour s'adapter automatiquement au port utilisé
                new Server().url("/").description("Serveur Relatif (Local/Auto)")
        ));
    }

    /**
     * Définit le groupe d'API exposé dans l'interface Swagger.
     * On filtre pour n'afficher que les endpoints commençant par "/api/**",
     * ce qui permet de masquer les endpoints internes de Spring Boot (comme /error).
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("solimus-api")
                .pathsToMatch("/api/**")
                .build();
    }
}
