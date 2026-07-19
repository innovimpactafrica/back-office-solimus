package com.example.solimus.repositories;

import com.example.solimus.entities.MeetingDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoOwnerDocumentRepository extends JpaRepository<MeetingDocument, Long> {

    // Fusionne 2 sources de documents différentes (AG + charges exceptionnelles) en une seule
    // liste triée par date, avec recherche, filtre et pagination gérés par la base de données.
    // Si un document AG n'a pas encore de document_type renseigné, on utilise "PV_AG" par défaut
    // (valeur exacte de l'enum MeetingDocumentType, pas une valeur inventée).
    @Query(
            value =
                    // Première partie : documents d'AG liés aux réunions où ce copropriétaire est convoqué
                    "SELECT 'MEETING' AS source_type, md.id AS source_id, md.file_name AS file_name, " +
                            "       md.file_url AS file_url, md.file_size_kb AS file_size_kb, " +
                            // Si le type existe, on le garde ; si document_type a une valeur, prends-le ; sinon, on prend 'PV_AG' comme nom de type de secours
                            "       COALESCE(md.document_type, 'PV_AG') AS category, md.created_at AS created_at " +
                            "FROM meeting_documents md " +
                            // On rejoint la table de convocation pour ne garder que les documents des réunions du copropriétaire
                            "JOIN meeting_participants mp ON mp.meeting_id = md.meeting_id " +
                            "WHERE mp.user_id = :userId " +
                            // Recherche optionnelle sur le nom du fichier, insensible à la casse
                            "AND (:search IS NULL OR LOWER(md.file_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                            // Filtre optionnel sur la catégorie, correspondance exacte (même valeur de secours appliquée ici)
                            "AND (:category IS NULL OR COALESCE(md.document_type, 'PV_AG') = :category) " +

                            "UNION ALL " +

                            // Deuxième partie : documents liés aux appels de charges exceptionnelles de ce copropriétaire
                            "SELECT 'EXCEPTIONAL_CALL' AS source_type, ecd.id AS source_id, ecd.file_name AS file_name, " +
                            "       ecd.file_url AS file_url, ecd.file_size_kb AS file_size_kb, " +
                            // Cette source n'a pas de vrai type en base, on met "Charges" en dur
                            "       'Charges' AS category, ecd.created_at AS created_at " +
                            "FROM exceptional_call_documents ecd " +
                            // On rejoint la table des items pour ne garder que les documents des appels concernant ce copropriétaire
                            "JOIN exceptional_call_items eci ON eci.exceptional_call_id = ecd.exceptional_call_id " +
                            "WHERE eci.co_owner_id = :userId " +
                            // Même recherche appliquée à cette source
                            "AND (:search IS NULL OR LOWER(ecd.file_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                            // Même filtre appliqué à cette source
                            "AND (:category IS NULL OR 'Charges' = :category) " +

                            // Les 2 sources fusionnées sont triées ensemble, du plus récent au plus ancien
                            "ORDER BY created_at DESC",

            // Requête séparée pour compter le nombre total de résultats (nécessaire pour la pagination),
            // avec exactement les mêmes filtres que la requête principale
            countQuery =
                    "SELECT COUNT(*) FROM (" +
                            "  SELECT md.id FROM meeting_documents md " +
                            "  JOIN meeting_participants mp ON mp.meeting_id = md.meeting_id " +
                            "  WHERE mp.user_id = :userId " +
                            "  AND (:search IS NULL OR LOWER(md.file_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                            "  AND (:category IS NULL OR COALESCE(md.document_type, 'PV_AG') = :category) " +
                            "  UNION ALL " +
                            "  SELECT ecd.id FROM exceptional_call_documents ecd " +
                            "  JOIN exceptional_call_items eci ON eci.exceptional_call_id = ecd.exceptional_call_id " +
                            "  WHERE eci.co_owner_id = :userId " +
                            "  AND (:search IS NULL OR LOWER(ecd.file_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                            "  AND (:category IS NULL OR 'Charges' = :category) " +
                            ") AS combined",

            // Requête SQL brute (pas du JPQL) car UNION n'est pas supporté par Hibernate directement
            nativeQuery = true
    )
    // Renvoie une page de résultats bruts : chaque ligne est un tableau de colonnes, pas un objet Java
    Page<Object[]> searchCoOwnerDocuments(@Param("userId") Long userId,
                                          @Param("search") String search,
                                          @Param("category") String category,
                                          Pageable pageable);
}