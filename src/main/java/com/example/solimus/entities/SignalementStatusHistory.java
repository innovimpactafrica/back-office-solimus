package com.example.solimus.entities;

import com.example.solimus.enums.SignalementStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "signalement_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalementStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "signalement_id", nullable = false)
    private Signalement signalement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalementStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}