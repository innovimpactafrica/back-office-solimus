package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_participants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    /**
     * Le copropriétaire convoqué à cette AG.
     * Toujours renseigné — uniquement les copropriétaires de la résidence, pas d'externes.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Liste des références de lots de ce copropriétaire, séparées par virgule (ex: "Apt 8D, Apt 4E")
    // Figée au moment de la génération, comme le tantième sur MeetingPresence
    @Column(name = "apartments")
    private String apartments;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}