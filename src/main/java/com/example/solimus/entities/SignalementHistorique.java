package com.example.solimus.entities;

import com.example.solimus.enums.SignalementEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "signalement_historique")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalementHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signalement_id", nullable = false)
    private Signalement signalement;

    @Enumerated(EnumType.STRING)
    private SignalementEventType typeEvenement;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private User auteur;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
