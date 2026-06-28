package com.example.solimus.entities;

import com.example.solimus.enums.FacilityCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Type d'équipement commun prédéfini.
 * Utilisé comme table de référence pour les équipements des résidences.
 * Le syndic peut configurer les valeurs (capacité, superficie, etc.) pour chaque résidence.
 */
@Entity
@Table(name = "facility_types")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FacilityType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Catégorie du type d'équipement — pour regrouper visuellement dans le catalogue.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private FacilityCategory category;

    /**
     * Icône affichée dans le catalogue (nom d'icône mappé côté front).
     * Ex: "elevator", "pool", "parking", "garden", "camera"
     */
    @Column(name = "icon")
    private String icon;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
