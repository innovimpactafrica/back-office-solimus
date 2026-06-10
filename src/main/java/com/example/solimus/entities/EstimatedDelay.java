package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité représentant un délai estimé pour une intervention.
 * Utilisée pour le calcul du score de recommandation des prestataires.
 */
@Entity
@Table(name = "estimated_delays")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EstimatedDelay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String label; // Ex: "Moins de 2h", "Dans la journée", etc.

    @Column(nullable = false, name = "days_equivalent", unique = true)
    private Integer days; // Équivalent en jours pour les calculs de score (ex: 1, 3, 7)
}
