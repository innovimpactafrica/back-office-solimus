package com.example.solimus.entities;

import com.example.solimus.enums.PropertyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "properties")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String reference;

    private Integer floor;

    private Double area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    // Un bien n'a qu'un seul propriétaire
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
}
