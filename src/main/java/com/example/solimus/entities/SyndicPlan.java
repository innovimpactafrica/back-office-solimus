package com.example.solimus.entities;

import com.example.solimus.enums.SyndicPlanFeature;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

// =============================================================================
// SYNDIC PLAN — Une formule d'abonnement syndic (plusieurs formules possibles)
// Contrairement à ProviderPlan (un seul plan fixe), plusieurs SyndicPlan
// peuvent coexister (ex: Basic, Premium, Entreprise), chacune avec ses
// propres limites et fonctionnalités.
// =============================================================================
@Entity
@Table(name = "syndic_plan")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyndicPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nom affiché de la formule (ex: "Premium") — unique parmi les formules syndic
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    // Texte descriptif affiché au syndic
    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "monthly_price", nullable = false)
    private BigDecimal monthlyPrice;

    // Prix annuel optionnel — peut être null si seul le mensuel est proposé
    @Column(name = "yearly_price")
    private BigDecimal yearlyPrice;

    // Limites — si nullable signifie illimité pour ce critère précis
    @Column(name = "max_residences")
    private Integer maxResidences;

    @Column(name = "max_co_owners")
    private Integer maxCoOwners;

    @Column(name = "max_users")
    private Integer maxUsers;

    // Fonctionnalités activées pour cette formule précise
    @ElementCollection(targetClass = SyndicPlanFeature.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "syndic_plan_features", joinColumns = @JoinColumn(name = "syndic_plan_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "feature")
    private Set<SyndicPlanFeature> features = new HashSet<>();

    // Statut affiché ("Actif"/"Inactif" sur la maquette)
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}