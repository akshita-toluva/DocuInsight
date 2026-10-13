package com.docuinsight.docuinsight.model;

import java.time.LocalDateTime;

public record FileSummaryResponse(
        Long id,
        String fileName,
        String fileType,
        Long fileSize,
        LocalDateTime uploadedAt,
        boolean extracted
) {
    public static FileSummaryResponse from(UploadedFile file) {
        return new FileSummaryResponse(
                file.getId(),
                file.getFileName(),
                file.getFileType(),
                file.getFileSize(),
                file.getUploadedAt(),
                file.getExtractedText() != null && !file.getExtractedText().isBlank()
        );
    }
}