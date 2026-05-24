package com.docuinsight.docuinsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="uploaded_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name="user_id",nullable = false)
    @JsonIgnoreProperties // Drops these fields from the JSON serialization pipeline
    private User user;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileType;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private long fileSize;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(name="uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist //Sets uploadedAt automatically before saving
    public void prePersist()
    {
        this.uploadedAt=LocalDateTime.now();
    }
}
