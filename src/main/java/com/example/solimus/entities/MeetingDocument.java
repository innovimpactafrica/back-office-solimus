package com.example.solimus.entities;

import com.example.solimus.enums.MeetingDocumentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileUrl;

    private Long fileSizeKb;

    @Enumerated(EnumType.STRING)
    private MeetingDocumentType documentType; // nullable

    private String title;           // nullable
    private String description;     // nullable
    private LocalDate documentDate; // nullable

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy; // nullable, qui a ajouté ce document

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
