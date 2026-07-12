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

@Entity
@Table(name = "charge_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence unique : CPY-123456
    @Column(unique = true, nullable = false)
    private String reference;

    // L'allocation concernée (part du copropriétaire)
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "allocation_id", nullable = false, unique = true)
    private ChargeAllocation allocation;

    // Le copropriétaire qui paie
    @ManyToOne(fetch = FetchType.EAGER)
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
