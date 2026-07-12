package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "exceptional_call_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionalCallDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileUrl;

    private Long fileSizeKb;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exceptional_call_id", nullable = false)
    private ExceptionalCall exceptionalCall;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
