package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Évaluation (note et commentaire) laissée par un syndic ou un copropriétaire sur le travail d'un prestataire.
 * Cette note est liée à une demande d'intervention spécifique.
 */
@Entity
@Table(name = "provider_ratings", uniqueConstraints = @UniqueConstraint(columnNames = "intervention_request_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1. La relation vers la Demande d'intervention (pour lier la note à un travail précis)
    // OneToOne car il ne peut y avoir qu'une seule note finale par intervention
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_request_id", nullable = false, unique = true)
    private InterventionRequest interventionRequest;

    // 2. La relation vers le Prestataire évalué
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    // 3. La relation vers la personne qui donne la note (Syndic ou Copropriétaire)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private User evaluator;

    // 4. Une note (ex: de 1 à 5)
    @Column(nullable = false)
    private Integer rating;

    // 5. Un commentaire textuel (optionnel)
    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
