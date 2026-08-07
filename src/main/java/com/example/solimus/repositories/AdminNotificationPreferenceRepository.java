package com.example.solimus.repositories;

import com.example.solimus.entities.AdminNotificationPreference;
import com.example.solimus.enums.AdminNotificationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminNotificationPreferenceRepository extends JpaRepository<AdminNotificationPreference, Long> {

    
    List<AdminNotificationPreference> findByAdminId(Long adminId);

    Optional<AdminNotificationPreference> findByAdminIdAndEventType(Long adminId, AdminNotificationEventType eventType);
}