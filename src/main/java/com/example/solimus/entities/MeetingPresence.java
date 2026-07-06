package com.example.solimus.entities;

import com.example.solimus.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PRÉSENCE À UNE AG
 * Une ligne par copropriétaire par AG. Distincte du vote (qui se décide par
 * résolution, potentiellement plusieurs fois par AG) — la présence, elle,
 * ne se décide qu'une seule fois pour toute l'assemblée.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_participant_id", nullable = false)
    private MeetingParticipant meetingParticipant;

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
     * Si attendanceStatus == REPRESENTE : qui représente ce copropriétaire (procuration).
     * Toujours un autre copropriétaire du système en V1 (pas d'externe).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "represented_by_user_id")
    private User representedByUser;

    /**
     * Signature électronique de présence.
     */
    @Column(name = "has_signed")
    private Boolean hasSigned = false;
}
