package com.example.solimus.entities;

import com.example.solimus.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * LIGNE DE DÉTAIL D'APPEL DE CHARGES
 * ============================================================================
 * Une ligne par copropriétaire dans un ChargeCall.
 * Contient la quote-part à payer et le statut de paiement.
 */
@Entity
@Table(name = "charge_call_items")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChargeCallItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================================
    // RELATION
    // =========================================================================

    /**
     * L'appel de charges parent.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_call_id", nullable = false)
    private ChargeCall chargeCall;

    /**
     * Référence de la ligne (ex: "ACI-2025-001-123")
     */
    @Column(name = "reference", nullable = false, unique = true)
    private String reference;

    // =========================================================================
    // COPROPRIÉTAIRE
    // =========================================================================

    /**
     * Le copropriétaire concerné par cette ligne.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coowner_id", nullable = false)
    private User coOwner;

    /**
     * Tantième cumulé du copropriétaire dans la résidence
     * au moment de la génération (somme de ses lots si plusieurs).
     */
    @Column(nullable = false)
    private BigDecimal tantieme;

    // =========================================================================
    // MONTANTS
    // =========================================================================

    /**
     * Quote-part du copropriétaire pour cette période. Figé au moment de la génération.
     */
    @Column(name = "quote_part", nullable = false)
    private BigDecimal quotePart;

    /**
     * Montant payé par le copropriétaire. Mis à jour lors des paiements.
     */
    @Column(name = "paid_amount")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /**
     * Montant restant à payer.
     * Calculé : quotePart - paidAmount
     */
    @Column(name = "remaining_amount")
    private BigDecimal remainingAmount;

    // =========================================================================
    // STATUT
    // =========================================================================

    /**
     * Statut de paiement de cette ligne.
     * PENDING : non payé
     * COMPLETED : payé intégralement
     * FAILED : paiement échoué
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    // =========================================================================
    // AUDIT
    // =========================================================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
