package com.example.solimus.repositories;

import com.example.solimus.entities.MeetingDocument;
import com.example.solimus.enums.MeetingDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingDocumentRepository extends JpaRepository<MeetingDocument, Long> {

    Page<MeetingDocument> findByMeetingId(Long meetingId, Pageable pageable);

    /**
     * Trouve tous les documents de réunion pour une résidence donnée.
     */
    List<MeetingDocument> findAllByMeetingResidenceId(Long residenceId);

    /**
     * Compte le nombre de documents de réunion pour une résidence.
     */
    int countByMeetingResidenceId(Long residenceId);


    @Query("SELECT d FROM MeetingDocument d " +
            "WHERE d.meeting.residence.syndic.id = :syndicId " +
            "AND (:search IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:documentType IS NULL OR d.documentType = :documentType) " +
            "AND (:residenceId IS NULL OR d.meeting.residence.id = :residenceId) " +
            "ORDER BY d.createdAt DESC")
    Page<MeetingDocument> searchDocuments(@Param("syndicId") Long syndicId,
                                          @Param("search") String search,
                                          @Param("documentType") MeetingDocumentType documentType,
                                          @Param("residenceId") Long residenceId,
                                          Pageable pageable);

    // Documents liés (même réunion, en excluant le document courant)
    List<MeetingDocument> findByMeetingIdAndIdNot(Long meetingId, Long excludedId);
}
