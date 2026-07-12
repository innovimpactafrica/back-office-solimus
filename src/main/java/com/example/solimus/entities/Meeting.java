package com.example.solimus.entities;


import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.MeetingType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant une réunion (assemblée générale, conseil syndical, etc.).
 * Contient les informations de base, l'ordre du jour, les documents et les participants.
 */
@Entity
@Table(name = "meetings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    // Type de réunion
    @Enumerated(EnumType.STRING)
    private MeetingType type;

    // Statut de la réunion (DRAFT, UPCOMING, IN_PROGRESS, COMPLETED, CANCELLED)
    @Enumerated(EnumType.STRING)
    private MeetingStatus status;

    // Date de la réunion
    private LocalDate meetingDate;

    // Heure de début de la réunion
    private LocalTime startTime;

    // Heure de fin de la réunion
    private LocalTime endTime;

    // Lieu de la réunion
    private String location;

    // Date d'envoi prévue/réelle des convocations
    private LocalDate convocationSentDate;

    // Message personnalisé inclus dans la convocation
    @Column(columnDefinition = "TEXT")
    private String convocationMessage;

    // Canaux d'envoi choisis pour la convocation
    private Boolean sendByEmail = false;
    private Boolean sendByPlatformNotification = false;
    private Boolean sendBySms = false;

    // Résidence concernée par la réunion
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    // Syndic organisateur de la réunion
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    // Ordre du jour de la réunion
    @OneToMany(mappedBy = "meeting",
        cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<MeetingAgendaItem> agendaItems = new ArrayList<>();

    // Documents joints à la réunion
    @OneToMany(mappedBy = "meeting",
        cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingDocument> documents = new ArrayList<>();

    // Participants à la réunion
    @OneToMany(mappedBy = "meeting",
        cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingParticipant> participants = new ArrayList<>();//

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
