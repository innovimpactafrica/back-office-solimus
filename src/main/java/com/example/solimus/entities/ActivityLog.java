package com.example.solimus.entities;

import com.example.solimus.enums.ActivityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * ============================================================================
 * JOURNAL D'ACTIVITÉ D'UNE RÉSIDENCE
 * ============================================================================
 * Une ligne par événement notable (paiement, intervention, appel de charges...).
  */
@Entity
@Table(name = "activity_logs")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * La résidence concernée par cet événement.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    /**
     * Type d'événement.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    /**
     * Lien générique vers l'entité concernée par cet événement.
     * Exemple : type = PAYMENT_RECEIVED → relatedEntityType = "PAYMENT", relatedEntityId = l'id du Payment.
     * Ce couple (type + id), plutôt qu'une colonne de relation JPA par type d'entité,
     * permet d'ajouter de nouveaux types d'activité (AG, document...) plus tard
     * sans modifier cette entité.
     */
    @Column(name = "related_entity_type")
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    /**
     * Qui a déclenché l'événement.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actor_id")
    private User actor;

    /**
     * Titre court affiché en gras
       */
    @Column(nullable = false)
    private String message;

    /**
     * Sous-texte affiché en dessous du message
     */
    @Column
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
