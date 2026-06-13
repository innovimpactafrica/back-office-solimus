package com.example.solimus.repositories;

import com.example.solimus.entities.Meeting;
import com.example.solimus.entities.Residence;
import com.example.solimus.enums.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByResidenceId(Long residenceId);

    org.springframework.data.domain.Page<Meeting> findByResidence(Residence residence, org.springframework.data.domain.Pageable pageable);

    List<Meeting> findByResidenceIdAndStatus(Long residenceId, MeetingStatus status);

    List<Meeting> findByResidenceIdAndMeetingDateAfterOrderByMeetingDateAsc(
        Long residenceId, LocalDateTime date);

    List<Meeting> findByResidenceOrderByMeetingDateAsc(Residence residence);

    List<Meeting> findByResidenceAndMeetingDateBetween(Residence residence,
                                                        LocalDate start,
                                                        LocalDate end);

    /**
     * Trouve les 5 prochaines réunions A_VENIR pour une résidence.
     */
    List<Meeting> findTop5ByResidenceIdAndStatusOrderByMeetingDateAsc(
            Long residenceId, MeetingStatus status);

    /**
     * Trouve les 2 prochaines réunions A_VENIR pour une résidence.
     */
    List<Meeting> findTop2ByResidenceIdAndStatusOrderByMeetingDateAsc(
            Long residenceId, MeetingStatus status);
}
