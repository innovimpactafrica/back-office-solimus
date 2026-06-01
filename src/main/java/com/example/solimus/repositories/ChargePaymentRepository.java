package com.example.solimus.repositories;

import com.example.solimus.entities.ChargePayment;
import com.example.solimus.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChargePaymentRepository extends JpaRepository<ChargePayment, Long> {

    Optional<ChargePayment> findByReference(String reference);

    boolean existsByAllocationIdAndStatus(Long allocationId, PaymentStatus status);
}
