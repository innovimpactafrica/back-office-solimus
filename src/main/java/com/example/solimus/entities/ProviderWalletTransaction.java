package com.example.solimus.entities;

import com.example.solimus.enums.ProviderWalletTransactionCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ============================================================================
// GRAND LIVRE DU WALLET PRESTATAIRE
// ============================================================================
// Le solde d'un ProviderWallet n'est jamais stocké : toujours recalculé en sommant ces lignes.
// Seuls les crédits sont enregistrés ici — les retraits sont suivis via ProviderWithdrawalRequest.
// ============================================================================
@Entity
@Table(name = "provider_wallet_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderWalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "wallet_id", nullable = false)
    private ProviderWallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderWalletTransactionCategory category;

    // Toujours positif (crédits uniquement)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String label;

    // Référence de l'intervention liée, copiée pour affichage rapide
    @Column(length = 255)
    private String reference;

    // Source du paiement
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "intervention_request_id")
    private InterventionRequest interventionRequest;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}