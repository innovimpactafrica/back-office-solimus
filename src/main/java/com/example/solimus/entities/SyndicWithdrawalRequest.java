package com.example.solimus.entities;

import com.example.solimus.enums.WithdrawalMode;
import com.example.solimus.enums.WithdrawalStatus;
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
 * DEMANDE DE RETRAIT DE FONDS PAR LE SYNDIC
 * ============================================================================
 * Représente une demande de retrait de fonds du portefeuille syndic.
 * Le syndic peut demander à retirer ses fonds collectés via différents modes.
 * La demande doit être validée par l'admin avant traitement.
 */
@Entity
@Table(name = "syndic_withdrawal_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicWithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Le portefeuille concerné
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private SyndicWallet wallet;

    // Montant demandé
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Mode de retrait choisi
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalMode mode;

    // La résidence concernée par ce retrait (utile même sans poste budgétaire précis,
    // car un syndic gère souvent plusieurs résidences)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id")
    private Residence residence;

    // Le poste budgétaire concerné par ce retrait (Uniquement pour les retraits liés à un poste sans bien commun)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_item_id")
    private BudgetItem budgetItem;

    // Statut de la demande
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalStatus status;

    // Date de la demande
    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    // Date de traitement (rempli quand le statut change)
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
