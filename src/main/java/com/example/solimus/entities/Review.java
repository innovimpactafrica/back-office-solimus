package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * ============================================================================
 * REVIEW - AVIS LAISSÉ APRÈS UNE INTERVENTION
 * ============================================================================
 * Une intervention terminée = un seul avis possible.
 * L'avis est laissé par le syndic OU le copropriétaire 
 * Il porte sur le prestataire sélectionné pour cette intervention.
 */
@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = "intervention_request_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * L'intervention concernée.
     * OneToOne car il ne peut y avoir qu'un seul avis par intervention.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_request_id", nullable = false, unique = true)
    private InterventionRequest interventionRequest;

    /**
     * Celui qui laisse l'avis : syndic ou copropriétaire.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    /**
     * Le prestataire noté.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    /**
     * Note entre 1 et 5 étoiles.
     */
    @Column(nullable = false)
    private Integer rating;

    /**
     * Commentaire optionnel.
     */
    @Column(columnDefinition = "TEXT")
    private String comment;

    // =========================================================================
    // AUDIT
    // =========================================================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
