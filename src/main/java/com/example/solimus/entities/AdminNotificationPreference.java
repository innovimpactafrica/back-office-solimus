package com.example.solimus.entities;

import com.example.solimus.enums.AdminNotificationEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//Entité représentant les préférences de notification d'un administrateur pour différents types d'événements. 
@Entity
@Table(name = "admin_notification_preferences",
       uniqueConstraints = @UniqueConstraint(columnNames = {"admin_id", "event_type"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AdminNotificationEventType eventType;//Les types d'événements pour lesquels l'administrateur peut recevoir des notifications 

    @Column(name = "platform_enabled", nullable = false)
    private boolean platformEnabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    // Stocké dès maintenant, envoi réel non implémenté tant qu'aucun provider SMS
    // (ex: Twilio) n'est branché au projet
    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;
}