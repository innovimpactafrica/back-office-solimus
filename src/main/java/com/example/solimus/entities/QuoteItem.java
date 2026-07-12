package com.example.solimus.entities;

import com.example.solimus.enums.QuoteItemType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "quote_items")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteItemType type;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    // Calcul du total de la ligne (quantité * prix unitaire)
    public BigDecimal getTotalPrice() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
