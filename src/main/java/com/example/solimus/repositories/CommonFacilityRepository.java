package com.example.solimus.repositories;

import com.example.solimus.entities.CommonFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommonFacilityRepository extends JpaRepository<CommonFacility, Long> {

    List<CommonFacility> findByResidenceId(Long residenceId);
}
