package com.example.solimus.repositories;

import com.example.solimus.entities.Signalement;
import com.example.solimus.entities.User;
import com.example.solimus.enums.SignalementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignalementRepository extends JpaRepository<Signalement, Long> {

    List<Signalement> findByDeclaredById(Long declaredById);

    List<Signalement> findByResidenceId(Long residenceId);

    long countByDeclaredBy(User declaredBy, Long residenceId);

    long countByDeclaredByAndStatus(User declaredBy, SignalementStatus status, Long residenceId);

    @Query("SELECT s FROM Signalement s WHERE s.declaredBy = :user " +
           "AND (:residenceId IS NULL OR s.residence.id = :residenceId) " +
           "AND (:search IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<Signalement> findByDeclaredByWithFiltersAndResidence(
            @Param("user") User user,
            @Param("search") String search,
            @Param("status") SignalementStatus status,
            @Param("residenceId") Long residenceId,
            Pageable pageable
    );

    @Query("SELECT s FROM Signalement s WHERE s.residence.syndic = :syndic " +
           "AND (:search IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:residenceId IS NULL OR s.residence.id = :residenceId)")
    Page<Signalement> findBySyndicWithFiltersAndResidence(
            @Param("syndic") User syndic,
            @Param("search") String search,
            @Param("status") SignalementStatus status,
            @Param("residenceId") Long residenceId,
            Pageable pageable
    );

    @Query("SELECT COUNT(s) FROM Signalement s WHERE s.residence.syndic = :syndic " +
           "AND (:residenceId IS NULL OR s.residence.id = :residenceId)")
    long countBySyndic(@Param("syndic") User syndic, @Param("residenceId") Long residenceId);

    @Query("SELECT COUNT(s) FROM Signalement s WHERE s.residence.syndic = :syndic " +
           "AND s.status = :status " +
           "AND (:residenceId IS NULL OR s.residence.id = :residenceId)")
    long countBySyndicAndStatus(
            @Param("syndic") User syndic,
            @Param("status") SignalementStatus status,
            @Param("residenceId") Long residenceId
    );
}
