package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderProfile;
import com.example.solimus.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, Long> {

    Optional<ProviderProfile> findByUser(User user);

    // On récupère tous les profils prestataires dont le compte (User) est ACTIVE et qui ont la spécialité demandée
    @Query("SELECT p FROM ProviderProfile p " +
            "WHERE p.specialty.id = :specialtyId " +
            "AND p.user.status = 'ACTIVE'")
    List<ProviderProfile> findActiveProvidersBySpecialty(@Param("specialtyId") Long specialtyId);
}

