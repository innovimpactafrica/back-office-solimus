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
 * Contient les informations de base, l'ordre du jour, les documents et les copropriétaires participants.
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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MeetingType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MeetingStatus status;

    private LocalDate meetingDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private String location;

    private LocalDate convocationSentDate;

    @Column(columnDefinition = "TEXT")
    private String convocationMessage;

    private Boolean sendByEmail = false;
    private Boolean sendByPlatformNotification = false;
    private Boolean sendBySms = false;

    // Passe à true une fois la convocation réellement envoyée par le job planifié
    @Column(name = "convocation_sent", nullable = false)
    private Boolean convocationSent = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    // Ordre du jour de la réunion
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<MeetingAgendaItem> agendaItems = new ArrayList<>();

    // Documents joints à la réunion
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingDocument> documents = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}