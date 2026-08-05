package com.example.solimus.repositories;

import com.example.solimus.entities.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Table à un seul enregistrement
@Repository
public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {
}