package com.example.solimus.repositories;

import com.example.solimus.entities.ExceptionalCallPayment;
import com.example.solimus.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExceptionalCallPaymentRepository extends JpaRepository<ExceptionalCallPayment, Long> {

    Optional<ExceptionalCallPayment> findByReference(String reference);

    Optional<ExceptionalCallPayment> findFirstByExceptionalCallItemIdOrderByPaidAtDesc(Long exceptionalCallItemId);

    // Récupère les paiements d'un appel exceptionnel, filtrés par statut, paginés directement en base
    Page<ExceptionalCallPayment> findByExceptionalCallItemExceptionalCallIdAndStatus(
            Long exceptionalCallId, PaymentStatus status, Pageable pageable);
}
