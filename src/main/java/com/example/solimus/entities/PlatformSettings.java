package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// Réglages globaux de la plateforme — table à un seul enregistrement (id toujours 1)
@Entity
@Table(name = "platform_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettings {

    @Id
    private Long id; // toujours 1, un seul enregistrement

    @Column(name = "platform_name", nullable = false)
    private String platformName;

    @Column(name = "website")
    private String website;

    @Column(name = "support_email")
    private String supportEmail;

    @Column(name = "support_phone")
    private String supportPhone;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "updated_by_id")
    private User updatedBy; // admin qui a modifié les réglages en dernier
}