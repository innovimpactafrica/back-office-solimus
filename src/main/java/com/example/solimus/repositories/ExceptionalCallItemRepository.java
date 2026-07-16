package com.example.solimus.repositories;

import com.example.solimus.entities.ExceptionalCallItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExceptionalCallItemRepository extends JpaRepository<ExceptionalCallItem, Long> {

    // Récupère tous les ExceptionalCallItem d'un copropriétaire, toutes résidences confondues
    List<ExceptionalCallItem> findByCoOwnerId(Long coOwnerId);

    // Récupère les lignes d'un appel exceptionnel, paginées directement en base
    Page<ExceptionalCallItem> findByExceptionalCallId(Long exceptionalCallId, Pageable pageable);

    // Supprime tous les items d'un appel exceptionnel via JPQL
    @Query("DELETE FROM ExceptionalCallItem eci WHERE eci.exceptionalCall.id = :exceptionalCallId")
    void deleteByExceptionalCallId(@Param("exceptionalCallId") Long exceptionalCallId);
}
