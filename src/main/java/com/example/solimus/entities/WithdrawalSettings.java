package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Réglages globaux liés aux retraits — table à un seul enregistrement (id toujours 1)
@Entity
@Table(name = "withdrawal_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalSettings {

    @Id
    private Long id; // toujours 1, un seul enregistrement

    @Column(name = "monthly_limit", nullable = false)
    private BigDecimal monthlyLimit; // montant  limite mensuelle

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "updated_by_id")
    private User updatedBy; // admin qui a modifié la limite en dernier
}