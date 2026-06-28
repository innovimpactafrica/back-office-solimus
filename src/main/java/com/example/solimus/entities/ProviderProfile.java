package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * PROFIL PRESTATAIRE
 * ============================================================================
 */
@Entity
@Table(name = "provider_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Identifiant unique du profil

    /**
     * Lien vers l'utilisateur.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user; // Utilisateur associé (relation 1-1)

    @Column(name = "company_name")
    private String companyName; // Nom de l'entreprise du prestataire

    @ManyToOne
    @JoinColumn(name = "specialty_id")
    private Specialty specialty; // Spécialité du prestataire (Plomberie, Électricité, etc.)

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude; // Latitude pour la géolocalisation

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude; // Longitude pour la géolocalisation

    @Column(name = "intervention_zone")
    private String interventionZone; // Zone d'intervention (ex: Paris, Lyon)

    // Position GPS temps réel — distincte de latitude/longitude qui restent la zone de référence fixe saisie à l'inscription
    @Column(name = "gps_latitude", precision = 10, scale = 7)
    private BigDecimal gpsLatitude; // Latitude de la position actuelle du prestataire

    @Column(name = "gps_longitude", precision = 10, scale = 7)
    private BigDecimal gpsLongitude; // Longitude de la position actuelle du prestataire

    @Column(name = "gps_updated_at")
    private LocalDateTime gpsUpdatedAt; // Horodatage de la dernière mise à jour GPS reçue

    @Column(name = "intervention_count")
    private Integer interventionCount = 0; // Nombre d'interventions réalisées

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // Date de création du profil

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // Date de dernière mise à jour
}