package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Entité représentant le portefeuille (wallet) d'un prestataire.
// Le solde n'est jamais stocké ici — toujours recalculé à la volée en sommant
// ProviderWalletTransaction (voir ProviderWalletBalanceService), exactement comme SyndicWallet.
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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}