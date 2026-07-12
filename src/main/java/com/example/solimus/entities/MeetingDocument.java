package com.example.solimus.entities;

import com.example.solimus.enums.MeetingDocumentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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
    private MeetingDocumentType documentType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
