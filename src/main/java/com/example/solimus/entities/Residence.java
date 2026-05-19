package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ============================================================
// Residence.java
// Représente un immeuble ou une résidence géré par un syndic
// ============================================================
@Entity
@Table(name = "residences")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Residence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nom commercial de la résidence
    @Column(nullable = false)
    private String name;

    // Adresse complète récupérée via l'autocomplétion (front)
    @Column(name = "full_address", nullable = false, length = 500)
    private String fullAddress;

    // Coordonnées GPS — envoyées par le front, jamais calculées ici
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    // Infos structurelles de l'immeuble
    @Column(name = "floor_count")
    private Integer floorCount;

    @Column(name = "apartment_count")
    private Integer apartmentCount;

    // Le syndic qui gère cette résidence
    // Plusieurs résidences peuvent être gérées par le même syndic
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    // Liste des biens (appartements) dans cette résidence
    // Si on supprime la résidence, ses biens sont supprimés aussi
    @OneToMany(mappedBy = "residence", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Property> properties = new ArrayList<>();

    // Liste des demandes d'intervention de la résidence
    @OneToMany(mappedBy = "residence", cascade = CascadeType.ALL)
    private List<InterventionRequest> interventionRequests = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
