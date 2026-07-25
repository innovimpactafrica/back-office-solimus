package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyndicProfileRepository extends JpaRepository<SyndicProfile, Long> {

    Optional<SyndicProfile> findByUserId(Long userId);
}