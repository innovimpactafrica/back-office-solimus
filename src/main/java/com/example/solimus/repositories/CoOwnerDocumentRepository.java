package com.example.solimus.repositories;

import com.example.solimus.entities.CoOwnerDocument;
import com.example.solimus.enums.CoOwnerDocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoOwnerDocumentRepository extends JpaRepository<CoOwnerDocument, Long> {

    // Trouver les documents d'un copropriétaire par catégorie
    List<CoOwnerDocument> findByCoOwnerIdAndCategory(Long coOwnerId, CoOwnerDocumentCategory category);

    // Trouver tous les documents d'un copropriétaire
    List<CoOwnerDocument> findByCoOwnerId(Long coOwnerId);
}
