package com.example.solimus.entities;

import com.example.solimus.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PRÉSENCE À UNE AG
 * Une ligne par copropriétaire par AG. Pas de notion de vote ni de représentation en V1.
 */
@Entity
@Table(name = "meeting_presences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingPresence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "meeting_participant_id", nullable = false)
    private MeetingParticipant meetingParticipant;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    /**
     * Statut de présence de ce copropriétaire le jour de l'AG.
     */
    @Enumerated(EnumType.STRING)
    private AttendanceStatus attendanceStatus;

    /**
     * Tantième de ce copropriétaire, SNAPSHOTTÉ au moment de l'AG (pas recalculé depuis
     * Property.tantieme). Nécessaire car le tantième réel d'un lot peut changer dans le temps
     * (revente, division) — on fige la valeur exacte utilisée pour CETTE AG précise, pour que
     * le quorum historique reste exact même si les tantièmes changent après coup.
     */
    @Column(name = "tantieme_snapshot")
    private BigDecimal tantiemeSnapshot;

    /**
     * Signature électronique de présence.
     */
    @Column(name = "has_signed")
    private Boolean hasSigned = false;
}