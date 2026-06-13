package com.example.solimus.entities;

import com.example.solimus.enums.MeetingMode;
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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private MeetingType type;

    @Enumerated(EnumType.STRING)
    private MeetingStatus status;

    private LocalDate meetingDate;
    private LocalTime startTime;
    private LocalTime endTime;

    private String location;

    @Enumerated(EnumType.STRING)
    private MeetingMode mode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    @OneToMany(mappedBy = "meeting",
        cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<MeetingAgendaItem> agendaItems = new ArrayList<>();

    @OneToMany(mappedBy = "meeting",
        cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "meeting",
        cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingParticipant> participants = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
