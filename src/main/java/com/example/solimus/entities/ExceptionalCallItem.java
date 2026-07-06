package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "exceptional_call_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionalCallItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exceptional_call_id", nullable = false)
    private ExceptionalCall exceptionalCall;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "co_owner_id", nullable = false)
    private User coOwner;

    // Tantième snapshotté même en mode CUSTOM, pour référence/affichage
    private BigDecimal tantieme;

    // Calculée automatiquement (OWNERSHIP_SHARES) ou saisie manuellement (CUSTOM)
    @Column(nullable = false)
    private BigDecimal quotePart;

    @Column(nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;
}
