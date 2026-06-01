package com.example.solimus.entities;

import com.example.solimus.enums.ChargeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Charge collective pour une résidence
@Entity
@Table(name = "charges")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Charge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identification
    @Column(unique = true, nullable = false)
    private String reference;  // CHG-XXXXXX

    @Column(nullable = false)
    private String title;  // "Charges mensuelles"

    @Column(columnDefinition = "TEXT")
    private String description;

    // Type et montant
    @Enumerated(EnumType.STRING)
    private ChargeType type;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    // Période et dates
    private String period;  // "Juin 2026"
    private LocalDate dueDate;

    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    @OneToMany(mappedBy = "charge", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChargeLine> lines = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "charge_documents",
        joinColumns = @JoinColumn(name = "charge_id"))
    @Column(name = "document_url")
    private List<String> documentUrls = new ArrayList<>();

    @OneToMany(mappedBy = "charge", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChargeAllocation> allocations = new ArrayList<>();

    // Audit
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
