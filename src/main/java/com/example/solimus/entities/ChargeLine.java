package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Ligne de détail d'une charge
@Entity
@Table(name = "charge_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargeLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;  // "Entretien parties communes"

    @Column(nullable = false)
    private BigDecimal amount;  // 85 000 FCFA

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;
}
