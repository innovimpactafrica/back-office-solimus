package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * ============================================================================
 * PORTEFEUILLE FINANCIER DU SYNDIC
 * ============================================================================
 * Représente le compte financier d'un syndic pour gérer les fonds collectés
 * auprès des copropriétaires (charges) et les dépenses (travaux, retraits).
 * Un seul wallet par syndic 
 * 
 */

@Entity
@Table(name = "syndic_wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyndicWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Le syndic propriétaire de ce wallet (un seul wallet par syndic)
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = false, unique = true)
    private User syndic;

    // Date de création du wallet
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
