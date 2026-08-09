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
 * Type d'équipement commun — propre à chaque syndic, jamais partagé entre syndics.
 * Utilisé comme table de référence pour les équipements des résidences.
 */
@Entity
@Table(name = "facility_types", uniqueConstraints = @UniqueConstraint(columnNames = {"syndic_id", "name"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FacilityType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Catégorie du type d'équipement — pour regrouper visuellement dans le catalogue.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private FacilityCategory category;

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
