package com.example.solimus.entities;

import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.enums.Currency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * PARAMÈTRES FINANCIERS DU SYNDIC
 * ============================================================================
 *
 * Configuration globale des paramètres financiers pour un syndic.
 * Ces paramètres s'appliquent à toutes les résidences gérées par ce syndic :
 *
 */
@Entity
@Table(name = "syndic_financial_settings")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyndicFinancialSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================================
    // RELATION AVEC LE SYNDIC
    // =========================================================================

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = false, unique = true)
    private User syndic;

    // =========================================================================
    // DEVISE ET FRÉQUENCE
    // =========================================================================

    // Devise monétaire utilisée pour toutes les transactions financières (FCFA, EUR, USD)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency = Currency.FCFA;

    // Fréquence des appels de charges (mensuel ou trimestriel)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeFrequency chargeFrequency = ChargeFrequency.TRIMESTRIEL;

    // =========================================================================
    // PÉNALITÉS ET RELANCES
    // =========================================================================

    // Taux de pénalité appliqué par mois de retard (en pourcentage, ex: 1.5 pour 1.5%)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal latePenaltyRate = new BigDecimal("1.5");

    // Nombre de jours après l'échéance avant la première relance
    @Column(nullable = false)
    private Integer reminderDelayDays = 30;

    // =========================================================================
    // FONDS DE RÉSERVE
    // =========================================================================

    // Pourcentage du budget annuel alloué au fonds de réserve par défaut (ex: 5 pour 5%)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal reserveFundPercentage = new BigDecimal("5");

    // =========================================================================
    // AUDIT (DATES AUTOMATIQUES)
    // =========================================================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
