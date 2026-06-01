package com.example.solimus.repositories;

import com.example.solimus.entities.Charge;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Repository pour les charges
@Repository
public interface ChargeRepository extends JpaRepository<Charge, Long> {

    List<Charge> findByResidenceOrderByCreatedAtDesc(Residence residence);
    List<Charge> findByResidenceIdOrderByCreatedAtDesc(Long residenceId);
    List<Charge> findBySyndicOrderByCreatedAtDesc(User syndic);
    Optional<Charge> findByReference(String reference);
    boolean existsByReferenceAndResidenceId(String reference, Long residenceId);
}
