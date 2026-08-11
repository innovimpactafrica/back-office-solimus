package com.example.solimus.entities;

import com.example.solimus.enums.ChargeItemPaymentStatus;
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
    @ManyToOne(fetch = FetchType.EAGER)
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
    @ManyToOne(fetch = FetchType.EAGER)
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

    // =========================================================================
    // STATUT
    // =========================================================================

    /**
     * Statut de paiement de cette ligne, posé explicitement au moment
     * de la confirmation d'un paiement (jamais recalculé à l'affichage).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeItemPaymentStatus status = ChargeItemPaymentStatus.PENDING;

    // =========================================================================
    // RELANCE / PÉNALITÉ DE RETARD (timeline "Option C" — voir PaymentStatusUtils)
    // Chaque champ *SentAt/*AppliedAt trace qu'une notification a déjà été envoyée une
    // fois, pour que le job planifié quotidien ne la renvoie jamais en double.
    // =========================================================================

    // Date d'envoi de l'unique relance automatique (jour reminderDelayDays après échéance)
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    // Date d'envoi de l'unique avertissement de pénalité (passage en IMPAYÉ, jour 31)
    @Column(name = "warning_sent_at")
    private LocalDateTime warningSentAt;

    // Montant de la pénalité de retard appliquée (0 tant qu'aucune pénalité n'est appliquée)
    @Column(name = "penalty_amount", precision = 19, scale = 2)
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    // Date d'application de l'unique pénalité (jour 31 + délai de grâce)
    @Column(name = "penalty_applied_at")
    private LocalDateTime penaltyAppliedAt;

    // =========================================================================
    // AUDIT
    // =========================================================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =========================================================================
    // MONTANTS DÉRIVÉS — centralise "quote-part + pénalité" pour ne jamais dupliquer
    // ce calcul aux endroits qui affichent/utilisent le montant restant à payer
    // =========================================================================

    // Montant total dû pour cette ligne, pénalité de retard incluse une fois appliquée
    public BigDecimal getTotalDue() {
        return quotePart.add(penaltyAmount != null ? penaltyAmount : BigDecimal.ZERO);
    }

    // Montant restant à payer (quote-part + pénalité - déjà payé)
    public BigDecimal getRemainingAmount() {
        BigDecimal paid = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        return getTotalDue().subtract(paid);
    }
}
