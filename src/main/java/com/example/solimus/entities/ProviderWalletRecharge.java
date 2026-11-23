package com.example.solimus.entities;

import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.ProofStatus;
import com.example.solimus.enums.RechargeOrigin;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// ============================================================================
// RECHARGE DU WALLET PRESTATAIRE (hors application)
// ============================================================================
// Le prestataire paie Solimus en dehors de l'application (Wave, Orange Money, virement, espèces…), puis
// envoie la preuve. L'admin la valide : le wallet est crédité (ligne RECHARGE du grand livre). Le solde
// rechargé sert de garantie pour voir les demandes de travaux et paie les commissions Solimus.
// Enums en varchar : extensibles sans ALTER.
// ============================================================================
@Entity
@Table(name = "provider_wallet_recharges", indexes = {
        @Index(name = "idx_pwr_status", columnList = "status"),
        @Index(name = "idx_pwr_provider", columnList = "provider_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderWalletRecharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RCH-000001, attribuée après le premier enregistrement
    @Column(unique = true, length = 30)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", columnDefinition = "varchar(30)")
    private OffPlatformPaymentMethod paymentMethod;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(length = 1000)
    private String description;

    // Justificatif (MinIO) — absent pour un crédit direct de l'admin
    @Column(name = "document_key", length = 500)
    private String documentKey;

    @Column(name = "document_name", length = 255)
    private String documentName;

    @Column(name = "document_content_type", length = 100)
    private String documentContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    @Builder.Default
    private RechargeOrigin origin = RechargeOrigin.PREUVE_PRESTATAIRE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    @Builder.Default
    private ProofStatus status = ProofStatus.PENDING_VALIDATION;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // Admin qui a validé, rejeté ou crédité directement
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // Ligne du grand livre créée à la validation (garde-fou : jamais deux crédits pour une recharge)
    @Column(name = "wallet_transaction_id")
    private Long walletTransactionId;
}
