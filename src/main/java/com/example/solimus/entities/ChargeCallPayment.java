package com.example.solimus.entities;

import com.example.solimus.enums.ChargePaymentMethod;
import com.example.solimus.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * PAIEMENT D'APPEL DE CHARGES
 * ============================================================================
 * Paiement effectué par un copropriétaire pour une ligne d'appel de charges.
 */
@Entity
@Table(name = "charge_call_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargeCallPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence unique : CCP-123456
    @Column(unique = true, nullable = false)
    private String reference;

    // La ligne d'appel de charges concernée
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_call_item_id", nullable = false)
    private ChargeCallItem chargeCallItem;

    // Le copropriétaire qui paie
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Montant payé
    @Column(nullable = false)
    private BigDecimal amount;

    // Moyen de paiement : WAVE, ORANGE_MONEY, CARTE_BANCAIRE
    @Enumerated(EnumType.STRING)
    private ChargePaymentMethod method;

    // PENDING → COMPLETED ou FAILED
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime paidAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
