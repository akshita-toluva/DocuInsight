package com.docuinsight.docuinsight.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileUploadResponse {
    private Long fileId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String message;
}
