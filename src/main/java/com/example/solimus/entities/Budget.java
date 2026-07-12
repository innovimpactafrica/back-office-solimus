package com.example.solimus.entities;

import com.example.solimus.enums.BudgetStatus;
import com.example.solimus.enums.RepartitionMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * BUDGET PRÉVISIONNEL D'UNE RÉSIDENCE
 * ============================================================================
 * Un seul budget actif par résidence et par année.
 */
@Entity
@Table(
    name = "budgets",
    uniqueConstraints = {
        // Empêche deux budgets pour la même résidence la même année
        @UniqueConstraint(columnNames = {"residence_id", "annee"})
    }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference; // Référence unique du budget (ex: BUD-2026-123456)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    @Column(nullable = false)
    private Integer annee; // Ex: 2026

    /**
     * Statut du budget
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BudgetStatus status = BudgetStatus.DRAFT;

    /**
     * Mode de répartition des charges entre copropriétaires.
     * Seul Tantièmes est actif pour cette V1.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "repartition_mode", nullable = false)
    private RepartitionMode repartitionMode = RepartitionMode.OWNERSHIP_SHARES;

    /**
     * Budget total estimé = somme des montants de tous les BudgetItem.
     * Recalculé automatiquement à chaque ajout/suppression de poste.
     */
    @Column(name = "budget_total", nullable = false)
    private BigDecimal budgetTotal = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BudgetItem> items = new ArrayList<>();

    /**
     * Appels de charges générés à partir de ce budget.
     * Un budget peut avoir plusieurs ChargeCall (un par période).
     */
    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL)
    private List<ChargeCall> chargeCalls = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
