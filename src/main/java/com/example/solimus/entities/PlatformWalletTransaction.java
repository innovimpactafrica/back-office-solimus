package com.example.solimus.entities;

import com.example.solimus.enums.CommissionSource;
import com.example.solimus.enums.PlatformWalletTransactionCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ============================================================================
// GRAND LIVRE DU WALLET DE LA PLATEFORME (ADMIN / SOLIMUS)
// ============================================================================
// Un seul wallet plateforme : son solde est la somme de ces lignes, jamais stocké. Chaque commission
// prélevée sur un paiement de travaux y est enregistrée avec son détail (montant brut, taux, origine).
// Enums en varchar : la liste des catégories / origines peut s'étendre sans ALTER de la colonne.
// ============================================================================
@Entity
@Table(name = "platform_wallet_transactions", indexes = {
        @Index(name = "idx_platform_wallet_tx_date", columnList = "transaction_date"),
        @Index(name = "idx_platform_wallet_tx_intervention", columnList = "intervention_request_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformWalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(40)")
    private PlatformWalletTransactionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(40)")
    private CommissionSource source;

    /** Montant encaissé par la plateforme (positif). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Montant du paiement de travaux sur lequel la commission a été calculée. */
    @Column(name = "gross_amount", precision = 19, scale = 2)
    private BigDecimal grossAmount;

    /** Taux appliqué, en % (ex : 5.00). */
    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(length = 255)
    private String label;

    /** Référence du paiement de travaux (PAY-…, SOL-…) quand elle existe. */
    @Column(name = "payment_reference", length = 50)
    private String paymentReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_request_id")
    private InterventionRequest interventionRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private User provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syndic_id")
    private User syndic;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
