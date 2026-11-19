package com.example.solimus.repositories;

import com.example.solimus.entities.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {
    boolean existsByNameIgnoreCase(String name);

    // Catalogue complet trié par nom (listes déroulantes : inscription prestataire, création de travaux)
    java.util.List<Specialty> findAllByOrderByNameAsc();

    java.util.List<Specialty> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

}
