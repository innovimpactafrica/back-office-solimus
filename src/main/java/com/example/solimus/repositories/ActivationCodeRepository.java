package com.example.solimus.repositories;

import com.example.solimus.entities.User;
import com.example.solimus.entities.ActivationCode;
import com.example.solimus.enums.CodeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActivationCodeRepository extends JpaRepository<ActivationCode, Long> {
    Optional<ActivationCode> findByCodeAndUser(String code, User user);
    Optional<ActivationCode> findByCodeAndTypeAndUsedFalse(String code, CodeType type);
    Optional<ActivationCode> findByUserAndType(User user, CodeType type);
    Optional<ActivationCode> findTopByUserAndTypeOrderByCreatedAtDesc(User user, CodeType type);
    void deleteByUserAndType(User user, CodeType type);
    void deleteByUser(User user);
    void deleteByExpiryDateBefore(java.time.LocalDateTime expiryDate);
}
