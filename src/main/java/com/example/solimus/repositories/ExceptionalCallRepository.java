package com.example.solimus.repositories;

import com.example.solimus.entities.ExceptionalCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExceptionalCallRepository extends JpaRepository<ExceptionalCall, Long> {
}
