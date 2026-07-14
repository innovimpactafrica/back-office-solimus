package com.example.solimus.repositories;

import com.example.solimus.entities.QuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuoteItemRepository extends JpaRepository<QuoteItem, Long> {
    void deleteByQuoteId(Long quoteId);
}
