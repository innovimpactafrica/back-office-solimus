package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * QUOTE-PART ANNUELLE FIGÉE D'UN COPROPRIÉTAIRE POUR UN BUDGET
 * ============================================================================
 * Calculée une seule fois via ChargeAllocationUtil.distributeByLargestRemainder, au
 * moment où le budget devient définitif (création, ou toute modification légitime du
 * montant/des postes/de la résidence/du mode — tant qu'aucun appel de charges n'a encore
 * été généré). Plus jamais recalculée ensuite : c'est la seule source de vérité pour la
 * "charge annuelle théorique" affichée au syndic (fiche copropriétaire, onglet Finances,
 * détail du budget).
 *
 * Ne concerne que le mode OWNERSHIP_SHARES — en mode CUSTOM, il n'existe pas de quote-part
 * théorique annuelle : chaque appel a son propre montant saisi manuellement par le syndic.
 */
@Entity
@Table(
    name = "budget_co_owner_allocations",
    uniqueConstraints = {
        // Une seule quote-part figée par copropriétaire et par budget
        @UniqueConstraint(columnNames = {"budget_id", "co_owner_id"})
    }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BudgetCoOwnerAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "co_owner_id", nullable = false)
    private User coOwner;

    // Quote-part annuelle figée (FCFA entiers), calculée via distributeByLargestRemainder —
    // jamais recalculée après coup, contrairement à un simple champ dérivé
    @Column(name = "annual_quote_part", nullable = false)
    private BigDecimal annualQuotePart;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
