package com.example.solimus.repositories;

import com.example.solimus.entities.ResidenceContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResidenceContactRepository extends JpaRepository<ResidenceContact, Long> {

    List<ResidenceContact> findByResidenceId(Long residenceId);

    void deleteByResidenceId(Long residenceId);

    // Vérifie l'unicité du téléphone au sein d'une résidence (création)
    boolean existsByResidenceIdAndPhone(Long residenceId, String phone);

    // Même vérification, en excluant le contact qu'on est en train de modifier (modification)
    boolean existsByResidenceIdAndPhoneAndIdNot(Long residenceId, String phone, Long id);
}
