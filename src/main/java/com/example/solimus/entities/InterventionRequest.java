package com.example.solimus.entities;

import com.example.solimus.enums.InitiatedBy;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ============================================================
// InterventionRequest
// Représente une demande de travaux créé par le Syndic ou le copropriétaire 
// ============================================================
@Entity
@Table(name = "intervention_requests")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InterventionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Identifiant unique de la demande

    @Column(name = "reference", unique = true)
    private String reference; // Référence unique de la demande (ex: INT-2024-001)

    @Column(nullable = false)
    private String title; // Titre court de la demande (ex: Fuite d'eau)

    @Column(columnDefinition = "TEXT")
    private String description; // Description détaillée du problème

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterventionStatus status; // Statut actuel de la demande (PENDING, FINISHED, etc.)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InitiatedBy initiatedBy; // Qui a initié la demande (SYNDIC ou OWNER)

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type")
    private IncidentLocationType locationType; // Type de localisation (APPARTEMENT ou PARTIE_COMMUNE)

    @Enumerated(EnumType.STRING)
    @Column(name = "management_mode")
    private InterventionManagementMode managementMode; // Mode de gestion (SYNDIC_GERE ou AUTO_GERE)

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level")
    private UrgencyLevel urgencyLevel; // Niveau d'urgence (LOW, MEDIUM, HIGH)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = true)
    private User syndic; // Syndic qui a créé la demande (si initiatedBy = SYNDIC)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private User owner; // Copropriétaire qui a créé la demande (si initiatedBy = OWNER)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence; // Résidence concernée par l'intervention

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "property_id")
    private Property property; // Bien/appartement concerné (si locationType = APPARTEMENT)


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "common_facility_id")
    private CommonFacility commonFacility; // Équipement commun concerné (ex: Piscine, Ascenseur)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "specialty_id")
    private Specialty specialty; // Spécialité requise (ex: Plomberie, Électricité)

    // Le prestataire finalement sélectionné (celui qui fera le travail)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "selected_provider_id")
    private User selectedProvider; // Prestataire sélectionné pour exécuter les travaux

   // Prestataires notifiés de cette demande
    @ManyToMany
    @JoinTable(
        name = "intervention_notified_providers",
        joinColumns = @JoinColumn(name = "intervention_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> notifiedProviders = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "intervention_photos", joinColumns = @JoinColumn(name = "intervention_id"))
    @Column(name = "photo_url")
    private List<String> photoUrls = new ArrayList<>(); // Photos du problème initial

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "intervention_work_photos", joinColumns = @JoinColumn(name = "intervention_id"))
    @Column(name = "work_photo_url")
    private List<String> workPhotoUrls = new ArrayList<>(); // Photos des travaux en cours/terminés

    @OneToMany(mappedBy = "interventionRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterventionComment> comments = new ArrayList<>(); // Commentaires sur l'intervention

    @OneToMany(mappedBy = "interventionRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterventionStatusHistory> history = new ArrayList<>(); // Historique des changements de statut

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // Date de création de la demande

    @Column(name = "started_at")
    private LocalDateTime startedAt; // Date de début des travaux

    @Column(name = "finished_at")
    private LocalDateTime finishedAt; // Date de fin des travaux

    @Column(name = "validated_at")
    private LocalDateTime validatedAt; // Date de validation finale par le syndic

    @Column(name = "quote_accepted_at")
    private LocalDateTime quoteAcceptedAt; // Date d'acceptation du devis

    // --- Gestion Financière ---
    // Montant total du devis accepté
    @Column(name = "total_amount")
    private BigDecimal totalAmount = BigDecimal.ZERO; // Montant total du devis accepté

    // Montant acompte déjà versé (0 si pas d'acompte)
    @Column(name = "deposit_amount")
    private BigDecimal depositAmount = BigDecimal.ZERO; // Montant de l'acompte versé

    // Solde restant à payer (Calculé automatiquement : totalAmount - depositAmount)
    @Column(name = "remaining_amount")
    private BigDecimal remainingAmount = BigDecimal.ZERO; // Solde restant à payer

    // Calcul automatique du solde restant avant persistance
    //Avant de sauvegarder ou modifier l’intervention, on recalcule automatiquement le solde restant
    @PrePersist
    @PreUpdate
    public void calculateRemainingAmount() {
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
        if (depositAmount == null) depositAmount = BigDecimal.ZERO;
        this.remainingAmount = totalAmount.subtract(depositAmount);
    }

     // Helper pour mettre à jour le statut et tracer l'historique automatiquement
     public void addStatusHistory(InterventionStatus newStatus, User user) {
         // Met à jour le statut actuel de l'intervention
         this.status = newStatus;

         // Crée un nouvel enregistrement d'historique
         InterventionStatusHistory record = new InterventionStatusHistory();
         record.setInterventionRequest(this); // Lie l'enregistrement à l'intervention
         record.setStatus(newStatus); // Définit le nouveau statut
         record.setChangedBy(user); // Définit l'utilisateur qui a fait le changement

         // Ajoute l'enregistrement à la liste d'historique
         // (sera automatiquement persisté lors du save de l'intervention grâce à CascadeType.ALL)
         this.history.add(record);
     }
}
