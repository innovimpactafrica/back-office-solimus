package com.example.solimus.repositories;

import com.example.solimus.entities.ExceptionalCall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExceptionalCallRepository extends JpaRepository<ExceptionalCall, Long> {

    // Récupère les appels exceptionnels d'un syndic, paginés directement en base
    Page<ExceptionalCall> findBySyndicId(Long syndicId, Pageable pageable);
}
