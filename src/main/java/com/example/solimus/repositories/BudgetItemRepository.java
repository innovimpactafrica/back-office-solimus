package com.example.solimus.repositories;

import com.example.solimus.entities.BudgetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour l'entité BudgetItem.
 */
@Repository
public interface BudgetItemRepository extends JpaRepository<BudgetItem, Long> {
}
