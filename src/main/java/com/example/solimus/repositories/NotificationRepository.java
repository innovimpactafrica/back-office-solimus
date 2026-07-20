package com.example.solimus.repositories;

import com.example.solimus.entities.Notification;
import com.example.solimus.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Notifications d'un utilisateur, paginées, triées par date décroissante
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // Compter les notifications non lues d'un utilisateur
    long countByUserAndReadFalse(User user);

    // Dans NotificationRepository
    // Marque toutes les notifications non lues d'un utilisateur comme lues, en une seule requête
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user = :user AND n.read = false")
    void markAllAsReadByUser(@Param("user") User user);
}
