package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderWithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<ProviderWithdrawalRequest, Long> {

    List<ProviderWithdrawalRequest> findAllByProviderIdOrderByCreatedAtDesc(Long providerId);

}
