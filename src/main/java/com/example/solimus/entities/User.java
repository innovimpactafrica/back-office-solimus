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
    // NOTIFICATIONS PUSH
    // =========================================================================

    /**
     * Token FCM (Firebase Cloud Messaging).
     * Envoyé par l'app mobile au démarrage et stocké ici
     * pour savoir où livrer les notifications push de cet utilisateur.
     * Peut être null si l'utilisateur n'a jamais connecté l'app mobile.
     */
    @Column(name = "fcm_token")
    private String fcmToken; // token unique qui identifie le téléphone de cet utilisateur auprès de Firebase

    // =========================================================================
    // PRÉFÉRENCES DE NOTIFICATION
    // =========================================================================

    /**
     * Indique si l'utilisateur souhaite recevoir les notifications par email.
     * Par défaut : true (activé).
     * Si false, seuls les emails critiques (sécurité, activation, reset password) sont envoyés.
     */
    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;


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