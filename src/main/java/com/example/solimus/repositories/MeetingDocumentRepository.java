package com.example.solimus.repositories;

import com.example.solimus.entities.MeetingDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingDocumentRepository extends JpaRepository<MeetingDocument, Long> {

    List<MeetingDocument> findByMeetingId(Long meetingId);

    /**
     * Trouve tous les documents de réunion pour une résidence donnée.
     */
    List<MeetingDocument> findAllByMeetingResidenceId(Long residenceId);

    /**
     * Compte le nombre de documents de réunion pour une résidence.
     */
    int countByMeetingResidenceId(Long residenceId);
}
