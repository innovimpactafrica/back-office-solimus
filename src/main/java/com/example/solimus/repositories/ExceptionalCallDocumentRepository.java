package com.example.solimus.repositories;

import com.example.solimus.entities.ExceptionalCallDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExceptionalCallDocumentRepository extends JpaRepository<ExceptionalCallDocument, Long> {

    // Récupère les documents d'un appel exceptionnel, paginés directement en base
    Page<ExceptionalCallDocument> findByExceptionalCallId(Long exceptionalCallId, Pageable pageable);

    // Supprime tous les documents d'un appel exceptionnel via JPQL
    @Query("DELETE FROM ExceptionalCallDocument ecd WHERE ecd.exceptionalCall.id = :exceptionalCallId")
    void deleteByExceptionalCallId(@Param("exceptionalCallId") Long exceptionalCallId);
}
