package com.example.solimus.repositories;

import com.example.solimus.entities.PropertyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyTypeRepository extends JpaRepository<PropertyType, Long> {

    boolean existsByNameIgnoreCase(String name);
}
