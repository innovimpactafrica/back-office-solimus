package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "charge_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    private String originalFileName;

    @Column(nullable = false)
    private String fileUrl;

    private Long fileSizeKb;

    private String contentType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
