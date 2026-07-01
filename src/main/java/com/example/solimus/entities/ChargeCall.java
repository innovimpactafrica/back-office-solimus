package com.example.solimus.entities;

import com.example.solimus.enums.ChargeCallStatus;
import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.enums.RepartitionMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * APPEL DE CHARGES (TRIMESTRIEL OU MENSUEL)
 * ============================================================================
 */
@Entity
@Table(
    name = "charge_calls",
    uniqueConstraints = {
        // Empêche de générer deux fois l'appel pour la même période du même budget
        @UniqueConstraint(columnNames = {"budget_id", "annee", "period_number"})
    }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChargeCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================================
    // SOURCE
    // =========================================================================

    /**
     * Le budget source dont est issu cet appel de charges.
     * Un budget peut avoir plusieurs ChargeCall (un par période).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    // =========================================================================
    // PÉRIODE
    // =========================================================================

    /**
     * Fréquence au moment de la génération.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeFrequency frequency;

    @Column(name = "annee", nullable = false)
    private Integer year;

    /**
     * Numéro de la période dans l'année.
     * 1 à 4 si frequency = TRIMESTRIEL, 1 à 12 si MENSUEL.
     * Le libellé d'affichage ("T1-2026") est calculé côté DTO.
     */
    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    // =========================================================================
    // RÉPARTITION ET MONTANT
    // =========================================================================

    /**
     * Mode de répartition au moment de la génération.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "repartition_mode", nullable = false)
    private RepartitionMode repartitionMode;

    /**
     * Montant total à collecter pour cette période, figé au moment du
     * "Générer & Envoyer".
     */
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    // =========================================================================
    // DATES
    // =========================================================================

    /**
     * Date d'envoi, choisie par le syndic dans le modal.
     */
    @Column(name = "sent_date", nullable = false)
    private LocalDate sentDate;

    /**
     * Date d'échéance, choisie par le syndic dans le modal.
     */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    // =========================================================================
    // STATUT
    // =========================================================================

    /**
     * Statut agrégé de l'appel.
     * Recalculé dans le service à partir du statut des ChargeCallItem.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeCallStatus status = ChargeCallStatus.SENT;

    // =========================================================================
    // LIGNES DE DÉTAIL
    // =========================================================================

    /**
     * Quotes-parts individuelles, une ligne par copropriétaire.
     */
    @OneToMany(mappedBy = "chargeCall", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChargeCallItem> items = new ArrayList<>();

    // =========================================================================
    // AUDIT
    // =========================================================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
