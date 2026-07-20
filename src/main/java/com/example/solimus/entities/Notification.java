package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * ============================================================================
 * NOTIFICATION PERSISTANTE
 * ============================================================================
 * Trace chaque notification envoyée à un utilisateur, en plus du push Firebase
 * éphémère. Permet d'afficher un historique et un compteur de notifications
 * non lues (badge sur la cloche).
 */
@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // L'utilisateur destinataire de cette notification
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    // Statut de lecture, false par défaut
    @Column(name = "`read`", nullable = false)
    private Boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
