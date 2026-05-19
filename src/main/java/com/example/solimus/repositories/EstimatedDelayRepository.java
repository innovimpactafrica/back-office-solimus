package com.example.solimus.repositories;

import com.example.solimus.entities.EstimatedDelay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EstimatedDelayRepository extends JpaRepository<EstimatedDelay, Long> {
}
