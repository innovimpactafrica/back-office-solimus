package com.example.solimus.repositories;

import com.example.solimus.entities.CoOwnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CoOwnerProfileRepository extends JpaRepository<CoOwnerProfile, Long> {

    Optional<CoOwnerProfile> findByUserId(Long userId);
}
