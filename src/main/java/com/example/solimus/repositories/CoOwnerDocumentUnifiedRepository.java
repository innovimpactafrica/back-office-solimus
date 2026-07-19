package com.example.solimus.repositories;

import com.example.solimus.entities.CoOwnerDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoOwnerDocumentUnifiedRepository extends JpaRepository<CoOwnerDocument, Long> {

    // Fusionne 3 sources de documents pour UN copropriétaire précis :
    // documents manuels (coffre), documents d'AG, documents de charges exceptionnelles
    @Query(
        value =
                // Source 1 : documents manuels uploadés directement par le syndic pour ce copropriétaire
                "SELECT 'MANUAL' AS source_type, cod.id AS source_id, cod.title AS title, " +
                "       cod.file_name AS file_name, cod.file_url AS file_url, cod.file_size_kb AS file_size_kb, " +
                "       cod.category AS category, cod.created_at AS created_at " +
                "FROM co_owner_documents cod " +
                "WHERE cod.co_owner_id = :coOwnerId " +
                // Recherche optionnelle sur le titre du document manuel
                "AND (:search IS NULL OR LOWER(cod.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                // Filtre optionnel sur la catégorie, correspondance exacte
                "AND (:category IS NULL OR cod.category = :category) " +

                "UNION ALL " +

                // Source 2 : documents d'AG des réunions où ce copropriétaire est convoqué
                "SELECT 'MEETING' AS source_type, md.id AS source_id, " +
                // Si le titre existe, on le garde ; sinon on utilise le nom du fichier comme titre affiché
                "       COALESCE(md.title, md.file_name) AS title, " +
                "       md.file_name AS file_name, md.file_url AS file_url, md.file_size_kb AS file_size_kb, " +
                // Si le type existe, on le garde ; sinon on utilise "PV_AG" comme valeur de secours
                "       COALESCE(md.document_type, 'PV_AG') AS category, md.created_at AS created_at " +
                "FROM meeting_documents md " +
                // On rejoint la table de convocation pour ne garder que les documents des réunions du copropriétaire
                "JOIN meeting_participants mp ON mp.meeting_id = md.meeting_id " +
                "WHERE mp.user_id = :coOwnerId " +
                // Recherche élargie : sur le nom du fichier OU le titre
                "AND (:search IS NULL OR LOWER(md.file_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(md.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:category IS NULL OR COALESCE(md.document_type, 'PV_AG') = :category) " +

                "UNION ALL " +

                // Source 3 : documents de charges exceptionnelles concernant ce copropriétaire
                "SELECT 'EXCEPTIONAL_CALL' AS source_type, ecd.id AS source_id, ecd.file_name AS title, " +
                "       ecd.file_name AS file_name, ecd.file_url AS file_url, ecd.file_size_kb AS file_size_kb, " +
                // Cette source n'a pas de vrai type en base, on met "Charges" en dur
                "       'Charges' AS category, ecd.created_at AS created_at " +
                "FROM exceptional_call_documents ecd " +
                // On rejoint la table des items pour ne garder que les documents concernant ce copropriétaire
                "JOIN exceptional_call_items eci ON eci.exceptional_call_id = ecd.exceptional_call_id " +
                "WHERE eci.co_owner_id = :coOwnerId " +
                "AND (:search IS NULL OR LOWER(ecd.file_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:category IS NULL OR 'Charges' = :category) " +

                // Les 3 sources fusionnées sont triées ensemble, du plus récent au plus ancien
                "ORDER BY created_at DESC",

        // Requête séparée pour compter le nombre total de résultats (nécessaire pour la pagination),
        // avec exactement les mêmes filtres que la requête principale
        countQuery =
                "SELECT COUNT(*) FROM (" +
                "  SELECT cod.id FROM co_owner_documents cod " +
                "  WHERE cod.co_owner_id = :coOwnerId " +
                "  AND (:search IS NULL OR LOWER(cod.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "  AND (:category IS NULL OR cod.category = :category) " +
                "  UNION ALL " +
                "  SELECT md.id FROM meeting_documents md " +
                "  JOIN meeting_participants mp ON mp.meeting_id = md.meeting_id " +
                "  WHERE mp.user_id = :coOwnerId " +
                "  AND (:search IS NULL OR LOWER(md.file_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(md.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "  AND (:category IS NULL OR COALESCE(md.document_type, 'PV_AG') = :category) " +
                "  UNION ALL " +
                "  SELECT ecd.id FROM exceptional_call_documents ecd " +
                "  JOIN exceptional_call_items eci ON eci.exceptional_call_id = ecd.exceptional_call_id " +
                "  WHERE eci.co_owner_id = :coOwnerId " +
                "  AND (:search IS NULL OR LOWER(ecd.file_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "  AND (:category IS NULL OR 'Charges' = :category) " +
                ") AS combined",

        // Requête SQL brute (pas du JPQL) car UNION n'est pas supporté par Hibernate directement
        nativeQuery = true
    )
    // Renvoie une page de résultats bruts : chaque ligne est un tableau de colonnes, pas un objet Java
    Page<Object[]> searchCoOwnerDocumentsUnified(@Param("coOwnerId") Long coOwnerId,
                                                   @Param("search") String search,
                                                   @Param("category") String category,
                                                   Pageable pageable);
}
