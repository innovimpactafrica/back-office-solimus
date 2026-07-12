package com.example.solimus.repositories;

import com.example.solimus.entities.ExceptionalCallDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExceptionalCallDocumentRepository extends JpaRepository<ExceptionalCallDocument, Long> {

    // Récupère les documents d'un appel exceptionnel, paginés directement en base
    Page<ExceptionalCallDocument> findByExceptionalCallId(Long exceptionalCallId, Pageable pageable);
}
