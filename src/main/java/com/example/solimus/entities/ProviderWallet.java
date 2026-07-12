package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//Entité réprésentant le portefeuille (wallet) d'un prestataire
//Gère les soldes disponibles, en attente de validation, et le total reçu mensuel
@Entity
@Table(name = "wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Un prestataire a un seul wallet
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "provider_id", nullable = false, unique = true)
    private User provider;

    // Solde disponible (total reçu - total retiré)
    @Column(name = "available_balance", nullable = false)
    @Builder.Default // "Si le builder ne reçoit pas de valeur, prends ZERO (la valeur par défaut) et pas null"
    private BigDecimal availableBalance = BigDecimal.ZERO;

    // Montant en attente (paiements pas encore validés)
    @Column(name = "pending_balance", nullable = false)
    @Builder.Default
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    // Total reçu ce mois
    @Column(name = "total_this_month", nullable = false)
    @Builder.Default
    private BigDecimal totalThisMonth = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
