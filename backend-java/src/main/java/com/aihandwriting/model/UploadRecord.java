package com.aihandwriting.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String extractedText;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String structuredJson;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
