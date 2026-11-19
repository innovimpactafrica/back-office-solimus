package com.example.solimus.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.example.solimus.entities.ExceptionalCallItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExceptionalCallItemRepository extends JpaRepository<ExceptionalCallItem, Long> {

    // Verrou en écriture : dépôt d'une preuve et validation ne s'exécutent jamais en parallèle sur la même ligne
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ExceptionalCallItem i WHERE i.id = :id")
    Optional<ExceptionalCallItem> findByIdForUpdate(@Param("id") Long id);


    // Récupère tous les ExceptionalCallItem d'un copropriétaire, toutes résidences confondues
    List<ExceptionalCallItem> findByCoOwnerId(Long coOwnerId);

    // Récupère les lignes d'un appel exceptionnel, paginées directement en base
    Page<ExceptionalCallItem> findByExceptionalCallId(Long exceptionalCallId, Pageable pageable);

    // Supprime tous les items d'un appel exceptionnel via JPQL
    @Query("DELETE FROM ExceptionalCallItem eci WHERE eci.exceptionalCall.id = :exceptionalCallId")
    void deleteByExceptionalCallId(@Param("exceptionalCallId") Long exceptionalCallId);
}
