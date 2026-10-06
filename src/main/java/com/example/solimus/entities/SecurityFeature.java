package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// =============================================================================
//
//  SECURITY FEATURE — Option de sécurité
//
//  Représente une option de sécurité disponible pour une résidence.
//  Gérée par le syndic via les paramètres.
//
//  Exemples :
//  → Vidéosurveillance
//  → Gardiens 24/7
//  → Contrôle d'accès
//
//  Le syndic choisit parmi ces options lors de la création ou modification d'une résidence.
//
// =============================================================================
@Entity
@Table(name = "security_features", uniqueConstraints = @UniqueConstraint(columnNames = {"label", "syndic_id"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SecurityFeature {

    // =========================================================================
    // IDENTIFIANT
    // =========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Syndic propriétaire de cette option — chaque syndic gère sa propre liste,
     * jamais partagée ni modifiable par un autre syndic.
     */
    @ManyToOne
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    // =========================================================================
    // INFORMATIONS
    // =========================================================================

    /**
     * Label affiché dans le formulaire et le dashboard.
     * Exemple : "Vidéosurveillance", "Gardiens 24/7"
     * Doit être unique pour un syndic donné — pas deux options identiques chez le même syndic.
     */
    @Column(nullable = false)
    private String label;

    /**
     * Description optionnelle de l'option de sécurité.
     * Exemple : "Caméras HD installées dans les parties communes"
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Indique si cette option est active et visible pour les syndics.
     * false → l'option est désactivée, elle n'apparaît plus
     *         dans le formulaire mais les résidences qui l'avaient gardent
     *         leur association.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * URL de l'icône stockée dans MinIO.
     * Optionnel, peut être null.
     */
    @Column
    private String icon;


    // =========================================================================
    // AUDIT
    // =========================================================================

    /**
     * Date de création par le syndic.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
