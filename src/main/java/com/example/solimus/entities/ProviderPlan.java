package com.example.solimus.entities;


import com.example.solimus.enums.ProviderPlanFeature;
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
//
//  PROVIDER PLAN — Configuration unique de l'abonnement prestataire
//
//  Il n'existe qu'UNE seule ligne dans cette table (id = 1 toujours).
//  L'admin peut modifier le nom, la description et les prix depuis son
//  espace "Abonnements > Prestataires"//
// =============================================================================
@Entity
@Table(name = "provider_plan")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProviderPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nom affiché de la formule (ex: "Premium").
     * Unique parmi les formules prestataire.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Texte descriptif affiché au prestataire (ex: "Accès complet, sans limite").
     * Limité à 250 caractères pour rester lisible dans l'espace UI restreint
     * (carte d'abonnement, écran de paiement mobile).
     */
    @Column(name = "description", length = 250)
    private String description;

    /**
     * Prix mensuel en FCFA. C'est CE champ qui est lu par le service de paiement
     */
    @Column(name = "monthly_price", nullable = false)
    private BigDecimal monthlyPrice;

    /**
     * Prix annuel en FCFA (optionnel — peut être null si l'admin ne propose
     * que du mensuel pour l'instant).
     */
    @Column(name = "yearly_price")
    private BigDecimal yearlyPrice;

    /**
     * Statut de la formule (permet de la retirer temporairement du catalogue sans la supprimer)
     */
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /**
     * Fonctionnalités incluses dans cette formule.
     */
    @ElementCollection(targetClass = ProviderPlanFeature.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "provider_plan_features", joinColumns = @JoinColumn(name = "provider_plan_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "feature")
    private Set<ProviderPlanFeature> features = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}