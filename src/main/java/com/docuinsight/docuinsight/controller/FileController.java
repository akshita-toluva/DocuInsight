package com.docuinsight.docuinsight.controller;

import com.docuinsight.docuinsight.model.ExtractionResponse;
import com.docuinsight.docuinsight.model.FileSummaryResponse;
import com.docuinsight.docuinsight.model.FileUploadResponse;
import com.docuinsight.docuinsight.service.FileService;
import com.docuinsight.docuinsight.service.TextExtractionService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;
    private final TextExtractionService textExtractionService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file")MultipartFile file,
            @AuthenticationPrincipal String email) throws IOException, java.io.IOException {
        FileUploadResponse response=fileService.uploadFile(file,email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-files")
    public ResponseEntity<List<FileSummaryResponse>> getMyFiles(
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(fileService.getUserFiles(email));
    }

    @PostMapping("/{fileId}/extract")
    public ResponseEntity<ExtractionResponse> extractText(
            @PathVariable Long fileId,
            @AuthenticationPrincipal String email) throws Exception {
        ExtractionResponse response= null;
        try {
            response = textExtractionService.extractText(fileId,email);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok(response);
    }
}
