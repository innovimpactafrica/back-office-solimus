package com.example.solimus.entities;

import com.example.solimus.enums.InterventionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


// Représente l'historique des changements de statut d'une intervention
@Entity
@Table(name = "intervention_status_history")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterventionStatusHistory {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "intervention_id", nullable = false)
    private InterventionRequest interventionRequest; // L'intervention concernée

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterventionStatus status; // Nouveau statut

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private User changedBy; // Utilisateur qui a effectué le changement

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // Date du changement
}