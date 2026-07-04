package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO pour le contenu de l'onglet Vue générale d'une résidence
 * Contient les informations générales et les contacts clés
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResidenceDetailDTO {
    // Champs directs depuis l'entité Residence
    private Long id;
    private String description;
    private String country;
    private Double latitude;
    private Double longitude;
    private LocalDate constructionDate;
    private LocalDate renovationDate;

    // Champs calculés
    // Niveau de sécurité obtenu en concaténant les noms des SecurityFeature actives
    // (exemple : "Gardiennage & CCTV"). Chaîne vide si aucune option associée.
    private String securityLevel;

    // Liste des contacts rattachés à cette résidence
    private List<KeyContactDTO> keyContacts;

    /**
     * DTO imbriqué pour les contacts clés
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class KeyContactDTO {
        private String fullName;
        private String role;
        private String email;
        private String phone;
        private String photo;
    }
}
