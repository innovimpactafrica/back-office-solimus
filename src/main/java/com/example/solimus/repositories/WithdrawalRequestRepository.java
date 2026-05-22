package com.example.solimus.repositories;

import com.example.solimus.entities.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    List<WithdrawalRequest> findAllByProviderIdOrderByCreatedAtDesc(Long providerId);
    Optional<WithdrawalRequest> findByReference(String reference);
}
