package com.example.solimus.entities;

import com.example.solimus.enums.ResolutionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//"Ordre du jour AG"
@Entity
@Table(name = "meeting_agenda_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingAgendaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private String title;

    // Description du point, optionnelle — remplie si le syndic l'a saisie, sinon reste null
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    // Indique si ce point nécessite une décision formelle (true) ou s'il est juste informatif (false)
    @Column(name = "requires_resolution", nullable = false)
    private Boolean requiresResolution = false;

    // ===== NOUVEAU : TEXTE DE LA RESOLUTION =====
    @Column(name = "resolution_text", columnDefinition = "TEXT")
    private String resolutionText; // rempli par le syndic apres la reunion, texte libre, nullable

    // ===== NOUVEAU : STATUT DE LA RESOLUTION =====
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false)
    private ResolutionStatus resolutionStatus = ResolutionStatus.EN_ATTENTE; // valeur par defaut a la creation
}

