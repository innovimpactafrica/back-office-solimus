package com.example.solimus.repositories;

import com.example.solimus.entities.ResidenceContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResidenceContactRepository extends JpaRepository<ResidenceContact, Long> {

    List<ResidenceContact> findByResidenceId(Long residenceId);
}
