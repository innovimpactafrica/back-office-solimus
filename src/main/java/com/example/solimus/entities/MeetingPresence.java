package com.example.solimus.entities;

import com.example.solimus.enums.AttendanceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PRÉSENCE À UNE AG
 * Une ligne par copropriétaire par AG. Pas de notion de vote en V1.
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

    /**
     * Tantième de ce copropriétaire, SNAPSHOTTÉ au moment de l'AG (pas recalculé depuis
     * Property.tantieme). Nécessaire car le tantième réel d'un lot peut changer dans le temps
     * (revente, division) — on fige la valeur exacte utilisée pour CETTE AG précise, pour que
     * le quorum historique reste exact même si les tantièmes changent après coup.
     */
    @Column(name = "tantieme_snapshot")
    private BigDecimal tantiemeSnapshot;

    /**
     * Présent / Procuration / Absent — déclaré par le copropriétaire lui-même (remplace
     * l'ancienne signature de présence par le syndic).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_type", nullable = false)
    private AttendanceType attendanceType = AttendanceType.ABSENT;

    // Nom du mandataire — rempli uniquement si attendanceType = PROXY
    @Column(name = "mandataire_name")
    private String mandataireName;
}