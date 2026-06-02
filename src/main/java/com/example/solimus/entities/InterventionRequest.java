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
// InterventionRequest.java
// Représente une demande créée par le syndic.
// Le syndic choisit manuellement les prestataires à notifier.
// ============================================================
@Entity
@Table(name = "intervention_requests")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InterventionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterventionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InitiatedBy initiatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type")
    private IncidentLocationType locationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "management_mode")
    private InterventionManagementMode managementMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level")
    private UrgencyLevel urgencyLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syndic_id")
    private User syndic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id")
    private Specialty specialty;

    // Le prestataire finalement sélectionné (celui qui fera le travail)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_provider_id")
    private User selectedProvider;

    // Les prestataires choisis par le syndic pour recevoir cette demande
    @ManyToMany
    @JoinTable(
        name = "intervention_notified_providers",
        joinColumns = @JoinColumn(name = "intervention_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> notifiedProviders = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "intervention_photos", joinColumns = @JoinColumn(name = "intervention_id"))
    @Column(name = "photo_url")
    private List<String> photoUrls = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "intervention_work_photos", joinColumns = @JoinColumn(name = "intervention_id"))
    @Column(name = "work_photo_url")
    private List<String> workPhotoUrls = new ArrayList<>();

    @OneToMany(mappedBy = "interventionRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterventionComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "interventionRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterventionStatusHistory> history = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    // --- Gestion Financière ---
    // Montant total du devis accepté
    @Column(name = "total_amount")
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // Montant acompte déjà versé (0 si pas d'acompte)
    @Column(name = "deposit_amount")
    private BigDecimal depositAmount = BigDecimal.ZERO;

    // Solde restant à payer (Calculé automatiquement : totalAmount - depositAmount)
    @Column(name = "remaining_amount")
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    @PrePersist
    @PreUpdate
    public void calculateRemainingAmount() {
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
        if (depositAmount == null) depositAmount = BigDecimal.ZERO;
        this.remainingAmount = totalAmount.subtract(depositAmount);
    }

    /**
     * Helper pour mettre à jour le statut et tracer l'historique automatiquement
     */
    public void addStatusHistory(InterventionStatus newStatus, User user) {
        this.status = newStatus;
        InterventionStatusHistory record = new InterventionStatusHistory();
        record.setInterventionRequest(this);
        record.setStatus(newStatus);
        record.setChangedBy(user);
        this.history.add(record);
    }
}
