package com.example.solimus.entities;

import com.example.solimus.enums.WalletTransactionCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * TRANSACTION DE PORTEFEUILLE SYNDIC
 * ============================================================================
 * Ligne du tableau des transactions financières du syndic.
 * Chaque entrée ou sortie de fonds du portefeuille crée une transaction.
 */
@Entity
@Table(name = "syndic_wallet_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicWalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Le portefeuille concerné
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private SyndicWallet wallet;

    // La résidence concernée (nullable selon le type de transaction)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id")
    private Residence residence;

    // Le copropriétaire concerné (rempli uniquement pour CHARGES)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "co_owner_id")
    private User coOwner;

    // Catégorie de transaction
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletTransactionCategory category;

    // Montant (positif pour recette, négatif pour dépense)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Libellé descriptif de la transaction
    @Column(length = 255)
    private String label;

    // Mode de paiement en texte libre, snapshotté au moment de la création
    // Ex: "Virement", "Prélèvement", "Carte Bancaire"
    @Column(length = 100)
    private String mode;

    // Date de la transaction
    @Column(nullable = false)
    private LocalDateTime transactionDate;

    // Référence vers la source (ChargeCallPayment, PaymentProvider, SyndicWithdrawalRequest)
    @Column(length = 255)
    private String reference;

    // Date de création de l'enregistrement
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
