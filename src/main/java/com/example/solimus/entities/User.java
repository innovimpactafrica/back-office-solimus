package com.example.solimus.entities;

import com.example.solimus.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * UTILISATEUR DU SYSTÈME SOLIMUS
 * ============================================================================
 *
 * Cette entité représente tous les utilisateurs de la plateforme :
 *
 * - Administrateur
 * - Syndic
 * - Copropriétaire
 * - Prestataire
 *
 * Certains champs sont spécifiques aux prestataires
 * (spécialité, coordonnées GPS, entreprise, etc.).
 */
@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    // =========================================================================
    // IDENTIFIANT UNIQUE
    // =========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================================
    // INFORMATIONS PERSONNELLES
    // =========================================================================

    @NotBlank(message = "Le prénom est obligatoire")
    @Column(nullable = false)
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Column(nullable = false)
    private String lastName;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(
            regexp = "^[+]?[0-9\\s\\-\\(\\)]{8,25}$",
            message = "Le numéro de téléphone doit être valide"
    )
    @Column(unique = true, nullable = false)
    private String phone;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * Mot de passe hashé.
     * Peut être null avant l'activation du compte.
     */
    @Column(nullable = true)
    private String password;


    // =========================================================================
    // INFORMATIONS PROFESSIONNELLES (PRESTATAIRE)
    // =========================================================================

    /**
     * Nom de l'entreprise du prestataire.
     */
    @Column(name = "company_name")
    private String companyName;

    /**
     * Spécialité du prestataire :
     * Plomberie, Électricité, Peinture, etc.
     */
    @ManyToOne
    @JoinColumn(name = "specialty_id")
    private Specialty specialty;

    /**
     * Coordonnées GPS utilisées pour calculer
     * la distance entre une résidence et un prestataire.
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * Zone d'intervention déclarée par le prestataire.
     */
    @Column(name = "intervention_zone")
    private String interventionZone;

    /**
     * Permet d'indiquer si un prestataire
     * est actuellement disponible pour recevoir
     * de nouvelles demandes d'intervention.
     */
    @Column(name = "is_available", nullable = false)
    private boolean isAvailable = true;

    // =========================================================================
    // RÔLE ET ÉTAT DU COMPTE
    // =========================================================================

    /**
     * Rôle de l'utilisateur :
     * ADMIN, SYNDIC, PRESTATAIRE, COPROPRIETAIRE...
     */
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * Statut du compte :
     * PENDING, ACTIVE, SUSPENDED...
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING;

    // =========================================================================
    // PHOTO DE PROFIL
    // =========================================================================

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;


    // =========================================================================
    // AUDIT (DATES AUTOMATIQUES)
    // =========================================================================

    /**
     * Date de création du compte.
     */
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm:ss")
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Dernière date de modification.
     */
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm:ss")
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}