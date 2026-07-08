package com.example.solimus.entities;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.enums.UrgencyLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ============================================================
// Signalement
// Représente un signalement créé par un copropriétaire, adressé au syndic.
// Peut être résolu directement, ou transformé en InterventionRequest si des travaux sont nécessaires.
// ============================================================
@Entity
@Table(name = "signalements")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Signalement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", unique = true)
    private String reference; // Référence unique, ex: SIG-2026-001

    @Column(nullable = false)
    private String title; // Titre court, ex: "Bruit excessif"

    @Column(columnDefinition = "TEXT")
    private String description; // Description détaillée du problème

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level", nullable = false)
    private UrgencyLevel urgencyLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private IncidentLocationType locationType; // Réutilisé depuis le module Travaux

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property; // Rempli si APPARTEMENT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "common_facility_id")
    private CommonFacility commonFacility; // Rempli si PARTIE_COMMUNE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner; // Copropriétaire qui a créé le signalement

    @ElementCollection
    @CollectionTable(name = "signalement_photos", joinColumns = @JoinColumn(name = "signalement_id"))
    @Column(name = "photo_url")
    private List<String> photoUrls = new ArrayList<>();

    // Note laissée par le syndic à la clôture (si résolu sans travaux)
    @Column(name = "closing_note", columnDefinition = "TEXT")
    private String closingNote;

    // Lien vers l'intervention créée si le signalement a été transformé en travaux
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_intervention_id")
    private InterventionRequest linkedIntervention;

    @OneToMany(mappedBy = "signalement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SignalementStatusHistory> history = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // Helper pour tracer un changement de statut, même pattern que InterventionRequest
    public void addStatusHistory(SignalementStatus newStatus, User user, String note) {
        this.status = newStatus;

        SignalementStatusHistory record = new SignalementStatusHistory();
        record.setSignalement(this);
        record.setStatus(newStatus);
        record.setChangedBy(user);
        record.setNote(note);

        this.history.add(record);
    }
}