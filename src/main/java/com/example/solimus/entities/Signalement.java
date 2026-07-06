package com.example.solimus.entities;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementEventType;
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

@Entity
@Table(name = "signalements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Signalement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reference; // ex: SIG-2025-001

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private SignalementStatus status; // EN_ATTENTE, EN_TRAVAUX, TRAITE

    @Enumerated(EnumType.STRING)
    private UrgencyLevel urgencyLevel; // réutilisé depuis le module Travaux

    @Enumerated(EnumType.STRING)
    private IncidentLocationType locationType; // réutilisé depuis le module Travaux

    @ManyToOne
    private Residence residence;

    @ManyToOne
    private Property property; // rempli si APPARTEMENT

    @ManyToOne
    private CommonFacility commonFacility; // rempli si PARTIE_COMMUNE

    @ManyToOne
    private User declaredBy; // le copropriétaire

    @ElementCollection
    private List<String> photoUrls = new ArrayList<>();

    @ManyToOne
    private InterventionRequest interventionRequest; // rempli si transformé en travaux

    @Column(columnDefinition = "TEXT")
    private String noteCloture; // note de clôture lors de la résolution sans travaux

    @OneToMany(mappedBy = "signalement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SignalementHistorique> historique = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void addHistorique(SignalementEventType type, String commentaire, User auteur) {
        this.status = mapEventToStatus(type);

        SignalementHistorique record = new SignalementHistorique();
        record.setSignalement(this);
        record.setTypeEvenement(type);
        record.setCommentaire(commentaire);
        record.setAuteur(auteur);

        this.historique.add(record);
    }

    private SignalementStatus mapEventToStatus(SignalementEventType type) {
        return switch (type) {
            case CREATION -> SignalementStatus.PENDING;
            case TRANSFORME_EN_TRAVAUX -> SignalementStatus.IN_TRAVAUX;
            case RESOLU_SANS_TRAVAUX, TRAVAUX_TERMINES -> SignalementStatus.RESOLVED;
        };
    }
}
